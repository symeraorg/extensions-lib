package org.symera.source.torrentutils.model

import java.io.Serializable
import org.symera.source.torrentutils.parser.encodeMagnetComponent

data class MagnetLink(
    val hashes: TorrentHashes,
    val displayName: String? = null,
    val trackers: List<String> = emptyList(),
    val webSeeds: List<String> = emptyList(),
    val selectedFiles: FileSelection? = null,
) : Serializable {
    fun toUri(): String = buildString {
        append("magnet:?")
        val parameters = buildList {
            hashes.exactTopics.forEach { add("xt" to it) }
            displayName?.takeIf(String::isNotBlank)?.let { add("dn" to it) }
            trackers.distinct().forEach { add("tr" to it) }
            webSeeds.distinct().forEach { add("ws" to it) }
            selectedFiles?.let { add("so" to it.toParameter()) }
        }
        parameters.forEachIndexed { index, (key, value) ->
            if (index > 0) append('&')
            append(key)
            append('=')
            append(encodeMagnetComponent(value))
        }
    }
}
