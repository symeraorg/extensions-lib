package org.symera.source.iptv

import java.net.URI

data class IptvEpgChannel(
    val id: String,
    val displayNames: List<IptvLocalizedText>,
    val icons: List<IptvImage> = emptyList(),
    val urls: List<URI> = emptyList(),
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(displayNames.isNotEmpty()) { "At least one display name is required" }
    }
}

data class IptvEpisodeNumber(
    val value: String,
    val system: String? = null,
) {
    init {
        require(value.isNotBlank()) { "value must not be blank" }
        require(system == null || system.isNotBlank()) { "system must be null or non-blank" }
    }
}

data class IptvRating(
    val value: String,
    val system: String? = null,
    val icon: IptvImage? = null,
) {
    init {
        require(value.isNotBlank()) { "value must not be blank" }
        require(system == null || system.isNotBlank()) { "system must be null or non-blank" }
    }
}

data class IptvProgramme(
    val channelId: String,
    val start: IptvInstant,
    val stop: IptvInstant? = null,
    val titles: List<IptvLocalizedText>,
    val subtitles: List<IptvLocalizedText> = emptyList(),
    val descriptions: List<IptvLocalizedText> = emptyList(),
    val categories: List<IptvLocalizedText> = emptyList(),
    val icon: IptvImage? = null,
    val episodeNumbers: List<IptvEpisodeNumber> = emptyList(),
    val ratings: List<IptvRating> = emptyList(),
) {
    init {
        require(channelId.isNotBlank()) { "channelId must not be blank" }
        require(titles.isNotEmpty()) { "At least one title is required" }
        require(stop == null || stop > start) { "stop must be after start" }
    }

    val range: IptvTimeRange? get() = stop?.let { IptvTimeRange(start, it) }
}

data class IptvEpgRequest(
    val channelIds: Set<String> = emptySet(),
    val range: IptvTimeRange? = null,
) {
    init {
        require(channelIds.none { it.isBlank() }) { "channelIds must not contain blanks" }
    }
}

data class IptvEpg(
    val channels: List<IptvEpgChannel>,
    val programmes: List<IptvProgramme>,
)

data class IptvNowNext(
    val channelId: String,
    val now: IptvProgramme?,
    val next: IptvProgramme?,
) {
    init {
        require(channelId.isNotBlank()) { "channelId must not be blank" }
        require(now == null || now.channelId == channelId) { "now belongs to a different channel" }
        require(next == null || next.channelId == channelId) { "next belongs to a different channel" }
    }
}
