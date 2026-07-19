package org.symera.source.iptv

import java.net.URI
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvSessionServicesTest {
    @Test
    fun credentialsAndPlaybackRequestsRedactSecrets() {
        val credentials = IptvCredentials(mapOf("password" to "super-secret"))
        assertTrue("super-secret" !in credentials.toString())

        val request = IptvPlaybackRequest(
            URI("https://example.com/live.m3u8?token=super-secret"),
            headers = mapOf("Authorization" to "super-secret"),
            authorizationHandle = IptvAuthorizationHandle("super-secret", setOf("https://example.com")),
        )
        assertTrue("super-secret" !in request.toString())
    }

    @Test
    fun minimalProviderComposesOnlyChannelsAndLivePlayback() = runBlocking {
        val channel = IptvChannel("one", "One")
        val playback = IptvPlaybackRequest(URI("https://example.com/live.m3u8"), IptvStreamProtocol.HLS)
        val catalog = object : IptvChannelCatalog {
            override val channelKinds = setOf(IptvChannelKind.TV)
            override val supportsSearch = false
            override suspend fun getChannels(query: IptvChannelQuery, page: IptvPageRequest) =
                IptvResult.Success(IptvPage(listOf(channel)))
        }
        var closeCount = 0
        val session = CompositeIptvSession(
            configuration = configuration(channel, playback),
            services = IptvSessionServices(
                channels = catalog,
                playback = IptvPlaybackServices(IptvLivePlaybackResolver { IptvResult.Success(playback) }),
            ),
            onClose = { closeCount++ },
        )

        assertEquals(setOf(IptvCapability.TV), session.capabilities.values)
        assertEquals(channel, ((session.getChannels() as IptvResult.Success).value).items.single())
        assertEquals(playback, (session.getPlaybackRequest(channel) as IptvResult.Success).value)
        assertTrue(session.getEpg(IptvEpgRequest()) is IptvResult.Failure)
        session.close()
        session.close()
        assertEquals(1, closeCount)
        assertTrue(session.getChannels() is IptvResult.Failure)
        assertTrue(runCatching { session.services }.isFailure)
    }

    @Test
    fun dynamicAuthorizationIsOriginBoundAndHeaderValidated() = runBlocking {
        val channel = IptvChannel("one", "One")
        val playback = IptvPlaybackRequest(URI("https://example.com/live.m3u8"), IptvStreamProtocol.HLS)
        val catalog = object : IptvChannelCatalog {
            override val channelKinds = setOf(IptvChannelKind.TV)
            override val supportsSearch = false
            override suspend fun getChannels(query: IptvChannelQuery, page: IptvPageRequest) =
                IptvResult.Success(IptvPage(listOf(channel)))
        }
        val session = CompositeIptvSession(
            configuration(channel, playback),
            IptvSessionServices(
                channels = catalog,
                playback = IptvPlaybackServices(IptvLivePlaybackResolver { IptvResult.Success(playback) }),
                dynamicHeaders = IptvDynamicHeaderProvider { _, _ ->
                    IptvResult.Success(mapOf("Authorization" to "bad\r\nvalue"))
                },
            ),
        )
        val handle = IptvAuthorizationHandle("opaque", setOf("https://example.com"))

        assertTrue(
            session.getDynamicHeaders(
                handle,
                IptvRequestContext(URI("https://other.example/segment"), IptvRequestKind.SEGMENT),
            ) is IptvResult.Failure,
        )
        assertTrue(
            session.getDynamicHeaders(
                handle,
                IptvRequestContext(URI("https://example.com/segment"), IptvRequestKind.SEGMENT),
            ) is IptvResult.Failure,
        )
    }

    private fun configuration(channel: IptvChannel, playback: IptvPlaybackRequest) = IptvConfiguration(
        id = "minimal",
        name = "Minimal",
        catalog = IptvCatalogConfiguration.Channels(listOf(IptvConfiguredChannel(channel, playback))),
    )
}
