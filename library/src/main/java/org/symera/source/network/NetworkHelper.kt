package org.symera.source.network

import android.content.Context
import okhttp3.Cache
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy

class NetworkHelper(context: Context) {
    private val cacheDir: File = File(context.cacheDir, "symera_network_cache")

    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    val client: OkHttpClient by lazy { buildClient() }

    val cloudflareClient: OkHttpClient by lazy {
        client.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val defaultCookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val hostCookies = cookieStore.getOrPut(url.host) { mutableListOf() }
            synchronized(hostCookies) {
                for (cookie in cookies) {
                    hostCookies.removeAll {
                        it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path
                    }
                    if (cookie.expiresAt > System.currentTimeMillis()) {
                        hostCookies.add(cookie)
                    }
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val hostCookies = cookieStore[url.host] ?: return emptyList()
            synchronized(hostCookies) {
                hostCookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
                return hostCookies.toList()
            }
        }
    }

    fun buildClient(
        baseClient: OkHttpClient = OkHttpClient(),
        cacheSize: Long = DEFAULT_CACHE_SIZE,
        proxyHost: String? = null,
        proxyPort: Int = 8080,
        followRedirects: Boolean = true,
    ): OkHttpClient {
        val builder = baseClient.newBuilder()
            .cookieJar(defaultCookieJar)
            .followRedirects(followRedirects)
            .followSslRedirects(followRedirects)

        if (cacheSize > 0) {
            cacheDir.mkdirs()
            builder.cache(Cache(cacheDir, cacheSize))
        }

        proxyHost?.let {
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(it, proxyPort)))
        }

        return builder.build()
    }

    fun clearCache() {
        cacheDir.deleteRecursively()
    }

    fun clearCookies() {
        cookieStore.clear()
    }

    fun clearCookiesForHost(host: String) {
        cookieStore.remove(host)
    }

    fun isCloudflareChallenge(response: Response): Boolean {
        val server = response.header("Server").orEmpty()
        return server.contains("cloudflare", ignoreCase = true) && response.code in setOf(403, 429, 503)
    }

    companion object {
        const val DEFAULT_CACHE_SIZE = 10L * 1024 * 1024
    }
}
