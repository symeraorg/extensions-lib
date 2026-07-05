package org.symera.source.network

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

fun Request.Builder.withReferer(referer: String): Request.Builder =
    header("Referer", referer)

fun Request.Builder.withOrigin(origin: String): Request.Builder =
    header("Origin", origin)

fun Request.Builder.withCookies(cookies: Map<String, String>): Request.Builder {
    val cookieString = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
    return header("Cookie", cookieString)
}

fun Request.Builder.withXRequestedWith(): Request.Builder =
    header("X-Requested-With", "XMLHttpRequest")

fun Request.Builder.withJsonBody(json: String): Request.Builder {
    val mediaType = "application/json".toMediaType()
    return post(json.toRequestBody(mediaType))
}

fun Request.Builder.withFormBody(form: Map<String, String>): Request.Builder {
    val body = FormBody.Builder().apply {
        form.forEach { (name, value) -> add(name, value) }
    }.build()
    return post(body)
}
