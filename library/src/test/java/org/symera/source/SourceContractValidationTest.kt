package org.symera.source

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.symera.source.model.ContentType
import org.symera.source.model.Filter
import org.symera.source.model.FilterList
import org.symera.source.model.SContent

class SourceContractValidationTest {
    @Test
    fun configurableSourcesExposeStableValidatedNamespaces() {
        val source = ConfigurableSource(42, "provider.account")

        source.validatedSourcePreferences()

        assertEquals("provider.account", source.sourcePreferenceNamespace)
        assertEquals("source.7", ConfigurableSource(7).sourcePreferenceNamespace)
        assertThrows(IllegalArgumentException::class.java) {
            ConfigurableSource(1, "invalid namespace").validatedSourcePreferences()
        }
    }

    @Test
    fun extensionRejectsDuplicatePreferenceNamespaces() {
        val factory = object : SymeraExtensionFactory {
            override fun createVodSources(environment: SourceEnvironment): List<SymeraSource> =
                listOf(
                    ConfigurableSource(1, "shared"),
                    ConfigurableSource(2, "shared"),
                )
        }

        assertThrows(IllegalArgumentException::class.java) { factory.loadVodSources(UNUSED_ENVIRONMENT) }
    }

    @Test
    fun extensionRejectsInvalidCatalogFilterState() {
        val filter = ExpiringFilter()
        val source = CatalogSource(FilterList(filter))
        filter.expired = true
        val factory = object : SymeraExtensionFactory {
            override fun createVodSources(environment: SourceEnvironment): List<SymeraSource> = listOf(source)
        }

        assertThrows(IllegalArgumentException::class.java) { factory.loadVodSources(UNUSED_ENVIRONMENT) }
    }

    private class ConfigurableSource(
        override val id: Long,
        override val sourcePreferenceNamespace: String = "source.$id",
    ) : ConfigurableSymeraSource {
        override val name = "Configurable $id"
        override val lang = "en"
        override val contentTypes = setOf(ContentType.MOVIE)
        override suspend fun getDetails(content: SContent): SContent = content
    }

    private class CatalogSource(
        private val filters: FilterList,
    ) : SymeraCatalogSource {
        override val id = 3L
        override val name = "Catalog"
        override val lang = "en"
        override val contentTypes = setOf(ContentType.MOVIE)
        override val catalogCapabilities = setOf(CatalogCapability.MOVIES)
        override fun getFilterList(feed: CatalogFeed): FilterList = filters
        override suspend fun getDetails(content: SContent): SContent = content
    }

    private class ExpiringFilter : Filter<Int>("Expiring", 0) {
        var expired = false

        override fun validateState(value: Int) {
            require(!expired) { "Filter expired" }
        }
    }

    private companion object {
        val UNUSED_ENVIRONMENT = object : SourceEnvironment {
            override val httpClient = OkHttpClient()
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
