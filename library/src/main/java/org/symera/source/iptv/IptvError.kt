package org.symera.source.iptv

/** Stable, typed failures returned across the extension boundary. */
sealed class IptvError(open val message: String, open val cause: Throwable? = null) {
    data class InvalidConfiguration(override val message: String) : IptvError(message)
    data class AuthenticationRequired(override val message: String = "Authentication is required") : IptvError(message)
    data class AuthenticationRejected(override val message: String) : IptvError(message)
    data class Network(override val message: String, override val cause: Throwable? = null) : IptvError(message, cause)
    data class Http(val statusCode: Int, override val message: String) : IptvError(message) {
        init {
            require(statusCode in 100..599) { "statusCode must be a valid HTTP status" }
        }
    }
    data class Parse(override val message: String, val line: Int? = null, override val cause: Throwable? = null) :
        IptvError(message, cause)
    data class Unsupported(override val message: String) : IptvError(message)
    data class NotFound(override val message: String) : IptvError(message)
    data class RateLimited(val retryAfterMillis: Long? = null, override val message: String = "Rate limited") :
        IptvError(message) {
        init {
            require(retryAfterMillis == null || retryAfterMillis >= 0) { "retryAfterMillis must not be negative" }
        }
    }
    data class Cancelled(override val message: String = "Operation cancelled") : IptvError(message)
    data class Provider(
        val code: String,
        override val message: String,
        val details: Map<String, String> = emptyMap(),
    ) : IptvError(message) {
        init {
            require(code.matches(Regex("[A-Za-z][A-Za-z0-9_.-]*"))) { "Invalid provider error code" }
            require(details.keys.none(String::isBlank)) { "Provider error detail keys cannot be blank" }
        }
    }
    data class Unexpected(override val message: String, override val cause: Throwable? = null) : IptvError(message, cause)
}

sealed interface IptvResult<out T> {
    data class Success<T>(val value: T) : IptvResult<T>
    data class Failure(val error: IptvError) : IptvResult<Nothing>

    fun <R> map(transform: (T) -> R): IptvResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }
}
