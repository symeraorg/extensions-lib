package org.symera.source.network

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

suspend fun OkHttpClient.await(request: Request): Response =
    newCall(request).awaitResponse()

suspend fun Call.awaitResponse(): Response = suspendCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }
    })
}

fun Response.bodyStringOrNull(): String? =
    body?.string()

fun Response.ensureSuccessful(): Response {
    if (!isSuccessful) {
        val code = code
        val msg = message
        close()
        throw HttpException(code, msg)
    }
    return this
}

class HttpException(val code: Int, message: String) : IOException("HTTP $code $message")

fun Response.headerValue(name: String): String? =
    header(name)

fun Response.cookies(): Map<String, String> {
    val setCookieHeaders = headers("Set-Cookie")
    val cookies = mutableMapOf<String, String>()
    for (header in setCookieHeaders) {
        val parts = header.split(";")
        val first = parts.firstOrNull() ?: continue
        val eqIndex = first.indexOf('=')
        if (eqIndex > 0) {
            cookies[first.substring(0, eqIndex).trim()] = first.substring(eqIndex + 1).trim()
        }
    }
    return cookies
}
