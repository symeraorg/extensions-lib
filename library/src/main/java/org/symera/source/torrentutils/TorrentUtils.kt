package org.symera.source.torrentutils

import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import okhttp3.OkHttpClient
import okhttp3.Request
import org.symera.source.torrentutils.model.DeadTorrentException
import org.symera.source.torrentutils.model.TorrentFile
import org.symera.source.torrentutils.model.TorrentInfo

object TorrentUtils {
    @Volatile
    var defaultClientProvider: () -> OkHttpClient = { fallbackClient }

    fun getTorrentInfo(url: String, title: String): TorrentInfo {
        val trimmedUrl = url.trim()
        if (trimmedUrl.startsWith("magnet:", ignoreCase = true)) {
            return parseMagnet(trimmedUrl, title)
        }

        val bytes = loadTorrentBytes(trimmedUrl)
        return parseTorrent(bytes, title)
    }

    private fun loadTorrentBytes(url: String): ByteArray {
        return try {
            when {
                url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true) -> {
                    val request = Request.Builder().url(url).build()
                    defaultClientProvider().newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw DeadTorrentException("Torrent request failed with HTTP ${response.code}")
                        }
                        response.body.bytes()
                    }
                }
                url.startsWith("file://", ignoreCase = true) -> File(URI(url)).readBytes()
                else -> File(url).takeIf { it.isFile }?.readBytes()
                    ?: throw DeadTorrentException("Unsupported torrent URL: $url")
            }
        } catch (exception: DeadTorrentException) {
            throw exception
        } catch (exception: Throwable) {
            throw DeadTorrentException("Unable to load torrent: $url", exception)
        }
    }

    private fun parseTorrent(bytes: ByteArray, fallbackTitle: String): TorrentInfo {
        return try {
            val root = BencodeParser(bytes).parse() as? BDictionary
                ?: throw DeadTorrentException("Torrent root is not a dictionary")
            val info = root.dictionary("info") ?: throw DeadTorrentException("Torrent is missing info dictionary")
            val trackers = root.extractTrackers()
            val hash = sha1Hex(bytes.copyOfRange(info.start, info.end))
            val title = info.string("name")?.takeIf { it.isNotBlank() } ?: fallbackTitle
            val files = info.list("files")?.values?.mapIndexedNotNull { index, value ->
                val file = value as? BDictionary ?: return@mapIndexedNotNull null
                val path = file.list("path")
                    ?.values
                    ?.mapNotNull { (it as? BBytes)?.string() }
                    ?.joinToString("/")
                    ?.takeIf { it.isNotBlank() }
                    ?: title
                val size = file.long("length") ?: 0L
                TorrentFile(path, index, size, hash, trackers)
            } ?: listOf(
                TorrentFile(
                    path = title,
                    indexFile = 0,
                    size = info.long("length") ?: 0L,
                    torrentHash = hash,
                    trackers = trackers,
                ),
            )

            TorrentInfo(
                title = title,
                files = files,
                hash = hash,
                size = files.sumOf { it.size },
                trackers = trackers,
            )
        } catch (exception: DeadTorrentException) {
            throw exception
        } catch (exception: Throwable) {
            throw DeadTorrentException("Unable to parse torrent", exception)
        }
    }

    private fun parseMagnet(url: String, fallbackTitle: String): TorrentInfo {
        val params = url.substringAfter('?', missingDelimiterValue = "")
            .split('&')
            .filter { it.isNotBlank() }
            .mapNotNull { part ->
                val key = part.substringBefore('=', missingDelimiterValue = "")
                val value = part.substringAfter('=', missingDelimiterValue = "")
                if (key.isBlank()) null else key.urlDecode() to value.urlDecode()
            }

        val hash = params
            .firstOrNull { it.first == "xt" && it.second.startsWith("urn:btih:", ignoreCase = true) }
            ?.second
            ?.substringAfterLast(':')
            ?.normalizeInfoHash()
            ?: throw DeadTorrentException("Magnet link is missing a BitTorrent info hash")

        val title = params.firstOrNull { it.first == "dn" }?.second?.takeIf { it.isNotBlank() } ?: fallbackTitle
        val trackers = params.filter { it.first == "tr" }.map { it.second }.filter { it.isNotBlank() }.distinct()

        return TorrentInfo(
            title = title,
            files = emptyList(),
            hash = hash,
            size = 0L,
            trackers = trackers,
        )
    }

    private fun BDictionary.extractTrackers(): List<String> {
        val trackers = linkedSetOf<String>()
        string("announce")?.takeIf { it.isNotBlank() }?.let(trackers::add)
        list("announce-list")?.values?.forEach { it.collectStrings(trackers) }
        return trackers.toList()
    }

    private fun BValue.collectStrings(output: MutableSet<String>) {
        when (this) {
            is BBytes -> string().takeIf { it.isNotBlank() }?.let(output::add)
            is BList -> values.forEach { it.collectStrings(output) }
            is BDictionary -> values.forEach { it.second.collectStrings(output) }
            is BInteger -> Unit
        }
    }

    private fun sha1Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-1")
            .digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun String.normalizeInfoHash(): String {
        val normalized = trim()
        return when {
            normalized.length == 40 && normalized.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' } -> normalized.lowercase()
            normalized.length == 32 -> decodeBase32(normalized).toHex()
            else -> throw DeadTorrentException("Unsupported BitTorrent info hash format")
        }
    }

    private fun decodeBase32(value: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var buffer = 0
        var bitsLeft = 0
        val output = ArrayList<Byte>()

        value.uppercase().forEach { char ->
            val index = alphabet.indexOf(char)
            if (index == -1) throw DeadTorrentException("Invalid base32 info hash")
            buffer = (buffer shl 5) or index
            bitsLeft += 5
            if (bitsLeft >= 8) {
                output += ((buffer shr (bitsLeft - 8)) and 0xff).toByte()
                bitsLeft -= 8
            }
        }

        return output.toByteArray()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun String.urlDecode(): String = URLDecoder.decode(this, Charsets.UTF_8.name())

    private val fallbackClient = OkHttpClient.Builder().build()
}

private sealed class BValue(open val start: Int, open val end: Int)

private data class BBytes(
    val value: ByteArray,
    override val start: Int,
    override val end: Int,
) : BValue(start, end) {
    fun string(): String = value.toString(Charsets.UTF_8)
}

private data class BInteger(
    val value: Long,
    override val start: Int,
    override val end: Int,
) : BValue(start, end)

private data class BList(
    val values: List<BValue>,
    override val start: Int,
    override val end: Int,
) : BValue(start, end)

private data class BDictionary(
    val values: List<Pair<String, BValue>>,
    override val start: Int,
    override val end: Int,
) : BValue(start, end) {
    fun string(key: String): String? = (value(key) as? BBytes)?.string()

    fun long(key: String): Long? = (value(key) as? BInteger)?.value

    fun list(key: String): BList? = value(key) as? BList

    fun dictionary(key: String): BDictionary? = value(key) as? BDictionary

    private fun value(key: String): BValue? = values.firstOrNull { it.first == key }?.second
}

private class BencodeParser(private val bytes: ByteArray) {
    private var index = 0

    fun parse(): BValue {
        val value = parseValue()
        if (index != bytes.size) throw DeadTorrentException("Torrent contains trailing bencode data")
        return value
    }

    private fun parseValue(): BValue {
        if (index >= bytes.size) throw DeadTorrentException("Unexpected end of bencode data")
        return when (bytes[index].toInt().toChar()) {
            'i' -> parseInteger()
            'l' -> parseList()
            'd' -> parseDictionary()
            in '0'..'9' -> parseBytes()
            else -> throw DeadTorrentException("Invalid bencode token at byte $index")
        }
    }

    private fun parseInteger(): BInteger {
        val start = index
        index++
        val valueStart = index
        while (index < bytes.size && bytes[index].toInt().toChar() != 'e') index++
        if (index >= bytes.size) throw DeadTorrentException("Unterminated bencode integer")
        val value = bytes.decodeToString(valueStart, index).toLongOrNull()
            ?: throw DeadTorrentException("Invalid bencode integer")
        index++
        return BInteger(value, start, index)
    }

    private fun parseBytes(): BBytes {
        val start = index
        var length = 0
        while (index < bytes.size && bytes[index].toInt().toChar().isDigit()) {
            length = length * 10 + (bytes[index].toInt().toChar() - '0')
            index++
        }
        if (index >= bytes.size || bytes[index].toInt().toChar() != ':') {
            throw DeadTorrentException("Invalid bencode byte string length")
        }
        index++
        val valueStart = index
        val valueEnd = valueStart + length
        if (valueEnd > bytes.size) throw DeadTorrentException("Bencode byte string exceeds torrent size")
        index = valueEnd
        return BBytes(bytes.copyOfRange(valueStart, valueEnd), start, index)
    }

    private fun parseList(): BList {
        val start = index
        index++
        val values = mutableListOf<BValue>()
        while (index < bytes.size && bytes[index].toInt().toChar() != 'e') {
            values += parseValue()
        }
        if (index >= bytes.size) throw DeadTorrentException("Unterminated bencode list")
        index++
        return BList(values, start, index)
    }

    private fun parseDictionary(): BDictionary {
        val start = index
        index++
        val values = mutableListOf<Pair<String, BValue>>()
        while (index < bytes.size && bytes[index].toInt().toChar() != 'e') {
            val key = parseBytes().string()
            val value = parseValue()
            values += key to value
        }
        if (index >= bytes.size) throw DeadTorrentException("Unterminated bencode dictionary")
        index++
        return BDictionary(values, start, index)
    }
}
