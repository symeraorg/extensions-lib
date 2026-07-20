package org.symera.source.iptv

import java.net.URI
import java.util.TimeZone

/**
 * Compiles placeholders commonly emitted by Extended M3U catch-up providers. Supported names are `utc`/`start`,
 * `utcend`/`end`, `lutc`, `lutcend`, `duration`, `offset`, and `timestamp`. Numeric values are epoch seconds;
 * local variants add the configured UTC offset. `timestamp` is an alias of the start epoch for compatibility.
 */
class CatchUpTemplateCompiler(
    private val localTimeZone: TimeZone = TimeZone.getTimeZone("UTC"),
) {
    fun compile(
        template: String,
        liveRequest: IptvPlaybackRequest,
        programme: IptvTimeRange,
        now: IptvInstant,
        correctionSeconds: Long = 0,
    ): IptvResult<IptvPlaybackRequest> {
        if (template.isBlank()) return IptvResult.Failure(IptvError.InvalidConfiguration("Catch-up template is blank"))

        return try {
            val start = Math.addExact(programme.start.epochMillis / 1_000, correctionSeconds)
            val end = Math.addExact(programme.end.epochMillis / 1_000, correctionSeconds)
            val nowSeconds = now.epochMillis / 1_000
            val startOffset = localTimeZone.getOffset(programme.start.epochMillis) / 1_000L
            val endOffset = localTimeZone.getOffset(programme.end.epochMillis) / 1_000L
            val durationMillis = programme.durationMillis
            val durationSeconds = durationMillis / 1_000 + if (durationMillis % 1_000 == 0L) 0 else 1
            val values = mapOf(
                "utc" to start,
                "start" to start,
                "utcend" to end,
                "end" to end,
                "lutc" to Math.addExact(start, startOffset),
                "lutcend" to Math.addExact(end, endOffset),
                "duration" to durationSeconds,
                "offset" to Math.subtractExact(nowSeconds, start).coerceAtLeast(0),
                "timestamp" to start,
            )

            var compiled = template
            values.forEach { (key, value) ->
                compiled = compiled.replace("{$key}", value.toString(), ignoreCase = true)
                compiled = compiled.replace("\${$key}", value.toString(), ignoreCase = true)
            }
            val unresolved = PLACEHOLDER.find(compiled)?.value
            if (unresolved != null) {
                return IptvResult.Failure(IptvError.InvalidConfiguration("Unknown catch-up placeholder: $unresolved"))
            }
            val (uriText, inlineHeaders) = splitUrlAndHeaders(compiled)
            val candidate = URI(uriText)
            val resolved = if (candidate.isAbsolute) candidate else liveRequest.uri.resolve(candidate)
            IptvUriPolicy.requireHttpUri(resolved, "Compiled catch-up URI")
            IptvHeaderPolicy.requireValid(inlineHeaders)
            val sameOrigin = IptvUriPolicy.sameOrigin(liveRequest.uri, resolved)
            val resolvedHeaders = if (sameOrigin) {
                IptvHeaderPolicy.merge(liveRequest.headers, inlineHeaders)
            } else {
                inlineHeaders
            }
            val inlineRule = inlineHeaders.takeIf(Map<*, *>::isNotEmpty)?.let { headers ->
                IptvHeaderRule(
                    requestKinds = setOf(
                        IptvRequestKind.MANIFEST,
                        IptvRequestKind.SEGMENT,
                        IptvRequestKind.INITIALIZATION_SEGMENT,
                        IptvRequestKind.ENCRYPTION_KEY,
                    ),
                    allowedOrigins = setOf(IptvUriPolicy.origin(resolved)),
                    headers = headers,
                )
            }
            IptvResult.Success(
                liveRequest.copy(
                    uri = resolved,
                    protocol = IptvStreamProtocol.AUTO,
                    mode = IptvPlaybackMode.CATCH_UP,
                    headers = resolvedHeaders,
                    headerRules = liveRequest.headerRules.takeIf { sameOrigin }.orEmpty() + listOfNotNull(inlineRule),
                    userAgent = liveRequest.userAgent.takeIf { sameOrigin },
                    referrer = liveRequest.referrer.takeIf { sameOrigin },
                    authorizationHandle = liveRequest.authorizationHandle.takeIf { sameOrigin },
                    programme = programme,
                ),
            )
        } catch (error: Exception) {
            IptvResult.Failure(IptvError.InvalidConfiguration("Invalid compiled catch-up URI: ${error.message}"))
        }
    }

    private fun splitUrlAndHeaders(value: String): Pair<String, Map<String, String>> {
        val separator = value.indexOf('|')
        if (separator < 0) return value to emptyMap()
        val uri = value.substring(0, separator)
        val headers = value.substring(separator + 1)
            .split('&')
            .mapNotNull { part ->
                val equals = part.indexOf('=')
                if (equals <= 0) null else percentDecode(part.substring(0, equals)) to percentDecode(part.substring(equals + 1))
            }
            .toMap()
        return uri to headers
    }

    private fun percentDecode(value: String): String {
        val output = StringBuilder(value.length)
        val bytes = ArrayList<Byte>()
        var index = 0
        while (index < value.length) {
            if (value[index] != '%' || index + 2 >= value.length) {
                output.append(value[index++])
                continue
            }
            bytes.clear()
            while (index + 2 < value.length && value[index] == '%') {
                val byte = value.substring(index + 1, index + 3).toIntOrNull(16) ?: break
                bytes += byte.toByte()
                index += 3
            }
            if (bytes.isEmpty()) {
                output.append(value[index++])
            } else {
                output.append(bytes.toByteArray().toString(Charsets.UTF_8))
            }
        }
        return output.toString()
    }

    private companion object {
        val PLACEHOLDER = Regex("(?:\\{[^{}]+}|\\$\\{[^{}]+})")
    }
}
