package org.symera.source.model

/** Structural media type. Categories such as anime or documentary are modeled separately. */
enum class ContentType {
    MOVIE,
    SERIES,
    SHORT,
    OTHER,
}

/** Optional editorial classifications reported by the source. */
enum class ContentCategory {
    ANIME,
    CARTOON,
    DOCUMENTARY,
    LIVE_ACTION,
    OTHER,
}

enum class ContentStatus {
    UPCOMING,
    ONGOING,
    COMPLETED,
    CANCELLED,
    ON_HIATUS,
}

/** How playable items are exposed by the website or API. */
enum class ContentStructure {
    UNKNOWN,
    SINGLE_ITEM,
    FLAT_ITEMS,
    SEASONS,
}
