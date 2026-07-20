package org.symera.source.iptv.parser

import org.symera.source.iptv.IptvCatchUp
import org.symera.source.iptv.IptvChannel
import org.symera.source.iptv.IptvChannelKind
import org.symera.source.iptv.IptvGroup
import org.symera.source.iptv.IptvHeaderRule
import org.symera.source.iptv.IptvPlaybackRequest
import org.symera.source.iptv.IptvRequestKind
import org.symera.source.iptv.IptvTimeshift
import java.io.BufferedReader
import java.io.Reader
import java.net.URI
import java.util.Locale

/** Real Extended M3U catalog parser. The supplied [Reader] remains owned by the caller. */
class ExtendedM3uParser(
    private val mode: IptvParseMode = IptvParseMode.LENIENT,
    private val limits: IptvParseLimits = IptvParseLimits(),
) {
    fun parse(reader: Reader, baseUri: URI? = null): IptvParseResult<IptvPlaylist> {
        val diagnostics = mutableListOf<IptvParseDiagnostic>()
        val entries = mutableListOf<IptvPlaylistEntry>()
        val playlistAttributes = linkedMapOf<String, String>()
        var pending: PendingEntry? = null
        var sawContent = false
        var lineNumber = 0
        val input = if (reader is BufferedReader) reader else reader.buffered()

        while (true) {
            val rawLine = input.readLine() ?: break
            lineNumber++
            if (lineNumber > limits.maximumLines) {
                fatal(diagnostics, IptvDiagnosticCode.RESOURCE_LIMIT_EXCEEDED, "Playlist exceeds line limit", lineNumber)
            }
            if (rawLine.length > limits.maximumLineLength) {
                fatal(diagnostics, IptvDiagnosticCode.RESOURCE_LIMIT_EXCEEDED, "Playlist line exceeds length limit", lineNumber)
            }
            val line = rawLine.removePrefix("\uFEFF").trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXT-X-", ignoreCase = true)) {
                fatal(
                    diagnostics,
                    IptvDiagnosticCode.HLS_MANIFEST,
                    "HLS media playlists are playback manifests, not channel catalogs",
                    lineNumber,
                )
            }

            if (!sawContent) {
                sawContent = true
                if (line.startsWith("#EXTM3U", ignoreCase = true)) {
                    playlistAttributes.putAll(parseAttributes(line.substring(7), lineNumber, diagnostics))
                    continue
                }
                report(
                    diagnostics,
                    IptvDiagnosticCode.INVALID_HEADER,
                    "Expected #EXTM3U as the first non-empty line",
                    lineNumber,
                )
            }

            when {
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    pending?.let {
                        report(
                            diagnostics,
                            IptvDiagnosticCode.MISSING_STREAM_URI,
                            "Discarded EXTINF without a following stream URI",
                            it.line,
                        )
                    }
                    pending = parseExtInf(line.substringAfter(':'), lineNumber, diagnostics)
                }
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val current = pending
                    if (current == null) {
                        report(
                            diagnostics,
                            IptvDiagnosticCode.MALFORMED_DIRECTIVE,
                            "EXTVLCOPT has no preceding EXTINF",
                            lineNumber,
                        )
                    } else {
                        val option = line.substringAfter(':')
                        val separator = option.indexOf('=')
                        if (separator <= 0) {
                            report(
                                diagnostics,
                                IptvDiagnosticCode.MALFORMED_DIRECTIVE,
                                "EXTVLCOPT must use key=value",
                                lineNumber,
                            )
                        } else {
                            current.vlcOptions[option.substring(0, separator).trim().lowercase(Locale.ROOT)] =
                                option.substring(separator + 1).trim()
                        }
                    }
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    val group = line.substringAfter(':').trim()
                    if (pending == null || group.isEmpty()) {
                        report(
                            diagnostics,
                            IptvDiagnosticCode.MALFORMED_DIRECTIVE,
                            "EXTGRP must follow EXTINF and contain a group",
                            lineNumber,
                        )
                    } else {
                        pending.group = group
                    }
                }
                line.startsWith('#') -> Unit
                else -> {
                    val metadata = pending
                    if (metadata == null) {
                        report(
                            diagnostics,
                            IptvDiagnosticCode.MISSING_CHANNEL_METADATA,
                            "Stream URI has no preceding EXTINF",
                            lineNumber,
                        )
                    }
                    buildEntry(metadata ?: PendingEntry(line = lineNumber), line, baseUri, diagnostics)?.let {
                        if (entries.size >= limits.maximumEntries) {
                            fatal(
                                diagnostics,
                                IptvDiagnosticCode.RESOURCE_LIMIT_EXCEEDED,
                                "Playlist exceeds channel limit",
                                lineNumber,
                            )
                        }
                        entries.add(it)
                    }
                    pending = null
                }
            }
        }

        pending?.let {
            report(
                diagnostics,
                IptvDiagnosticCode.MISSING_STREAM_URI,
                "Playlist ended before the stream URI for EXTINF",
                it.line,
            )
        }
        if (!sawContent) {
            report(diagnostics, IptvDiagnosticCode.INVALID_HEADER, "Playlist is empty", 1)
        }

        val groups = entries
            .flatMap { entry -> entry.channel.groupIds.map { it to it } }
            .distinctBy { it.first }
            .map { (id, name) -> IptvGroup(id, name) }
        return IptvParseResult(IptvPlaylist(entries, groups, playlistAttributes), diagnostics)
    }

    private fun parseExtInf(
        value: String,
        line: Int,
        diagnostics: MutableList<IptvParseDiagnostic>,
    ): PendingEntry {
        val comma = findUnquotedComma(value)
        if (comma < 0) {
            report(diagnostics, IptvDiagnosticCode.MALFORMED_DIRECTIVE, "EXTINF is missing its title separator", line)
        }
        val metadata = if (comma >= 0) value.substring(0, comma) else value
        val title = if (comma >= 0) value.substring(comma + 1).trim().ifEmpty { null } else null
        val firstSpace = metadata.indexOfFirst { it.isWhitespace() }
        val durationText = if (firstSpace < 0) metadata.trim() else metadata.substring(0, firstSpace).trim()
        val attributesText = if (firstSpace < 0) "" else metadata.substring(firstSpace + 1)
        val duration = durationText.toLongOrNull()
        if (duration == null && durationText.isNotEmpty()) {
            report(diagnostics, IptvDiagnosticCode.INVALID_ATTRIBUTE, "Invalid EXTINF duration: $durationText", line)
        }
        return PendingEntry(
            line = line,
            durationSeconds = duration?.takeIf { it >= 0 },
            title = title,
            attributes = parseAttributes(attributesText, line, diagnostics).toMutableMap(),
        )
    }

    private fun parseAttributes(
        value: String,
        line: Int,
        diagnostics: MutableList<IptvParseDiagnostic>,
    ): Map<String, String> {
        val result = linkedMapOf<String, String>()
        var index = 0
        while (index < value.length) {
            while (index < value.length && value[index].isWhitespace()) index++
            if (index >= value.length) break
            val keyStart = index
            while (index < value.length && !value[index].isWhitespace() && value[index] != '=') index++
            val key = value.substring(keyStart, index).trim().lowercase(Locale.ROOT)
            while (index < value.length && value[index].isWhitespace()) index++
            if (key.isEmpty() || index >= value.length || value[index] != '=') {
                while (index < value.length && !value[index].isWhitespace()) index++
                report(diagnostics, IptvDiagnosticCode.INVALID_ATTRIBUTE, "Malformed attribute near '${value.substring(keyStart, index)}'", line)
                continue
            }
            index++
            while (index < value.length && value[index].isWhitespace()) index++
            val attributeValue = StringBuilder()
            if (index < value.length && (value[index] == '"' || value[index] == '\'')) {
                val quote = value[index++]
                var closed = false
                while (index < value.length) {
                    val character = value[index++]
                    when {
                        character == '\\' && index < value.length -> attributeValue.append(value[index++])
                        character == quote -> {
                            closed = true
                            break
                        }
                        else -> attributeValue.append(character)
                    }
                }
                if (!closed) {
                    report(diagnostics, IptvDiagnosticCode.INVALID_ATTRIBUTE, "Unclosed quoted value for $key", line)
                }
            } else {
                while (index < value.length && !value[index].isWhitespace()) attributeValue.append(value[index++])
            }
            result[key] = attributeValue.toString()
            if (result.size > limits.maximumAttributesPerEntry) {
                fatal(
                    diagnostics,
                    IptvDiagnosticCode.RESOURCE_LIMIT_EXCEEDED,
                    "Playlist entry exceeds attribute limit",
                    line,
                )
            }
        }
        return result
    }

    private fun buildEntry(
        pending: PendingEntry,
        rawLocation: String,
        baseUri: URI?,
        diagnostics: MutableList<IptvParseDiagnostic>,
    ): IptvPlaylistEntry? {
        val (location, inlineHeaders) = splitUrlHeaders(rawLocation, pending.line, diagnostics)
        val uri = resolveUri(location, baseUri, pending.line, diagnostics) ?: return null
        val attributes = pending.attributes
        val name = attributes["tvg-name"]?.takeIf { it.isNotBlank() }
            ?: pending.title?.takeIf { it.isNotBlank() }
        if (name == null) {
            report(diagnostics, IptvDiagnosticCode.MISSING_REQUIRED_VALUE, "Channel has no source-provided name", pending.line)
            return null
        }
        val epgId = attributes["tvg-id"]?.takeIf { it.isNotBlank() }
        val groupName = attributes["group-title"]?.takeIf { it.isNotBlank() } ?: pending.group?.takeIf { it.isNotBlank() }
        val channelNumber = (attributes["tvg-chno"] ?: attributes["channel-number"])?.takeIf(String::isNotBlank)
        val id = stableId(uri, epgId, name, groupName, channelNumber)
        val logo = attributes["tvg-logo"]?.takeIf { it.isNotBlank() }?.let {
            resolveUri(it, baseUri, pending.line, diagnostics, HTTP_SCHEMES)
        }
        val radio = sequenceOf(attributes["radio"], attributes["tvg-radio"])
            .filterNotNull()
            .any(::parseBoolean) ||
            attributes["type"].equals("radio", ignoreCase = true) ||
            attributes["tvg-type"].equals("radio", ignoreCase = true)
        val catchUpMode = attributes["catchup"] ?: attributes["catchup-type"]
        val catchUpSource = attributes["catchup-source"] ?: attributes["catchup-url"] ?: attributes["tvg-rec-src"]
        val catchUp = catchUpMode
            ?.takeIf { it.isNotBlank() && !it.equals("false", true) && it != "0" }
            ?.let { mode ->
            IptvCatchUp(
                mode = mode,
                sourceTemplate = catchUpSource,
                days = attributes["catchup-days"]?.toIntOrNull()?.takeIf { it > 0 },
                correctionSeconds = attributes["catchup-correction"]?.toLongOrNull() ?: 0,
            )
        } ?: catchUpSource?.takeIf { it.isNotBlank() }?.let { source ->
            IptvCatchUp(mode = "default", sourceTemplate = source)
        }
        val timeshift = attributes["timeshift"]?.let { value ->
            val hours = value.toDoubleOrNull()
            val millis = hours?.times(3_600_000.0)
            if (
                hours == null || !hours.isFinite() || hours <= 0 || millis == null ||
                !millis.isFinite() || millis < 1 || millis > Long.MAX_VALUE
            ) {
                report(diagnostics, IptvDiagnosticCode.INVALID_ATTRIBUTE, "Invalid timeshift window: $value", pending.line)
                null
            } else {
                IptvTimeshift(millis.toLong())
            }
        }

        val vlcUserAgent = pending.vlcOptions["http-user-agent"]
        val vlcReferrer = pending.vlcOptions["http-referrer"] ?: pending.vlcOptions["http-referer"]
        val rawUserAgent = header(inlineHeaders, "User-Agent") ?: attributes["user-agent"] ?: vlcUserAgent
        val userAgent = rawUserAgent?.takeIf { '\r' !in it && '\n' !in it }
        if (rawUserAgent != null && userAgent == null) {
            report(diagnostics, IptvDiagnosticCode.INVALID_ATTRIBUTE, "Unsafe User-Agent value", pending.line)
        }
        val referrerText = header(inlineHeaders, "Referer") ?: header(inlineHeaders, "Referrer")
            ?: attributes["http-referrer"] ?: attributes["referrer"] ?: vlcReferrer
        val referrer = referrerText?.let { resolveUri(it, baseUri, pending.line, diagnostics, HTTP_SCHEMES) }
        if (inlineHeaders.isNotEmpty() && uri.scheme.lowercase(Locale.ROOT) !in HTTP_SCHEMES) {
            report(diagnostics, IptvDiagnosticCode.INVALID_ATTRIBUTE, "Inline headers require HTTP(S) playback", pending.line)
            return null
        }

        return try {
            IptvPlaylistEntry(
                channel = IptvChannel(
                    id = id,
                    name = name,
                    kind = if (radio) IptvChannelKind.RADIO else IptvChannelKind.TV,
                    groupIds = listOfNotNull(groupName),
                    logo = logo,
                    channelNumber = channelNumber,
                    epgId = epgId,
                    language = attributes["tvg-language"],
                    country = attributes["tvg-country"],
                    catchUp = catchUp,
                    timeshift = timeshift,
                    attributes = attributes.toMap(),
                ),
                playback = IptvPlaybackRequest(
                    uri = uri,
                    protocol = detectProtocol(uri, attributes),
                    headers = inlineHeaders,
                    headerRules = inlineHeaders.takeIf { it.isNotEmpty() }?.let { headers ->
                        listOf(
                            IptvHeaderRule(
                                requestKinds = setOf(
                                    IptvRequestKind.MANIFEST,
                                    IptvRequestKind.SEGMENT,
                                    IptvRequestKind.INITIALIZATION_SEGMENT,
                                    IptvRequestKind.ENCRYPTION_KEY,
                                ),
                                allowedOrigins = setOf(uri.origin()),
                                headers = headers,
                            ),
                        )
                    }.orEmpty(),
                    userAgent = userAgent,
                    referrer = referrer,
                ),
                durationSeconds = pending.durationSeconds,
                vlcOptions = pending.vlcOptions.toMap(),
            )
        } catch (error: IllegalArgumentException) {
            report(
                diagnostics,
                IptvDiagnosticCode.INVALID_ATTRIBUTE,
                "Invalid channel or playback metadata: ${error.message}",
                pending.line,
            )
            null
        }
    }

    private fun splitUrlHeaders(
        value: String,
        line: Int,
        diagnostics: MutableList<IptvParseDiagnostic>,
    ): Pair<String, Map<String, String>> {
        val separator = value.indexOf('|')
        if (separator < 0) return value.trim() to emptyMap()
        val headers = linkedMapOf<String, String>()
        value.substring(separator + 1).split('&').filter { it.isNotEmpty() }.forEach { part ->
            val equals = part.indexOf('=')
            if (equals <= 0) {
                report(diagnostics, IptvDiagnosticCode.INVALID_ATTRIBUTE, "Malformed inline URL header: $part", line)
            } else {
                val key = percentDecode(part.substring(0, equals))
                val headerValue = percentDecode(part.substring(equals + 1))
                if (!HEADER_NAME.matches(key) || '\r' in headerValue || '\n' in headerValue) {
                    report(diagnostics, IptvDiagnosticCode.INVALID_ATTRIBUTE, "Unsafe inline URL header", line)
                } else {
                    headers.keys.firstOrNull { it.equals(key, ignoreCase = true) }?.let { existing ->
                        report(diagnostics, IptvDiagnosticCode.INVALID_ATTRIBUTE, "Duplicate inline URL header: $key", line)
                        headers.remove(existing)
                    }
                    headers[key] = headerValue
                }
            }
        }
        return value.substring(0, separator).trim() to headers
    }

    private fun resolveUri(
        value: String,
        baseUri: URI?,
        line: Int,
        diagnostics: MutableList<IptvParseDiagnostic>,
        allowedSchemes: Set<String> = PLAYBACK_SCHEMES,
    ): URI? = try {
        val parsed = URI(value.trim())
        val resolved = if (parsed.isAbsolute) parsed else baseUri?.resolve(parsed)
        if (
            resolved == null || !resolved.isAbsolute || resolved.userInfo != null ||
            resolved.scheme.lowercase(Locale.ROOT) !in allowedSchemes ||
            resolved.scheme.lowercase(Locale.ROOT) in HOST_BASED_SCHEMES && resolved.host == null
        ) {
            report(diagnostics, IptvDiagnosticCode.INVALID_URI, "Relative URI requires an absolute base URI: $value", line)
            null
        } else {
            resolved
        }
    } catch (_: Exception) {
        report(diagnostics, IptvDiagnosticCode.INVALID_URI, "Invalid URI: $value", line)
        null
    }

    private fun report(
        diagnostics: MutableList<IptvParseDiagnostic>,
        code: IptvDiagnosticCode,
        message: String,
        line: Int,
    ) {
        val diagnostic = IptvParseDiagnostic(
            severity = if (mode == IptvParseMode.STRICT) IptvDiagnosticSeverity.ERROR else IptvDiagnosticSeverity.WARNING,
            code = code,
            message = message,
            line = line,
        )
        diagnostics += diagnostic
        if (mode == IptvParseMode.STRICT) throw IptvParseException(diagnostic)
    }

    private fun fatal(
        diagnostics: MutableList<IptvParseDiagnostic>,
        code: IptvDiagnosticCode,
        message: String,
        line: Int,
    ): Nothing {
        val diagnostic = IptvParseDiagnostic(IptvDiagnosticSeverity.ERROR, code, message, line)
        diagnostics += diagnostic
        throw IptvParseException(diagnostic)
    }

    private data class PendingEntry(
        val line: Int,
        val durationSeconds: Long? = null,
        val title: String? = null,
        val attributes: MutableMap<String, String> = linkedMapOf(),
        val vlcOptions: MutableMap<String, String> = linkedMapOf(),
        var group: String? = null,
    )

    private companion object {
        val HTTP_SCHEMES = setOf("http", "https")
        val PLAYBACK_SCHEMES = HTTP_SCHEMES + setOf("rtsp", "rtsps", "rtp", "udp")
        val HOST_BASED_SCHEMES = HTTP_SCHEMES + setOf("rtsp", "rtsps")
        val HEADER_NAME = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
    }
}
