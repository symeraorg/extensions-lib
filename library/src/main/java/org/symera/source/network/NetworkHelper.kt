package org.symera.source.network

import android.content.Context
import java.io.Closeable
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import okhttp3.Cache
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

/** Owns exactly one cache and client. Derive custom clients with [client.newBuilder]. */
class NetworkHelper(
    context: Context,
    cacheNamespace: String,
    cacheSize: Long = DEFAULT_CACHE_SIZE,
    private val managedCookieJar: ManagedCookieJar = MemoryCookieJar(),
    baseClient: OkHttpClient = OkHttpClient(),
    proxyHost: String? = null,
    proxyPort: Int = 8080,
    followRedirects: Boolean = true,
) : Closeable {
    private val cache: Cache? = if (cacheSize > 0) {
        require(cacheNamespace.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid cache namespace" }
        val directory = File(context.cacheDir, "symera_network_cache/$cacheNamespace")
        require(directory.exists() || directory.mkdirs()) { "Unable to create network cache directory" }
        Cache(directory, cacheSize)
    } else {
        null
    }

    val client: OkHttpClient = baseClient.newBuilder()
        .cookieJar(managedCookieJar)
        .cache(cache)
        .followRedirects(followRedirects)
        .followSslRedirects(followRedirects)
        .apply {
            proxyHost?.let { host ->
                require(proxyPort in 1..65535) { "Proxy port is invalid" }
                proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, proxyPort)))
            }
        }
        .build()

    fun clearCache() {
        cache?.evictAll()
    }

    fun clearCookies() = managedCookieJar.clearAll()

    fun clearCookiesForHost(host: String) = managedCookieJar.clearForHost(host)

    override fun close() {
        cache?.close()
    }

    companion object {
        const val DEFAULT_CACHE_SIZE = 10L * 1024L * 1024L
    }
}

interface ManagedCookieJar : CookieJar {
    fun clearAll()
    fun clearForHost(host: String)
}

class MemoryCookieJar : ManagedCookieJar {
    private val lock = Any()
    private val cookies = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            cookies.forEach { incoming ->
                this.cookies.removeAll {
                    it.name == incoming.name && it.domain == incoming.domain && it.path == incoming.path
                }
                if (incoming.expiresAt > System.currentTimeMillis()) this.cookies += incoming
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        cookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
        cookies.filter { it.matches(url) }
    }

    override fun clearAll() {
        synchronized(lock) { cookies.clear() }
    }

    override fun clearForHost(host: String) {
        synchronized(lock) {
            cookies.removeAll { cookie ->
                cookie.domain == host || host.endsWith(".${cookie.domain.trimStart('.')}")
            }
        }
    }
}
