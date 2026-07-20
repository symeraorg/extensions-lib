package org.symera.source.iptv.parser

import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import org.symera.source.iptv.IptvStreamProtocol

internal fun findUnquotedComma(value: String): Int {
    var quote: Char? = null
    var escaped = false
    value.forEachIndexed { index, character ->
        if (escaped) {
            escaped = false
        } else if (character == '\\') {
            escaped = true
        } else if (quote != null && character == quote) {
            quote = null
        } else if (quote == null && (character == '"' || character == '\'')) {
            quote = character
        } else if (quote == null && character == ',') {
            return index
        }
    }
    return -1
}

internal fun detectProtocol(uri: URI, attributes: Map<String, String>): IptvStreamProtocol {
    attributes["mime-type"]?.lowercase(Locale.ROOT)?.let { mime ->
        if ("mpegurl" in mime) return IptvStreamProtocol.HLS
        if ("dash" in mime) return IptvStreamProtocol.DASH
    }
    return when (uri.scheme?.lowercase(Locale.ROOT)) {
        "rtsp", "rtsps" -> IptvStreamProtocol.RTSP
        "rtp" -> IptvStreamProtocol.RTP
        "udp" -> IptvStreamProtocol.UDP
        else -> when {
            uri.path.orEmpty().endsWith(".m3u8", true) -> IptvStreamProtocol.HLS
            uri.path.orEmpty().endsWith(".mpd", true) -> IptvStreamProtocol.DASH
            else -> IptvStreamProtocol.AUTO
        }
    }
}

internal fun stableId(
    uri: URI,
    epgId: String?,
    name: String,
    group: String?,
    channelNumber: String?,
): String {
    val providerIdentity = epgId?.takeIf(String::isNotBlank)?.let { "epg:$it" }
        ?: "uri:${uri.normalize().toASCIIString()}"
    val identity = listOf(providerIdentity, name, group.orEmpty(), channelNumber.orEmpty()).joinToString("\n")
    val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
    return "m3u:" + digest.take(12).joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
}

internal fun URI.origin(): String = buildString {
    val defaultPort = if (scheme.equals("https", true)) 443 else 80
    append(scheme.lowercase(Locale.ROOT)).append("://")
        .append(requireNotNull(host) { "Playback URI has no host" }.lowercase(Locale.ROOT))
    if (port >= 0 && port != defaultPort) append(':').append(port)
}

internal fun header(headers: Map<String, String>, name: String): String? =
    headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

internal fun parseBoolean(value: String): Boolean =
    value.equals("true", true) || value.equals("yes", true) || value == "1"

internal fun percentDecode(value: String): String {
    val result = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        if (value[index] == '%' && index + 2 < value.length) {
            val bytes = mutableListOf<Byte>()
            while (index + 2 < value.length && value[index] == '%') {
                val number = value.substring(index + 1, index + 3).toIntOrNull(16) ?: break
                bytes += number.toByte()
                index += 3
            }
            if (bytes.isNotEmpty()) {
                result.append(bytes.toByteArray().toString(Charsets.UTF_8))
                continue
            }
        }
        result.append(value[index++])
    }
    return result.toString()
}
