package org.symera.source.network

import java.io.Closeable

/** Host-provided browser session used by extractors to resolve JavaScript media URLs. */
interface MediaBrowser : Closeable {
    suspend fun resolve(request: MediaBrowserRequest): MediaBrowserResult
}

fun interface MediaBrowserFactory {
    fun create(): MediaBrowser
}

data class MediaBrowserRequest(
    val entryUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val mediaUrlPattern: String = "(?i).*\\.(m3u8|mp4|mpd)(\\?.*)?$",
    val allowedTopLevelHosts: Set<String> = emptySet(),
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    init {
        require(entryUrl.startsWith("https://")) { "Media browser requires HTTPS entry URL" }
        require(timeoutMillis > 0) { "Media browser timeout must be positive" }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 30_000L
    }
}

data class MediaBrowserResult(
    val mediaUrl: String,
    val headers: Map<String, String>,
)
