package org.symera.source.network

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

suspend fun OkHttpClient.await(request: Request): Response = newCall(request).await()

suspend fun OkHttpClient.awaitSuccess(request: Request): Response = newCall(request).await().ensureSuccessful()

suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                } else {
                    response.close()
                }
            }
        },
    )
}

fun Response.ensureSuccessful(): Response {
    if (isSuccessful) return this

    val exception = HttpStatusException(
        statusCode = code,
        statusMessage = message,
        requestUrl = request.url.toString(),
        retryAfter = header("Retry-After"),
    )
    close()
    throw exception
}

class HttpStatusException(
    val statusCode: Int,
    val statusMessage: String,
    val requestUrl: String,
    val retryAfter: String?,
) : IOException("HTTP $statusCode $statusMessage for $requestUrl")

fun Response.bodyStringOrNull(): String? = body.string().takeIf(String::isNotEmpty)

fun Response.cookies(): List<Cookie> = Cookie.parseAll(request.url, headers)
