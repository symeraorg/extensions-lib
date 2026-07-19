package org.symera.source.network

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Host-provided WebView utility for sources that require a JavaScript-rendered page. */
class JavaScriptEngine(
    context: Context,
    private val allowNetworkLoads: Boolean = true,
    private val navigationPolicy: (String) -> Boolean = { true },
) : Closeable {
    private val appContext = context.applicationContext
    private var webView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingLoad: PendingLoad? = null
    @Volatile private var destroyed = false

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView = WebView(appContext).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowContentAccess = false
        settings.blockNetworkLoads = !allowNetworkLoads
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (navigationPolicy(url)) return false
                failPending(view, JavaScriptNavigationException(url))
                return true
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (!navigationPolicy(url)) {
                    view.stopLoading()
                    failPending(view, JavaScriptNavigationException(url))
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                val pending = pendingLoad?.takeIf { it.view === view } ?: return
                if (pending.continuation.isActive) pending.continuation.resume(url)
                pendingLoad = null
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: android.webkit.WebResourceError,
            ) {
                if (request.isForMainFrame) {
                    val pending = pendingLoad?.takeIf { it.view === view } ?: return
                    if (pending.continuation.isActive) pending.continuation.resumeWithException(
                        JavaScriptPageException(error.errorCode, error.description.toString()),
                    )
                    pendingLoad = null
                }
            }
        }
    }

    private fun requireWebView(): WebView {
        check(!destroyed) { "JavaScriptEngine has been closed" }
        return webView ?: createWebView().also { webView = it }
    }

    suspend fun loadUrl(url: String, timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS): String =
        load(timeoutMillis) {
            if (!navigationPolicy(url)) throw JavaScriptNavigationException(url)
            it.loadUrl(url)
        }

    suspend fun loadHtml(
        html: String,
        baseUrl: String? = null,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    ): String = load(timeoutMillis) {
        it.loadDataWithBaseURL(baseUrl, html, "text/html", Charsets.UTF_8.name(), null)
    }

    suspend fun executeScript(script: String, timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS): String? =
        withContext(Dispatchers.Main.immediate) {
            withTimeout(timeoutMillis) {
                suspendCancellableCoroutine { continuation ->
                    requireWebView().evaluateJavascript(script) { value ->
                        if (continuation.isActive) continuation.resume(value?.takeUnless { it == "null" })
                    }
                }
            }
        }

    private suspend fun load(timeoutMillis: Long, start: (WebView) -> Unit): String =
        withContext(Dispatchers.Main.immediate) {
            withTimeout(timeoutMillis) {
                suspendCancellableCoroutine { continuation ->
                    check(pendingLoad == null) { "Another page load is already active" }
                    val view = requireWebView()
                    pendingLoad = PendingLoad(view, continuation)
                    continuation.invokeOnCancellation {
                        mainHandler.post {
                            if (pendingLoad?.continuation === continuation) {
                                pendingLoad = null
                                resetWebView(view)
                            }
                        }
                    }
                    try {
                        start(view)
                    } catch (exception: Exception) {
                        pendingLoad = null
                        continuation.resumeWithException(exception)
                    }
                }
            }
        }

    override fun close() {
        if (destroyed) return
        destroyed = true
        mainHandler.post {
            pendingLoad?.continuation?.cancel()
            pendingLoad = null
            webView?.let(::resetWebView)
        }
    }

    private fun failPending(view: WebView, exception: Exception) {
        val pending = pendingLoad?.takeIf { it.view === view } ?: return
        if (pending.continuation.isActive) pending.continuation.resumeWithException(exception)
        pendingLoad = null
    }

    private fun resetWebView(view: WebView) {
        if (webView === view) webView = null
        view.stopLoading()
        view.removeAllViews()
        view.destroy()
    }

    private data class PendingLoad(
        val view: WebView,
        val continuation: CancellableContinuation<String>,
    )

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000L
    }
}

fun interface JavaScriptEngineFactory {
    fun create(
        allowNetworkLoads: Boolean,
        navigationPolicy: (String) -> Boolean,
    ): JavaScriptEngine
}

class JavaScriptPageException(
    val errorCode: Int,
    message: String,
) : Exception("WebView page load failed ($errorCode): $message")

class JavaScriptNavigationException(url: String) : Exception("WebView navigation was blocked: $url")
