package org.symera.source.online

import org.symera.source.SymeraSource
import org.symera.source.model.SContent
import org.symera.source.model.SPlayableItem

/**
 * A source that may handle opening Symera content or playable items for a given URI.
 */
interface ResolvableSymeraSource : SymeraSource {
    /**
     * Returns what the given URI may open.
     *
     * Return [UriType.Unknown] if the source is not able to resolve the URI.
     */
    fun getUriType(uri: String): UriType

    /**
     * Called when [getUriType] returns [UriType.Content].
     *
     * Returns the corresponding [SContent], if possible.
     */
    suspend fun getContent(uri: String): SContent?

    /**
     * Called when [getUriType] returns [UriType.PlayableItem].
     *
     * Returns the corresponding [SPlayableItem], if possible.
     */
    suspend fun getPlayableItem(uri: String): SPlayableItem?
}

sealed interface UriType {
    data object Content : UriType
    data object PlayableItem : UriType
    data object Unknown : UriType
}
