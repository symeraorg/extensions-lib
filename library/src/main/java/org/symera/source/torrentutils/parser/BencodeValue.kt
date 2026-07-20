package org.symera.source.torrentutils.parser

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import org.symera.source.torrentutils.model.DeadTorrentException

internal sealed class BencodeValue(
    open val start: Int,
    open val end: Int,
)

internal data class BencodeBytes(
    val bytes: ByteArray,
    override val start: Int,
    override val end: Int,
) : BencodeValue(start, end) {
    fun utf8(label: String, maxLength: Int): String {
        if (bytes.size > maxLength) throw DeadTorrentException("$label exceeds the configured text limit")
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (exception: java.nio.charset.CharacterCodingException) {
            throw DeadTorrentException("$label is not valid UTF-8", exception)
        }
    }
}

internal data class BencodeInteger(
    val value: Long,
    override val start: Int,
    override val end: Int,
) : BencodeValue(start, end)

internal data class BencodeList(
    val values: List<BencodeValue>,
    override val start: Int,
    override val end: Int,
) : BencodeValue(start, end)

internal data class BencodeDictionary(
    val entries: List<Pair<BencodeBytes, BencodeValue>>,
    override val start: Int,
    override val end: Int,
) : BencodeValue(start, end) {
    fun value(key: String): BencodeValue? {
        val encodedKey = key.toByteArray(StandardCharsets.US_ASCII)
        return entries.firstOrNull { (entryKey) -> entryKey.bytes.contentEquals(encodedKey) }?.second
    }

    fun integer(key: String): Long? = (value(key) as? BencodeInteger)?.value
}

internal fun compareUnsigned(left: ByteArray, right: ByteArray): Int {
    val commonLength = minOf(left.size, right.size)
    for (index in 0 until commonLength) {
        val comparison = (left[index].toInt() and 0xff).compareTo(right[index].toInt() and 0xff)
        if (comparison != 0) return comparison
    }
    return left.size.compareTo(right.size)
}
