package org.symera.source.challenge

import okhttp3.Request
import okhttp3.HttpUrl
import okhttp3.Interceptor
import java.util.Locale

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

data class WebChallengePolicy(
    val mode: InteractiveChallengeMode = InteractiveChallengeMode.HOST_DEFAULT,
    val entryUrl: HttpUrl? = null,
    val verificationUrl: HttpUrl? = null,
    val allowedTopLevelHosts: Set<String> = emptySet(),
    val initialHeaders: Map<String, String> = emptyMap(),
    val retryPolicy: ChallengeRetryPolicy = ChallengeRetryPolicy.SAFE_METHODS_ONLY,
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    init {
        require(entryUrl == null || entryUrl.isHttps) { "Challenge entry URL must use HTTPS" }
        require(verificationUrl == null || verificationUrl.isHttps) {
            "Challenge verification URL must use HTTPS"
        }
        require(allowedTopLevelHosts.none(String::isBlank)) { "Allowed challenge hosts cannot be blank" }
        require(initialHeaders.keys.none { it.isBlank() || '\r' in it || '\n' in it }) { "Invalid challenge header name" }
        require(initialHeaders.values.none { '\r' in it || '\n' in it }) { "Invalid challenge header value" }
        require(initialHeaders.keys.none { it.lowercase(Locale.ROOT) in FORBIDDEN_BROWSER_HEADERS }) {
            "Challenge headers contain a browser-controlled field"
        }
        require(timeoutMillis > 0) { "Challenge timeout must be positive" }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 60_000L
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
