package org.symera.source.iptv

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchUpTemplateCompilerTest {
    @Test
    fun crossOriginCatchUpDoesNotLeakLiveCredentials() {
        val live = IptvPlaybackRequest(
            uri = URI("https://live.example/channel.m3u8"),
            protocol = IptvStreamProtocol.HLS,
            headers = mapOf("Authorization" to "secret"),
            headerRules = listOf(
                IptvHeaderRule(
                    requestKinds = setOf(IptvRequestKind.MANIFEST),
                    allowedOrigins = setOf("https://live.example", "https://archive.example"),
                    headers = mapOf("Cookie" to "credential"),
                ),
            ),
            userAgent = "Private agent",
            referrer = URI("https://live.example/"),
            authorizationHandle = IptvAuthorizationHandle("dynamic-secret", setOf("https://live.example")),
        )
        val result = CatchUpTemplateCompiler().compile(
            template = "https://archive.example/{utc}.m3u8|X-Archive=yes",
            liveRequest = live,
            programme = IptvTimeRange(IptvInstant(1_000), IptvInstant(2_000)),
            now = IptvInstant(3_000),
        ) as IptvResult.Success

        assertEquals(mapOf("X-Archive" to "yes"), result.value.headers)
        assertNull(result.value.userAgent)
        assertNull(result.value.referrer)
        assertNull(result.value.authorizationHandle)
        assertEquals(IptvStreamProtocol.AUTO, result.value.protocol)
        assertEquals(setOf("https://archive.example"), result.value.headerRules.single().allowedOrigins)
        assertTrue("credential" !in result.value.toString())
    }

    @Test
    fun unsupportedCompiledSchemeIsRejected() {
        val live = IptvPlaybackRequest(URI("https://live.example/channel.m3u8"), IptvStreamProtocol.HLS)
        val result = CatchUpTemplateCompiler().compile(
            template = "file:///tmp/{utc}.m3u8",
            liveRequest = live,
            programme = IptvTimeRange(IptvInstant(1_000), IptvInstant(2_000)),
            now = IptvInstant(3_000),
        )
        assertTrue(result is IptvResult.Failure)
    }
}
