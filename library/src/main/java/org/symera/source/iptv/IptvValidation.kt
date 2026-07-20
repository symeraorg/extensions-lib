package org.symera.source.iptv

import java.net.URI
import java.util.Locale

internal object IptvUriPolicy {
    private val httpSchemes = setOf("http", "https")
    private val playbackSchemes = httpSchemes + setOf("rtsp", "rtsps", "rtp", "udp")

    fun requireHttpUri(uri: URI, label: String, httpsOnly: Boolean = false) {
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        require(uri.isAbsolute && uri.host != null && uri.rawUserInfo == null) {
            "$label must be an absolute host-based URI without embedded credentials"
        }
        require(if (httpsOnly) scheme == "https" else scheme in httpSchemes) {
            "$label must use ${if (httpsOnly) "HTTPS" else "HTTP(S)"}"
        }
    }

    fun requirePlaybackUri(uri: URI, protocol: IptvStreamProtocol) {
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        require(uri.isAbsolute && scheme in playbackSchemes) { "Unsupported IPTV playback URI: $uri" }
        require(uri.rawUserInfo == null) { "Playback credentials must not be embedded in the URI" }
        when (protocol) {
            IptvStreamProtocol.HLS,
            IptvStreamProtocol.DASH,
            IptvStreamProtocol.SMOOTH_STREAMING,
            IptvStreamProtocol.HTTP_PROGRESSIVE,
            -> require(scheme in httpSchemes) { "$protocol playback must use HTTP(S)" }
            IptvStreamProtocol.RTSP -> require(scheme == "rtsp" || scheme == "rtsps") { "RTSP playback needs an RTSP URI" }
            IptvStreamProtocol.RTP -> require(scheme == "rtp") { "RTP playback needs an RTP URI" }
            IptvStreamProtocol.UDP -> require(scheme == "udp") { "UDP playback needs a UDP URI" }
            IptvStreamProtocol.AUTO -> Unit
        }
        if (scheme in httpSchemes || scheme == "rtsp" || scheme == "rtsps") {
            require(uri.host != null) { "Playback URI must contain a host" }
        }
    }

    fun requireCanonicalOrigin(value: String) {
        val uri = URI(value)
        requireHttpUri(uri, "Allowed origin")
        require(uri.rawPath.orEmpty().isEmpty() && uri.rawQuery == null && uri.rawFragment == null) {
            "Allowed origins must contain only scheme, host, and port"
        }
    }

    fun sameOrigin(first: URI, second: URI): Boolean =
        first.scheme.equals(second.scheme, true) &&
            first.host.equals(second.host, true) &&
            effectivePort(first) == effectivePort(second)

    fun origin(uri: URI): String {
        requireHttpUri(uri, "Origin URI")
        val defaultPort = if (uri.scheme.equals("https", true)) 443 else 80
        return buildString {
            append(uri.scheme.lowercase(Locale.ROOT)).append("://").append(uri.host.lowercase(Locale.ROOT))
            if (uri.port >= 0 && uri.port != defaultPort) append(':').append(uri.port)
        }
    }

    private fun effectivePort(uri: URI): Int =
        if (uri.port >= 0) uri.port else if (uri.scheme.equals("https", true)) 443 else 80
}

internal object IptvHeaderPolicy {
    private val namePattern = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")

    fun requireValid(headers: Map<String, String>, label: String = "HTTP") {
        require(headers.keys.all(namePattern::matches)) { "Invalid $label header name" }
        require(headers.values.none { '\r' in it || '\n' in it }) { "Invalid $label header value" }
        val normalized = headers.keys.map { it.lowercase(Locale.ROOT) }
        require(normalized.distinct().size == normalized.size) { "$label header names must be unique ignoring case" }
    }

    fun requireDisjoint(first: Map<String, *>, second: Map<String, *>) {
        val firstNames = first.keys.mapTo(mutableSetOf()) { it.lowercase(Locale.ROOT) }
        require(second.keys.none { it.lowercase(Locale.ROOT) in firstNames }) {
            "Static and secret header names must not overlap"
        }
    }

    fun merge(base: Map<String, String>, overrides: Map<String, String>): Map<String, String> {
        val overrideNames = overrides.keys.mapTo(mutableSetOf()) { it.lowercase(Locale.ROOT) }
        return buildMap {
            base.forEach { (name, value) -> if (name.lowercase(Locale.ROOT) !in overrideNames) put(name, value) }
            putAll(overrides)
        }
    }
}

internal fun URI.redactedForLog(): String = buildString {
    append(scheme).append("://").append(host ?: "<redacted>")
    if (port >= 0) append(':').append(port)
    if (!rawPath.isNullOrEmpty()) append(rawPath)
    if (rawQuery != null) append("?<redacted>")
    if (rawFragment != null) append("#<redacted>")
}
