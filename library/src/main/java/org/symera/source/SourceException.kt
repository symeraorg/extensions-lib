package org.symera.source

sealed class SourceException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class UnsupportedCapability(val capability: SymeraCapability) :
        SourceException("Source capability is not supported: $capability")

    class AuthenticationRequired(message: String = "Authentication is required") : SourceException(message)

    class RateLimited(val retryAfterEpochMillis: Long? = null) : SourceException("Source rate limit exceeded")

    class ContentUnavailable(message: String) : SourceException(message)

    class Parse(message: String, cause: Throwable? = null) : SourceException(message, cause)

    class Network(message: String, cause: Throwable? = null) : SourceException(message, cause)
}
