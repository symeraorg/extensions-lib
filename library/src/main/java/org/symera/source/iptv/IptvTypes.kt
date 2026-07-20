package org.symera.source.iptv

import java.net.URI

/** Milliseconds since the Unix epoch. This keeps the SDK usable on Android API 24 and 25. */
@JvmInline
value class IptvInstant(val epochMillis: Long) : Comparable<IptvInstant> {
    override fun compareTo(other: IptvInstant): Int = epochMillis.compareTo(other.epochMillis)

    fun plusMillis(value: Long): IptvInstant = IptvInstant(Math.addExact(epochMillis, value))
}

/** Half-open time interval: [start, end). */
data class IptvTimeRange(
    val start: IptvInstant,
    val end: IptvInstant,
) {
    val durationMillis: Long

    init {
        durationMillis = try {
            Math.subtractExact(end.epochMillis, start.epochMillis)
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException("IPTV time range is too large")
        }
        require(durationMillis > 0) { "end must be after start" }
    }

    operator fun contains(instant: IptvInstant): Boolean = instant >= start && instant < end
}

data class IptvLocalizedText(
    val value: String,
    val language: String? = null,
) {
    init {
        require(value.isNotBlank()) { "value must not be blank" }
        require(language == null || language.isNotBlank()) { "language must be null or non-blank" }
    }
}

data class IptvImage(
    val uri: URI,
    val width: Int? = null,
    val height: Int? = null,
) {
    init {
        IptvUriPolicy.requireHttpUri(uri, "IPTV image URI")
        require(width == null || width > 0) { "width must be positive" }
        require(height == null || height > 0) { "height must be positive" }
    }

    override fun toString(): String =
        "IptvImage(uri=${uri.redactedForLog()}, width=$width, height=$height)"
}
