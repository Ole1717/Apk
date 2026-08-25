package com.instaweb.browser

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var root: FrameLayout

    // WebView с интерфейсом Instaweb
    private lateinit var uiWeb: WebView

    // WebView для настоящих сайтов
    private lateinit var browserWeb: WebView

    private val prefs by lazy {
        getSharedPreferences("instaweb", Context.MODE_PRIVATE)
    }

    private var privateMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)

        window.statusBarColor = Color.rgb(5, 5, 5)
        window.navigationBarColor = Color.BLACK

        root = FrameLayout(this)

        // =========================
        // UI WEBVIEW
        // =========================

        uiWeb = WebView(this)

        uiWeb.setBackgroundColor(Color.rgb(8, 8, 10))

        setupUiWebView()

        // =========================
        // BROWSER WEBVIEW
        // =========================

        browserWeb = WebView(this)

        browserWeb.setBackgroundColor(Color.WHITE)

        setupBrowserWebView()

        root.addView(
            uiWeb,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        root.addView(
            browserWeb,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // Начинаем с интерфейса
        browserWeb.visibility = View.GONE
        uiWeb.visibility = View.VISIBLE

        setContentView(root)

        setupBackButton()

        uiWeb.loadUrl(
            "file:///android_asset/index.html"
        )
    }

    // =========================================================
    // UI WEBVIEW
    // =========================================================

    private fun setupUiWebView() {

        val settings = uiWeb.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true

        settings.loadsImagesAutomatically = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)

        settings.cacheMode = WebSettings.LOAD_DEFAULT

        settings.userAgentString =
            settings.userAgentString + " Instaweb/1.0"

        uiWeb.webViewClient = WebViewClient()

        uiWeb.webChromeClient = WebChromeClient()

        uiWeb.addJavascriptInterface(
            Bridge(),
            "InstawebNative"
        )
    }

    // =========================================================
    // REAL BROWSER WEBVIEW
    // =========================================================

    private fun setupBrowserWebView() {

        val settings = browserWeb.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true

        settings.loadsImagesAutomatically = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        settings.javaScriptCanOpenWindowsAutomatically = true

        // ВАЖНО:
        // не создаём дополнительные окна WebView.
        settings.setSupportMultipleWindows(false)

        settings.mediaPlaybackRequiresUserGesture = false

        settings.cacheMode = WebSettings.LOAD_DEFAULT

        settings.userAgentString =
            settings.userAgentString + " Instaweb/1.0"

        CookieManager.getInstance()
            .setAcceptCookie(true)

        CookieManager.getInstance()
            .setAcceptThirdPartyCookies(
                browserWeb,
                true
            )

        browserWeb.webViewClient =
            object : WebViewClient() {

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {

                    // Оставляем все обычные ссылки
                    // внутри нашего WebView.
                    return false
                }

                override fun onPageStarted(
                    view: WebView,
                    url: String,
                    favicon: android.graphics.Bitmap?
                ) {

                    super.onPageStarted(
                        view,
                        url,
                        favicon
                    )

                    sendUrlToInterface(url)
                }

                override fun onPageFinished(
                    view: WebView,
                    url: String
                ) {

                    super.onPageFinished(
                        view,
                        url
                    )

                    if (
                        !privateMode &&
                        url.isNotBlank() &&
                        !url.startsWith("file://")
                    ) {

                        addHistory(
                            url,
                            view.title ?: url
                        )
                    }

                    sendUrlToInterface(url)
                }
            }

        browserWeb.webChromeClient =
            WebChromeClient()

        browserWeb.setDownloadListener(
            DownloadListener { url,
                userAgent,
                contentDisposition,
                mimeType,
                _ ->

                enqueueDownload(
                    url,
                    userAgent,
                    contentDisposition,
                    mimeType
                )
            }
        )
    }

    // =========================================================
    // SHOW / HIDE BROWSER
    // =========================================================

    private fun showBrowser(url: String) {

        runOnUiThread {

            uiWeb.visibility = View.GONE
            browserWeb.visibility = View.VISIBLE

            browserWeb.loadUrl(url)
        }
    }

    private fun showHome() {

        runOnUiThread {

            browserWeb.stopLoading()

            browserWeb.visibility = View.GONE
            uiWeb.visibility = View.VISIBLE

            // Возвращаем интерфейс на главный экран.
            uiWeb.evaluateJavascript(
                """
                if (typeof home === 'function') {
                    home();
                }
                """.trimIndent(),
                null
            )
        }
    }

    // =========================================================
    // URL -> UI
    // =========================================================

    private fun sendUrlToInterface(url: String) {

        val safeUrl =
            JSONObject.quote(url)

        uiWeb.evaluateJavascript(
            """
            if (window.instawebNativeUrl) {
                window.instawebNativeUrl($safeUrl);
            }
            """.trimIndent(),
            null
        )
    }

    // =========================================================
    // SYSTEM BACK
    // =========================================================

    private fun setupBackButton() {

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {

                    if (
                        browserWeb.visibility ==
                        View.VISIBLE
                    ) {

                        if (
                            browserWeb.canGoBack()
                        ) {

                            browserWeb.goBack()

                        } else {

                            showHome()
                        }

                    } else {

                        // Мы уже на главном экране.
                        finish()
                    }
                }
            }
        )
    }

    // =========================================================
    // URL NORMALIZATION
    // =========================================================

    private fun normalizeUrl(
        input: String
    ): String {

        val value =
            input.trim()

        if (value.isEmpty()) {

            return "https://www.google.com"
        }

        if (
            value.startsWith(
                "http://",
                ignoreCase = true
            ) ||
            value.startsWith(
                "https://",
                ignoreCase = true
            )
        ) {

            return value
        }

        // Явный IP
        if (
            value.matches(
                Regex(
                    """\d{1,3}(\.\d{1,3}){3}(:\d+)?"""
                )
            )
        ) {

            return "http://$value"
        }

        // Домен
        if (
            value.matches(
                Regex(
                    """[A-Za-z0-9][A-Za-z0-9.-]*\.[A-Za-z]{2,}.*"""
                )
            )
        ) {

            return "https://$value"
        }

        // Всё остальное — поиск Google.
        return "https://www.google.com/search?q=" +
                Uri.encode(value)
    }

    // =========================================================
    // NAVIGATION
    // =========================================================

    private fun navigate(
        input: String
    ) {

        val url =
            normalizeUrl(input)

        showBrowser(url)
    }

    // =========================================================
    // DOWNLOADS
    // =========================================================

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
                mimeType
                    ?: "application/octet-stream"
            )

            request.addRequestHeader(
                "User-Agent",
                userAgent
                    ?: browserWeb
                        .settings
                        .userAgentString
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
                DownloadManager
                    .Request
                    .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )

            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                android.webkit.URLUtil
                    .guessFileName(
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
            // Не даём загрузке уронить приложение.
        }
    }

    // =========================================================
    // HISTORY
    // =========================================================

    private fun addHistory(
        url: String,
        title: String
    ) {

        if (
            url.startsWith("file://")
        ) {
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

                    put(
                        "url",
                        url
                    )

                    put(
                        "title",
                        title
                    )

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
                    item.optString(
                        "url"
                    ) != url
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

    // =========================================================
    // JAVASCRIPT BRIDGE
    // =========================================================

    inner class Bridge {

        @JavascriptInterface
        fun navigate(
            url: String
        ) {

            this@MainActivity.navigate(url)
        }

        @JavascriptInterface
        fun home() {

            this@MainActivity.showHome()
        }

        @JavascriptInterface
        fun back() {

            runOnUiThread {

                if (
                    browserWeb.visibility ==
                    View.VISIBLE
                ) {

                    if (
                        browserWeb.canGoBack()
                    ) {

                        browserWeb.goBack()

                    } else {

                        showHome()
                    }
                }
            }
        }

        @JavascriptInterface
        fun forward() {

            runOnUiThread {

                if (
                    browserWeb.visibility ==
                    View.VISIBLE &&
                    browserWeb.canGoForward()
                ) {

                    browserWeb.goForward()
                }
            }
        }

        @JavascriptInterface
        fun refresh() {

            runOnUiThread {

                if (
                    browserWeb.visibility ==
                    View.VISIBLE
                ) {

                    browserWeb.reload()
                }
            }
        }

        // =====================================================
        // HISTORY
        // =====================================================

        @JavascriptInterface
        fun getHistory(): String {

            return prefs.getString(
                "history",
                "[]"
            ) ?: "[]"
        }

        @JavascriptInterface
        fun clearHistory() {

            prefs.edit()
                .remove("history")
                .apply()
        }

        // =====================================================
        // BOOKMARKS
        // =====================================================

        @JavascriptInterface
        fun getBookmarks(): String {

            return prefs.getString(
                "bookmarks",
                "[]"
            ) ?: "[]"
        }

        @JavascriptInterface
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

        // =====================================================
        // PRIVATE MODE
        // =====================================================

        @JavascriptInterface
        fun setPrivateMode(
            value: Boolean
        ) {

            privateMode = value
        }

        @JavascriptInterface
        fun isPrivateMode(): Boolean {

            return privateMode
        }

        // =====================================================
        // SHARE
        // =====================================================

        @JavascriptInterface
        fun share(
            text: String
        ) {

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

    // =========================================================
    // DESTROY
    // =========================================================

    override fun onDestroy() {

        try {

            uiWeb.stopLoading()

            uiWeb.removeJavascriptInterface(
                "InstawebNative"
            )

            uiWeb.destroy()

        } catch (_: Exception) {
        }

        try {

            browserWeb.stopLoading()

            browserWeb.destroy()

        } catch (_: Exception) {
        }

        super.onDestroy()
    }
}
