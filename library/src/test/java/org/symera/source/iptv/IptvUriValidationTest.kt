package org.symera.source.iptv

import java.net.URI
import org.junit.Assert.assertThrows
import org.junit.Test

class IptvUriValidationTest {
    @Test
    fun resourceModelsRejectNonHttpSchemesAndEmbeddedCredentials() {
        assertThrows(IllegalArgumentException::class.java) {
            IptvPlaylistLocation(URI("file:///tmp/list.m3u"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            IptvEpgLocation(URI("https://user:password@example.com/guide.xml"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            IptvImage(URI("javascript:alert(1)"))
        }
    }

    @Test
    fun playbackProtocolMustMatchItsUriScheme() {
        assertThrows(IllegalArgumentException::class.java) {
            IptvPlaybackRequest(URI("udp://239.1.1.1:1234"), IptvStreamProtocol.HLS)
        }
        IptvPlaybackRequest(URI("udp://239.1.1.1:1234"), IptvStreamProtocol.UDP)
        IptvPlaybackRequest(URI("rtsp://example.com/live"), IptvStreamProtocol.RTSP)
    }
}
