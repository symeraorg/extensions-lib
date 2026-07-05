package org.symera.source.torrentutils.model

import java.io.Serializable
import java.net.URLEncoder

data class TorrentFile(
    val path: String,
    val indexFile: Int,
    val size: Long,
    private val torrentHash: String,
    private val trackers: List<String> = emptyList(),
) : Serializable {
    fun toMagnetURI(): String {
        return buildString {
            append("magnet:?xt=urn:btih:")
            append(torrentHash)
            if (path.isNotBlank()) {
                append("&dn=")
                append(path.urlEncode())
            }
            trackers.distinct().forEach { tracker ->
                append("&tr=")
                append(tracker.urlEncode())
            }
        }
    }

    internal fun withTrackers(trackers: List<String>): TorrentFile {
        return TorrentFile(path, indexFile, size, torrentHash, trackers)
    }
}

private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
