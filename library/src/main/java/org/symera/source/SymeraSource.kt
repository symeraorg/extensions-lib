package org.symera.source

import org.symera.source.model.ContentType
import org.symera.source.model.DeferredStream
import org.symera.source.model.PlayableStream
import org.symera.source.model.SContent
import org.symera.source.model.SHoster
import org.symera.source.model.SPlayableItem
import org.symera.source.model.SSeason
import org.symera.source.model.SStream

/** Base VOD contract implemented by online, local, or provider-backed sources. */
interface SymeraSource {
    /** Unique and stable source ID. */
    val id: Long

    val name: String

    /** BCP 47 language tag, or `und` when the source itself does not define one. */
    val lang: String

    val contentTypes: Set<ContentType>

    val sourceCapabilities: Set<SourceCapability>
        get() = emptySet()

    suspend fun getDetails(content: SContent): SContent

    /** Used by movies, flat series, and anime pages that do not expose an explicit season. */
    suspend fun getPlayableItems(content: SContent): List<SPlayableItem> =
        unsupported(SourceCapability.PLAYABLE_ITEMS)

    /** Returns only seasons explicitly exposed by the source. */
    suspend fun getSeasons(content: SContent): List<SSeason> =
        unsupported(SourceCapability.SEASONS)

    /** Used after the user selects a season in a multi-season source. */
    suspend fun getPlayableItems(season: SSeason): List<SPlayableItem> =
        season.playableItems
            ?: unsupported(SourceCapability.SEASONS)

    /** Direct playback path for providers that do not expose a meaningful hoster stage. */
    suspend fun getStreams(item: SPlayableItem): List<SStream> =
        unsupported(SourceCapability.ITEM_STREAMS)

    suspend fun getHosters(item: SPlayableItem): List<SHoster> =
        unsupported(SourceCapability.HOSTERS)

    suspend fun getStreams(hoster: SHoster): List<SStream> =
        hoster.streams
            ?: unsupported(SourceCapability.HOSTERS)

    suspend fun getRelated(content: SContent): List<SContent> = emptyList()

    /** Allows a source to normalize an item immediately before the host persists it. */
    fun prepareNewPlayableItem(item: SPlayableItem, content: SContent): SPlayableItem = item

    private fun unsupported(capability: SourceCapability): Nothing =
        throw SourceException.UnsupportedCapability(capability)
}

/** Optional capability for source-specific, delayed stream extraction. */
interface StreamResolver {
    suspend fun resolveStream(stream: DeferredStream): PlayableStream?
}
