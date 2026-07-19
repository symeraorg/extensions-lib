package org.symera.source

sealed interface SymeraCapability

enum class SourceCapability : SymeraCapability {
    PLAYABLE_ITEMS,
    SEASONS,
    ITEM_STREAMS,
    HOSTERS,
    RELATED_CONTENT,
    RELATED_SEARCH,
    DEFERRED_STREAMS,
}

enum class CatalogCapability : SymeraCapability {
    MOVIES,
    SERIES,
    POPULAR,
    LATEST,
    SEARCH,
    HOME_SECTIONS,
}
