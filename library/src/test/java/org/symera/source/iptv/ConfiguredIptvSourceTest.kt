package org.symera.source.iptv

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URI
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.symera.source.iptv.playlist.ConfiguredIptvSource
import org.symera.source.iptv.playlist.ConfiguredIptvAuthenticators
import org.symera.source.iptv.playlist.ConfiguredIptvComponents
import org.symera.source.iptv.playlist.IptvResourceLoader

class ConfiguredIptvSourceTest {
    @Test
    fun playlistUrlProducesChannelsGroupsAndEpg() = runBlocking {
        val playlistUri = URI("https://example.com/channels.m3u")
        val epgUri = URI("https://example.com/guide.xml")
        val loader = InMemoryLoader(
            mapOf(
                playlistUri to """
                    #EXTM3U
                    #EXTINF:-1 tvg-id="news" group-title="News",News
                    https://stream.example/news.m3u8
                """.trimIndent(),
                epgUri to """
                    <tv>
                      <channel id="news"><display-name>News</display-name></channel>
                      <programme channel="news" start="20260719120000 +0000" stop="20260719123000 +0000">
                        <title>Noon News</title>
                      </programme>
                    </tv>
                """.trimIndent(),
            ),
        )
        val configuration = IptvConfiguration(
            id = "playlist",
            name = "Playlist",
            catalog = IptvCatalogConfiguration.Playlists(listOf(IptvPlaylistLocation(playlistUri))),
            epg = listOf(IptvEpgLocation(epgUri)),
        )
        val source = ConfiguredIptvSource(1, "Configured", loader)

        val opened = source.openSession(configuration)
        assertTrue(opened is IptvResult.Success)
        val session = (opened as IptvResult.Success).value
        val channels = session.getChannels()
        val groups = session.getGroups()
        val channelPage = (channels as IptvResult.Success).value
        val epg = session.getEpg(IptvEpgRequest(setOf(channelPage.items.single().id)))

        assertEquals("News", channelPage.items.single().name)
        assertEquals("News", (groups as IptvResult.Success).value.items.single().name)
        assertEquals("Noon News", (epg as IptvResult.Success).value.programmes.single().titles.single().value)
    }

    @Test
    fun individuallyConfiguredChannelNeedsNoPlaylistDownload() = runBlocking {
        val channel = IptvChannel(id = "one", name = "One")
        val playback = IptvPlaybackRequest(URI("https://example.com/one.m3u8"), IptvStreamProtocol.HLS)
        val configuration = IptvConfiguration(
            id = "single",
            name = "Single channel",
            catalog = IptvCatalogConfiguration.Channels(listOf(IptvConfiguredChannel(channel, playback))),
        )
        val source = ConfiguredIptvSource(2, "Configured", FailingLoader)
        val session = (source.openSession(configuration) as IptvResult.Success).value

        val channels = session.getChannels() as IptvResult.Success
        val resolved = session.getPlaybackRequest(channel) as IptvResult.Success

        assertEquals(listOf(channel), channels.value.items)
        assertEquals(playback, resolved.value)
        assertEquals(setOf(IptvCapability.TV, IptvCapability.SEARCH), session.capabilities.values)
    }

    @Test
    fun timeshiftRequiresAndValidatesChannelWindow() = runBlocking {
        val channel = IptvChannel(id = "dvr", name = "DVR", timeshift = IptvTimeshift(10_000))
        val playback = IptvPlaybackRequest(URI("https://example.com/dvr.m3u8"), IptvStreamProtocol.HLS)
        val configuration = IptvConfiguration(
            id = "dvr",
            name = "DVR",
            catalog = IptvCatalogConfiguration.Channels(listOf(IptvConfiguredChannel(channel, playback))),
        )
        val session = (ConfiguredIptvSource(3, "Configured", FailingLoader).openSession(configuration) as IptvResult.Success).value

        val valid = session.getPlaybackRequest(
            channel,
            IptvPlaybackIntent(IptvPlaybackMode.TIMESHIFT, liveOffsetMillis = 5_000),
        ) as IptvResult.Success
        val invalid = session.getPlaybackRequest(
            channel,
            IptvPlaybackIntent(IptvPlaybackMode.TIMESHIFT, liveOffsetMillis = 11_000),
        )

        assertEquals(5_000L, valid.value.liveOffsetMillis)
        assertTrue(invalid is IptvResult.Failure)
    }

    @Test
    fun credentialsNeverRenderSecretValues() {
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
    fun configuredAuthenticationIsAppliedAndRequired() = runBlocking {
        val playlistUri = URI("https://example.com/channels.m3u")
        val loader = InMemoryLoader(
            mapOf(playlistUri to "#EXTM3U\n#EXTINF:-1,One\nhttps://stream.example/one.m3u8"),
        )
        val source = ConfiguredIptvSource(
            id = 10,
            name = "Authenticated",
            resourceLoader = loader,
            authenticator = ConfiguredIptvAuthenticators.bearerToken(),
        )
        val configuration = IptvConfiguration(
            id = "auth",
            name = "Auth",
            catalog = IptvCatalogConfiguration.Playlists(listOf(IptvPlaylistLocation(playlistUri))),
        )

        assertTrue(source.openSession(configuration) is IptvResult.Failure)
        assertTrue(source.openSession(configuration, IptvCredentials(mapOf("token" to ""))) is IptvResult.Failure)
        assertTrue(source.openSession(configuration, IptvCredentials(mapOf("token" to "secret"))) is IptvResult.Success)
        assertEquals("Bearer secret", loader.headersByUri.getValue(playlistUri)["Authorization"])
    }

    @Test
    fun configuredIdentityStrategyRunsBeforeCatalogMerge() = runBlocking {
        val playlistUri = URI("https://example.com/channels.m3u")
        val loader = InMemoryLoader(
            mapOf(playlistUri to "#EXTM3U\n#EXTINF:-1,One\nhttps://stream.example/one.m3u8"),
        )
        val components = ConfiguredIptvComponents(
            channelIdentity = org.symera.source.iptv.playlist.IptvChannelIdentity { _, context ->
                "custom-${context.index}"
            },
        )
        val source = ConfiguredIptvSource(11, "Custom", loader, components = components)
        val configuration = IptvConfiguration(
            id = "custom",
            name = "Custom",
            catalog = IptvCatalogConfiguration.Playlists(listOf(IptvPlaylistLocation(playlistUri))),
        )
        val session = (source.openSession(configuration) as IptvResult.Success).value

        assertEquals("custom-0", ((session.getChannels() as IptvResult.Success).value).items.single().id)
    }

    @Test
    fun authenticationIsNotForwardedToDiscoveredCrossOriginEpg() = runBlocking {
        val playlistUri = URI("https://catalog.example/channels.m3u")
        val epgUri = URI("https://guide.example/guide.xml")
        val loader = InMemoryLoader(
            mapOf(
                playlistUri to "#EXTM3U x-tvg-url=\"$epgUri\"\n#EXTINF:-1 tvg-id=\"one\",One\nhttps://stream.example/one.m3u8",
                epgUri to "<tv><channel id=\"one\"><display-name>One</display-name></channel></tv>",
            ),
        )
        val source = ConfiguredIptvSource(
            id = 13,
            name = "Scoped",
            resourceLoader = loader,
            authenticator = ConfiguredIptvAuthenticators.bearerToken(),
        )
        val configuration = IptvConfiguration(
            id = "scoped",
            name = "Scoped",
            catalog = IptvCatalogConfiguration.Playlists(listOf(IptvPlaylistLocation(playlistUri))),
        )
        val session = (source.openSession(configuration, IptvCredentials(mapOf("token" to "secret"))) as IptvResult.Success).value
        session.getEpg(IptvEpgRequest())

        assertEquals("Bearer secret", loader.headersByUri.getValue(playlistUri)["Authorization"])
        assertTrue(loader.headersByUri.getValue(epgUri).isEmpty())
    }

    @Test
    fun operationsAfterCloseReturnTypedFailure() = runBlocking {
        val channel = IptvChannel(id = "closed", name = "Closed")
        val playback = IptvPlaybackRequest(URI("https://example.com/closed.m3u8"), IptvStreamProtocol.HLS)
        val configuration = IptvConfiguration(
            id = "closed",
            name = "Closed",
            catalog = IptvCatalogConfiguration.Channels(listOf(IptvConfiguredChannel(channel, playback))),
        )
        val session = (ConfiguredIptvSource(12, "Configured", FailingLoader).openSession(configuration) as IptvResult.Success).value
        session.close()

        assertTrue(session.getChannels() is IptvResult.Failure)
    }

    @Test
    fun playlistHeaderCanDiscoverEpgWithoutExplicitConfiguration() = runBlocking {
        val playlistUri = URI("https://example.com/channels.m3u")
        val epgUri = URI("https://example.com/guide.xml")
        val loader = InMemoryLoader(
            mapOf(
                playlistUri to """
                    #EXTM3U x-tvg-url="guide.xml"
                    #EXTINF:-1 tvg-id="news",News
                    https://stream.example/news.m3u8
                """.trimIndent(),
                epgUri to """
                    <tv>
                      <channel id="news"><display-name>News</display-name></channel>
                      <programme channel="news" start="20260719120000 +0000" stop="20260719123000 +0000">
                        <title>Noon News</title>
                      </programme>
                    </tv>
                """.trimIndent(),
            ),
        )
        val configuration = IptvConfiguration(
            id = "discovered-epg",
            name = "Discovered EPG",
            catalog = IptvCatalogConfiguration.Playlists(listOf(IptvPlaylistLocation(playlistUri))),
        )
        val session = (ConfiguredIptvSource(4, "Configured", loader).openSession(configuration) as IptvResult.Success).value
        val channel = ((session.getChannels() as IptvResult.Success).value).items.single()
        val epg = session.getEpg(IptvEpgRequest(setOf(channel.id))) as IptvResult.Success

        assertEquals("Noon News", epg.value.programmes.single().titles.single().value)
    }

    @Test
    fun rangedEpgKeepsOpenEndedFinalProgramme() = runBlocking {
        val channel = IptvChannel(id = "open", name = "Open", epgId = "open")
        val playback = IptvPlaybackRequest(URI("https://example.com/open.m3u8"), IptvStreamProtocol.HLS)
        val epgUri = URI("https://example.com/open.xml")
        val loader = InMemoryLoader(
            mapOf(
                epgUri to """
                    <tv>
                      <channel id="open"><display-name>Open</display-name></channel>
                      <programme channel="open" start="20260719120000 +0000"><title>Open programme</title></programme>
                    </tv>
                """.trimIndent(),
            ),
        )
        val configuration = IptvConfiguration(
            id = "open",
            name = "Open",
            catalog = IptvCatalogConfiguration.Channels(listOf(IptvConfiguredChannel(channel, playback))),
            epg = listOf(IptvEpgLocation(epgUri)),
        )
        val session = (ConfiguredIptvSource(14, "Configured", loader).openSession(configuration) as IptvResult.Success).value
        val start = IptvInstant(1_784_462_400_000)
        val epg = session.getEpg(
            IptvEpgRequest(
                channelIds = setOf("open"),
                range = IptvTimeRange(start, start.plusMillis(60_000)),
            ),
        ) as IptvResult.Success

        assertEquals("Open programme", epg.value.programmes.single().titles.single().value)
    }

    private class InMemoryLoader(
        private val resources: Map<URI, String>,
    ) : IptvResourceLoader {
        val headersByUri = mutableMapOf<URI, Map<String, String>>()

        override suspend fun <T> read(
            uri: URI,
            headers: Map<String, String>,
            maximumBytes: Long,
            block: (InputStream) -> T,
        ): T {
            headersByUri[uri] = headers
            val bytes = requireNotNull(resources[uri]) { "Missing fixture: $uri" }.toByteArray()
            require(bytes.size <= maximumBytes)
            return ByteArrayInputStream(bytes).use(block)
        }
    }

    private object FailingLoader : IptvResourceLoader {
        override suspend fun <T> read(
            uri: URI,
            headers: Map<String, String>,
            maximumBytes: Long,
            block: (InputStream) -> T,
        ): T = error("Static channels must not load a playlist")
    }
}
