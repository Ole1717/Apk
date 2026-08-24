package com.instaweb.browser

import android.app.DownloadManager
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private val prefs by lazy { getSharedPreferences("instaweb", Context.MODE_PRIVATE) }
    private var privateMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = Color.rgb(5,5,5)
        window.navigationBarColor = Color.BLACK

        web = WebView(this)
        web.setBackgroundColor(Color.rgb(5,5,5))
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.databaseEnabled = true
        web.settings.loadsImagesAutomatically = true
        web.settings.allowFileAccess = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.userAgentString = web.settings.userAgentString + " Instaweb/1.0"

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return false
            }
            override fun onPageFinished(view: WebView, url: String) {
                if (!privateMode && url.isNotBlank()) {
                    addHistory(url, view.title ?: url)
                }
                web.evaluateJavascript("window.instawebNativeUrl && window.instawebNativeUrl(${JSONObject.quote(url)});", null)
            }
        }

        web.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            enqueueDownload(url, userAgent, contentDisposition, mimeType)
        }

        web.addJavascriptInterface(Bridge(), "InstawebNative")
        setContentView(web)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        web.loadUrl("file:///android_asset/index.html")
    }

    private fun enqueueDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        try {
            val req = DownloadManager.Request(Uri.parse(url))
            req.setMimeType(mimeType ?: "application/octet-stream")
            req.addRequestHeader("User-Agent", userAgent ?: web.settings.userAgentString)
            val cookie = CookieManager.getInstance().getCookie(url)
            if (!cookie.isNullOrBlank()) req.addRequestHeader("Cookie", cookie)
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimeType))
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        } catch (_: Exception) {}
    }

    private fun addHistory(url: String, title: String) {
        val arr = JSONArray(prefs.getString("history", "[]"))
        val out = JSONArray()
        out.put(JSONObject().apply {
            put("url", url); put("title", title); put("time", System.currentTimeMillis())
        })
        for (i in 0 until minOf(arr.length(), 499)) {
            val o = arr.getJSONObject(i)
            if (o.optString("url") != url) out.put(o)
        }
        prefs.edit().putString("history", out.toString()).apply()
    }

    inner class Bridge {
        @android.webkit.JavascriptInterface
        fun navigate(url: String) {
            runOnUiThread { web.loadUrl(url) }
        }
        @android.webkit.JavascriptInterface
        fun back() { runOnUiThread { if (web.canGoBack()) web.goBack() } }
        @android.webkit.JavascriptInterface
        fun forward() { runOnUiThread { if (web.canGoForward()) web.goForward() } }
        @android.webkit.JavascriptInterface
        fun refresh() { runOnUiThread { web.reload() } }
        @android.webkit.JavascriptInterface
        fun getHistory(): String = prefs.getString("history", "[]") ?: "[]"
        @android.webkit.JavascriptInterface
        fun clearHistory() { prefs.edit().remove("history").apply() }
        @android.webkit.JavascriptInterface
        fun getBookmarks(): String = prefs.getString("bookmarks", "[]") ?: "[]"
        @android.webkit.JavascriptInterface
        fun saveBookmarks(value: String) { prefs.edit().putString("bookmarks", value).apply() }
        @android.webkit.JavascriptInterface
        fun setPrivateMode(value: Boolean) { privateMode = value }
        @android.webkit.JavascriptInterface
        fun isPrivateMode(): Boolean = privateMode
        @android.webkit.JavascriptInterface
        fun share(text: String) {
            val i = android.content.Intent(android.content.Intent.ACTION_SEND)
            i.type = "text/plain"; i.putExtra(android.content.Intent.EXTRA_TEXT, text)
            startActivity(android.content.Intent.createChooser(i, "Поделиться"))
        }
    }
}
