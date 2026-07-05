package org.symera.source.network

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class JavaScriptEngine(context: Context, private val allowNetworkLoads: Boolean = true) {
    private val appContext: Context = context.applicationContext ?: context
    private var webView: WebView? = null
    private var destroyed = false

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context): WebView {
        val wv = WebView(context)
        wv.settings.javaScriptEnabled = true
        wv.settings.allowContentAccess = false
        wv.settings.allowFileAccess = false
        wv.settings.blockNetworkLoads = !allowNetworkLoads
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            wv.settings.mixedContentMode =
                android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {}
        }
        return wv
    }

    private fun getWebView(): WebView {
        return webView ?: createWebView(appContext).also { webView = it }
    }

    suspend fun loadUrl(url: String) {
        withContext(Dispatchers.Main) {
            check(!destroyed) { "JavaScriptEngine has been destroyed" }
            getWebView().loadUrl(url)
        }
    }

    suspend fun loadHtml(html: String, baseUrl: String? = null) {
        withContext(Dispatchers.Main) {
            check(!destroyed) { "JavaScriptEngine has been destroyed" }
            getWebView().loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
        }
    }

    suspend fun executeScript(script: String, timeoutMs: Long = 5000): String? =
        withContext(Dispatchers.Main) {
            check(!destroyed) { "JavaScriptEngine has been destroyed" }
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    getWebView().evaluateJavascript(script) { value ->
                        if (continuation.isActive) continuation.resume(value)
                    }
                }
            }
        }

    fun destroy() {
        if (!destroyed) {
            destroyed = true
            val view = webView
            webView = null
            view?.post {
                view.removeAllViews()
                view.destroy()
            }
        }
    }
}
