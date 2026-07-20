package org.symera.source.torrentutils.parser

import org.symera.source.torrentutils.TorrentLimits
import org.symera.source.torrentutils.model.DeadTorrentException

internal fun parseV2Files(
    root: BencodeDictionary,
    info: BencodeDictionary,
    limits: TorrentLimits,
): List<ParsedFile> {
    val tree = info.requiredDictionary("file tree")
    val files = ArrayList<ParsedFile>()
    val pieceLength = info.requiredInteger("piece length")
    parseFileTree(tree, emptyList(), files, limits, depth = 0)
    if (files.isEmpty()) throw DeadTorrentException("v2 torrent file tree is empty")
    validateUniquePaths(files)
    sumSizes(files.map(ParsedFile::size), limits)
    validatePieceLayers(root, files, pieceLength)
    return files
}

private fun parseFileTree(
    node: BencodeDictionary,
    parent: List<String>,
    output: MutableList<ParsedFile>,
    limits: TorrentLimits,
    depth: Int,
) {
    if (depth >= limits.maxPathSegments) throw DeadTorrentException("Torrent path exceeds the configured segment count")
    node.entries.forEach { (rawName, rawValue) ->
        if (rawName.bytes.isEmpty()) throw DeadTorrentException("Unexpected v2 file leaf")
        val name = rawName.utf8("Torrent path segment", limits.maxTextLength)
        validatePathSegment(name, limits)
        val child = rawValue as? BencodeDictionary ?: throw DeadTorrentException("v2 file tree node is not a dictionary")
        val path = parent + name
        val leaf = child.value("")
        if (leaf != null) {
            parseFileLeaf(child, leaf, path, output, limits)
        } else {
            parseFileTree(child, path, output, limits, depth + 1)
        }
    }
}

private fun parseFileLeaf(
    node: BencodeDictionary,
    leaf: BencodeValue,
    path: List<String>,
    output: MutableList<ParsedFile>,
    limits: TorrentLimits,
) {
    if (node.entries.size != 1) throw DeadTorrentException("v2 path is both a file and a directory")
    val attributes = leaf as? BencodeDictionary ?: throw DeadTorrentException("v2 file leaf is not a dictionary")
    val attributeFlags = attributes.optionalText("attr", limits)
    if (attributes.value("symlink path") != null || attributeFlags?.contains('l') == true) {
        throw DeadTorrentException("Symbolic links are not supported in torrent paths")
    }
    val size = validateFileSize(attributes.requiredInteger("length"), limits)
    val piecesRoot = attributes.value("pieces root")?.let {
        val root = it as? BencodeBytes ?: throw DeadTorrentException("v2 pieces root is not a byte string")
        if (root.bytes.size != V2_HASH_SIZE) throw DeadTorrentException("v2 pieces root must contain 32 bytes")
        root.bytes
    }
    if ((size > 0L) != (piecesRoot != null)) {
        throw DeadTorrentException("v2 pieces root presence does not match the file size")
    }
    output += ParsedFile(
        path = path.joinToString("/"),
        size = size,
        isPadding = attributeFlags?.contains('p') == true,
        piecesRoot = piecesRoot,
    )
    if (output.size > limits.maxFiles) throw DeadTorrentException("Torrent exceeds the configured file count")
}

private fun validatePieceLayers(
    root: BencodeDictionary,
    files: List<ParsedFile>,
    pieceLength: Long,
) {
    val expected = files.filter { it.size > pieceLength }
        .groupBy { requireNotNull(it.piecesRoot).hexKey() }
    val layers = root.requiredDictionary("piece layers")
    if (layers.entries.size != expected.size) throw DeadTorrentException("v2 piece layers do not match the file tree")
    layers.entries.forEach { (rawRoot, rawLayer) ->
        if (rawRoot.bytes.size != V2_HASH_SIZE) throw DeadTorrentException("v2 piece layer key must contain 32 bytes")
        val matchingFiles = expected[rawRoot.bytes.hexKey()]
            ?: throw DeadTorrentException("v2 piece layer has no matching file")
        val layer = rawLayer as? BencodeBytes ?: throw DeadTorrentException("v2 piece layer is not a byte string")
        val pieceCounts = matchingFiles.mapTo(mutableSetOf()) { ((it.size - 1L) / pieceLength) + 1L }
        if (pieceCounts.size != 1) {
            throw DeadTorrentException("Files sharing a v2 pieces root require different piece layer lengths")
        }
        val pieceCount = pieceCounts.single()
        if (pieceCount > Int.MAX_VALUE / V2_HASH_SIZE || layer.bytes.size.toLong() != pieceCount * V2_HASH_SIZE) {
            throw DeadTorrentException("v2 piece layer length does not match its file")
        }
        val rootHash = buildMerkleRoot(layer.bytes.asListOfHashes(), zeroPieceHash(pieceLength))
        if (matchingFiles.any { !rootHash.contentEquals(it.piecesRoot) }) {
            throw DeadTorrentException("v2 piece layer does not match its pieces root")
        }
    }
}

private fun ByteArray.asListOfHashes(): MutableList<ByteArray> {
    val hashes = ArrayList<ByteArray>(size / V2_HASH_SIZE)
    var offset = 0
    while (offset < size) {
        hashes += copyOfRange(offset, offset + V2_HASH_SIZE)
        offset += V2_HASH_SIZE
    }
    return hashes
}

private fun buildMerkleRoot(hashes: MutableList<ByteArray>, paddingHash: ByteArray): ByteArray {
    var targetSize = 1
    while (targetSize < hashes.size) targetSize = targetSize shl 1
    while (hashes.size < targetSize) hashes += paddingHash
    var level = hashes
    while (level.size > 1) {
        val parent = ArrayList<ByteArray>(level.size / 2)
        for (index in level.indices step 2) parent += hashPair(level[index], level[index + 1])
        level = parent
    }
    return level.single()
}

private fun zeroPieceHash(pieceLength: Long): ByteArray {
    var blocks = pieceLength / MIN_V2_PIECE_LENGTH
    var hash = ByteArray(V2_HASH_SIZE)
    while (blocks > 1L) {
        hash = hashPair(hash, hash)
        blocks /= 2L
    }
    return hash
}

private fun hashPair(left: ByteArray, right: ByteArray): ByteArray {
    val data = ByteArray(V2_HASH_SIZE * 2)
    left.copyInto(data, destinationOffset = 0)
    right.copyInto(data, destinationOffset = V2_HASH_SIZE)
    return digest("SHA-256", data)
}

private fun ByteArray.hexKey(): String = joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
