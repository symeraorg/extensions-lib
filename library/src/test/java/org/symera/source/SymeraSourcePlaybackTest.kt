package org.symera.source

import java.net.URI
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.symera.source.model.ContentType
import org.symera.source.model.MediaRequest
import org.symera.source.model.PlayableItemType
import org.symera.source.model.PlayableStream
import org.symera.source.model.SContent
import org.symera.source.model.SPlayableItem

class SymeraSourcePlaybackTest {
    @Test
    fun directStreamSourceDoesNotNeedSyntheticHosters() = runBlocking {
        val source = DirectSource()
        val item = SPlayableItem("/movie", type = PlayableItemType.MOVIE)

        assertEquals("direct", (source.getStreams(item).single() as PlayableStream).id)
        val failure = assertThrows(SourceException.UnsupportedCapability::class.java) {
            runBlocking { source.getHosters(item) }
        }
        assertEquals(SourceCapability.HOSTERS, failure.capability)
    }

    private class DirectSource : SymeraSource {
        override val id = 1L
        override val name = "Direct"
        override val lang = "en"
        override val contentTypes = setOf(ContentType.MOVIE)
        override val sourceCapabilities = setOf(SourceCapability.PLAYABLE_ITEMS, SourceCapability.ITEM_STREAMS)

        override suspend fun getDetails(content: SContent): SContent = content

        override suspend fun getStreams(item: SPlayableItem) = listOf(
            PlayableStream("direct", request = MediaRequest(URI("https://example.com/movie.mp4").toString())),
        )
    }
}
