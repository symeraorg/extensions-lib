package org.symera.source.torrentutils.parser

import java.net.URI
import java.util.Locale
import org.symera.source.torrentutils.TorrentLimits
import org.symera.source.torrentutils.model.DeadTorrentException
import org.symera.source.torrentutils.model.FileSelection
import org.symera.source.torrentutils.model.MagnetLink
import org.symera.source.torrentutils.model.TorrentHashes
import org.symera.source.torrentutils.model.V1InfoHash
import org.symera.source.torrentutils.model.V2InfoHash

object MagnetParser {
    fun parse(uri: String, limits: TorrentLimits = TorrentLimits()): MagnetLink {
        val value = uri.trim()
        if (value.length > limits.maxMagnetLength) throw DeadTorrentException("Magnet URI exceeds the configured length")
        if (!value.startsWith("magnet:?", ignoreCase = true)) throw DeadTorrentException("Not a magnet URI")

        val rawQuery = value.substringAfter('?').substringBefore('#')
        val parts = if (rawQuery.isEmpty()) emptyList() else rawQuery.split('&')
        if (parts.size > limits.maxMagnetParameters) {
            throw DeadTorrentException("Magnet URI exceeds the configured parameter count")
        }
        val parameters = parts.map { part ->
            val separator = part.indexOf('=')
            val rawKey = if (separator < 0) part else part.substring(0, separator)
            val rawValue = if (separator < 0) "" else part.substring(separator + 1)
            val key = decodeMagnetComponent(rawKey).lowercase()
            val decodedValue = decodeMagnetComponent(rawValue)
            if (key.length > limits.maxTextLength || decodedValue.length > limits.maxTextLength) {
                throw DeadTorrentException("Magnet parameter exceeds the configured text limit")
            }
            key to decodedValue
        }

        var v1: V1InfoHash? = null
        var v2: V2InfoHash? = null
        parameters.filter { it.first == "xt" }.forEach { (_, topic) ->
            when {
                topic.startsWith(BTIH_PREFIX, ignoreCase = true) -> {
                    val parsed = V1InfoHash.fromString(topic.substring(BTIH_PREFIX.length))
                    if (v1 != null && v1 != parsed) throw DeadTorrentException("Magnet URI contains conflicting v1 hashes")
                    v1 = parsed
                }
                topic.startsWith(BTMH_PREFIX, ignoreCase = true) -> {
                    val parsed = V2InfoHash.fromMultihash(topic.substring(BTMH_PREFIX.length))
                    if (v2 != null && v2 != parsed) throw DeadTorrentException("Magnet URI contains conflicting v2 hashes")
                    v2 = parsed
                }
            }
        }
        if (v1 == null && v2 == null) throw DeadTorrentException("Magnet URI is missing a supported BitTorrent exact topic")

        val displayNames = parameters.filter { it.first == "dn" }.map { it.second }.filter(String::isNotBlank)
        if (displayNames.distinct().size > 1) throw DeadTorrentException("Magnet URI contains conflicting display names")
        displayNames.forEach { it.validateSafeText("display name") }
        val selections = parameters.filter { it.first == "so" }.map { it.second }
        if (selections.distinct().size > 1) throw DeadTorrentException("Magnet URI contains conflicting file selections")
        val trackers = parameters.values("tr").onEach { it.validateEndpoint("tracker", TRACKER_SCHEMES) }
        val webSeeds = parameters.values("ws").onEach { it.validateEndpoint("web seed", WEB_SEED_SCHEMES) }

        return MagnetLink(
            hashes = TorrentHashes(v1 = v1, v2 = v2),
            displayName = displayNames.firstOrNull(),
            trackers = trackers,
            webSeeds = webSeeds,
            selectedFiles = selections.firstOrNull()?.let { FileSelection.parse(it, limits.maxFiles) },
        )
    }
}

private fun List<Pair<String, String>>.values(key: String): List<String> =
    asSequence().filter { it.first == key }.map { it.second }.filter(String::isNotBlank).distinct().toList()

private fun String.validateSafeText(label: String) {
    if (any { it == '\u0000' || it.isISOControl() }) throw DeadTorrentException("Magnet $label contains control characters")
}

private fun String.validateEndpoint(label: String, allowedSchemes: Set<String>) {
    validateSafeText(label)
    val uri = runCatching { URI(this) }.getOrElse { throw DeadTorrentException("Magnet $label is not a valid URI", it) }
    if (!uri.isAbsolute || uri.host == null || uri.userInfo != null || uri.scheme.lowercase(Locale.ROOT) !in allowedSchemes) {
        throw DeadTorrentException("Magnet $label uses an unsupported or unsafe URI")
    }
}

private const val BTIH_PREFIX = "urn:btih:"
private const val BTMH_PREFIX = "urn:btmh:"
private val TRACKER_SCHEMES = setOf("http", "https", "udp", "ws", "wss")
private val WEB_SEED_SCHEMES = setOf("http", "https")
