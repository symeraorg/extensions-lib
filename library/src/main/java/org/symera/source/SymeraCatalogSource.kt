package org.symera.source

import org.symera.source.model.ContentPage
import org.symera.source.model.FilterList
import org.symera.source.model.HomeSection

/** Source that can be browsed or searched from Symera's catalog UI. */
interface SymeraCatalogSource : SymeraSource {
    suspend fun getMovies(page: Int): ContentPage

    suspend fun getSeries(page: Int): ContentPage

    suspend fun search(page: Int, query: String, filters: FilterList): ContentPage

    fun getFilterList(): FilterList

    suspend fun getHomeSections(): List<HomeSection> = emptyList()

    suspend fun getSectionItems(section: HomeSection, page: Int): ContentPage = ContentPage.Empty
}
