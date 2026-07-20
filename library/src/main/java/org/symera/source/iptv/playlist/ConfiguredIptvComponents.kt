package org.symera.source.iptv.playlist

import java.io.InputStream
import java.io.InputStreamReader
import java.net.URI
import java.util.TimeZone
import org.symera.source.iptv.CatchUpTemplateCompiler
import org.symera.source.iptv.IptvCatchUpRequest
import org.symera.source.iptv.IptvCatchUpResolver
import org.symera.source.iptv.IptvChannel
import org.symera.source.iptv.IptvClock
import org.symera.source.iptv.IptvEpgChannel
import org.symera.source.iptv.IptvError
import org.symera.source.iptv.IptvGroup
import org.symera.source.iptv.IptvPlaybackMode
import org.symera.source.iptv.IptvPlaybackRequest
import org.symera.source.iptv.IptvResult
import org.symera.source.iptv.IptvTimeshiftRequest
import org.symera.source.iptv.IptvTimeshiftResolver
import org.symera.source.iptv.parser.ExtendedM3uParser
import org.symera.source.iptv.parser.IptvParseLimits
import org.symera.source.iptv.parser.IptvPlaylist
import org.symera.source.iptv.parser.IptvPlaylistEntry
import org.symera.source.iptv.parser.XmlTvConsumer
import org.symera.source.iptv.parser.XmlTvParser
import org.symera.source.iptv.parser.XmlTvParserOptions

fun interface IptvPlaylistParser {
    fun parse(input: InputStream, baseUri: URI, limits: IptvParseLimits): IptvPlaylist
}

fun interface IptvEpgParser {
    fun parse(input: InputStream, options: XmlTvParserOptions, consumer: XmlTvConsumer)
}

data class IptvPlaylistEntryContext(
    val playlistUri: URI?,
    val index: Int,
)

fun interface IptvChannelIdentity {
    fun id(entry: IptvPlaylistEntry, context: IptvPlaylistEntryContext): String
}

data class IptvCatalogPart(
    val sourceUri: URI?,
    val groups: List<IptvGroup>,
    val entries: List<IptvPlaylistEntry>,
    val epgLocations: List<org.symera.source.iptv.IptvEpgLocation>,
)

data class IptvMergedCatalog(
    val groups: List<IptvGroup>,
    val entries: List<IptvPlaylistEntry>,
    val epgLocations: List<org.symera.source.iptv.IptvEpgLocation>,
)

fun interface IptvCatalogMerger {
    fun merge(parts: List<IptvCatalogPart>): IptvMergedCatalog
}

fun interface IptvEpgMatcher {
    fun match(
        epgChannelId: String,
        epgChannel: IptvEpgChannel?,
        channels: List<IptvChannel>,
    ): Set<String>
}

data class ConfiguredIptvComponents(
    val playlistParser: IptvPlaylistParser = IptvPlaylistParser { input, baseUri, limits ->
        ExtendedM3uParser(limits = limits).parse(InputStreamReader(input, Charsets.UTF_8), baseUri).value
    },
    val epgParser: IptvEpgParser = IptvEpgParser { input, options, consumer ->
        XmlTvParser(options).parse(input, consumer)
    },
    val channelIdentity: IptvChannelIdentity = IptvChannelIdentity { entry, _ -> entry.channel.id },
    val catalogMerger: IptvCatalogMerger = FirstWinsIptvCatalogMerger,
    val epgMatcher: IptvEpgMatcher = IptvEpgMatcher { epgChannelId, _, channels ->
        channels.asSequence()
            .filter { (it.epgId ?: it.id) == epgChannelId }
            .mapTo(linkedSetOf(), IptvChannel::id)
    },
    val catchUpResolver: IptvCatchUpResolver? = TemplateIptvCatchUpResolver(),
    val timeshiftResolver: IptvTimeshiftResolver? = WindowIptvTimeshiftResolver(),
    val clock: IptvClock = IptvClock.SYSTEM,
)

object FirstWinsIptvCatalogMerger : IptvCatalogMerger {
    override fun merge(parts: List<IptvCatalogPart>): IptvMergedCatalog {
        val groups = LinkedHashMap<String, IptvGroup>()
        val entries = LinkedHashMap<String, IptvPlaylistEntry>()
        val epgLocations = LinkedHashMap<URI, org.symera.source.iptv.IptvEpgLocation>()
        parts.forEach { part ->
            part.groups.forEach { groups.putIfAbsent(it.id, it) }
            part.entries.forEach { entries.putIfAbsent(it.channel.id, it) }
            part.epgLocations.forEach { epgLocations.putIfAbsent(it.uri, it) }
        }
        return IptvMergedCatalog(groups.values.toList(), entries.values.toList(), epgLocations.values.toList())
    }
}

class TemplateIptvCatchUpResolver : IptvCatchUpResolver {
    override fun supports(channel: IptvChannel): Boolean = !channel.catchUp?.sourceTemplate.isNullOrBlank()

    override suspend fun resolve(request: IptvCatchUpRequest): IptvResult<IptvPlaybackRequest> {
        val policy = request.channel.catchUp
            ?: return IptvResult.Failure(IptvError.Unsupported("Channel has no catch-up policy"))
        val template = policy.sourceTemplate
            ?: return IptvResult.Failure(IptvError.Unsupported("Catch-up provider needs a custom resolver"))
        if (request.programme.end > request.now) {
            return IptvResult.Failure(IptvError.InvalidConfiguration("Catch-up programme has not finished"))
        }
        val retentionMillis = policy.days?.toLong()?.let { Math.multiplyExact(it, MILLIS_PER_DAY) }
        val earliest = retentionMillis?.let {
            if (request.now.epochMillis < Long.MIN_VALUE + it) Long.MIN_VALUE else request.now.epochMillis - it
        }
        if (earliest != null && request.programme.start.epochMillis < earliest) {
            return IptvResult.Failure(IptvError.NotFound("Programme is outside the catch-up window"))
        }
        return CatchUpTemplateCompiler(TimeZone.getTimeZone(request.timeZoneId)).compile(
            template = template,
            liveRequest = request.liveRequest,
            programme = request.programme,
            now = request.now,
            correctionSeconds = policy.correctionSeconds,
        )
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}

class WindowIptvTimeshiftResolver : IptvTimeshiftResolver {
    override fun supports(channel: IptvChannel): Boolean = channel.timeshift != null

    override suspend fun resolve(request: IptvTimeshiftRequest): IptvResult<IptvPlaybackRequest> {
        val policy = request.channel.timeshift
            ?: return IptvResult.Failure(IptvError.Unsupported("Channel has no timeshift window"))
        if (request.liveOffsetMillis > policy.windowMillis) {
            return IptvResult.Failure(IptvError.InvalidConfiguration("Timeshift offset exceeds channel window"))
        }
        return IptvResult.Success(
            request.liveRequest.copy(
                mode = IptvPlaybackMode.TIMESHIFT,
                liveOffsetMillis = request.liveOffsetMillis,
            ),
        )
    }
}
