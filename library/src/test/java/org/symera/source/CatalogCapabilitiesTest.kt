package org.symera.source

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.symera.source.model.ContentPage
import org.symera.source.model.ContentType
import org.symera.source.model.PageRequest
import org.symera.source.model.SContent

class CatalogCapabilitiesTest {
    @Test
    fun sourceCanImplementOnlyLatest() = runBlocking {
        val source = LatestOnlySource()
        assertEquals(setOf(CatalogCapability.LATEST), source.catalogCapabilities)
        assertEquals("Latest", source.getLatest(PageRequest()).contents.single().title)
        assertThrows(SourceException.UnsupportedCapability::class.java) {
            runBlocking { source.getMovies(PageRequest()) }
        }
        Unit
    }

    @Test
    fun relatedSearchRequiresExplicitOptIn() = runBlocking {
        val disabled = RelatedSource(emptySet())
        val enabled = RelatedSource(setOf(SourceCapability.RELATED_SEARCH))
        val content = SContent("/original", "Example (2026)")

        assertTrue(disabled.getRelated(content).isEmpty())
        assertEquals(0, disabled.searchCount)
        assertEquals("Related", enabled.getRelated(content).single().title)
        assertEquals(1, enabled.searchCount)
    }

    private class LatestOnlySource : SymeraCatalogSource {
        override val id = 1L
        override val name = "Latest only"
        override val lang = "en"
        override val contentTypes = setOf(ContentType.SERIES)
        override val catalogCapabilities = setOf(CatalogCapability.LATEST)

        override suspend fun getLatest(request: PageRequest): ContentPage =
            ContentPage(listOf(SContent("/latest", "Latest")), false)

        override suspend fun getDetails(content: SContent): SContent = content
    }

    private class RelatedSource(
        override val sourceCapabilities: Set<SourceCapability>,
    ) : SymeraCatalogSource {
        override val id = 2L
        override val name = "Related"
        override val lang = "en"
        override val contentTypes = setOf(ContentType.MOVIE)
        override val catalogCapabilities = setOf(CatalogCapability.SEARCH)
        var searchCount = 0

        override suspend fun search(
            request: PageRequest,
            query: String,
            filters: org.symera.source.model.FilterList,
        ): ContentPage {
            searchCount++
            return ContentPage(listOf(SContent("/related", "Related")), false)
        }

        override suspend fun getDetails(content: SContent): SContent = content
    }
}
