package org.symera.source.model

enum class HttpMethod {
    GET,
    POST,
}

data class HttpHeader(
    val name: String,
    val value: String,
) {
    init {
        require(name.isNotBlank()) { "Header name cannot be blank" }
        require(!name.contains('\n') && !name.contains('\r')) { "Header name contains a line break" }
        require(!value.contains('\n') && !value.contains('\r')) { "Header value contains a line break" }
    }
}

enum class HeaderScope {
    PRIMARY_REQUEST_ONLY,
    SAME_ORIGIN_DERIVED_REQUESTS,
    ALL_DERIVED_REQUESTS,
}

data class MediaRequest(
    val uri: String,
    val method: HttpMethod = HttpMethod.GET,
    val body: ByteArray? = null,
    val headers: List<HttpHeader> = emptyList(),
    val headerScope: HeaderScope = HeaderScope.SAME_ORIGIN_DERIVED_REQUESTS,
) {
    init {
        require(uri.isNotBlank()) { "Media URI cannot be blank" }
        require(method == HttpMethod.POST || body == null) { "Only POST media requests may contain a body" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaRequest) return false
        return uri == other.uri && method == other.method && body.contentEquals(other.body) &&
            headers == other.headers && headerScope == other.headerScope
    }

    override fun hashCode(): Int {
        var result = uri.hashCode()
        result = 31 * result + method.hashCode()
        result = 31 * result + (body?.contentHashCode() ?: 0)
        result = 31 * result + headers.hashCode()
        return 31 * result + headerScope.hashCode()
    }
}

enum class StreamProtocol {
    AUTO,
    PROGRESSIVE,
    HLS,
    DASH,
    SMOOTH_STREAMING,
    RTSP,
    TORRENT,
}

data class StreamHints(
    val containerMimeType: String? = null,
    /** Codec identifiers as reported by the source, preferably RFC 6381 values. */
    val codecs: List<String> = emptyList(),
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Double? = null,
    val bitrateBitsPerSecond: Long? = null,
) {
    init {
        require(width == null || width > 0) { "Stream width must be positive" }
        require(height == null || height > 0) { "Stream height must be positive" }
        require(frameRate == null || frameRate.isFinite() && frameRate > 0) { "Frame rate must be positive" }
        require(bitrateBitsPerSecond == null || bitrateBitsPerSecond > 0) { "Bitrate must be positive" }
    }
}

enum class DrmScheme {
    WIDEVINE,
    CLEARKEY,
    PLAYREADY,
    CUSTOM,
}

enum class LicenseUriPolicy {
    MANIFEST,
    FALLBACK,
    OVERRIDE,
}

data class StreamDrm(
    val scheme: DrmScheme,
    val customSchemeUuid: String? = null,
    val licenseRequest: MediaRequest? = null,
    val licenseUriPolicy: LicenseUriPolicy = LicenseUriPolicy.MANIFEST,
    val multiSession: Boolean = false,
) {
    init {
        require(scheme == DrmScheme.CUSTOM || customSchemeUuid == null) {
            "A custom DRM UUID is only valid for CUSTOM"
        }
        require(scheme != DrmScheme.CUSTOM || !customSchemeUuid.isNullOrBlank()) {
            "CUSTOM DRM requires a UUID"
        }
    }
}

enum class SubtitleFormat {
    UNKNOWN,
    WEBVTT,
    TTML,
    SUBRIP,
    SSA_ASS,
}

enum class SubtitleRole {
    SUBTITLE,
    CAPTION,
    FORCED,
    HEARING_IMPAIRED,
    DESCRIPTION,
}

data class SubtitleTrack(
    val id: String,
    val request: MediaRequest,
    val language: String? = null,
    val label: String? = null,
    val format: SubtitleFormat = SubtitleFormat.UNKNOWN,
    val roles: Set<SubtitleRole> = emptySet(),
    val default: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "Subtitle track ID cannot be blank" }
    }
}

enum class AudioRole {
    MAIN,
    ALTERNATE,
    COMMENTARY,
    DUB,
    DESCRIPTION,
}

data class AudioTrack(
    val id: String,
    val request: MediaRequest,
    val language: String? = null,
    val label: String? = null,
    val mimeType: String? = null,
    val codecs: List<String> = emptyList(),
    val roles: Set<AudioRole> = emptySet(),
    val default: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "Audio track ID cannot be blank" }
    }
}

enum class StreamTimestampType {
    OPENING,
    ENDING,
    RECAP,
    MIXED,
    OTHER,
}

data class StreamTimestamp(
    val startMillis: Long,
    val endMillis: Long? = null,
    val title: String,
    val type: StreamTimestampType = StreamTimestampType.OTHER,
) {
    init {
        require(startMillis >= 0) { "Chapter start cannot be negative" }
        require(endMillis == null || endMillis > startMillis) { "Chapter end must be after its start" }
        require(title.isNotBlank()) { "Chapter title cannot be blank" }
    }
}

sealed interface SStream {
    val id: String
    val title: String?
    val preferred: Boolean
}

data class DeferredStream(
    override val id: String,
    override val title: String? = null,
    val resolverData: String,
    override val preferred: Boolean = false,
) : SStream {
    init {
        require(id.isNotBlank()) { "Stream ID cannot be blank" }
        require(title == null || title.isNotBlank()) { "Stream title cannot be blank" }
        require(resolverData.isNotBlank()) { "Deferred stream resolver data cannot be blank" }
    }
}

data class PlayableStream(
    override val id: String,
    override val title: String? = null,
    val request: MediaRequest,
    val protocol: StreamProtocol = StreamProtocol.AUTO,
    val hints: StreamHints = StreamHints(),
    val drm: StreamDrm? = null,
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
    val timestamps: List<StreamTimestamp> = emptyList(),
    override val preferred: Boolean = false,
) : SStream {
    init {
        require(id.isNotBlank()) { "Stream ID cannot be blank" }
        require(title == null || title.isNotBlank()) { "Stream title cannot be blank" }
    }
}

data class SHoster(
    val id: String,
    val name: String,
    val requestUrl: String? = null,
    /** Null requires [org.symera.source.SymeraSource.getStreams]; empty is a resolved empty result. */
    val streams: List<SStream>? = null,
    val resolverData: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Hoster ID cannot be blank" }
        require(name.isNotBlank()) { "Hoster name cannot be blank" }
    }

}
