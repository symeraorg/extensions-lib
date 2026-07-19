package org.symera.source.model

import java.math.BigDecimal

enum class PlayableItemType {
    MOVIE,
    EPISODE,
    SPECIAL,
    TRAILER,
    EXTRA,
    OTHER,
}

/** Numeric episode identifier that preserves decimals without Float/Double precision loss. */
class EpisodeNumber(value: BigDecimal) : Comparable<EpisodeNumber> {
    val value: BigDecimal = value.stripTrailingZeros()

    constructor(value: Int) : this(value.toBigDecimal())
    constructor(value: Long) : this(value.toBigDecimal())
    constructor(value: String) : this(value.toBigDecimal())

    override fun compareTo(other: EpisodeNumber): Int = value.compareTo(other.value)
    override fun equals(other: Any?): Boolean = other is EpisodeNumber && value.compareTo(other.value) == 0
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = value.stripTrailingZeros().toPlainString()
}

/** An individual movie, episode, special, trailer, or extra exposed by a source. */
data class SPlayableItem(
    val url: String,
    /** Optional source-provided title. The host can render the mandatory episode number instead. */
    val title: String? = null,
    val type: PlayableItemType,
    val episodeNumber: EpisodeNumber? = null,
    val seasonNumber: Int? = null,
    val absoluteEpisodeNumber: Int? = null,
    val summary: String? = null,
    val thumbnailUrl: String? = null,
    val airDate: SourceDate? = null,
    /** When the source published the item, if it exposes that information. */
    val publishedAtEpochMillis: Long? = null,
    val durationMillis: Long? = null,
    val isFiller: Boolean? = null,
    val attributes: Map<String, String> = emptyMap(),
) {
    init {
        require(url.isNotBlank()) { "Playable item URL/reference cannot be blank" }
        require(title == null || title.isNotBlank()) { "Playable item title cannot be blank" }
        require(type != PlayableItemType.EPISODE || episodeNumber != null) {
            "An episode must declare its episode number"
        }
        require(seasonNumber == null || seasonNumber >= 0) { "Season number cannot be negative" }
        require(absoluteEpisodeNumber == null || absoluteEpisodeNumber > 0) { "Absolute episode number must be positive" }
        require(publishedAtEpochMillis == null || publishedAtEpochMillis >= 0) { "Publication timestamp cannot be negative" }
        require(durationMillis == null || durationMillis > 0) { "Playable item duration must be positive" }
    }
}
