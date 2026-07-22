package org.symera.source.online

import java.io.IOException
import okio.Buffer
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

fun GET(url: String, headers: Headers = Headers.headersOf()): Request =
    Request.Builder().url(url).headers(headers).get().build()

fun GET(url: HttpUrl, headers: Headers = Headers.headersOf()): Request =
    Request.Builder().url(url).headers(headers).get().build()

fun POST(url: String, headers: Headers = Headers.headersOf(), body: RequestBody): Request =
    Request.Builder().url(url).headers(headers).post(body).build()

fun POST(url: HttpUrl, headers: Headers = Headers.headersOf(), body: RequestBody): Request =
    Request.Builder().url(url).headers(headers).post(body).build()

fun Response.asJsoup(
    html: String? = null,
    maximumBytes: Long = DEFAULT_MAXIMUM_BODY_BYTES,
): Document = Jsoup.parse(html ?: bodyString(maximumBytes), request.url.toString())

fun Response.bodyString(maximumBytes: Long = DEFAULT_MAXIMUM_BODY_BYTES): String {
    require(maximumBytes > 0) { "Maximum body size must be positive" }
    val contentLength = body.contentLength()
    if (contentLength > maximumBytes) throw IOException("HTTP body exceeds $maximumBytes bytes")
    val buffer = Buffer()
    val source = body.source()
    while (true) {
        val read = source.read(buffer, BODY_READ_CHUNK_SIZE)
        if (read == -1L) break
        if (buffer.size > maximumBytes) throw IOException("HTTP body exceeds $maximumBytes bytes")
    }
    val bytes = buffer.readByteArray()
    return bytes.toString(body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
}

const val DEFAULT_MAXIMUM_BODY_BYTES = 16L * 1024L * 1024L
private const val BODY_READ_CHUNK_SIZE = 8L * 1024L
