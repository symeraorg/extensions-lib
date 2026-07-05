package org.symera.source.model

import java.io.Serializable
import okhttp3.Headers

enum class ContentType {
    MOVIE,
    SERIES,
    ANIME,
    CARTOON,
    DOCUMENTARY,
    SHORT,
    OTHER,
}

enum class ContentStatus {
    UNKNOWN,
    ONGOING,
    COMPLETED,
    CANCELLED,
    ON_HIATUS,
}

interface SContent : Serializable {
    var url: String
    var title: String
    var originalTitle: String?
    var description: String?
    var posterUrl: String?
    var backdropUrl: String?
    var genres: List<String>?
    var contentType: ContentType?
    var status: ContentStatus?
    var year: Int?
    var rating: Double?
    var durationSeconds: Long?
    var initialized: Boolean

    fun copy(): SContent = create().also {
        it.url = url
        it.title = title
        it.originalTitle = originalTitle
        it.description = description
        it.posterUrl = posterUrl
        it.backdropUrl = backdropUrl
        it.genres = genres
        it.contentType = contentType
        it.status = status
        it.year = year
        it.rating = rating
        it.durationSeconds = durationSeconds
        it.initialized = initialized
    }

    companion object {
        fun create(): SContent = SContentImpl()
    }
}

class SContentImpl : SContent {
    override lateinit var url: String
    override lateinit var title: String
    override var originalTitle: String? = null
    override var description: String? = null
    override var posterUrl: String? = null
    override var backdropUrl: String? = null
    override var genres: List<String>? = null
    override var contentType: ContentType? = null
    override var status: ContentStatus? = null
    override var year: Int? = null
    override var rating: Double? = null
    override var durationSeconds: Long? = null
    override var initialized: Boolean = false
}

interface SPlayableItem : Serializable {
    var url: String
    var title: String
    var seasonNumber: Double?
    var episodeNumber: Double?
    var summary: String?
    var thumbnailUrl: String?
    var airDate: Long?
    var durationSeconds: Long?
    var isSpecial: Boolean

    fun copy(): SPlayableItem = create().also {
        it.url = url
        it.title = title
        it.seasonNumber = seasonNumber
        it.episodeNumber = episodeNumber
        it.summary = summary
        it.thumbnailUrl = thumbnailUrl
        it.airDate = airDate
        it.durationSeconds = durationSeconds
        it.isSpecial = isSpecial
    }

    companion object {
        fun create(): SPlayableItem = SPlayableItemImpl()
    }
}

class SPlayableItemImpl : SPlayableItem {
    override lateinit var url: String
    override lateinit var title: String
    override var seasonNumber: Double? = null
    override var episodeNumber: Double? = null
    override var summary: String? = null
    override var thumbnailUrl: String? = null
    override var airDate: Long? = null
    override var durationSeconds: Long? = null
    override var isSpecial: Boolean = false
}

interface SSeason : Serializable {
    var url: String
    var title: String
    var seasonNumber: Double?
    var posterUrl: String?
    var description: String?

    fun copy(): SSeason = create().also {
        it.url = url
        it.title = title
        it.seasonNumber = seasonNumber
        it.posterUrl = posterUrl
        it.description = description
    }

    companion object {
        fun create(): SSeason = SSeasonImpl()
    }
}

class SSeasonImpl : SSeason {
    override lateinit var url: String
    override lateinit var title: String
    override var seasonNumber: Double? = null
    override var posterUrl: String? = null
    override var description: String? = null
}

data class SHoster(
    val hosterUrl: String = "",
    val hosterName: String = "",
    val displayName: String = hosterName,
    val streamList: List<SStream>? = null,
    val internalData: String = "",
    val lazy: Boolean = false,
) : Serializable

data class SStream(
    val url: String = "",
    val title: String = "",
    val resolution: Int? = null,
    val bitrate: Int? = null,
    val headers: Headers? = null,
    val preferred: Boolean = false,
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
    val timestamps: List<StreamTimestamp> = emptyList(),
    val internalData: String = "",
    val initialized: Boolean = false,
)

data class SubtitleTrack(
    val url: String,
    val lang: String,
    val label: String = lang,
)

data class AudioTrack(
    val url: String,
    val lang: String,
    val label: String = lang,
)

data class StreamTimestamp(
    val startSeconds: Double,
    val endSeconds: Double,
    val title: String,
    val type: StreamTimestampType = StreamTimestampType.OTHER,
)

enum class StreamTimestampType {
    OPENING,
    ENDING,
    RECAP,
    MIXED,
    OTHER,
}

data class ContentPage(
    val contents: List<SContent>,
    val hasNextPage: Boolean,
) {
    companion object {
        val Empty = ContentPage(emptyList(), false)
    }
}

data class HomeSection(
    val id: String,
    val title: String,
)
