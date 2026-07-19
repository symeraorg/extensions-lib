package org.symera.source.model

/**
 * An explicit season grouping reported by a source.
 *
 * Flat anime sources should not manufacture season one; they return items directly from content.
 */
data class SSeason(
    val url: String,
    val number: Int,
    val title: String? = null,
    val description: String? = null,
    val posterUrl: String? = null,
    val airDate: SourceDate? = null,
    /** Null means the source requires a separate request; empty means it resolved to no items. */
    val playableItems: List<SPlayableItem>? = null,
    val attributes: Map<String, String> = emptyMap(),
) {
    init {
        require(url.isNotBlank()) { "Season URL/reference cannot be blank" }
        require(number >= 0) { "Season number cannot be negative" }
    }
}
