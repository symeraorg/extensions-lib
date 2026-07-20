package org.symera.source.iptv

/** Extension entry point for an IPTV provider. */
interface IptvSource {
    val id: Long
    val name: String
    val capabilities: IptvCapabilities
    val authentication: IptvAuthentication get() = IptvAuthentication.NONE

    /** Presets offered by the extension. User-entered configurations may also be passed to [openSession]. */
    fun configurations(): List<IptvConfiguration> = emptyList()

    suspend fun openSession(
        configuration: IptvConfiguration,
        credentials: IptvCredentials = IptvCredentials.EMPTY,
    ): IptvResult<IptvSession>
}

/** Stateful, closeable view of one configured IPTV account or catalog. */
interface IptvSession : AutoCloseable {
    val configuration: IptvConfiguration
    val services: IptvSessionServices
    val capabilities: IptvCapabilities
        get() = services.capabilities

    suspend fun getGroups(page: IptvPageRequest = IptvPageRequest()): IptvResult<IptvPage<IptvGroup>> =
        services.groups?.getGroups(page)
            ?: IptvResult.Failure(IptvError.Unsupported("Channel groups are not supported"))

    suspend fun getChannels(
        query: IptvChannelQuery = IptvChannelQuery(),
        page: IptvPageRequest = IptvPageRequest(),
    ): IptvResult<IptvPage<IptvChannel>> = services.channels.getChannels(query, page)

    suspend fun getEpg(request: IptvEpgRequest): IptvResult<IptvEpg> =
        services.epg?.getEpg(request) ?: IptvResult.Failure(IptvError.Unsupported("EPG is not supported"))

    suspend fun getNowNext(
        channelIds: Set<String>,
        at: IptvInstant,
    ): IptvResult<List<IptvNowNext>> =
        services.nowNext?.getNowNext(channelIds, at)
            ?: IptvResult.Failure(IptvError.Unsupported("EPG now/next is not supported"))

    suspend fun getPlaybackRequest(
        channel: IptvChannel,
        intent: IptvPlaybackIntent = IptvPlaybackIntent(),
    ): IptvResult<IptvPlaybackRequest> = services.playback.resolve(
        channel = channel,
        intent = intent,
        now = services.clock.now(),
        timeZoneId = configuration.timeZoneId,
    )

    suspend fun getDynamicHeaders(
        authorizationHandle: IptvAuthorizationHandle,
        context: IptvRequestContext,
    ): IptvResult<Map<String, String>> {
        if (!authorizationHandle.allows(context.uri)) {
            return IptvResult.Failure(IptvError.InvalidConfiguration("Authorization handle does not allow this origin"))
        }
        val provider = services.dynamicHeaders
            ?: return IptvResult.Failure(IptvError.Unsupported("Dynamic authorization is not supported"))
        return when (val result = provider.getDynamicHeaders(authorizationHandle, context)) {
            is IptvResult.Failure -> result
            is IptvResult.Success -> runCatching {
                IptvHeaderPolicy.requireValid(result.value, "dynamic")
                result
            }.getOrElse { IptvResult.Failure(IptvError.InvalidConfiguration(it.message.orEmpty())) }
        }
    }

    suspend fun exchangeLicense(
        exchangeId: String,
        challenge: ByteArray,
        context: IptvRequestContext,
    ): IptvResult<ByteArray> = services.licenseExchange?.exchangeLicense(exchangeId, challenge, context)
        ?: IptvResult.Failure(IptvError.Unsupported("Provider DRM exchange is not supported"))

    /** Refreshes provider metadata. Sources without [IptvCapability.REFRESH] return [IptvError.Unsupported]. */
    suspend fun refresh(): IptvResult<Unit> = services.refresher?.refresh()
        ?: IptvResult.Failure(IptvError.Unsupported("Refresh is not supported"))

    override fun close()
}
