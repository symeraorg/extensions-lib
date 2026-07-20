package org.symera.source.torrentutils.parser

import java.security.MessageDigest
import java.net.URI
import java.util.Locale
import org.symera.source.torrentutils.TorrentLimits
import org.symera.source.torrentutils.model.DeadTorrentException
import org.symera.source.torrentutils.model.TorrentFile
import org.symera.source.torrentutils.model.TorrentHashes
import org.symera.source.torrentutils.model.TorrentInfo
import org.symera.source.torrentutils.model.V1InfoHash
import org.symera.source.torrentutils.model.V2InfoHash

object TorrentMetainfoParser {
    fun parse(
        bytes: ByteArray,
        fallbackTitle: String = "",
        limits: TorrentLimits = TorrentLimits(),
    ): TorrentInfo {
        val root = BencodeParser(bytes, limits).parse() as? BencodeDictionary
            ?: throw DeadTorrentException("Torrent root is not a dictionary")
        val info = root.requiredDictionary("info")
        val metaVersion = info.integer("meta version")
        if (metaVersion != null && metaVersion != V2_META_VERSION) {
            throw DeadTorrentException("Unsupported torrent meta version: $metaVersion")
        }

        val title = info.optionalText("name.utf-8", limits)
            ?: info.optionalText("name", limits)
            ?: fallbackTitle.takeIf(String::isNotBlank)
            ?: throw DeadTorrentException("Torrent is missing a display name")
        validateDisplayName(title, limits)
        validatePieceLength(info, metaVersion == V2_META_VERSION)

        val hasV1Pieces = info.value("pieces") != null
        val v1Files = if (metaVersion == null || hasV1Pieces) parseV1Files(info, title, limits) else null
        val v2Files = if (metaVersion == V2_META_VERSION) parseV2Files(root, info, limits) else null
        if (metaVersion == V2_META_VERSION && hasV1Pieces) {
            validateHybridFiles(v1Files.orEmpty(), v2Files.orEmpty(), info.requiredInteger("piece length"))
        }
        val parsedFiles = v1Files ?: v2Files ?: throw DeadTorrentException("Torrent contains no supported file metadata")

        val infoBytes = bytes.copyOfRange(info.start, info.end)
        val hashes = TorrentHashes(
            v1 = if (v1Files != null) V1InfoHash.fromBytes(digest("SHA-1", infoBytes)) else null,
            v2 = if (v2Files != null) V2InfoHash.fromBytes(digest("SHA-256", infoBytes)) else null,
        )
        val trackers = extractTrackers(root, limits)
        val webSeeds = extractWebSeeds(root, limits)
        val files = parsedFiles.mapIndexed { index, file ->
            TorrentFile(
                path = file.path,
                indexFile = index,
                size = file.size,
                hashes = hashes,
                trackers = trackers,
                webSeeds = webSeeds,
                displayName = title,
                isPadding = file.isPadding,
            )
        }
        val totalSize = sumSizes(files.map(TorrentFile::size), limits)
        return TorrentInfo(
            title = title,
            files = files,
            hashes = hashes,
            size = totalSize,
            trackers = trackers,
            webSeeds = webSeeds,
        )
    }
}

private fun parseV1Files(
    info: BencodeDictionary,
    title: String,
    limits: TorrentLimits,
): List<ParsedFile> {
    val pieces = info.requiredBytes("pieces").bytes
    if (pieces.size % V1_HASH_SIZE != 0) throw DeadTorrentException("Torrent pieces length is not a multiple of 20")

    val filesValue = info.value("files")
    val lengthValue = info.value("length")
    if ((filesValue == null) == (lengthValue == null)) {
        throw DeadTorrentException("v1 torrent must contain exactly one of files or length")
    }
    val files = if (filesValue != null) {
        val list = filesValue as? BencodeList ?: throw DeadTorrentException("Torrent files is not a list")
        if (list.values.isEmpty()) throw DeadTorrentException("Torrent files list is empty")
        if (list.values.size > limits.maxFiles) throw DeadTorrentException("Torrent exceeds the configured file count")
        list.values.map { value ->
            val file = value as? BencodeDictionary ?: throw DeadTorrentException("Torrent file entry is not a dictionary")
            val attributes = file.optionalText("attr", limits)
            if (file.value("symlink path") != null || attributes?.contains('l') == true) {
                throw DeadTorrentException("Symbolic links are not supported in torrent paths")
            }
            val pathList = (file.value("path.utf-8") ?: file.value("path")) as? BencodeList
                ?: throw DeadTorrentException("Torrent file is missing its path")
            val path = parsePath(pathList, limits)
            ParsedFile(
                path = path,
                size = validateFileSize(file.requiredInteger("length"), limits),
                isPadding = attributes?.contains('p') == true,
            )
        }
    } else {
        validatePathSegment(title, limits)
        listOf(
            ParsedFile(
                path = title,
                size = validateFileSize(info.requiredInteger("length"), limits),
                isPadding = info.optionalText("attr", limits)?.contains('p') == true,
            ),
        )
    }
    validateUniquePaths(files)
    val totalSize = sumSizes(files.map(ParsedFile::size), limits)
    val pieceLength = info.requiredInteger("piece length")
    val expectedPieces = if (totalSize == 0L) 0L else ((totalSize - 1L) / pieceLength) + 1L
    if (expectedPieces > Int.MAX_VALUE / V1_HASH_SIZE || pieces.size.toLong() != expectedPieces * V1_HASH_SIZE) {
        throw DeadTorrentException("Torrent pieces count does not match its file sizes")
    }
    return files
}

private fun validatePieceLength(info: BencodeDictionary, isV2: Boolean) {
    val pieceLength = info.requiredInteger("piece length")
    if (pieceLength <= 0) throw DeadTorrentException("Torrent piece length must be positive")
    if (isV2 && (pieceLength < MIN_V2_PIECE_LENGTH || pieceLength and (pieceLength - 1L) != 0L)) {
        throw DeadTorrentException("v2 piece length must be a power of two of at least 16 KiB")
    }
}

private fun validateHybridFiles(
    v1: List<ParsedFile>,
    v2: List<ParsedFile>,
    pieceLength: Long,
) {
    val contentFiles = v1.filterNot(ParsedFile::isPadding)
    if (contentFiles.size != v2.size) throw DeadTorrentException("Hybrid torrent file trees do not match")
    contentFiles.zip(v2).forEach { (legacy, modern) ->
        if (legacy.path != modern.path || legacy.size != modern.size || modern.isPadding) {
            throw DeadTorrentException("Hybrid torrent file trees do not match")
        }
    }
    var offset = 0L
    v1.forEach { file ->
        if (!file.isPadding && file.size > 0L && offset % pieceLength != 0L) {
            throw DeadTorrentException("Hybrid torrent files are not aligned to v2 piece boundaries")
        }
        if (offset > Long.MAX_VALUE - file.size) throw DeadTorrentException("Hybrid torrent file offsets overflow")
        offset += file.size
    }
}

private fun parsePath(path: BencodeList, limits: TorrentLimits): String {
    if (path.values.isEmpty()) throw DeadTorrentException("Torrent file path is empty")
    if (path.values.size > limits.maxPathSegments) throw DeadTorrentException("Torrent path exceeds the configured segment count")
    return path.values.joinToString("/") { value ->
        val segment = (value as? BencodeBytes)?.utf8("Torrent path segment", limits.maxTextLength)
            ?: throw DeadTorrentException("Torrent path segment is not a byte string")
        validatePathSegment(segment, limits)
        segment
    }
}

private fun validateDisplayName(name: String, limits: TorrentLimits) {
    if (name.isBlank()) throw DeadTorrentException("Torrent display name is blank")
    if (name.length > limits.maxTextLength) throw DeadTorrentException("Torrent display name exceeds the configured text limit")
    if (name.any { it == '\u0000' || it.isISOControl() }) throw DeadTorrentException("Torrent display name contains control characters")
}

internal fun validatePathSegment(segment: String, limits: TorrentLimits) {
    if (segment.isEmpty() || segment == "." || segment == "..") throw DeadTorrentException("Torrent contains an unsafe path segment")
    if (segment.length > limits.maxTextLength) throw DeadTorrentException("Torrent path segment exceeds the configured text limit")
    if (segment.any { it == '/' || it == '\\' || it == ':' || it == '\u0000' || it.isISOControl() }) {
        throw DeadTorrentException("Torrent path contains traversal or platform separator characters")
    }
}

internal fun validateFileSize(size: Long, limits: TorrentLimits): Long {
    if (size < 0) throw DeadTorrentException("Torrent file size cannot be negative")
    if (size > limits.maxFileSize) throw DeadTorrentException("Torrent file exceeds the configured size")
    return size
}

internal fun sumSizes(sizes: Iterable<Long>, limits: TorrentLimits): Long {
    var total = 0L
    sizes.forEach { size ->
        if (size < 0 || total > Long.MAX_VALUE - size) throw DeadTorrentException("Torrent total size overflows a signed 64-bit value")
        total += size
        if (total > limits.maxTotalSize) throw DeadTorrentException("Torrent exceeds the configured total size")
    }
    return total
}

internal fun validateUniquePaths(files: List<ParsedFile>) {
    val paths = HashSet<String>(files.size)
    files.forEach { file ->
        if (!paths.add(file.path)) throw DeadTorrentException("Torrent contains duplicate file paths")
    }
    val sorted = paths.sorted()
    for (index in 1 until sorted.size) {
        if (sorted[index].startsWith(sorted[index - 1] + "/")) {
            throw DeadTorrentException("Torrent path is both a file and a directory")
        }
    }
}

private fun extractTrackers(root: BencodeDictionary, limits: TorrentLimits): List<String> {
    val trackers = linkedSetOf<String>()
    root.optionalText("announce", limits)?.takeIf(String::isNotBlank)?.let {
        validateEndpointText(it, "tracker", TRACKER_SCHEMES)
        trackers += it
    }
    val tiers = root.value("announce-list")
    if (tiers != null) {
        val tierList = tiers as? BencodeList ?: throw DeadTorrentException("Torrent announce-list is not a list")
        tierList.values.forEach { tierValue ->
            val tier = tierValue as? BencodeList ?: throw DeadTorrentException("Torrent tracker tier is not a list")
            tier.values.forEach { trackerValue ->
                val tracker = (trackerValue as? BencodeBytes)?.utf8("Torrent tracker", limits.maxTextLength)
                    ?: throw DeadTorrentException("Torrent tracker is not a byte string")
                tracker.takeIf(String::isNotBlank)?.let {
                    validateEndpointText(it, "tracker", TRACKER_SCHEMES)
                    trackers += it
                }
            }
        }
    }
    return trackers.toList()
}

private fun extractWebSeeds(root: BencodeDictionary, limits: TorrentLimits): List<String> {
    val seeds = linkedSetOf<String>()
    collectStringOrList(root.value("url-list"), "Torrent web seed", limits, seeds, WEB_SEED_SCHEMES)
    collectStringOrList(root.value("httpseeds"), "Torrent HTTP seed", limits, seeds, WEB_SEED_SCHEMES)
    return seeds.toList()
}

private fun collectStringOrList(
    value: BencodeValue?,
    label: String,
    limits: TorrentLimits,
    output: MutableSet<String>,
    allowedSchemes: Set<String>,
) {
    when (value) {
        null -> Unit
        is BencodeBytes -> value.utf8(label, limits.maxTextLength).takeIf(String::isNotBlank)?.let {
            validateEndpointText(it, label, allowedSchemes)
            output += it
        }
        is BencodeList -> value.values.forEach { entry ->
            val text = (entry as? BencodeBytes)?.utf8(label, limits.maxTextLength)
                ?: throw DeadTorrentException("$label list contains a non-string value")
            text.takeIf(String::isNotBlank)?.let {
                validateEndpointText(it, label, allowedSchemes)
                output += it
            }
        }
        else -> throw DeadTorrentException("$label is neither a string nor a list")
    }
}

private fun validateEndpointText(value: String, label: String, allowedSchemes: Set<String>) {
    if (value.any { it == '\u0000' || it.isISOControl() }) {
        throw DeadTorrentException("Torrent $label contains control characters")
    }
    val uri = runCatching { URI(value) }.getOrElse { throw DeadTorrentException("Torrent $label is not a valid URI", it) }
    if (!uri.isAbsolute || uri.host == null || uri.userInfo != null || uri.scheme.lowercase(Locale.ROOT) !in allowedSchemes) {
        throw DeadTorrentException("Torrent $label uses an unsupported or unsafe URI")
    }
}

private val TRACKER_SCHEMES = setOf("http", "https", "udp", "ws", "wss")
private val WEB_SEED_SCHEMES = setOf("http", "https")

internal fun BencodeDictionary.requiredDictionary(key: String): BencodeDictionary =
    value(key) as? BencodeDictionary ?: throw DeadTorrentException("Torrent is missing dictionary: $key")

private fun BencodeDictionary.requiredBytes(key: String): BencodeBytes =
    value(key) as? BencodeBytes ?: throw DeadTorrentException("Torrent is missing byte string: $key")

internal fun BencodeDictionary.requiredInteger(key: String): Long =
    (value(key) as? BencodeInteger)?.value ?: throw DeadTorrentException("Torrent is missing integer: $key")

internal fun BencodeDictionary.optionalText(key: String, limits: TorrentLimits): String? {
    val value = value(key) ?: return null
    return (value as? BencodeBytes)?.utf8("Torrent $key", limits.maxTextLength)
        ?: throw DeadTorrentException("Torrent $key is not a byte string")
}

internal fun digest(algorithm: String, bytes: ByteArray): ByteArray = MessageDigest.getInstance(algorithm).digest(bytes)

internal data class ParsedFile(
    val path: String,
    val size: Long,
    val isPadding: Boolean,
    val piecesRoot: ByteArray? = null,
)

private const val V1_HASH_SIZE = 20
internal const val V2_HASH_SIZE = 32
private const val V2_META_VERSION = 2L
internal const val MIN_V2_PIECE_LENGTH = 16L * 1024L
