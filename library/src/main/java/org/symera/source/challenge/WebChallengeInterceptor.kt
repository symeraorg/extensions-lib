package org.symera.source.challenge

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/** Detects a browser challenge and hands control back to the host without blocking an OkHttp thread. */
class WebChallengeInterceptor(
    private val sourceId: Long,
    private val policyProvider: (Request) -> WebChallengePolicy,
    private val detector: WebChallengeDetector = CloudflareChallengeDetector,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val policy = policyProvider(response.request)
        if (policy.mode == InteractiveChallengeMode.DISABLED || !detector.isChallenge(response)) {
            return response
        }

        response.close()
        throw WebChallengeRequiredException(sourceId, response.request, policy)
    }
}

class WebChallengeRequiredException(
    val sourceId: Long,
    val failedRequest: Request,
    val policy: WebChallengePolicy,
) : IOException("Interactive browser challenge required for ${failedRequest.url.host}")

fun interface WebChallengeDetector {
    fun isChallenge(response: Response): Boolean
}

object CloudflareChallengeDetector : WebChallengeDetector {
    private const val MAX_INSPECTION_BYTES = 64L * 1024L
    private val markers = listOf(
        "challenge-error-title",
        "challenge-error-text",
        "window._cf_chl_opt",
        "cf-chl-",
    )

    override fun isChallenge(response: Response): Boolean {
        if (response.header("cf-mitigated").equals("challenge", ignoreCase = true)) return true
        if (response.code !in setOf(403, 503)) return false
        if (!response.header("Server").orEmpty().contains("cloudflare", ignoreCase = true)) return false
        if (!response.header("Content-Type").orEmpty().contains("text/html", ignoreCase = true)) return false

        val preview = runCatching { response.peekBody(MAX_INSPECTION_BYTES).string() }.getOrDefault("")
        return markers.any { marker -> preview.contains(marker, ignoreCase = true) }
    }
}
