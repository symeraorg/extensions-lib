package org.symera.source.iptv

import java.util.concurrent.atomic.AtomicBoolean

interface IptvChannelCatalog {
    val channelKinds: Set<IptvChannelKind>
    val supportsSearch: Boolean

    suspend fun getChannels(
        query: IptvChannelQuery = IptvChannelQuery(),
        page: IptvPageRequest = IptvPageRequest(),
    ): IptvResult<IptvPage<IptvChannel>>
}

fun interface IptvClock {
    fun now(): IptvInstant

    companion object {
        val SYSTEM = IptvClock { IptvInstant(System.currentTimeMillis()) }
    }
}

fun interface IptvGroupCatalog {
    suspend fun getGroups(page: IptvPageRequest): IptvResult<IptvPage<IptvGroup>>
}

fun interface IptvEpgProvider {
    suspend fun getEpg(request: IptvEpgRequest): IptvResult<IptvEpg>
}

fun interface IptvNowNextProvider {
    suspend fun getNowNext(channelIds: Set<String>, at: IptvInstant): IptvResult<List<IptvNowNext>>
}

fun interface IptvLivePlaybackResolver {
    suspend fun resolve(channel: IptvChannel): IptvResult<IptvPlaybackRequest>
}

data class IptvCatchUpRequest(
    val channel: IptvChannel,
    val liveRequest: IptvPlaybackRequest,
    val programme: IptvTimeRange,
    val now: IptvInstant,
    val timeZoneId: String,
)

interface IptvCatchUpResolver {
    fun supports(channel: IptvChannel): Boolean
    suspend fun resolve(request: IptvCatchUpRequest): IptvResult<IptvPlaybackRequest>
}

data class IptvTimeshiftRequest(
    val channel: IptvChannel,
    val liveRequest: IptvPlaybackRequest,
    val liveOffsetMillis: Long,
)

interface IptvTimeshiftResolver {
    fun supports(channel: IptvChannel): Boolean
    suspend fun resolve(request: IptvTimeshiftRequest): IptvResult<IptvPlaybackRequest>
}

fun interface IptvDynamicHeaderProvider {
    suspend fun getDynamicHeaders(
        authorizationHandle: IptvAuthorizationHandle,
        context: IptvRequestContext,
    ): IptvResult<Map<String, String>>
}

fun interface IptvLicenseExchanger {
    suspend fun exchangeLicense(
        exchangeId: String,
        challenge: ByteArray,
        context: IptvRequestContext,
    ): IptvResult<ByteArray>
}

fun interface IptvRefresher {
    suspend fun refresh(): IptvResult<Unit>
}

data class IptvPlaybackServices(
    val live: IptvLivePlaybackResolver,
    val catchUp: IptvCatchUpResolver? = null,
    val timeshift: IptvTimeshiftResolver? = null,
) {
    suspend fun resolve(
        channel: IptvChannel,
        intent: IptvPlaybackIntent = IptvPlaybackIntent(),
        now: IptvInstant,
        timeZoneId: String = "UTC",
    ): IptvResult<IptvPlaybackRequest> {
        val liveRequest = when (val result = live.resolve(channel)) {
            is IptvResult.Success -> result.value
            is IptvResult.Failure -> return result
        }
        return when (intent.mode) {
            IptvPlaybackMode.LIVE -> IptvResult.Success(liveRequest)
            IptvPlaybackMode.CATCH_UP -> {
                val resolver = catchUp
                    ?: return IptvResult.Failure(IptvError.Unsupported("Catch-up is not supported"))
                if (!resolver.supports(channel)) {
                    return IptvResult.Failure(IptvError.Unsupported("Channel has no catch-up policy"))
                }
                resolver.resolve(
                    IptvCatchUpRequest(channel, liveRequest, requireNotNull(intent.programme), now, timeZoneId),
                )
            }
            IptvPlaybackMode.TIMESHIFT -> {
                val resolver = timeshift
                    ?: return IptvResult.Failure(IptvError.Unsupported("Timeshift is not supported"))
                if (!resolver.supports(channel)) {
                    return IptvResult.Failure(IptvError.Unsupported("Channel has no timeshift window"))
                }
                resolver.resolve(
                    IptvTimeshiftRequest(channel, liveRequest, requireNotNull(intent.liveOffsetMillis)),
                )
            }
        }
    }
}

data class IptvSessionServices(
    val channels: IptvChannelCatalog,
    val playback: IptvPlaybackServices,
    val groups: IptvGroupCatalog? = null,
    val epg: IptvEpgProvider? = null,
    val nowNext: IptvNowNextProvider? = null,
    val dynamicHeaders: IptvDynamicHeaderProvider? = null,
    val licenseExchange: IptvLicenseExchanger? = null,
    val refresher: IptvRefresher? = null,
    val clock: IptvClock = IptvClock.SYSTEM,
) {
    val capabilities: IptvCapabilities = IptvCapabilities(buildSet {
        if (groups != null) add(IptvCapability.GROUPS)
        if (IptvChannelKind.TV in channels.channelKinds) add(IptvCapability.TV)
        if (IptvChannelKind.RADIO in channels.channelKinds) add(IptvCapability.RADIO)
        if (epg != null) add(IptvCapability.EPG)
        if (nowNext != null) add(IptvCapability.NOW_NEXT)
        if (playback.catchUp != null) add(IptvCapability.CATCH_UP)
        if (playback.timeshift != null) add(IptvCapability.TIMESHIFT)
        if (channels.supportsSearch) add(IptvCapability.SEARCH)
        if (refresher != null) add(IptvCapability.REFRESH)
        if (dynamicHeaders != null) add(IptvCapability.DYNAMIC_HEADERS)
        if (licenseExchange != null) add(IptvCapability.LICENSE_EXCHANGE)
    })
}

class CompositeIptvSession(
    override val configuration: IptvConfiguration,
    services: IptvSessionServices,
    private val onClose: () -> Unit = {},
) : IptvSession {
    private val closed = AtomicBoolean()
    private val serviceDelegate = services

    override val services: IptvSessionServices
        get() = checkNotNull(serviceDelegate.takeUnless { closed.get() }) { "IPTV session is closed" }

    override val capabilities: IptvCapabilities
        get() = if (closed.get()) IptvCapabilities.NONE else serviceDelegate.capabilities

    override suspend fun getGroups(page: IptvPageRequest): IptvResult<IptvPage<IptvGroup>> =
        ifClosed() ?: super<IptvSession>.getGroups(page)

    override suspend fun getChannels(
        query: IptvChannelQuery,
        page: IptvPageRequest,
    ): IptvResult<IptvPage<IptvChannel>> = ifClosed() ?: super<IptvSession>.getChannels(query, page)

    override suspend fun getEpg(request: IptvEpgRequest): IptvResult<IptvEpg> =
        ifClosed() ?: super<IptvSession>.getEpg(request)

    override suspend fun getNowNext(
        channelIds: Set<String>,
        at: IptvInstant,
    ): IptvResult<List<IptvNowNext>> = ifClosed() ?: super<IptvSession>.getNowNext(channelIds, at)

    override suspend fun getPlaybackRequest(
        channel: IptvChannel,
        intent: IptvPlaybackIntent,
    ): IptvResult<IptvPlaybackRequest> = ifClosed() ?: super<IptvSession>.getPlaybackRequest(channel, intent)

    override suspend fun getDynamicHeaders(
        authorizationHandle: IptvAuthorizationHandle,
        context: IptvRequestContext,
    ): IptvResult<Map<String, String>> =
        ifClosed() ?: super<IptvSession>.getDynamicHeaders(authorizationHandle, context)

    override suspend fun exchangeLicense(
        exchangeId: String,
        challenge: ByteArray,
        context: IptvRequestContext,
    ): IptvResult<ByteArray> = ifClosed() ?: super<IptvSession>.exchangeLicense(exchangeId, challenge, context)

    override suspend fun refresh(): IptvResult<Unit> = ifClosed() ?: super<IptvSession>.refresh()

    override fun close() {
        if (closed.compareAndSet(false, true)) onClose()
    }

    private fun <T> ifClosed(): IptvResult<T>? =
        IptvResult.Failure(IptvError.Cancelled("IPTV session is closed")).takeIf { closed.get() }
}
