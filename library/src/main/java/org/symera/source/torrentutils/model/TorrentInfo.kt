package org.symera.source.torrentutils.model

import java.io.Serializable

data class TorrentInfo(
    val title: String?,
    val files: List<TorrentFile>,
    val hashes: TorrentHashes,
    val size: Long?,
    val trackers: List<String> = emptyList(),
    val webSeeds: List<String> = emptyList(),
    val selectedFiles: FileSelection? = null,
) : Serializable {
    init {
        require(title == null || title.isNotBlank()) { "Torrent title cannot be blank" }
        require(size == null || size >= 0) { "Torrent size cannot be negative" }
    }

    val hash: String
        get() = hashes.preferred.hex

    constructor(
        title: String,
        files: List<TorrentFile>,
        hash: String,
        size: Long,
        trackers: List<String> = emptyList(),
    ) : this(
        title = title,
        files = files,
        hashes = TorrentHashes(v1 = V1InfoHash.fromString(hash)),
        size = size,
        trackers = trackers.distinct(),
    )

    fun setTrackers(trackers: List<String>): TorrentInfo {
        val distinctTrackers = trackers.distinct()
        return copy(
            files = files.map { it.withTrackers(distinctTrackers) },
            trackers = distinctTrackers,
        )
    }
}

class DeadTorrentException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
