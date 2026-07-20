package org.symera.source.iptv

import java.io.StringReader
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.symera.source.iptv.parser.ExtendedM3uParser
import org.symera.source.iptv.parser.IptvParseException
import org.symera.source.iptv.parser.IptvDiagnosticCode
import org.symera.source.iptv.parser.IptvParseLimits

class ExtendedM3uParserTest {
    @Test
    fun parsesPlaylistUrlWithMultipleChannelsAndHeaders() {
        val playlist = """
            #EXTM3U x-tvg-url="https://guide.example/epg.xml"
            #EXTINF:-1 tvg-id="news" group-title="News, World" tvg-logo="/news.png",News, International
            #EXTVLCOPT:http-user-agent=Symera Test
            streams/news.m3u8|Referer=https%3A%2F%2Fexample.com
            #EXTINF:-1 tvg-id="radio" radio="true" group-title="Radio",Radio One
            https://radio.example/live.mp3
        """.trimIndent()

        val result = ExtendedM3uParser().parse(StringReader(playlist), URI("https://example.com/list/main.m3u"))

        assertEquals(2, result.value.entries.size)
        assertEquals("News, International", result.value.entries[0].channel.name)
        assertEquals(URI("https://example.com/list/streams/news.m3u8"), result.value.entries[0].playback.uri)
        assertEquals("https://example.com", result.value.entries[0].playback.headers["Referer"])
        assertEquals(IptvChannelKind.RADIO, result.value.entries[1].channel.kind)
    }

    @Test
    fun rejectsHlsManifestAsChannelCatalog() {
        val hls = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nsegment.ts"
        assertThrows(IptvParseException::class.java) {
            ExtendedM3uParser().parse(StringReader(hls), URI("https://example.com/live.m3u8"))
        }
    }

    @Test
    fun enforcesChannelLimit() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1,One
            https://example.com/one
            #EXTINF:-1,Two
            https://example.com/two
        """.trimIndent()
        assertThrows(IptvParseException::class.java) {
            ExtendedM3uParser(limits = IptvParseLimits(maximumEntries = 1))
                .parse(StringReader(playlist))
        }
    }

    @Test
    fun variantsSharingEpgIdReceiveDifferentInternalIds() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="news",News HD
            https://example.com/news-hd.m3u8?token=one
            #EXTINF:-1 tvg-id="news",News SD
            https://example.com/news-sd.m3u8?token=two
        """.trimIndent()
        val entries = ExtendedM3uParser().parse(StringReader(playlist)).value.entries

        assertEquals(2, entries.map { it.channel.id }.distinct().size)
        assertEquals(listOf("news", "news"), entries.map { it.channel.epgId })
    }

    @Test
    fun queryStringParticipatesInGeneratedChannelIdentity() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1,News
            https://example.com/live.m3u8?channel=one
            #EXTINF:-1,News
            https://example.com/live.m3u8?channel=two
        """.trimIndent()

        val entries = ExtendedM3uParser().parse(StringReader(playlist)).value.entries
        assertEquals(2, entries.map { it.channel.id }.distinct().size)
    }

    @Test
    fun invalidTinyTimeshiftBecomesParserDiagnostic() {
        val playlist = "#EXTM3U\n#EXTINF:-1 timeshift=\"0.00000001\",One\nhttps://example.com/one.m3u8"
        val result = ExtendedM3uParser().parse(StringReader(playlist))

        assertEquals(null, result.value.entries.single().channel.timeshift)
        assertEquals(IptvDiagnosticCode.INVALID_ATTRIBUTE, result.diagnostics.single().code)
    }
}
