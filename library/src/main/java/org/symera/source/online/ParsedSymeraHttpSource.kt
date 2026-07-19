package org.symera.source.online

import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.symera.source.CatalogCapability
import org.symera.source.SourceCapability
import org.symera.source.SourceEnvironment
import org.symera.source.SourceException
import org.symera.source.model.ContentPage
import org.symera.source.model.SContent
import org.symera.source.model.SHoster
import org.symera.source.model.SPlayableItem
import org.symera.source.model.SSeason
import org.symera.source.model.SStream

/** Jsoup conveniences. Only selectors for advertised capabilities are required. */
abstract class ParsedSymeraHttpSource(
    environment: SourceEnvironment,
) : SymeraHttpSource(environment) {
    override fun moviesParse(response: Response): ContentPage = response.asJsoup().parsePage(
        selector = moviesContentSelector() ?: unsupported(CatalogCapability.MOVIES),
        nextSelector = moviesContentNextPageSelector(),
        mapper = ::moviesContentFromElement,
    )

    protected open fun moviesContentSelector(): String? = null
    protected open fun moviesContentFromElement(element: Element): SContent = unsupported(CatalogCapability.MOVIES)
    protected open fun moviesContentNextPageSelector(): String? = null

    override fun seriesParse(response: Response): ContentPage = response.asJsoup().parsePage(
        selector = seriesContentSelector() ?: unsupported(CatalogCapability.SERIES),
        nextSelector = seriesContentNextPageSelector(),
        mapper = ::seriesContentFromElement,
    )

    protected open fun seriesContentSelector(): String? = null
    protected open fun seriesContentFromElement(element: Element): SContent = unsupported(CatalogCapability.SERIES)
    protected open fun seriesContentNextPageSelector(): String? = null

    override fun popularParse(response: Response): ContentPage = response.asJsoup().parsePage(
        selector = popularContentSelector() ?: unsupported(CatalogCapability.POPULAR),
        nextSelector = popularContentNextPageSelector(),
        mapper = ::popularContentFromElement,
    )

    protected open fun popularContentSelector(): String? = null
    protected open fun popularContentFromElement(element: Element): SContent = unsupported(CatalogCapability.POPULAR)
    protected open fun popularContentNextPageSelector(): String? = null

    override fun latestParse(response: Response): ContentPage = response.asJsoup().parsePage(
        selector = latestContentSelector() ?: unsupported(CatalogCapability.LATEST),
        nextSelector = latestContentNextPageSelector(),
        mapper = ::latestContentFromElement,
    )

    protected open fun latestContentSelector(): String? = null
    protected open fun latestContentFromElement(element: Element): SContent = unsupported(CatalogCapability.LATEST)
    protected open fun latestContentNextPageSelector(): String? = null

    override fun searchParse(response: Response): ContentPage = response.asJsoup().parsePage(
        selector = searchContentSelector() ?: unsupported(CatalogCapability.SEARCH),
        nextSelector = searchContentNextPageSelector(),
        mapper = ::searchContentFromElement,
    )

    protected open fun searchContentSelector(): String? = null
    protected open fun searchContentFromElement(element: Element): SContent = unsupported(CatalogCapability.SEARCH)
    protected open fun searchContentNextPageSelector(): String? = null

    override fun contentDetailsParse(response: Response): SContent = contentDetailsParse(response.asJsoup())
    protected open fun contentDetailsParse(document: Document): SContent =
        throw SourceException.Parse("No HTML content details parser is configured")

    override fun playableItemsParse(response: Response): List<SPlayableItem> =
        response.asJsoup().select(
            playableItemsSelector() ?: unsupported(SourceCapability.PLAYABLE_ITEMS),
        ).map(::playableItemFromElement)

    protected open fun playableItemsSelector(): String? = null
    protected open fun playableItemFromElement(element: Element): SPlayableItem =
        unsupported(SourceCapability.PLAYABLE_ITEMS)

    override fun seasonsParse(response: Response): List<SSeason> = response.asJsoup().select(
        seasonsSelector() ?: unsupported(SourceCapability.SEASONS),
    ).map(::seasonFromElement)

    protected open fun seasonsSelector(): String? = null
    protected open fun seasonFromElement(element: Element): SSeason = unsupported(SourceCapability.SEASONS)

    override fun seasonItemsParse(response: Response, season: SSeason): List<SPlayableItem> =
        response.asJsoup().select(
            seasonItemsSelector(season) ?: unsupported(SourceCapability.SEASONS),
        ).map { seasonItemFromElement(it, season) }

    protected open fun seasonItemsSelector(season: SSeason): String? = null
    protected open fun seasonItemFromElement(element: Element, season: SSeason): SPlayableItem =
        unsupported(SourceCapability.SEASONS)

    override fun relatedParse(response: Response): List<SContent> = response.asJsoup().select(
        relatedContentSelector() ?: unsupported(SourceCapability.RELATED_CONTENT),
    ).map(::relatedContentFromElement)

    protected open fun relatedContentSelector(): String? = null
    protected open fun relatedContentFromElement(element: Element): SContent = unsupported(SourceCapability.RELATED_CONTENT)

    override fun itemStreamsParse(response: Response, item: SPlayableItem): List<SStream> =
        response.asJsoup().select(
            itemStreamsSelector(item) ?: unsupported(SourceCapability.ITEM_STREAMS),
        ).map { itemStreamFromElement(it, item) }

    protected open fun itemStreamsSelector(item: SPlayableItem): String? = null
    protected open fun itemStreamFromElement(element: Element, item: SPlayableItem): SStream =
        unsupported(SourceCapability.ITEM_STREAMS)

    override fun hostersParse(response: Response): List<SHoster> =
        response.asJsoup().select(hostersSelector() ?: unsupported(SourceCapability.HOSTERS)).map(::hosterFromElement)

    protected open fun hostersSelector(): String? = null
    protected open fun hosterFromElement(element: Element): SHoster = unsupported(SourceCapability.HOSTERS)

    override fun streamsParse(response: Response, hoster: SHoster): List<SStream> =
        response.asJsoup().select(
            streamsSelector(hoster) ?: unsupported(SourceCapability.HOSTERS),
        ).map { streamFromElement(it, hoster) }

    protected open fun streamsSelector(hoster: SHoster): String? = null
    protected open fun streamFromElement(element: Element, hoster: SHoster): SStream =
        unsupported(SourceCapability.HOSTERS)

    private fun Document.parsePage(
        selector: String,
        nextSelector: String?,
        mapper: (Element) -> SContent,
    ): ContentPage = ContentPage(
        contents = select(selector).map(mapper),
        hasNextPage = nextSelector?.let { selectFirst(it) != null } == true,
    )

    private fun unsupported(capability: org.symera.source.SymeraCapability): Nothing =
        throw SourceException.UnsupportedCapability(capability)
}
