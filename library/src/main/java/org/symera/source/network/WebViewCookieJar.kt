package org.symera.source.network

import android.webkit.CookieManager
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** Shares the Android WebView cookie store with OkHttp without exposing cookie values to sources. */
class WebViewCookieJar(
    private val cookieManager: CookieManager = CookieManager.getInstance(),
) : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cookie -> cookieManager.setCookie(url.toString(), cookie.toString()) }
        cookieManager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = cookieManager.getCookie(url.toString())
        ?.split(';')
        ?.mapNotNull { value -> Cookie.parse(url, value.trim()) }
        .orEmpty()

    suspend fun clearAll() {
        suspendCancellableCoroutine { continuation ->
            cookieManager.removeAllCookies {
                cookieManager.flush()
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }
}
