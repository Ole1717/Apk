package com.instaweb.browser

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView

    private val prefs by lazy {
        getSharedPreferences("instaweb", Context.MODE_PRIVATE)
    }

    private var privateMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)

        window.statusBarColor = Color.rgb(5, 5, 5)
        window.navigationBarColor = Color.BLACK

        web = WebView(this)

        web.setBackgroundColor(Color.WHITE)

        setupWebView()

        setContentView(web)

        setupBackButton()

        web.loadUrl("file:///android_asset/index.html")
    }

    private fun setupWebView() {

        val settings = web.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true

        settings.loadsImagesAutomatically = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(false)

        settings.mediaPlaybackRequiresUserGesture = false

        settings.cacheMode = WebSettings.LOAD_DEFAULT

        settings.userAgentString =
            settings.userAgentString + " Instaweb/1.0"

        CookieManager.getInstance().setAcceptCookie(true)

        CookieManager.getInstance()
            .setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {

                return false
            }

            override fun onPageStarted(
                view: WebView,
                url: String,
                favicon: android.graphics.Bitmap?
            ) {

                super.onPageStarted(view, url, favicon)

                sendUrlToInterface(url)
            }

            override fun onPageFinished(
                view: WebView,
                url: String
            ) {

                super.onPageFinished(view, url)

                if (!privateMode && url.isNotBlank()) {
                    addHistory(
                        url,
                        view.title ?: url
                    )
                }

                sendUrlToInterface(url)
            }
        }

        web.webChromeClient = WebChromeClient()

        web.setDownloadListener(
            DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->

                enqueueDownload(
                    url,
                    userAgent,
                    contentDisposition,
                    mimeType
                )
            }
        )

        web.addJavascriptInterface(
            Bridge(),
            "InstawebNative"
        )
    }

    private fun sendUrlToInterface(url: String) {

        val safeUrl =
            JSONObject.quote(url)

        web.evaluateJavascript(
            """
            if (window.instawebSetUrl) {
                window.instawebSetUrl($safeUrl);
            }
            """.trimIndent(),
            null
        )
    }

    private fun setupBackButton() {

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {

                    if (web.canGoBack()) {

                        web.goBack()

                    } else {

                        finish()
                    }
                }
            }
        )
    }

    private fun normalizeUrl(input: String): String {

        val value =
            input.trim()

        if (value.isEmpty()) {
            return "https://www.google.com"
        }

        if (
            value.startsWith("http://") ||
            value.startsWith("https://")
        ) {
            return value
        }

        if (
            value.contains(" ") ||
            !value.contains(".")
        ) {

            return "https://www.google.com/search?q=" +
                    Uri.encode(value)
        }

        return "https://$value"
    }

    private fun navigate(input: String) {

        val url =
            normalizeUrl(input)

        runOnUiThread {

            web.loadUrl(url)
        }
    }

    private fun enqueueDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {

        try {

            val request =
                DownloadManager.Request(
                    Uri.parse(url)
                )

            request.setMimeType(
                mimeType ?: "application/octet-stream"
            )

            request.addRequestHeader(
                "User-Agent",
                userAgent ?: web.settings.userAgentString
            )

            val cookie =
                CookieManager
                    .getInstance()
                    .getCookie(url)

            if (!cookie.isNullOrBlank()) {

                request.addRequestHeader(
                    "Cookie",
                    cookie
                )
            }

            request.setNotificationVisibility(
                DownloadManager.Request
                    .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )

            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                android.webkit.URLUtil.guessFileName(
                    url,
                    contentDisposition,
                    mimeType
                )
            )

            val manager =
                getSystemService(
                    DOWNLOAD_SERVICE
                ) as DownloadManager

            manager.enqueue(request)

        } catch (_: Exception) {
        }
    }

    private fun addHistory(
        url: String,
        title: String
    ) {

        if (url.startsWith("file://")) {
            return
        }

        try {

            val old =
                JSONArray(
                    prefs.getString(
                        "history",
                        "[]"
                    )
                )

            val result =
                JSONArray()

            result.put(
                JSONObject().apply {

                    put("url", url)
                    put("title", title)
                    put(
                        "time",
                        System.currentTimeMillis()
                    )
                }
            )

            for (
                i in 0 until minOf(
                    old.length(),
                    499
                )
            ) {

                val item =
                    old.getJSONObject(i)

                if (
                    item.optString("url") != url
                ) {

                    result.put(item)
                }
            }

            prefs.edit()
                .putString(
                    "history",
                    result.toString()
                )
                .apply()

        } catch (_: Exception) {
        }
    }

    inner class Bridge {

        @android.webkit.JavascriptInterface
        fun navigate(url: String) {

            navigate(url)
        }

        @android.webkit.JavascriptInterface
        fun back() {

            runOnUiThread {

                if (web.canGoBack()) {
                    web.goBack()
                }
            }
        }

        @android.webkit.JavascriptInterface
        fun forward() {

            runOnUiThread {

                if (web.canGoForward()) {
                    web.goForward()
                }
            }
        }

        @android.webkit.JavascriptInterface
        fun refresh() {

            runOnUiThread {
                web.reload()
            }
        }

        @android.webkit.JavascriptInterface
        fun getHistory(): String {

            return prefs.getString(
                "history",
                "[]"
            ) ?: "[]"
        }

        @android.webkit.JavascriptInterface
        fun clearHistory() {

            prefs.edit()
                .remove("history")
                .apply()
        }

        @android.webkit.JavascriptInterface
        fun getBookmarks(): String {

            return prefs.getString(
                "bookmarks",
                "[]"
            ) ?: "[]"
        }

        @android.webkit.JavascriptInterface
        fun saveBookmarks(
            value: String
        ) {

            prefs.edit()
                .putString(
                    "bookmarks",
                    value
                )
                .apply()
        }

        @android.webkit.JavascriptInterface
        fun setPrivateMode(
            value: Boolean
        ) {

            privateMode = value
        }

        @android.webkit.JavascriptInterface
        fun isPrivateMode(): Boolean {

            return privateMode
        }

        @android.webkit.JavascriptInterface
        fun share(text: String) {

            runOnUiThread {

                val intent =
                    Intent(
                        Intent.ACTION_SEND
                    )

                intent.type =
                    "text/plain"

                intent.putExtra(
                    Intent.EXTRA_TEXT,
                    text
                )

                startActivity(
                    Intent.createChooser(
                        intent,
                        "Поделиться"
                    )
                )
            }
        }
    }

    override fun onDestroy() {

        web.stopLoading()

        web.removeJavascriptInterface(
            "InstawebNative"
        )

        web.destroy()

        super.onDestroy()
    }
}
