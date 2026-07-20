package org.symera.source.iptv.playlist

import java.io.IOException
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.symera.source.iptv.IptvCapabilities
import org.symera.source.iptv.IptvCatalogConfiguration
import org.symera.source.iptv.IptvCapability
import org.symera.source.iptv.IptvChannel
import org.symera.source.iptv.IptvChannelCatalog
import org.symera.source.iptv.IptvChannelKind
import org.symera.source.iptv.IptvChannelQuery
import org.symera.source.iptv.IptvConfiguration
import org.symera.source.iptv.IptvCredentials
import org.symera.source.iptv.IptvEpg
import org.symera.source.iptv.IptvEpgProvider
import org.symera.source.iptv.IptvEpgChannel
import org.symera.source.iptv.IptvEpgLocation
import org.symera.source.iptv.IptvEpgRequest
import org.symera.source.iptv.IptvError
import org.symera.source.iptv.IptvGroup
import org.symera.source.iptv.IptvGroupCatalog
import org.symera.source.iptv.IptvHeaderPolicy
import org.symera.source.iptv.IptvInstant
import org.symera.source.iptv.IptvNowNext
import org.symera.source.iptv.IptvNowNextProvider
import org.symera.source.iptv.IptvPage
import org.symera.source.iptv.IptvPageRequest
import org.symera.source.iptv.IptvPlaybackIntent
import org.symera.source.iptv.IptvPlaybackMode
import org.symera.source.iptv.IptvPlaybackRequest
import org.symera.source.iptv.IptvPlaybackServices
import org.symera.source.iptv.IptvProgramme
import org.symera.source.iptv.IptvResult
import org.symera.source.iptv.IptvSession
import org.symera.source.iptv.IptvSessionServices
import org.symera.source.iptv.IptvLivePlaybackResolver
import org.symera.source.iptv.IptvCatchUpResolver
import org.symera.source.iptv.IptvCatchUpRequest
import org.symera.source.iptv.IptvTimeshiftResolver
import org.symera.source.iptv.IptvTimeshiftRequest
import org.symera.source.iptv.IptvRefresher
import org.symera.source.iptv.computeNowNext
import org.symera.source.iptv.inferProgrammeStops
import org.symera.source.iptv.parser.IptvParseException
import org.symera.source.iptv.parser.IptvParseLimits
import org.symera.source.iptv.parser.IptvPlaylistEntry
import org.symera.source.iptv.parser.XmlTvConsumer
import org.symera.source.iptv.parser.XmlTvParserOptions
import org.symera.source.network.HttpStatusException

internal class ConfiguredIptvSession(
    override val configuration: IptvConfiguration,
    private val loader: IptvResourceLoader,
    private val credentials: IptvCredentials,
    private val limits: ConfiguredSessionLimits,
    private val authenticationHeaders: ConfiguredIptvHeaderProvider,
    private val components: ConfiguredIptvComponents,
) : IptvSession {
    private val catalogMutex = Mutex()
    private val epgMutex = Mutex()
    private val stateLock = Any()
    private val activeOperations = ConcurrentHashMap.newKeySet<Job>()
    private val generation = AtomicLong()

    @Volatile private var catalogSnapshot: CatalogSnapshot? = null
    @Volatile private var epgSnapshot: EpgSnapshot? = null
    @Volatile private var serviceSnapshot: IptvSessionServices? = null
    @Volatile private var closed = false

    override val services: IptvSessionServices
        get() = checkNotNull(serviceSnapshot) { "IPTV session has not been initialized" }

    override val capabilities: IptvCapabilities
        get() = serviceSnapshot?.capabilities ?: IptvCapabilities.NONE

    internal suspend fun initialize(): IptvResult<Unit> = result {
        ensureCatalog()
        Unit
    }

    override suspend fun getGroups(page: IptvPageRequest): IptvResult<IptvPage<IptvGroup>> = result {
        val snapshot = ensureCatalog()
        page(snapshot.groups, page, snapshot.generation)
    }

    override suspend fun getChannels(
        query: IptvChannelQuery,
        page: IptvPageRequest,
    ): IptvResult<IptvPage<IptvChannel>> = result {
        val snapshot = ensureCatalog()
        val search = query.search?.lowercase(Locale.ROOT)
        val filtered = snapshot.channels.filter { channel ->
            (query.groupId == null || query.groupId in channel.groupIds) &&
                (query.kind == null || channel.kind == query.kind) &&
                (search == null || channel.name.lowercase(Locale.ROOT).contains(search))
        }
        page(filtered, page, snapshot.generation)
    }

    override suspend fun getEpg(request: IptvEpgRequest): IptvResult<IptvEpg> {
        if (IptvCapability.EPG !in capabilities) {
            return IptvResult.Failure(IptvError.Unsupported("EPG is not configured"))
        }
        return result {
            val epg = ensureEpg()
            val channels = if (request.channelIds.isEmpty()) epg.channels else epg.channels.filter { it.id in request.channelIds }
            val programmes = epg.programmes.filter { programme ->
                (request.channelIds.isEmpty() || programme.channelId in request.channelIds) &&
                    (request.range == null || (
                        programme.start < request.range.end &&
                            (programme.stop == null || programme.stop > request.range.start)
                    ))
            }
            IptvEpg(channels, programmes)
        }
    }

    override suspend fun getNowNext(
        channelIds: Set<String>,
        at: IptvInstant,
    ): IptvResult<List<IptvNowNext>> {
        if (IptvCapability.NOW_NEXT !in capabilities) {
            return IptvResult.Failure(IptvError.Unsupported("EPG now/next is not configured"))
        }
        return result { computeNowNext(ensureEpg().programmes, channelIds, at) }
    }

    override suspend fun getPlaybackRequest(
        channel: IptvChannel,
        intent: IptvPlaybackIntent,
    ): IptvResult<IptvPlaybackRequest> = resultValue {
        val catalog = ensureCatalog()
        val knownChannel = catalog.channelsById[channel.id]
            ?: return@resultValue IptvResult.Failure(IptvError.NotFound("Unknown IPTV channel: ${channel.id}"))
        val live = catalog.playbackByChannelId[knownChannel.id]
            ?: return@resultValue IptvResult.Failure(IptvError.NotFound("Channel has no playback request"))

        when (intent.mode) {
            IptvPlaybackMode.LIVE -> IptvResult.Success(live)
            IptvPlaybackMode.TIMESHIFT -> {
                val resolver = components.timeshiftResolver
                    ?: return@resultValue IptvResult.Failure(IptvError.Unsupported("Timeshift is not supported"))
                if (!resolver.supports(knownChannel)) {
                    return@resultValue IptvResult.Failure(IptvError.Unsupported("Channel has no timeshift window"))
                }
                resolver.resolve(IptvTimeshiftRequest(knownChannel, live, requireNotNull(intent.liveOffsetMillis)))
            }
            IptvPlaybackMode.CATCH_UP -> {
                val resolver = components.catchUpResolver
                    ?: return@resultValue IptvResult.Failure(IptvError.Unsupported("Catch-up is not supported"))
                if (!resolver.supports(knownChannel)) {
                    return@resultValue IptvResult.Failure(IptvError.Unsupported("Channel has no catch-up policy"))
                }
                resolver.resolve(
                    IptvCatchUpRequest(
                        knownChannel,
                        live,
                        requireNotNull(intent.programme),
                        components.clock.now(),
                        configuration.timeZoneId,
                    ),
                )
            }
        }
    }

    override suspend fun refresh(): IptvResult<Unit> = result {
        val loaded = catalogMutex.withLock { loadCatalog() }
        publishCatalog(loaded)
    }

    override fun close() {
        val operations: List<Job>
        synchronized(stateLock) {
            if (closed) return
            closed = true
            catalogSnapshot = null
            epgSnapshot = null
            serviceSnapshot = null
            operations = activeOperations.toList()
        }
        operations.forEach { it.cancel(CancellationException("IPTV session was closed")) }
    }

    private suspend fun ensureCatalog(): CatalogSnapshot {
        requireOpen()
        catalogSnapshot?.let { return it }
        return catalogMutex.withLock {
            catalogSnapshot ?: loadCatalog().also(::publishCatalog)
        }
    }

    private suspend fun ensureEpg(): EpgSnapshot {
        requireOpen()
        epgSnapshot?.let { return it }
        return epgMutex.withLock {
            var resolved = epgSnapshot
            while (resolved == null) {
                val catalog = ensureCatalog()
                val loaded = loadEpg(catalog)
                val published = synchronized(stateLock) {
                    check(!closed) { "IPTV session is closed" }
                    if (catalogSnapshot?.generation == catalog.generation) {
                        epgSnapshot = loaded
                        true
                    } else {
                        false
                    }
                }
                if (published) resolved = loaded
            }
            requireNotNull(resolved)
        }
    }

    private fun publishCatalog(loaded: CatalogSnapshot) {
        synchronized(stateLock) {
            check(!closed) { "IPTV session is closed" }
            catalogSnapshot = loaded
            epgSnapshot = null
            serviceSnapshot = buildServices(loaded)
        }
    }

    private fun buildServices(snapshot: CatalogSnapshot): IptvSessionServices {
        val channelCatalog = object : IptvChannelCatalog {
            override val channelKinds: Set<IptvChannelKind> = snapshot.channels.mapTo(linkedSetOf(), IptvChannel::kind)
            override val supportsSearch: Boolean = true

            override suspend fun getChannels(
                query: IptvChannelQuery,
                page: IptvPageRequest,
            ): IptvResult<IptvPage<IptvChannel>> = this@ConfiguredIptvSession.getChannels(query, page)
        }
        val live = IptvLivePlaybackResolver { channel ->
            this@ConfiguredIptvSession.getPlaybackRequest(channel, IptvPlaybackIntent())
        }
        val catchUp = components.catchUpResolver?.takeIf { resolver -> snapshot.channels.any(resolver::supports) }
        val timeshift = components.timeshiftResolver?.takeIf { resolver -> snapshot.channels.any(resolver::supports) }
        val hasEpg = snapshot.epgLocations.isNotEmpty()
        val canRefresh = configuration.catalog is IptvCatalogConfiguration.Playlists || hasEpg
        return IptvSessionServices(
            channels = channelCatalog,
            playback = IptvPlaybackServices(live, catchUp, timeshift),
            groups = IptvGroupCatalog { page -> this@ConfiguredIptvSession.getGroups(page) }
                .takeIf { snapshot.groups.isNotEmpty() },
            epg = IptvEpgProvider { request -> this@ConfiguredIptvSession.getEpg(request) }.takeIf { hasEpg },
            nowNext = IptvNowNextProvider { channelIds, at ->
                this@ConfiguredIptvSession.getNowNext(channelIds, at)
            }.takeIf { hasEpg },
            refresher = IptvRefresher { this@ConfiguredIptvSession.refresh() }.takeIf { canRefresh },
            clock = components.clock,
        )
    }

    private suspend fun loadCatalog(): CatalogSnapshot {
        val parts = mutableListOf<IptvCatalogPart>()
        when (val catalog = configuration.catalog) {
            is IptvCatalogConfiguration.Playlists -> {
                require(catalog.locations.size <= limits.maximumLocations) { "Too many IPTV playlist locations" }
                catalog.locations.forEach { location ->
                    val playlist = loader.read(
                        location.uri,
                        resolveHeaders(
                            location.uri,
                            location.headers,
                            location.secretHeaderReferences,
                            ConfiguredIptvResourceKind.PLAYLIST,
                        ),
                        limits.maximumPlaylistBytes,
                    ) { stream ->
                        components.playlistParser.parse(
                            stream,
                            location.uri,
                            IptvParseLimits(maximumEntries = limits.maximumChannels),
                        )
                    }
                    val entries = playlist.entries.mapIndexed { index, entry ->
                        val id = components.channelIdentity.id(entry, IptvPlaylistEntryContext(location.uri, index))
                        require(id.isNotBlank()) { "Channel identity strategy returned a blank ID" }
                        entry.copy(channel = entry.channel.copy(id = id))
                    }
                    parts += IptvCatalogPart(
                        sourceUri = location.uri,
                        groups = playlist.groups,
                        entries = entries,
                        epgLocations = playlist.epgUris(location.uri).map(::IptvEpgLocation),
                    )
                }
            }
            is IptvCatalogConfiguration.Channels -> {
                require(catalog.entries.size <= limits.maximumChannels) { "IPTV channel count exceeds configured limit" }
                parts += IptvCatalogPart(
                    sourceUri = null,
                    groups = catalog.groups,
                    entries = catalog.entries.map { IptvPlaylistEntry(it.channel, it.playback) },
                    epgLocations = emptyList(),
                )
            }
            is IptvCatalogConfiguration.Provider ->
                throw IllegalArgumentException("Provider configuration requires its extension adapter")
        }

        val merged = components.catalogMerger.merge(parts)
        require(merged.entries.size <= limits.maximumChannels) { "IPTV channel count exceeds configured limit" }
        require(merged.entries.map { it.channel.id }.distinct().size == merged.entries.size) {
            "Merged IPTV channel IDs must be unique"
        }
        require(merged.groups.map(IptvGroup::id).distinct().size == merged.groups.size) {
            "Merged IPTV group IDs must be unique"
        }
        val groupIds = merged.groups.mapTo(mutableSetOf(), IptvGroup::id)
        require(merged.entries.flatMap { it.channel.groupIds }.all(groupIds::contains)) {
            "Every merged channel group must have group metadata"
        }
        val channels = merged.entries.associateTo(LinkedHashMap()) { it.channel.id to it.channel }
        val playback = merged.entries.associateTo(LinkedHashMap()) { it.channel.id to it.playback }
        val epgLocations = (configuration.epg + merged.epgLocations).distinctBy { it.uri }
        require(epgLocations.size <= limits.maximumLocations) { "Too many IPTV EPG locations" }

        return CatalogSnapshot(
            generation = generation.incrementAndGet(),
            groups = merged.groups,
            channels = channels.values.toList(),
            channelsById = channels,
            playbackByChannelId = playback,
            epgLocations = epgLocations,
        )
    }

    private suspend fun loadEpg(catalog: CatalogSnapshot): EpgSnapshot {
        require(catalog.epgLocations.size <= limits.maximumLocations) { "Too many IPTV EPG locations" }
        val rawChannels = LinkedHashMap<String, IptvEpgChannel>()
        val channels = LinkedHashMap<String, IptvEpgChannel>()
        val programmes = mutableListOf<IptvProgramme>()
        val consumer = object : XmlTvConsumer {
            override fun onChannel(channel: IptvEpgChannel) {
                if (rawChannels.size >= limits.maximumChannels && channel.id !in rawChannels) {
                    throw IllegalArgumentException("IPTV EPG channel count exceeds configured limit")
                }
                rawChannels[channel.id] = channel
                components.epgMatcher.match(channel.id, channel, catalog.channels).forEach { internalId ->
                    require(internalId in catalog.channelsById) { "EPG matcher returned an unknown channel ID" }
                    channels.putIfAbsent(internalId, channel.copy(id = internalId))
                }
            }

            override fun onProgramme(programme: IptvProgramme) {
                components.epgMatcher.match(
                    programme.channelId,
                    rawChannels[programme.channelId],
                    catalog.channels,
                ).forEach { internalId ->
                    require(internalId in catalog.channelsById) { "EPG matcher returned an unknown channel ID" }
                    if (programmes.size >= limits.maximumProgrammes) {
                        throw IllegalArgumentException("IPTV programme count exceeds configured limit")
                    }
                    programmes += programme.copy(channelId = internalId)
                }
            }
        }

        catalog.epgLocations.forEach { location ->
            loader.read(
                location.uri,
                resolveHeaders(
                    location.uri,
                    location.headers,
                    location.secretHeaderReferences,
                    ConfiguredIptvResourceKind.EPG,
                ),
                limits.maximumEpgBytes,
            ) { stream ->
                components.epgParser.parse(
                    stream,
                    XmlTvParserOptions(
                        defaultTimeZoneId = configuration.timeZoneId,
                        baseUri = location.uri,
                        limits = IptvParseLimits(maximumEntries = maxOf(limits.maximumChannels, limits.maximumProgrammes)),
                        maximumChannels = limits.maximumChannels,
                        maximumProgrammes = limits.maximumProgrammes,
                    ),
                    consumer,
                )
            }
        }

        return EpgSnapshot(
            channels = channels.values.toList(),
            programmes = inferProgrammeStops(programmes).sortedBy { it.start.epochMillis },
        )
    }

    private fun resolveHeaders(
        uri: URI,
        headers: Map<String, String>,
        references: Map<String, String>,
        kind: ConfiguredIptvResourceKind,
    ): Map<String, String> {
        val secretHeaders = references.mapValues { (_, credentialKey) ->
            requireNotNull(credentials[credentialKey]?.takeIf(String::isNotBlank)) {
                "Missing IPTV credential: $credentialKey"
            }
        }
        return mergeHeaders(mergeHeaders(authenticationHeaders.headers(uri, kind), headers), secretHeaders)
            .also(IptvHeaderPolicy::requireValid)
    }

    private fun <T> page(items: List<T>, request: IptvPageRequest, currentGeneration: Long): IptvPage<T> {
        val cursor = request.cursor?.let(::parseCursor)
        require(cursor == null || cursor.first == currentGeneration) { "IPTV page cursor belongs to an old snapshot" }
        val offset = cursor?.second ?: 0
        require(offset in 0..items.size) { "Invalid IPTV page cursor" }
        val end = (offset + request.limit).coerceAtMost(items.size)
        return IptvPage(
            items = items.subList(offset, end),
            nextCursor = end.takeIf { it < items.size }?.let { "g:$currentGeneration:o:$it" },
            totalCount = items.size.toLong(),
        )
    }

    private fun parseCursor(value: String): Pair<Long, Int> {
        val match = CURSOR_PATTERN.matchEntire(value) ?: throw IllegalArgumentException("Invalid IPTV page cursor")
        return requireNotNull(match.groupValues[1].toLongOrNull()) to
            requireNotNull(match.groupValues[2].toIntOrNull())
    }

    private suspend fun <T> result(block: suspend () -> T): IptvResult<T> =
        try {
            operation {
                IptvResult.Success(block())
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: IptvParseException) {
            IptvResult.Failure(IptvError.Parse(exception.message.orEmpty(), exception.diagnostic.line, exception))
        } catch (exception: HttpStatusException) {
            IptvResult.Failure(exception.toIptvError())
        } catch (exception: IOException) {
            IptvResult.Failure(IptvError.Network(exception.message.orEmpty(), exception))
        } catch (exception: IllegalArgumentException) {
            IptvResult.Failure(IptvError.InvalidConfiguration(exception.message.orEmpty()))
        } catch (exception: IllegalStateException) {
            IptvResult.Failure(IptvError.Cancelled(exception.message.orEmpty()))
        } catch (exception: Exception) {
            IptvResult.Failure(IptvError.Unexpected(exception.message.orEmpty(), exception))
        }

    private suspend fun <T> resultValue(block: suspend () -> IptvResult<T>): IptvResult<T> =
        try {
            operation(block)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: HttpStatusException) {
            IptvResult.Failure(exception.toIptvError())
        } catch (exception: IOException) {
            IptvResult.Failure(IptvError.Network(exception.message.orEmpty(), exception))
        } catch (exception: IllegalArgumentException) {
            IptvResult.Failure(IptvError.InvalidConfiguration(exception.message.orEmpty()))
        } catch (exception: IllegalStateException) {
            IptvResult.Failure(IptvError.Cancelled(exception.message.orEmpty()))
        } catch (exception: Exception) {
            IptvResult.Failure(IptvError.Unexpected(exception.message.orEmpty(), exception))
        }

    private suspend fun <T> operation(block: suspend () -> T): T = coroutineScope {
        val job = requireNotNull(currentCoroutineContext()[Job])
        synchronized(stateLock) {
            requireOpen()
            activeOperations += job
        }
        try {
            block()
        } finally {
            activeOperations -= job
        }
    }

    private fun requireOpen() = check(!closed) { "IPTV session is closed" }

    private data class CatalogSnapshot(
        val generation: Long,
        val groups: List<IptvGroup>,
        val channels: List<IptvChannel>,
        val channelsById: Map<String, IptvChannel>,
        val playbackByChannelId: Map<String, IptvPlaybackRequest>,
        val epgLocations: List<IptvEpgLocation>,
    )

    private data class EpgSnapshot(
        val channels: List<IptvEpgChannel>,
        val programmes: List<IptvProgramme>,
    )

    private companion object {
        val CURSOR_PATTERN = Regex("g:(\\d+):o:(\\d+)")
    }
}

private fun org.symera.source.iptv.parser.IptvPlaylist.epgUris(baseUri: URI): List<URI> =
    sequenceOf(attributes["x-tvg-url"], attributes["url-tvg"], attributes["tvg-url"])
        .filterNotNull()
        .flatMap { value -> value.split(',').asSequence() }
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map(baseUri::resolve)
        .filter {
            it.isAbsolute && it.host != null && it.userInfo == null &&
                (it.scheme.equals("http", true) || it.scheme.equals("https", true))
        }
        .toList()

private fun HttpStatusException.toIptvError(): IptvError = when (statusCode) {
    401 -> IptvError.AuthenticationRequired()
    403 -> IptvError.AuthenticationRejected("IPTV provider rejected authentication")
    429 -> IptvError.RateLimited(retryAfter.toDelayMillis(), message.orEmpty())
    else -> IptvError.Http(statusCode, message.orEmpty())
}

private fun String?.toDelayMillis(nowMillis: Long = System.currentTimeMillis()): Long? {
    val value = this?.trim()?.takeIf(String::isNotEmpty) ?: return null
    value.toLongOrNull()?.let { seconds ->
        if (seconds < 0 || seconds > Long.MAX_VALUE / 1_000) return null
        return seconds * 1_000
    }
    val formats = listOf(
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "EEEE, dd-MMM-yy HH:mm:ss zzz",
        "EEE MMM d HH:mm:ss yyyy",
    )
    val parsed = formats.firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("GMT")
            }.parse(value)?.time
        }.getOrNull()
    } ?: return null
    return runCatching { Math.subtractExact(parsed, nowMillis).coerceAtLeast(0) }.getOrNull()
}

private fun mergeHeaders(base: Map<String, String>, overrides: Map<String, String>): Map<String, String> {
    val overrideNames = overrides.keys.mapTo(mutableSetOf()) { it.lowercase(Locale.ROOT) }
    return buildMap {
        base.forEach { (name, value) -> if (name.lowercase(Locale.ROOT) !in overrideNames) put(name, value) }
        putAll(overrides)
    }
}
