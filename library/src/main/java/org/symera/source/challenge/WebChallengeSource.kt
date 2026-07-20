package org.symera.source.challenge

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request

enum class InteractiveChallengeMode {
    /** The host may try a non-interactive browser session before presenting UI. */
    HOST_DEFAULT,

    /** Always present a visible host-owned browser session. */
    MANUAL_ONLY,

    /** Return the original HTTP response without challenge handling. */
    DISABLED,
}

enum class ChallengeRetryPolicy {
    /** Retry only GET, HEAD, OPTIONS, and TRACE after successful verification. */
    SAFE_METHODS_ONLY,

    /** Also allow a replayable request body when the extension knows the operation is idempotent. */
    REPLAYABLE_IDEMPOTENT_REQUESTS,

    /** Resolve browser state but never repeat the failed operation automatically. */
    NEVER,
}

class WebChallengePolicy(
    val mode: InteractiveChallengeMode = InteractiveChallengeMode.HOST_DEFAULT,
    val entryUrl: HttpUrl? = null,
    val verificationUrl: HttpUrl? = null,
    additionalAllowedTopLevelOrigins: Collection<HttpUrl> = emptySet(),
    initialHeaders: Map<String, String> = emptyMap(),
    val retryPolicy: ChallengeRetryPolicy = ChallengeRetryPolicy.SAFE_METHODS_ONLY,
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    val additionalAllowedTopLevelOrigins: Set<HttpUrl> = additionalAllowedTopLevelOrigins.toSet()
    val initialHeaders: Map<String, String> = initialHeaders.toMap()

    init {
        entryUrl?.requireSecureBrowserUrl("entry URL")
        verificationUrl?.requireSecureBrowserUrl("verification URL")
        require(this.additionalAllowedTopLevelOrigins.size <= MAXIMUM_ADDITIONAL_ORIGINS) {
            "Challenge policy has too many allowed origins"
        }
        this.additionalAllowedTopLevelOrigins.forEach { origin ->
            origin.requireSecureBrowserUrl("allowed origin")
            require(origin == origin.origin()) { "Challenge allowed values must be origins" }
        }
        require(this.initialHeaders.keys.none { it.isBlank() || '\r' in it || '\n' in it }) {
            "Invalid challenge header name"
        }
        require(this.initialHeaders.values.none { '\r' in it || '\n' in it }) { "Invalid challenge header value" }
        require(this.initialHeaders.keys.none { it.lowercase() in FORBIDDEN_BROWSER_HEADERS }) {
            "Challenge headers contain a browser-controlled field"
        }
        require(timeoutMillis in 1..MAXIMUM_TIMEOUT_MILLIS) { "Challenge timeout is outside the supported range" }
    }

    private fun HttpUrl.requireSecureBrowserUrl(label: String) {
        require(isHttps) { "Challenge $label must use HTTPS" }
        require(username.isEmpty() && password.isEmpty()) { "Challenge $label cannot contain credentials" }
    }

    private fun HttpUrl.origin(): HttpUrl =
        newBuilder()
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .build()

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 60_000L
        const val MAXIMUM_TIMEOUT_MILLIS = 5 * 60_000L
        const val MAXIMUM_ADDITIONAL_ORIGINS = 16
        private val FORBIDDEN_BROWSER_HEADERS = setOf(
            "connection",
            "content-length",
            "cookie",
            "host",
            "proxy-authorization",
            "set-cookie",
            "transfer-encoding",
        )
    }
}

/** Optional source policy consumed by the host-owned challenge coordinator. */
interface WebChallengeSource {
    fun webChallengePolicy(failedRequest: Request): WebChallengePolicy = WebChallengePolicy()
}

/** Creates host-owned challenge handling for one source without giving the extension control of UI. */
fun interface WebChallengeInterceptorFactory {
    fun create(
        sourceId: Long,
        policyProvider: (Request) -> WebChallengePolicy,
    ): Interceptor
}
