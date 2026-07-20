package org.symera.source.torrentutils

import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Assert.assertNull
import org.symera.source.torrentutils.service.OkHttpTorrentFetcher
import org.junit.Test

class TorrentUtilsTest {
    @Test
    fun parsesV1MagnetWithoutInventingFileMetadata() {
        val info = TorrentUtils.parseMagnet(
            "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=Example&tr=https%3A%2F%2Ftracker.example%2Fannounce",
        )

        assertEquals("Example", info.title)
        assertTrue(info.files.isEmpty())
        assertEquals(null, info.size)
        assertEquals(1, info.trackers.size)
    }

    @Test
    fun preservesBep53FileSelection() {
        val info = TorrentUtils.parseMagnet(
            "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&so=1-3",
        )
        assertEquals("1-3", info.selectedFiles.toString())
    }

    @Test
    fun acceptsEmptyV2PieceLayersWhenNoFileNeedsOne() {
        val output = ByteArrayOutputStream()
        output.write("d4:infod9:file treed8:file.bind0:d6:lengthi1e11:pieces root32:".toByteArray())
        output.write(ByteArray(32) { it.toByte() })
        output.write("eee12:meta versioni2e4:name4:test12:piece lengthi16384ee12:piece layersdee".toByteArray())

        val info = TorrentUtils.parseTorrent(output.toByteArray())

        assertEquals("test", info.title)
        assertEquals(1L, info.size)
        assertEquals("file.bin", info.files.single().path)
        assertTrue(info.hashes.v2 != null)
    }

    @Test
    fun rejectsUnsafeMagnetEndpoints() {
        assertThrows(org.symera.source.torrentutils.model.DeadTorrentException::class.java) {
            TorrentUtils.parseMagnet(
                "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&ws=file%3A%2F%2F%2Ftmp%2Fvideo",
            )
        }
    }

    @Test
    fun authenticatedTorrentRedirectDropsCrossOriginHeaders() = runBlocking {
        var redirectedApiKey: String? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            if (request.url.host == "source.example") {
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(302)
                    .message("Found")
                    .header("Location", "https://target.example/file.torrent")
                    .body(ByteArray(0).toResponseBody())
                    .build()
            } else {
                redirectedApiKey = request.header("X-Api-Key")
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("torrent".toResponseBody())
                    .build()
            }
        }.build()
        val request = Request.Builder()
            .url("https://source.example/file.torrent")
            .header("X-Api-Key", "secret")
            .build()

        assertEquals("torrent", OkHttpTorrentFetcher(client).fetch(request).toString(Charsets.UTF_8))
        assertNull(redirectedApiKey)
    }
}
