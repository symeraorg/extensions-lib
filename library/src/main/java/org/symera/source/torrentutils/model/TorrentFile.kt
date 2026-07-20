package org.symera.source.torrentutils.model

import java.io.Serializable

data class TorrentFile(
    val path: String,
    val indexFile: Int,
    val size: Long,
    val hashes: TorrentHashes,
    private val trackers: List<String> = emptyList(),
    private val webSeeds: List<String> = emptyList(),
    private val displayName: String = path,
    val isPadding: Boolean = false,
) : Serializable {
    init {
        require(path.isNotBlank()) { "Torrent file path cannot be blank" }
        require(indexFile >= 0) { "Torrent file index cannot be negative" }
        require(size >= 0) { "Torrent file size cannot be negative" }
    }

    constructor(
        path: String,
        indexFile: Int,
        size: Long,
        torrentHash: String,
        trackers: List<String> = emptyList(),
    ) : this(
        path = path,
        indexFile = indexFile,
        size = size,
        hashes = TorrentHashes(v1 = V1InfoHash.fromString(torrentHash)),
        trackers = trackers.distinct(),
    )

    fun toMagnetURI(): String {
        return MagnetLink(
            hashes = hashes,
            displayName = displayName.takeIf(String::isNotBlank),
            trackers = trackers,
            webSeeds = webSeeds,
            selectedFiles = FileSelection.single(indexFile),
        ).toUri()
    }

    internal fun withTrackers(trackers: List<String>): TorrentFile {
        return copy(trackers = trackers.distinct())
    }
}
