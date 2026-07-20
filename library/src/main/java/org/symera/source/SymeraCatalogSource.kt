package org.symera.source

import org.symera.source.model.ContentPage
import org.symera.source.model.FilterList
import org.symera.source.model.HomeSection
import org.symera.source.model.PageRequest
import org.symera.source.model.SContent

enum class CatalogFeed {
    MOVIES,
    SERIES,
    POPULAR,
    LATEST,
    SEARCH,
}

/**
 * Optional catalog capabilities. A source advertises and implements any combination of the four
 * main feeds, search, and home sections. An empty filter list is a fully supported configuration.
 */
interface SymeraCatalogSource : SymeraSource {
    val catalogCapabilities: Set<CatalogCapability>

    fun getFilterList(feed: CatalogFeed): FilterList = FilterList()

    fun getFilterList(section: HomeSection): FilterList = FilterList()

    suspend fun getMovies(
        request: PageRequest,
        filters: FilterList = getFilterList(CatalogFeed.MOVIES),
    ): ContentPage = unsupported(CatalogCapability.MOVIES)

    suspend fun getSeries(
        request: PageRequest,
        filters: FilterList = getFilterList(CatalogFeed.SERIES),
    ): ContentPage = unsupported(CatalogCapability.SERIES)

    suspend fun getPopular(
        request: PageRequest,
        filters: FilterList = getFilterList(CatalogFeed.POPULAR),
    ): ContentPage = unsupported(CatalogCapability.POPULAR)

    suspend fun getLatest(
        request: PageRequest,
        filters: FilterList = getFilterList(CatalogFeed.LATEST),
    ): ContentPage = unsupported(CatalogCapability.LATEST)

    suspend fun search(
        request: PageRequest,
        query: String,
        filters: FilterList = getFilterList(CatalogFeed.SEARCH),
    ): ContentPage =
        unsupported(CatalogCapability.SEARCH)

    suspend fun getHomeSections(): List<HomeSection> = unsupported(CatalogCapability.HOME_SECTIONS)

    suspend fun getSectionItems(
        section: HomeSection,
        request: PageRequest,
        filters: FilterList = getFilterList(section),
    ): ContentPage =
        unsupported(CatalogCapability.HOME_SECTIONS)

    override suspend fun getRelated(content: SContent): List<SContent> =
        if (SourceCapability.RELATED_SEARCH in sourceCapabilities) getRelatedBySearch(content) else emptyList()

    suspend fun getRelatedBySearch(content: SContent): List<SContent> {
        if (CatalogCapability.SEARCH !in catalogCapabilities) return emptyList()

        val related = mutableListOf<SContent>()
        val filters = getFilterList(CatalogFeed.SEARCH).requireValid()
        content.relatedSearchTerms().forEach { term ->
            search(PageRequest(), term, filters).contents
                .filterNot { it.sameIdentityAs(content) }
                .forEach { candidate ->
                    if (related.none(candidate::sameIdentityAs)) related += candidate
                }
        }
        return related
    }

    private fun unsupported(capability: SymeraCapability): Nothing =
        throw SourceException.UnsupportedCapability(capability)
}

private fun SContent.relatedSearchTerms(): List<String> = buildList {
    add(title)
    originalTitle?.let(::add)
}.map { title ->
    title
        .replace(Regex("\\[[^]]*]"), " ")
        .replace(Regex("\\([^)]*\\)"), " ")
        .replace(Regex("(?i)\\b(s\\d{1,2}|season\\s*\\d+|temporada\\s*\\d+)\\b"), " ")
        .replace(Regex("\\b(19|20)\\d{2}\\b"), " ")
        .trim()
}.filter { it.length >= 3 }.distinctBy { it.lowercase(java.util.Locale.ROOT) }

private fun SContent.sameIdentityAs(other: SContent): Boolean {
    val ids = externalIds.mapTo(mutableSetOf()) {
        "${it.provider.trim().lowercase(java.util.Locale.ROOT)}:${it.value.trim()}"
    }
    val otherIds = other.externalIds.mapTo(mutableSetOf()) {
        "${it.provider.trim().lowercase(java.util.Locale.ROOT)}:${it.value.trim()}"
    }
    return ids.intersect(otherIds).isNotEmpty() || url.trim() == other.url.trim()
}
