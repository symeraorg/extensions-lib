package org.symera.source.iptv

import java.net.URI

/** Player-independent stream protocol hint. AUTO asks the host application to inspect the resource. */
enum class IptvStreamProtocol {
    AUTO,
    HLS,
    DASH,
    SMOOTH_STREAMING,
    HTTP_PROGRESSIVE,
    RTSP,
    RTP,
    UDP,
}

enum class IptvPlaybackMode {
    LIVE,
    CATCH_UP,
    TIMESHIFT,
}

/** Intent supplied by the host when resolving a channel to a concrete stream. */
data class IptvPlaybackIntent(
    val mode: IptvPlaybackMode = IptvPlaybackMode.LIVE,
    val programme: IptvTimeRange? = null,
    /** Requested distance behind the live edge for server DVR/timeshift. */
    val liveOffsetMillis: Long? = null,
) {
    init {
        require(mode != IptvPlaybackMode.CATCH_UP || programme != null) { "Catch-up requires a programme range" }
        require(mode != IptvPlaybackMode.TIMESHIFT || liveOffsetMillis != null) { "Timeshift requires a live offset" }
        require(liveOffsetMillis == null || liveOffsetMillis >= 0) { "Live offset must not be negative" }
    }
}

/**
 * IPTV-owned playback primitive. Hosts map this to their current player stack; no Media3 type leaks into extensions.
 */
data class IptvPlaybackRequest(
    val uri: URI,
    val protocol: IptvStreamProtocol = IptvStreamProtocol.AUTO,
    val mode: IptvPlaybackMode = IptvPlaybackMode.LIVE,
    val headers: Map<String, String> = emptyMap(),
    val headerRules: List<IptvHeaderRule> = emptyList(),
    val mimeType: String? = null,
    val userAgent: String? = null,
    val referrer: URI? = null,
    val programme: IptvTimeRange? = null,
    val drm: IptvDrm? = null,
    val authorizationHandle: IptvAuthorizationHandle? = null,
    val expiresAt: IptvInstant? = null,
    val liveOffsetMillis: Long? = null,
) {
    init {
        IptvUriPolicy.requirePlaybackUri(uri, protocol)
        IptvHeaderPolicy.requireValid(headers)
        require(headers.isEmpty() || uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) {
            "Inline HTTP headers require HTTP(S) playback"
        }
        require(mimeType == null || mimeType.isNotBlank()) { "mimeType must be null or non-blank" }
        require(userAgent == null || ('\r' !in userAgent && '\n' !in userAgent)) { "Invalid User-Agent value" }
        if (referrer != null) IptvUriPolicy.requireHttpUri(referrer, "Playback referrer")
        require(mode != IptvPlaybackMode.CATCH_UP || programme != null) { "Catch-up requires a programme range" }
        require(mode != IptvPlaybackMode.TIMESHIFT || liveOffsetMillis != null) { "Timeshift requires a live offset" }
        require(liveOffsetMillis == null || liveOffsetMillis >= 0) { "Live offset must not be negative" }
        require(authorizationHandle == null || authorizationHandle.allows(uri)) {
            "authorizationHandle must allow the playback origin"
        }
    }

    override fun toString(): String =
        "IptvPlaybackRequest(uri=${uri.redactedForLog()}, protocol=$protocol, mode=$mode, headerNames=${headers.keys}, " +
            "headerRules=$headerRules, mimeType=$mimeType, userAgent=${userAgent?.let { "<redacted>" }}, " +
            "referrer=${referrer?.redactedForLog()}, programme=$programme, drm=$drm, authorizationHandle=$authorizationHandle, " +
            "expiresAt=$expiresAt, liveOffsetMillis=$liveOffsetMillis)"
}

data class IptvCatchUp(
    /** Provider value from `catchup`; retained because values outside the common ecosystem are provider-defined. */
    val mode: String,
    val sourceTemplate: String? = null,
    val days: Int? = null,
    val correctionSeconds: Long = 0,
) {
    init {
        require(mode.isNotBlank()) { "mode must not be blank" }
        require(sourceTemplate == null || sourceTemplate.isNotBlank()) { "sourceTemplate must be null or non-blank" }
        require(days == null || days > 0) { "days must be positive" }
    }

    override fun toString(): String =
        "IptvCatchUp(mode=$mode, sourceTemplate=${sourceTemplate?.let { "<redacted>" }}, " +
            "days=$days, correctionSeconds=$correctionSeconds)"
}

data class IptvTimeshift(
    val windowMillis: Long,
) {
    init {
        require(windowMillis > 0) { "windowMillis must be positive" }
    }
}
