package org.symera.source.browser

import okhttp3.HttpUrl
import org.symera.source.SymeraSource

/** A visible, user-initiated browser session requested by a source. */
class InteractiveBrowserRequest(
    val entryUrl: HttpUrl,
    additionalAllowedTopLevelOrigins: Collection<HttpUrl> = emptySet(),
) {
    val additionalAllowedTopLevelOrigins: Set<HttpUrl> = additionalAllowedTopLevelOrigins.toSet()

    val allowedTopLevelOrigins: Set<HttpUrl>
        get() = buildSet {
            add(entryUrl.origin())
            addAll(additionalAllowedTopLevelOrigins)
        }

    init {
        entryUrl.requireSecureBrowserUrl("entry URL")
        require(entryUrl.toString().length <= MAXIMUM_URL_LENGTH) { "Interactive browser entry URL is too long" }
        require(additionalAllowedTopLevelOrigins.size <= MAXIMUM_ADDITIONAL_ORIGINS) {
            "Interactive browser request has too many allowed origins"
        }
        additionalAllowedTopLevelOrigins.forEach { origin ->
            origin.requireSecureBrowserUrl("allowed origin")
            require(origin == origin.origin()) { "Interactive browser allowed values must be origins" }
        }
    }

    private fun HttpUrl.origin(): HttpUrl =
        newBuilder()
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .build()

    private fun HttpUrl.requireSecureBrowserUrl(label: String) {
        require(isHttps) { "Interactive browser $label must use HTTPS" }
        require(username.isEmpty() && password.isEmpty()) {
            "Interactive browser $label cannot contain credentials"
        }
    }

    companion object {
        const val MAXIMUM_ADDITIONAL_ORIGINS = 16
        const val MAXIMUM_URL_LENGTH = 2_048
    }
}

/** Optional source capability for an explicit host-owned browser action. */
interface InteractiveBrowserSource : SymeraSource {
    fun interactiveBrowserRequest(): InteractiveBrowserRequest
}
