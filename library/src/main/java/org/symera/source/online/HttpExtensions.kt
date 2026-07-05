package org.symera.source.online

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

fun GET(url: String, headers: Headers = Headers.headersOf()): Request {
    return Request.Builder()
        .url(url)
        .headers(headers)
        .get()
        .build()
}

fun POST(url: String, headers: Headers = Headers.headersOf(), body: RequestBody): Request {
    return Request.Builder()
        .url(url)
        .headers(headers)
        .post(body)
        .build()
}

suspend fun OkHttpClient.awaitSuccess(request: Request): Response {
    return newCall(request).awaitSuccess()
}

suspend fun Call.awaitSuccess(): Response = suspendCoroutine { continuation ->
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    continuation.resume(response)
                } else {
                    val code = response.code
                    val message = response.message
                    response.close()
                    continuation.resumeWithException(IOException("HTTP $code $message"))
                }
            }
        },
    )
}

fun Response.asJsoup(): Document {
    val html = body?.string().orEmpty()
    return Jsoup.parse(html, request.url.toString())
}

fun Response.bodyString(): String = body?.string().orEmpty()
