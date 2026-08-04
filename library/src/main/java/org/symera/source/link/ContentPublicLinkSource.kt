package org.symera.source.link

import okhttp3.HttpUrl
import org.symera.source.SymeraSource
import org.symera.source.model.SContent
import java.util.Locale

/**
 * Optional source capability for a public content page suitable for opening outside Symera or
 * sharing. Returned links must not identify a user or require source session state.
 */
interface ContentPublicLinkSource : SymeraSource {
    fun publicContentLink(content: SContent): PublicContentLink?
}

/** A validated public HTTPS link. Cookies, headers, credentials, and signed query parameters are excluded. */
class PublicContentLink(
    val url: HttpUrl,
) {
    init {
        require(url.isHttps) { "Public content link must use HTTPS" }
        require(url.username.isEmpty() && url.password.isEmpty()) {
            "Public content link cannot contain credentials"
        }
        require(url.toString().length <= MAXIMUM_URL_LENGTH) { "Public content link is too long" }
        require(url.queryParameterNames.none { it.lowercase(Locale.ROOT) in SENSITIVE_QUERY_PARAMETER_NAMES }) {
            "Public content link cannot contain sensitive query parameters"
        }
    }

    companion object {
        const val MAXIMUM_URL_LENGTH = 2_048

        private val SENSITIVE_QUERY_PARAMETER_NAMES =
            setOf("access_token", "auth", "cookie", "expires", "session", "sig", "signature", "token")
    }
}
