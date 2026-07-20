package org.symera.source

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.symera.source.model.ContentPage
import org.symera.source.model.ContentType
import org.symera.source.model.Filter
import org.symera.source.model.FilterList
import org.symera.source.model.HomeSection
import org.symera.source.model.PageRequest
import org.symera.source.model.SContent
import org.symera.source.online.SymeraHttpSource

class CatalogFiltersTest {
    @Test
    fun everyFeedUsesItsOwnFilterSchema() = runBlocking {
        val source = DirectSource()
        val section = HomeSection("featured", "Featured")

        source.getMovies(PageRequest())
        source.getSeries(PageRequest())
        source.getPopular(PageRequest())
        source.getLatest(PageRequest())
        source.getSectionItems(section, PageRequest())

        assertEquals(listOf("movies", "series", "popular", "latest", "section:featured"), source.calls)
        assertSame(source.feedFilters.getValue(CatalogFeed.MOVIES), source.receivedFilters[0])
        assertSame(source.sectionFilters, source.receivedFilters[4])
    }

    @Test
    fun searchHasAnIndependentFilterSchema() {
        val source = DirectSource()

        assertSame(source.feedFilters.getValue(CatalogFeed.SEARCH), source.getFilterList(CatalogFeed.SEARCH))
        assertSame(source.feedFilters.getValue(CatalogFeed.MOVIES), source.getFilterList(CatalogFeed.MOVIES))
    }

    @Test
    fun httpSourceUsesFilteredRequestHookOnlyWhenFeedAdvertisesFilters() = runBlocking {
        val source = FilteredHttpSource()
        val filters = source.getFilterList(CatalogFeed.MOVIES)

        source.getMovies(PageRequest(), filters)

        assertSame(filters, source.receivedFilters)

        val expired = ExpiringFilter()
        val invalidFilters = FilterList(expired)
        expired.expired = true
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { source.getMovies(PageRequest(), invalidFilters) }
        }
        Unit
    }

    private class DirectSource : SymeraCatalogSource {
        override val id = 1L
        override val name = "Legacy"
        override val lang = "en"
        override val contentTypes = setOf(ContentType.MOVIE, ContentType.SERIES)
        override val catalogCapabilities = CatalogCapability.entries.toSet()
        val calls = mutableListOf<String>()
        val receivedFilters = mutableListOf<FilterList>()
        val feedFilters = CatalogFeed.entries.associateWith { FilterList(Filter.Text(it.name)) }
        val sectionFilters = FilterList(Filter.Text("Section"))

        override fun getFilterList(feed: CatalogFeed): FilterList = feedFilters.getValue(feed)
        override fun getFilterList(section: HomeSection): FilterList = sectionFilters
        override suspend fun getMovies(request: PageRequest, filters: FilterList): ContentPage = result("movies", filters)
        override suspend fun getSeries(request: PageRequest, filters: FilterList): ContentPage = result("series", filters)
        override suspend fun getPopular(request: PageRequest, filters: FilterList): ContentPage = result("popular", filters)
        override suspend fun getLatest(request: PageRequest, filters: FilterList): ContentPage = result("latest", filters)

        override suspend fun getSectionItems(
            section: HomeSection,
            request: PageRequest,
            filters: FilterList,
        ): ContentPage = result("section:${section.id}", filters)

        override suspend fun getDetails(content: SContent): SContent = content

        private fun result(call: String, filters: FilterList): ContentPage {
            filters.requireValid()
            calls += call
            receivedFilters += filters
            return ContentPage.Empty
        }
    }

    private class FilteredHttpSource : SymeraHttpSource(TEST_ENVIRONMENT) {
        override val name = "Filtered HTTP"
        override val lang = "en"
        override val contentTypes = setOf(ContentType.MOVIE)
        override val catalogCapabilities = setOf(CatalogCapability.MOVIES)
        override val baseUrl = "https://example.com"
        var receivedFilters: FilterList? = null

        override fun getFilterList(feed: CatalogFeed): FilterList =
            if (feed == CatalogFeed.MOVIES) FilterList(Filter.Text("Genre")) else super.getFilterList(feed)

        override fun moviesRequest(request: PageRequest, filters: FilterList): Request {
            receivedFilters = filters
            return Request.Builder().url(baseUrl).build()
        }

        override fun moviesParse(response: Response): ContentPage = ContentPage.Empty
        override fun contentDetailsParse(response: Response): SContent = error("Not used")
    }

    private class ExpiringFilter : Filter<Int>("Expiring", 0) {
        var expired = false

        override fun validateState(value: Int) {
            require(!expired) { "Filter expired" }
        }
    }

    private companion object {
        val TEST_ENVIRONMENT = object : SourceEnvironment {
            override val httpClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("".toResponseBody())
                        .build()
                }
                .build()
            override val userAgent = "Symera Test"
            override val appInfo = HostAppInfo(1, "1", 4)
            override val logger = object : SourceLogger {
                override fun debug(message: String) = Unit
                override fun warning(message: String, cause: Throwable?) = Unit
                override fun error(message: String, cause: Throwable?) = Unit
            }

            override fun preferencesFor(namespace: String): SourcePreferenceValues = error("Not used")
        }
    }
}
