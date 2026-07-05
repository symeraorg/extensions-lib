package org.symera.source.online

import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.symera.source.model.ContentPage
import org.symera.source.model.SContent
import org.symera.source.model.SHoster
import org.symera.source.model.SPlayableItem
import org.symera.source.model.SSeason
import org.symera.source.model.SStream

/** Convenience base class for HTML sources parsed with Jsoup selectors. */
abstract class ParsedSymeraHttpSource : SymeraHttpSource() {
    override fun moviesParse(response: Response): ContentPage {
        val document = response.asJsoup()
        val contents = document.select(moviesContentSelector()).map(::moviesContentFromElement)
        val hasNextPage = moviesContentNextPageSelector()?.let { document.selectFirst(it) } != null
        return ContentPage(contents, hasNextPage)
    }

    protected abstract fun moviesContentSelector(): String
    protected abstract fun moviesContentFromElement(element: Element): SContent
    protected abstract fun moviesContentNextPageSelector(): String?

    override fun seriesParse(response: Response): ContentPage {
        val document = response.asJsoup()
        val contents = document.select(seriesContentSelector()).map(::seriesContentFromElement)
        val hasNextPage = seriesContentNextPageSelector()?.let { document.selectFirst(it) } != null
        return ContentPage(contents, hasNextPage)
    }

    protected abstract fun seriesContentSelector(): String
    protected abstract fun seriesContentFromElement(element: Element): SContent
    protected abstract fun seriesContentNextPageSelector(): String?

    override fun searchParse(response: Response): ContentPage {
        val document = response.asJsoup()
        val contents = document.select(searchContentSelector()).map(::searchContentFromElement)
        val hasNextPage = searchContentNextPageSelector()?.let { document.selectFirst(it) } != null
        return ContentPage(contents, hasNextPage)
    }

    protected abstract fun searchContentSelector(): String
    protected abstract fun searchContentFromElement(element: Element): SContent
    protected abstract fun searchContentNextPageSelector(): String?

    override fun contentDetailsParse(response: Response): SContent = contentDetailsParse(response.asJsoup())

    protected abstract fun contentDetailsParse(document: Document): SContent

    override fun playableItemsParse(response: Response): List<SPlayableItem> {
        val document = response.asJsoup()
        return document.select(playableItemsSelector()).map(::playableItemFromElement)
    }

    protected abstract fun playableItemsSelector(): String
    protected abstract fun playableItemFromElement(element: Element): SPlayableItem

    override fun seasonsParse(response: Response): List<SSeason> {
        val selector = seasonsSelector() ?: return emptyList()
        return response.asJsoup().select(selector).map(::seasonFromElement)
    }

    protected open fun seasonsSelector(): String? = null
    protected open fun seasonFromElement(element: Element): SSeason = throw UnsupportedOperationException("Seasons are not supported")

    override fun hostersParse(response: Response): List<SHoster> {
        val selector = hostersSelector() ?: return emptyList()
        return response.asJsoup().select(selector).map(::hosterFromElement)
    }

    protected open fun hostersSelector(): String? = null
    protected open fun hosterFromElement(element: Element): SHoster = throw UnsupportedOperationException("Hosters are not supported")

    override fun streamsParse(response: Response, hoster: SHoster): List<SStream> {
        val selector = streamsSelector(hoster) ?: return emptyList()
        return response.asJsoup().select(selector).map { streamFromElement(it, hoster) }
    }

    protected open fun streamsSelector(hoster: SHoster): String? = null
    protected open fun streamFromElement(element: Element, hoster: SHoster): SStream = throw UnsupportedOperationException("Streams are not supported")
}
