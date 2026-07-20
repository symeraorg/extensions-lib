package org.symera.source.torrentutils.parser

import java.io.ByteArrayOutputStream
import org.symera.source.torrentutils.model.DeadTorrentException

internal fun decodeMagnetComponent(value: String): String {
    val bytes = ByteArrayOutputStream(value.length)
    var index = 0
    while (index < value.length) {
        val character = value[index]
        if (character == '%') {
            if (index + 2 >= value.length) throw DeadTorrentException("Magnet URI contains incomplete percent encoding")
            val high = value[index + 1].digitToIntOrNull(16)
                ?: throw DeadTorrentException("Magnet URI contains invalid percent encoding")
            val low = value[index + 2].digitToIntOrNull(16)
                ?: throw DeadTorrentException("Magnet URI contains invalid percent encoding")
            bytes.write((high shl 4) or low)
            index += 3
        } else {
            val codePoint = value.codePointAt(index)
            if (codePoint in Character.MIN_SURROGATE.code..Character.MAX_SURROGATE.code) {
                throw DeadTorrentException("Magnet URI contains an unpaired Unicode surrogate")
            }
            val encoded = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8)
            bytes.write(encoded, 0, encoded.size)
            index += Character.charCount(codePoint)
        }
    }
    return BencodeBytes(bytes.toByteArray(), 0, bytes.size()).utf8("Magnet parameter", Int.MAX_VALUE)
}

internal fun encodeMagnetComponent(value: String): String = buildString {
    value.toByteArray(Charsets.UTF_8).forEach { byte ->
        val unsigned = byte.toInt() and 0xff
        if ((unsigned in 'a'.code..'z'.code) ||
            (unsigned in 'A'.code..'Z'.code) ||
            (unsigned in '0'.code..'9'.code) ||
            unsigned == '-'.code || unsigned == '.'.code || unsigned == '_'.code || unsigned == '~'.code
        ) {
            append(unsigned.toChar())
        } else {
            append('%')
            append(HEX[unsigned ushr 4])
            append(HEX[unsigned and 0x0f])
        }
    }
}

private const val HEX = "0123456789ABCDEF"
