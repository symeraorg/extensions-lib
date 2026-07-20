package org.symera.source.torrentutils.parser

import org.symera.source.torrentutils.TorrentLimits
import org.symera.source.torrentutils.model.DeadTorrentException

internal class BencodeParser(
    private val input: ByteArray,
    private val limits: TorrentLimits,
) {
    private var position = 0
    private var elementCount = 0

    fun parse(): BencodeValue {
        if (input.size > limits.maxInputBytes) throw DeadTorrentException("Torrent exceeds the configured byte limit")
        if (input.isEmpty()) throw DeadTorrentException("Torrent data is empty")
        val value = parseValue(depth = 1)
        if (position != input.size) throw DeadTorrentException("Torrent contains trailing bencode data")
        return value
    }

    private fun parseValue(depth: Int): BencodeValue {
        if (depth > limits.maxDepth) throw DeadTorrentException("Bencode nesting exceeds the configured depth")
        countElement()
        if (position >= input.size) throw DeadTorrentException("Unexpected end of bencode data")
        return when (input[position].toInt().toChar()) {
            'i' -> parseInteger()
            'l' -> parseList(depth)
            'd' -> parseDictionary(depth)
            in '0'..'9' -> parseBytes(countAsElement = false)
            else -> throw DeadTorrentException("Invalid bencode token at byte $position")
        }
    }

    private fun parseInteger(): BencodeInteger {
        val start = position++
        val numberStart = position
        while (position < input.size && input[position] != END) position++
        if (position >= input.size) throw DeadTorrentException("Unterminated bencode integer")
        if (numberStart == position) throw DeadTorrentException("Empty bencode integer")

        val first = input[numberStart]
        val digitsStart = if (first == MINUS) numberStart + 1 else numberStart
        if (digitsStart == position) throw DeadTorrentException("Invalid bencode integer")
        if (input[digitsStart] == ZERO && position - digitsStart > 1) {
            throw DeadTorrentException("Bencode integer has a leading zero")
        }
        if (first == MINUS && input[digitsStart] == ZERO) throw DeadTorrentException("Bencode negative zero is not canonical")
        for (index in digitsStart until position) {
            if (input[index] !in ZERO..NINE) throw DeadTorrentException("Invalid bencode integer")
        }

        val text = input.decodeToString(numberStart, position)
        val value = text.toLongOrNull() ?: throw DeadTorrentException("Bencode integer overflows a signed 64-bit value")
        position++
        return BencodeInteger(value, start, position)
    }

    private fun parseBytes(countAsElement: Boolean): BencodeBytes {
        if (countAsElement) countElement()
        val start = position
        if (input[position] == ZERO && position + 1 < input.size && input[position + 1] != COLON) {
            throw DeadTorrentException("Bencode byte string length has a leading zero")
        }

        var length = 0
        while (position < input.size && input[position] in ZERO..NINE) {
            val digit = input[position] - ZERO
            if (length > (Int.MAX_VALUE - digit) / 10) {
                throw DeadTorrentException("Bencode byte string length overflows")
            }
            length = length * 10 + digit
            position++
        }
        if (position >= input.size || input[position] != COLON) {
            throw DeadTorrentException("Invalid bencode byte string length")
        }
        if (length > limits.maxByteStringLength) {
            throw DeadTorrentException("Bencode byte string exceeds the configured length")
        }
        position++
        val valueStart = position
        if (length > input.size - valueStart) throw DeadTorrentException("Bencode byte string exceeds torrent size")
        val valueEnd = valueStart + length
        position = valueEnd
        return BencodeBytes(input.copyOfRange(valueStart, valueEnd), start, position)
    }

    private fun parseList(depth: Int): BencodeList {
        val start = position++
        val values = ArrayList<BencodeValue>()
        while (position < input.size && input[position] != END) {
            values += parseValue(depth + 1)
        }
        if (position >= input.size) throw DeadTorrentException("Unterminated bencode list")
        position++
        return BencodeList(values, start, position)
    }

    private fun parseDictionary(depth: Int): BencodeDictionary {
        val start = position++
        val entries = ArrayList<Pair<BencodeBytes, BencodeValue>>()
        var previousKey: ByteArray? = null
        while (position < input.size && input[position] != END) {
            if (input[position] !in ZERO..NINE) throw DeadTorrentException("Bencode dictionary key is not a byte string")
            val key = parseBytes(countAsElement = true)
            previousKey?.let { previous ->
                if (compareUnsigned(previous, key.bytes) >= 0) {
                    throw DeadTorrentException("Bencode dictionary keys are duplicated or not canonically sorted")
                }
            }
            previousKey = key.bytes
            entries += key to parseValue(depth + 1)
        }
        if (position >= input.size) throw DeadTorrentException("Unterminated bencode dictionary")
        position++
        return BencodeDictionary(entries, start, position)
    }

    private fun countElement() {
        elementCount++
        if (elementCount > limits.maxElements) throw DeadTorrentException("Bencode element count exceeds the configured limit")
    }

    private companion object {
        val ZERO = '0'.code.toByte()
        val NINE = '9'.code.toByte()
        val MINUS = '-'.code.toByte()
        val COLON = ':'.code.toByte()
        val END = 'e'.code.toByte()
    }
}
