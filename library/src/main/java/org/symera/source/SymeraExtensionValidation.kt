package org.symera.source

import org.symera.source.browser.InteractiveBrowserSource
import org.symera.source.iptv.IptvSource

/** Host registration path that validates extension-owned declarations before exposing them to UI. */
fun SymeraExtensionFactory.loadVodSources(environment: SourceEnvironment): List<SymeraSource> =
    createVodSources(environment).also { sources ->
        require(sources.map(SymeraSource::id).distinct().size == sources.size) { "VOD source IDs must be unique" }
        sources.forEach(SymeraSource::validateContract)
        val preferenceNamespaces = sources
            .filterIsInstance<ConfigurableSymeraSource>()
            .map(ConfigurableSymeraSource::sourcePreferenceNamespace)
        require(preferenceNamespaces.distinct().size == preferenceNamespaces.size) {
            "VOD source preference namespaces must be unique"
        }
    }

/** Host registration path equivalent for independently configured IPTV providers. */
fun SymeraExtensionFactory.loadIptvSources(environment: SourceEnvironment): List<IptvSource> =
    createIptvSources(environment).also { sources ->
        require(sources.map(IptvSource::id).distinct().size == sources.size) { "IPTV source IDs must be unique" }
        require(sources.none { it.name.isBlank() }) { "IPTV source names must not be blank" }
    }

private fun SymeraSource.validateContract() {
    require(name.isNotBlank()) { "VOD source names must not be blank" }
    require(lang.isNotBlank()) { "VOD source language must not be blank" }
    require(SourceCapability.DEFERRED_STREAMS !in sourceCapabilities || this is StreamResolver) {
        "A source advertising DEFERRED_STREAMS must implement StreamResolver"
    }
    if (SourceCapability.RELATED_SEARCH in sourceCapabilities) {
        require(this is SymeraCatalogSource && CatalogCapability.SEARCH in catalogCapabilities) {
            "RELATED_SEARCH requires a searchable catalog source"
        }
    }
    if (this is SymeraCatalogSource) {
        STANDARD_CATALOG_FEEDS.forEach { (feed, capability) ->
            if (capability in catalogCapabilities) getFilterList(feed).requireValid()
        }
    }
    if (this is ConfigurableSymeraSource) {
        val preferences = validatedSourcePreferences()
        require(preferences.none { it is org.symera.source.model.SourcePreference.Action } || this is ActionableSymeraSource) {
            "A source exposing preference actions must implement ActionableSymeraSource"
        }
    }
    if (this is InteractiveBrowserSource) interactiveBrowserRequest()
}

private val STANDARD_CATALOG_FEEDS = mapOf(
    CatalogFeed.MOVIES to CatalogCapability.MOVIES,
    CatalogFeed.SERIES to CatalogCapability.SERIES,
    CatalogFeed.POPULAR to CatalogCapability.POPULAR,
    CatalogFeed.LATEST to CatalogCapability.LATEST,
    CatalogFeed.SEARCH to CatalogCapability.SEARCH,
)
