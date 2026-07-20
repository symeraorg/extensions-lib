package org.symera.source.online

import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.symera.source.CatalogCapability
import org.symera.source.CatalogFeed
import org.symera.source.SourceCapability
import org.symera.source.SourceEnvironment
import org.symera.source.SourceException
import org.symera.source.SourceIdGenerator
import org.symera.source.SymeraCatalogSource
import org.symera.source.challenge.WebChallengeSource
import org.symera.source.model.ContentPage
import org.symera.source.model.FilterList
import org.symera.source.model.HomeSection
import org.symera.source.model.PageRequest
import org.symera.source.model.SContent
import org.symera.source.model.SHoster
import org.symera.source.model.SPlayableItem
import org.symera.source.model.SSeason
import org.symera.source.model.SStream
import org.symera.source.network.awaitSuccess

/** HTTP request/parse base. A source only overrides methods for capabilities it advertises. */
abstract class SymeraHttpSource(
    protected val environment: SourceEnvironment,
) : SymeraCatalogSource {
    abstract val baseUrl: String

    open val versionId: Int = 1

    override val id: Long by lazy { generateId(name, lang, versionId) }

    open val client: OkHttpClient by lazy {
        val challengeSource = this as? WebChallengeSource
        val challengeFactory = environment.webChallengeInterceptorFactory
        environment.httpClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request().let { current ->
                    if (current.header("User-Agent") == null) {
                        current.newBuilder().header("User-Agent", environment.userAgent).build()
                    } else {
                        current
                    }
                }
                chain.proceed(request)
            }
            .apply {
                if (challengeSource != null && challengeFactory != null) {
                    addInterceptor(challengeFactory.create(id, challengeSource::webChallengePolicy))
                }
            }
            .build()
    }

    val headers: Headers by lazy { headersBuilder().build() }

    protected open fun headersBuilder(): Headers.Builder =
        Headers.Builder().add("User-Agent", environment.userAgent)

    override suspend fun getMovies(request: PageRequest, filters: FilterList): ContentPage =
        execute(moviesRequest(request, filters.requireValid()), ::moviesParse)

    protected open fun moviesRequest(request: PageRequest, filters: FilterList): Request =
        unsupported(CatalogCapability.MOVIES)

    protected open fun moviesParse(response: Response): ContentPage = unsupported(CatalogCapability.MOVIES)

    override suspend fun getSeries(request: PageRequest, filters: FilterList): ContentPage =
        execute(seriesRequest(request, filters.requireValid()), ::seriesParse)

    protected open fun seriesRequest(request: PageRequest, filters: FilterList): Request =
        unsupported(CatalogCapability.SERIES)

    protected open fun seriesParse(response: Response): ContentPage = unsupported(CatalogCapability.SERIES)

    override suspend fun getPopular(request: PageRequest, filters: FilterList): ContentPage =
        execute(popularRequest(request, filters.requireValid()), ::popularParse)

    protected open fun popularRequest(request: PageRequest, filters: FilterList): Request =
        unsupported(CatalogCapability.POPULAR)

    protected open fun popularParse(response: Response): ContentPage = unsupported(CatalogCapability.POPULAR)

    override suspend fun getLatest(request: PageRequest, filters: FilterList): ContentPage =
        execute(latestRequest(request, filters.requireValid()), ::latestParse)

    protected open fun latestRequest(request: PageRequest, filters: FilterList): Request =
        unsupported(CatalogCapability.LATEST)

    protected open fun latestParse(response: Response): ContentPage = unsupported(CatalogCapability.LATEST)

    override suspend fun search(request: PageRequest, query: String, filters: FilterList): ContentPage =
        execute(searchRequest(request, query, filters.requireValid()), ::searchParse)

    protected open fun searchRequest(request: PageRequest, query: String, filters: FilterList): Request =
        unsupported(CatalogCapability.SEARCH)

    protected open fun searchParse(response: Response): ContentPage = unsupported(CatalogCapability.SEARCH)

    override suspend fun getHomeSections(): List<HomeSection> =
        execute(homeSectionsRequest(), ::homeSectionsParse)

    protected open fun homeSectionsRequest(): Request = unsupported(CatalogCapability.HOME_SECTIONS)
    protected open fun homeSectionsParse(response: Response): List<HomeSection> = unsupported(CatalogCapability.HOME_SECTIONS)

    override suspend fun getSectionItems(
        section: HomeSection,
        request: PageRequest,
        filters: FilterList,
    ): ContentPage = execute(sectionItemsRequest(section, request, filters.requireValid())) { sectionItemsParse(it, section) }

    protected open fun sectionItemsRequest(
        section: HomeSection,
        request: PageRequest,
        filters: FilterList,
    ): Request = unsupported(CatalogCapability.HOME_SECTIONS)

    protected open fun sectionItemsParse(response: Response, section: HomeSection): ContentPage =
        unsupported(CatalogCapability.HOME_SECTIONS)

    override suspend fun getDetails(content: SContent): SContent =
        execute(contentDetailsRequest(content), ::contentDetailsParse).copy(initialized = true)

    protected open fun contentDetailsRequest(content: SContent): Request = GET(getContentUrl(content), headers)
    protected abstract fun contentDetailsParse(response: Response): SContent

    override suspend fun getPlayableItems(content: SContent): List<SPlayableItem> =
        execute(playableItemsRequest(content), ::playableItemsParse)

    protected open fun playableItemsRequest(content: SContent): Request = GET(getContentUrl(content), headers)
    protected open fun playableItemsParse(response: Response): List<SPlayableItem> =
        unsupported(SourceCapability.PLAYABLE_ITEMS)

    override suspend fun getSeasons(content: SContent): List<SSeason> =
        execute(seasonsRequest(content), ::seasonsParse)

    protected open fun seasonsRequest(content: SContent): Request = GET(getContentUrl(content), headers)
    protected open fun seasonsParse(response: Response): List<SSeason> = unsupported(SourceCapability.SEASONS)

    override suspend fun getPlayableItems(season: SSeason): List<SPlayableItem> =
        season.playableItems ?: execute(seasonItemsRequest(season), { seasonItemsParse(it, season) })

    protected open fun seasonItemsRequest(season: SSeason): Request = GET(absoluteUrl(season.url), headers)
    protected open fun seasonItemsParse(response: Response, season: SSeason): List<SPlayableItem> =
        unsupported(SourceCapability.SEASONS)

    override suspend fun getRelated(content: SContent): List<SContent> =
        if (SourceCapability.RELATED_CONTENT in sourceCapabilities) {
            val direct = execute(relatedRequest(content), ::relatedParse)
            if (direct.isNotEmpty() || SourceCapability.RELATED_SEARCH !in sourceCapabilities) {
                direct
            } else {
                super<SymeraCatalogSource>.getRelated(content)
            }
        } else {
            super<SymeraCatalogSource>.getRelated(content)
        }

    protected open fun relatedRequest(content: SContent): Request = GET(getContentUrl(content), headers)
    protected open fun relatedParse(response: Response): List<SContent> = unsupported(SourceCapability.RELATED_CONTENT)

    override suspend fun getStreams(item: SPlayableItem): List<SStream> =
        execute(itemStreamsRequest(item)) { itemStreamsParse(it, item) }.sortStreams()

    protected open fun itemStreamsRequest(item: SPlayableItem): Request = GET(getPlayableItemUrl(item), headers)
    protected open fun itemStreamsParse(response: Response, item: SPlayableItem): List<SStream> =
        unsupported(SourceCapability.ITEM_STREAMS)

    override suspend fun getHosters(item: SPlayableItem): List<SHoster> =
        execute(hostersRequest(item), ::hostersParse).sortHosters()

    protected open fun hostersRequest(item: SPlayableItem): Request = GET(getPlayableItemUrl(item), headers)
    protected open fun hostersParse(response: Response): List<SHoster> = unsupported(SourceCapability.HOSTERS)

    override suspend fun getStreams(hoster: SHoster): List<SStream> =
        (hoster.streams ?: execute(streamsRequest(hoster), { streamsParse(it, hoster) })).sortStreams()

    protected open fun streamsRequest(hoster: SHoster): Request =
        GET(hoster.requestUrl?.let(::absoluteUrl) ?: unsupported(SourceCapability.HOSTERS), headers)

    protected open fun streamsParse(response: Response, hoster: SHoster): List<SStream> =
        unsupported(SourceCapability.HOSTERS)

    open fun getContentUrl(content: SContent): String = absoluteUrl(content.url)
    open fun getPlayableItemUrl(item: SPlayableItem): String = absoluteUrl(item.url)
    open fun List<SHoster>.sortHosters(): List<SHoster> = this
    open fun List<SStream>.sortStreams(): List<SStream> = this
    override fun getFilterList(feed: CatalogFeed): FilterList = FilterList()
    override fun getFilterList(section: HomeSection): FilterList = FilterList()
    override fun toString(): String = "$name (${lang.uppercase(Locale.ROOT)})"

    fun contentWithRelativeUrl(content: SContent, url: String): SContent = content.copy(url = relativeUrl(url))
    fun itemWithRelativeUrl(item: SPlayableItem, url: String): SPlayableItem = item.copy(url = relativeUrl(url))

    protected fun generateId(name: String, lang: String, versionId: Int): Long {
        return SourceIdGenerator.generate(name, lang, versionId)
    }

    protected fun absoluteUrl(reference: String): String {
        val absolute = runCatching { reference.toHttpUrl() }.getOrNull()
        if (absolute != null) return absolute.toString()
        return requireNotNull(baseUrl.toHttpUrl().resolve(reference)) { "Cannot resolve URL reference: $reference" }.toString()
    }

    protected fun relativeUrl(url: String): String {
        val parsed = url.toHttpUrl()
        return buildString {
            append(parsed.encodedPath)
            parsed.encodedQuery?.let { append('?').append(it) }
            parsed.encodedFragment?.let { append('#').append(it) }
        }
    }

    protected suspend fun <T> execute(request: Request, parse: (Response) -> T): T =
        client.awaitSuccess(request).use { response ->
            withContext(Dispatchers.IO) { parse(response) }
        }

    private fun unsupported(capability: org.symera.source.SymeraCapability): Nothing =
        throw SourceException.UnsupportedCapability(capability)
}
