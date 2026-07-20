package org.symera.source.torrentutils.service

import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.symera.source.network.await
import org.symera.source.torrentutils.TorrentLimits
import org.symera.source.torrentutils.model.DeadTorrentException

class OkHttpTorrentFetcher(
    client: OkHttpClient,
    private val maximumRedirects: Int = 5,
) {
    private val client = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    init {
        require(maximumRedirects >= 0) { "Maximum redirects cannot be negative" }
    }

    suspend fun fetch(url: String, limits: TorrentLimits = TorrentLimits()): ByteArray {
        validateRemoteUrl(url)
        val request = try {
            Request.Builder().url(url).get().build()
        } catch (exception: IllegalArgumentException) {
            throw DeadTorrentException("Invalid torrent URL", exception)
        }
        return fetch(request, limits)
    }

    /** Executes a complete extension-provided request for authenticated or signed torrent endpoints. */
    suspend fun fetch(request: Request, limits: TorrentLimits = TorrentLimits()): ByteArray {
        var current = request
        repeat(maximumRedirects + 1) { redirectCount ->
            validateRemoteUrl(current.url.toString())
            val response = client.newCall(current).await()
            val code = response.code
            val redirect = try {
                response.header("Location")?.takeIf { code in REDIRECT_CODES }?.let { current.url.resolve(it) }
            } catch (exception: Exception) {
                response.close()
                throw DeadTorrentException("Invalid torrent redirect", exception)
            }
            if (code in REDIRECT_CODES) {
                response.close()
                val target = redirect ?: throw DeadTorrentException("Torrent redirect has no valid Location header")
                validateRemoteUrl(target.toString())
                if (redirectCount == maximumRedirects) throw DeadTorrentException("Torrent request exceeded redirect limit")
                if (current.url.isHttps && !target.isHttps) {
                    throw DeadTorrentException("Torrent request refused an HTTPS downgrade")
                }
                val sameOrigin = current.url.scheme.equals(target.scheme, true) &&
                    current.url.host.equals(target.host, true) && current.url.port == target.port
                val builder = current.newBuilder().url(target)
                if (!sameOrigin) builder.headers(Headers.Builder().build())
                current = when (code) {
                    303 -> builder.get().build()
                    301, 302 -> if (current.method == "GET" || current.method == "HEAD") {
                        builder.build()
                    } else {
                        throw DeadTorrentException("Torrent redirect requires an explicit replay decision")
                    }
                    else -> builder.build()
                }
                return@repeat
            }
            return runInterruptible(Dispatchers.IO) {
                response.use {
                    if (!it.isSuccessful) throw DeadTorrentException("Torrent request failed with HTTP ${it.code}")
                    val body = it.body
                    val contentLength = body.contentLength()
                    if (contentLength > limits.maxInputBytes.toLong()) {
                        throw DeadTorrentException("Torrent response exceeds the configured byte limit")
                    }
                    body.byteStream().use { stream -> stream.readBytesBounded(limits.maxInputBytes) }
                }
            }
        }
        throw IOException("Torrent request could not be loaded")
    }

    private companion object {
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}

private fun validateRemoteUrl(url: String) {
    val uri = try {
        URI(url)
    } catch (exception: URISyntaxException) {
        throw DeadTorrentException("Invalid torrent URL", exception)
    }
    val scheme = uri.scheme
    if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) {
        throw DeadTorrentException("Only HTTP(S) torrent URLs are supported")
    }
    if (uri.host == null || uri.userInfo != null) {
        throw DeadTorrentException("Torrent URL must contain a host and no embedded credentials")
    }
}
