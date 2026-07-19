package org.symera.source.model

/**
 * A source-owned movie, series, short, or other VOD entry.
 *
 * Only [url] and [title] are required because every other field may be absent from the website.
 * [url] may be absolute, source-relative, or an opaque source reference understood by the source.
 */
data class SContent(
    val url: String,
    val title: String,
    val originalTitle: String? = null,
    val alternativeTitles: List<String> = emptyList(),
    val description: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val contentType: ContentType? = null,
    val categories: Set<ContentCategory> = emptySet(),
    val status: ContentStatus? = null,
    val release: ContentRelease? = null,
    val rating: ContentRating? = null,
    val durationMillis: Long? = null,
    val ageRating: String? = null,
    val countries: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val credits: ContentCredits? = null,
    val externalIds: Set<ExternalId> = emptySet(),
    val structure: ContentStructure = ContentStructure.UNKNOWN,
    val updateStrategy: UpdateStrategy = UpdateStrategy.DEFAULT,
    val seasonCount: Int? = null,
    val episodeCount: Int? = null,
    val initialized: Boolean = false,
    val attributes: Map<String, String> = emptyMap(),
) {
    init {
        require(url.isNotBlank()) { "Content URL/reference cannot be blank" }
        require(title.isNotBlank()) { "Content title cannot be blank" }
        require(durationMillis == null || durationMillis > 0) { "Content duration must be positive" }
        require(seasonCount == null || seasonCount >= 0) { "Season count cannot be negative" }
        require(episodeCount == null || episodeCount >= 0) { "Episode count cannot be negative" }
    }
}
