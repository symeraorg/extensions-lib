package org.symera.source.iptv.playlist

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import java.net.URI
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runInterruptible
import okhttp3.Call
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.symera.source.network.await
import org.symera.source.network.ensureSuccessful

interface IptvResourceLoader {
    suspend fun <T> read(
        uri: URI,
        headers: Map<String, String>,
        maximumBytes: Long,
        block: (InputStream) -> T,
    ): T
}

class OkHttpIptvResourceLoader(
    client: OkHttpClient,
    private val maximumRedirects: Int = 5,
) : IptvResourceLoader {
    private val client = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    init {
        require(maximumRedirects >= 0) { "Maximum redirects cannot be negative" }
    }

    override suspend fun <T> read(
        uri: URI,
        headers: Map<String, String>,
        maximumBytes: Long,
        block: (InputStream) -> T,
    ): T {
        require(uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("http", ignoreCase = true)) {
            "The OkHttp IPTV loader only supports HTTP(S)"
        }
        require(uri.userInfo == null) { "Credentials must not be embedded in IPTV resource URIs" }
        require(maximumBytes > 0) { "Maximum resource size must be positive" }

        var currentUri = uri
        var currentHeaders = headers
        repeat(maximumRedirects + 1) { redirectCount ->
            val call = newCall(currentUri, currentHeaders)
            val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
                if (cause is CancellationException) call.cancel()
            }
            val response = try {
                call.await()
            } catch (exception: Exception) {
                cancellationHandle?.dispose()
                throw exception
            }

            val redirect = try {
                response.redirectLocation(currentUri)
            } catch (exception: Exception) {
                response.close()
                cancellationHandle?.dispose()
                throw exception
            }
            if (redirect != null) {
                response.close()
                cancellationHandle?.dispose()
                if (redirectCount == maximumRedirects) throw IOException("IPTV resource exceeded redirect limit")
                if (currentUri.scheme.equals("https", true) && !redirect.scheme.equals("https", true)) {
                    throw IOException("IPTV resource refused an HTTPS downgrade")
                }
                if (!currentUri.sameOrigin(redirect)) currentHeaders = emptyMap()
                currentUri = redirect
                return@repeat
            }

            try {
                response.ensureSuccessful()
                return runInterruptible(Dispatchers.IO) {
                    response.body.byteStream().use { raw ->
                        val declaredLength = response.body.contentLength()
                        if (declaredLength > maximumBytes) {
                            throw IptvResourceLimitException("IPTV resource exceeds $maximumBytes bytes")
                        }
                        val compressedLimit = LimitedInputStream(raw, maximumBytes)
                        val decoded = compressedLimit.gzipIfNeeded()
                        LimitedInputStream(decoded, maximumBytes).use(block)
                    }
                }
            } finally {
                response.close()
                cancellationHandle?.dispose()
            }
        }
        throw IOException("IPTV resource could not be loaded")
    }

    private fun newCall(uri: URI, headers: Map<String, String>): Call {
        val requestHeaders = Headers.Builder().apply { headers.forEach(::add) }.build()
        val request = Request.Builder().url(uri.toString()).headers(requestHeaders).get().build()
        return client.newCall(request)
    }
}

class IptvResourceLimitException(message: String) : IOException(message)

private class LimitedInputStream(
    input: InputStream,
    private val maximumBytes: Long,
) : FilterInputStream(input) {
    private var consumed = 0L

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) account(1)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = super.read(buffer, offset, length)
        if (read > 0) account(read.toLong())
        return read
    }

    private fun account(bytes: Long) {
        consumed = Math.addExact(consumed, bytes)
        if (consumed > maximumBytes) throw IptvResourceLimitException("IPTV resource exceeds $maximumBytes bytes")
    }
}

private fun Response.redirectLocation(base: URI): URI? {
    if (code !in setOf(301, 302, 303, 307, 308)) return null
    val location = header("Location") ?: throw IOException("IPTV redirect has no Location header")
    return base.resolve(location).also {
        require(it.scheme.equals("http", true) || it.scheme.equals("https", true)) { "Unsupported redirect scheme" }
        require(it.userInfo == null) { "Redirect URI contains credentials" }
    }
}

private fun URI.sameOrigin(other: URI): Boolean =
    scheme.equals(other.scheme, true) && host.equals(other.host, true) && effectivePort() == other.effectivePort()

private fun URI.effectivePort(): Int = if (port >= 0) port else if (scheme.equals("https", true)) 443 else 80

private fun InputStream.gzipIfNeeded(): InputStream {
    val pushback = PushbackInputStream(this, 2)
    val first = pushback.read()
    val second = pushback.read()
    if (second >= 0) pushback.unread(second)
    if (first >= 0) pushback.unread(first)
    return if (first == GZIP_MAGIC_FIRST && second == GZIP_MAGIC_SECOND) GZIPInputStream(pushback) else pushback
}

private const val GZIP_MAGIC_FIRST = 0x1f
private const val GZIP_MAGIC_SECOND = 0x8b
