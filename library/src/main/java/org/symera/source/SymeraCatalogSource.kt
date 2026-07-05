package org.symera.source

import kotlinx.coroutines.CancellationException
import org.symera.source.model.ContentPage
import org.symera.source.model.FilterList
import org.symera.source.model.HomeSection
import org.symera.source.model.SContent

/** Source that can be browsed or searched from Symera's catalog UI. */
interface SymeraCatalogSource : SymeraSource {
    /** Whether this source can parse or request related movies/series from the content page itself. */
    val supportsRelatedContent: Boolean get() = false

    /** Whether Symera should skip the title-search fallback for related movies/series. */
    val disableRelatedContentBySearch: Boolean get() = false

    /** Whether Symera should hide related movies/series for this source entirely. */
    val disableRelatedContent: Boolean get() = false

    suspend fun getMovies(page: Int): ContentPage

    suspend fun getSeries(page: Int): ContentPage

    suspend fun search(page: Int, query: String, filters: FilterList): ContentPage

    fun getFilterList(): FilterList

    suspend fun getHomeSections(): List<HomeSection> = emptyList()

    suspend fun getSectionItems(section: HomeSection, page: Int): ContentPage = ContentPage.Empty

    override suspend fun getRelated(content: SContent): List<SContent> {
        val related = LinkedHashMap<String, SContent>()
        getRelatedContent(
            content = content,
            exceptionHandler = {},
            pushResults = { result, _ ->
                result.second
                    .filterNot { it.isSameContentAs(content) }
                    .forEach { related.putIfAbsent(it.relatedContentKey(), it) }
            },
        )
        return related.values.toList()
    }

    /**
     * Pushes related content in batches. The first element in the pair is the origin keyword or
     * section label; the second is the list of related movies/series found for it.
     */
    suspend fun getRelatedContent(
        content: SContent,
        exceptionHandler: (Throwable) -> Unit,
        pushResults: suspend (relatedContent: Pair<String, List<SContent>>, completed: Boolean) -> Unit,
    ) {
        if (disableRelatedContent) {
            pushResults(RELATED_CONTENT_SECTION to emptyList(), true)
            return
        }

        var emittedExtensionResults = false
        if (supportsRelatedContent) {
            try {
                val related = fetchRelatedContentList(content)
                emittedExtensionResults = true
                pushResults(RELATED_CONTENT_SECTION to related, disableRelatedContentBySearch)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                exceptionHandler(throwable)
            }
        }

        if (!disableRelatedContentBySearch) {
            getRelatedContentBySearch(content, exceptionHandler, pushResults)
        } else if (!emittedExtensionResults) {
            pushResults(RELATED_CONTENT_SECTION to emptyList(), true)
        }
    }

    /** Fetches related content directly from the source/site. */
    suspend fun fetchRelatedContentList(content: SContent): List<SContent> = throw UnsupportedOperationException("Related content is not supported")

    /** Splits a movie/series title into search keywords suitable for related-content fallback. */
    fun String.stripKeywordForRelatedContent(): List<String> {
        val cleaned = this
            .replace(Regex("\\[[^]]*]"), " ")
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("(?i)\\b(s\\d{1,2}|season\\s*\\d+|temporada\\s*\\d+)\\b"), " ")
            .replace(Regex("(?i)\\b(episode|episodio|capitulo|chapter)\\s*\\d+\\b"), " ")
            .replace(Regex("\\b(19|20)\\d{2}\\b"), " ")
            .trim()

        return cleaned
            .split(Regex("[:/|,;\\-]+"))
            .map { it.trim() }
            .filter { it.length >= RELATED_CONTENT_MIN_KEYWORD_LENGTH }
            .distinctBy { it.lowercase() }
            .ifEmpty { listOf(cleaned).filter { it.length >= RELATED_CONTENT_MIN_KEYWORD_LENGTH } }
    }

    /** Uses catalog search with stripped title keywords to find related movies/series. */
    suspend fun getRelatedContentBySearch(
        content: SContent,
        exceptionHandler: (Throwable) -> Unit,
        pushResults: suspend (relatedContent: Pair<String, List<SContent>>, completed: Boolean) -> Unit,
    ) {
        val keywords = buildList {
            addAll(content.title.stripKeywordForRelatedContent())
            content.originalTitle?.let { addAll(it.stripKeywordForRelatedContent()) }
        }.distinctBy { it.lowercase() }

        if (keywords.isEmpty()) {
            pushResults(RELATED_CONTENT_SECTION to emptyList(), true)
            return
        }

        keywords.forEachIndexed { index, keyword ->
            val completed = index == keywords.lastIndex
            try {
                val contents = search(1, keyword, getFilterList())
                    .contents
                    .filterNot { it.isSameContentAs(content) }
                pushResults(keyword to contents, completed)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                exceptionHandler(throwable)
                if (completed) {
                    pushResults(keyword to emptyList(), true)
                }
            }
        }
    }

    companion object {
        const val RELATED_CONTENT_SECTION = "related"
        private const val RELATED_CONTENT_MIN_KEYWORD_LENGTH = 3
    }
}

private fun SContent.relatedContentKey(): String {
    val safeUrl = runCatching { url }.getOrDefault("")
    if (safeUrl.isNotBlank()) return safeUrl

    val safeTitle = runCatching { title }.getOrDefault("")
    return listOfNotNull(safeTitle.takeIf { it.isNotBlank() }, year?.toString()).joinToString("|")
}

private fun SContent.isSameContentAs(other: SContent): Boolean {
    val key = relatedContentKey()
    val otherKey = other.relatedContentKey()
    return key.isNotBlank() && key == otherKey
}
