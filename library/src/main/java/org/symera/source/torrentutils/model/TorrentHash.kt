package org.symera.source.torrentutils.model

import java.io.Serializable

sealed interface TorrentHash : Serializable {
    val hex: String
    val exactTopic: String
}

class V1InfoHash private constructor(
    override val hex: String,
) : TorrentHash {
    override val exactTopic: String
        get() = "urn:btih:$hex"

    override fun equals(other: Any?): Boolean = other is V1InfoHash && hex == other.hex

    override fun hashCode(): Int = hex.hashCode()

    override fun toString(): String = hex

    companion object {
        fun fromString(value: String): V1InfoHash {
            val normalized = value.trim()
            val bytes = when {
                normalized.length == SHA1_HEX_LENGTH -> decodeHex(normalized)
                normalized.length == SHA1_BASE32_LENGTH -> decodeBase32(normalized)
                else -> throw DeadTorrentException("Unsupported v1 info hash format")
            }
            if (bytes.size != SHA1_BYTES) throw DeadTorrentException("A v1 info hash must contain 20 bytes")
            return fromBytes(bytes)
        }

        fun fromBytes(bytes: ByteArray): V1InfoHash {
            if (bytes.size != SHA1_BYTES) throw DeadTorrentException("A v1 info hash must contain 20 bytes")
            return V1InfoHash(bytes.toHex())
        }
    }
}

class V2InfoHash private constructor(
    override val hex: String,
) : TorrentHash {
    val multihashHex: String
        get() = SHA256_MULTIHASH_PREFIX + hex

    override val exactTopic: String
        get() = "urn:btmh:$multihashHex"

    override fun equals(other: Any?): Boolean = other is V2InfoHash && hex == other.hex

    override fun hashCode(): Int = hex.hashCode()

    override fun toString(): String = hex

    companion object {
        fun fromMultihash(value: String): V2InfoHash {
            val normalized = value.trim()
            val multihash = when {
                normalized.startsWith("b", ignoreCase = true) -> decodeBase32(normalized.drop(1))
                normalized.startsWith("f", ignoreCase = true) -> decodeHex(normalized.drop(1))
                else -> decodeHex(normalized)
            }
            if (multihash.size != SHA256_MULTIHASH_BYTES ||
                multihash[0] != SHA256_CODE ||
                multihash[1] != SHA256_BYTES.toByte()
            ) {
                throw DeadTorrentException("btmh must be a 32-byte SHA-256 multihash")
            }
            return fromBytes(multihash.copyOfRange(2, multihash.size))
        }

        fun fromBytes(bytes: ByteArray): V2InfoHash {
            if (bytes.size != SHA256_BYTES) throw DeadTorrentException("A v2 info hash must contain 32 bytes")
            return V2InfoHash(bytes.toHex())
        }
    }
}

data class TorrentHashes(
    val v1: V1InfoHash? = null,
    val v2: V2InfoHash? = null,
) : Serializable {
    init {
        require(v1 != null || v2 != null) { "At least one torrent hash is required" }
    }

    val preferred: TorrentHash
        get() = v1 ?: requireNotNull(v2)

    val exactTopics: List<String>
        get() = listOfNotNull(v1?.exactTopic, v2?.exactTopic)
}

private const val SHA1_BYTES = 20
private const val SHA1_HEX_LENGTH = SHA1_BYTES * 2
private const val SHA1_BASE32_LENGTH = 32
private const val SHA256_BYTES = 32
private const val SHA256_MULTIHASH_BYTES = 34
private const val SHA256_MULTIHASH_PREFIX = "1220"
private const val SHA256_CODE: Byte = 0x12
private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

private fun decodeHex(value: String): ByteArray {
    if (value.length % 2 != 0) throw DeadTorrentException("Hex hash has an odd length")
    return ByteArray(value.length / 2) { index ->
        val high = value[index * 2].digitToIntOrNull(16)
            ?: throw DeadTorrentException("Hash contains invalid hexadecimal data")
        val low = value[index * 2 + 1].digitToIntOrNull(16)
            ?: throw DeadTorrentException("Hash contains invalid hexadecimal data")
        ((high shl 4) or low).toByte()
    }
}

private fun decodeBase32(value: String): ByteArray {
    if (value.isEmpty()) throw DeadTorrentException("Base32 hash is empty")
    var buffer = 0
    var bits = 0
    val output = ArrayList<Byte>((value.length * 5) / 8)
    value.uppercase().forEach { character ->
        val digit = BASE32_ALPHABET.indexOf(character)
        if (digit < 0) throw DeadTorrentException("Hash contains invalid base32 data")
        buffer = (buffer shl 5) or digit
        bits += 5
        if (bits >= 8) {
            bits -= 8
            output += ((buffer shr bits) and 0xff).toByte()
            buffer = buffer and ((1 shl bits) - 1)
        }
    }
    if (bits > 0 && buffer != 0) throw DeadTorrentException("Base32 hash has non-zero padding bits")
    return output.toByteArray()
}

internal fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
    HEX_DIGITS[(byte.toInt() ushr 4) and 0x0f].toString() + HEX_DIGITS[byte.toInt() and 0x0f]
}

private const val HEX_DIGITS = "0123456789abcdef"
