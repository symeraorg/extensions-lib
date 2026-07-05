package org.symera.source.online

import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.symera.source.SymeraCatalogSource
import org.symera.source.model.ContentPage
import org.symera.source.model.FilterList
import org.symera.source.model.SContent
import org.symera.source.model.SHoster
import org.symera.source.model.SPlayableItem
import org.symera.source.model.SSeason
import org.symera.source.model.SStream

/** Base class for sources backed by an HTTP website or API. */
abstract class SymeraHttpSource : SymeraCatalogSource {
    abstract val baseUrl: String

    open val versionId: Int = 1

    override val id: Long by lazy { generateId(name, lang, versionId) }

    open val client: OkHttpClient
        get() = defaultClientProvider()

    open val cloudflareClient: OkHttpClient
        get() = defaultCloudflareClientProvider()

    val headers: Headers by lazy { headersBuilder().build() }

    protected open fun headersBuilder(): Headers.Builder = Headers.Builder().add("User-Agent", defaultUserAgentProvider())

    override suspend fun getMovies(page: Int): ContentPage {
        return client.awaitSuccess(moviesRequest(page)).use(::moviesParse)
    }

    protected abstract fun moviesRequest(page: Int): Request
    protected abstract fun moviesParse(response: Response): ContentPage

    override suspend fun getSeries(page: Int): ContentPage {
        return client.awaitSuccess(seriesRequest(page)).use(::seriesParse)
    }

    protected abstract fun seriesRequest(page: Int): Request
    protected abstract fun seriesParse(response: Response): ContentPage

    override suspend fun search(page: Int, query: String, filters: FilterList): ContentPage {
        return client.awaitSuccess(searchRequest(page, query, filters)).use(::searchParse)
    }

    protected abstract fun searchRequest(page: Int, query: String, filters: FilterList): Request
    protected abstract fun searchParse(response: Response): ContentPage

    override suspend fun getDetails(content: SContent): SContent {
        return client.awaitSuccess(contentDetailsRequest(content)).use { response ->
            contentDetailsParse(response).apply { initialized = true }
        }
    }

    protected open fun contentDetailsRequest(content: SContent): Request = GET(getContentUrl(content), headers)
    protected abstract fun contentDetailsParse(response: Response): SContent

    override suspend fun getPlayableItems(content: SContent): List<SPlayableItem> {
        return client.awaitSuccess(playableItemsRequest(content)).use(::playableItemsParse)
    }

    protected open fun playableItemsRequest(content: SContent): Request = GET(getContentUrl(content), headers)
    protected abstract fun playableItemsParse(response: Response): List<SPlayableItem>

    override suspend fun getSeasons(content: SContent): List<SSeason> {
        return client.awaitSuccess(seasonsRequest(content)).use(::seasonsParse)
    }

    protected open fun seasonsRequest(content: SContent): Request = GET(getContentUrl(content), headers)
    protected open fun seasonsParse(response: Response): List<SSeason> = emptyList()

    override suspend fun getHosters(item: SPlayableItem): List<SHoster> {
        return client.awaitSuccess(hostersRequest(item)).use { response -> hostersParse(response).sortHosters() }
    }

    protected open fun hostersRequest(item: SPlayableItem): Request = GET(getPlayableItemUrl(item), headers)
    protected abstract fun hostersParse(response: Response): List<SHoster>

    override suspend fun getStreams(hoster: SHoster): List<SStream> {
        val streams = hoster.streamList ?: client.awaitSuccess(streamsRequest(hoster)).use { response -> streamsParse(response, hoster) }
        return streams.sortStreams()
    }

    protected open fun streamsRequest(hoster: SHoster): Request = GET(hoster.hosterUrl, headers)
    protected abstract fun streamsParse(response: Response, hoster: SHoster): List<SStream>

    open suspend fun resolveStream(stream: SStream): SStream? = stream

    open fun getContentUrl(content: SContent): String = absoluteUrl(content.url)

    open fun getPlayableItemUrl(item: SPlayableItem): String = absoluteUrl(item.url)

    open fun List<SHoster>.sortHosters(): List<SHoster> = this

    open fun List<SStream>.sortStreams(): List<SStream> = this

    fun SContent.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    fun SPlayableItem.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    override fun getFilterList(): FilterList = FilterList()

    override fun toString(): String = "$name (${lang.uppercase()})"

    protected fun generateId(name: String, lang: String, versionId: Int): Long {
        val key = "${name.lowercase()}/$lang/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7)
            .map { (bytes[it].toLong() and 0xff) shl (8 * (7 - it)) }
            .reduce(Long::or) and Long.MAX_VALUE
    }

    private fun absoluteUrl(url: String): String {
        return if (url.startsWith("http://") || url.startsWith("https://")) url else baseUrl.trimEnd('/') + "/" + url.trimStart('/')
    }

    private fun getUrlWithoutDomain(original: String): String {
        return try {
            val uri = URI(original)
            buildString {
                append(uri.path)
                uri.query?.let { append('?').append(it) }
                uri.fragment?.let { append('#').append(it) }
            }
        } catch (_: URISyntaxException) {
            original
        }
    }

    companion object {
        const val DEFAULT_USER_AGENT = "Symera/1.0"

        private val fallbackClient: OkHttpClient = OkHttpClient.Builder().build()

        @Volatile
        var defaultClientProvider: () -> OkHttpClient = { fallbackClient }

        @Volatile
        var defaultCloudflareClientProvider: () -> OkHttpClient = { defaultClientProvider() }

        @Volatile
        var defaultUserAgentProvider: () -> String = { DEFAULT_USER_AGENT }
    }
}
