package org.symera.source.torrentutils.model

import java.io.Serializable

data class TorrentInfo(
    val title: String,
    val files: List<TorrentFile>,
    val hash: String,
    val size: Long,
    val trackers: List<String> = emptyList(),
) : Serializable {
    fun setTrackers(trackers: List<String>): TorrentInfo {
        val distinctTrackers = trackers.distinct()
        return TorrentInfo(
            title = title,
            files = files.map { it.withTrackers(distinctTrackers) },
            hash = hash,
            size = size,
            trackers = distinctTrackers,
        )
    }
}

class DeadTorrentException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
