package org.symera.source

import org.symera.source.model.ContentType
import org.symera.source.model.SContent
import org.symera.source.model.SHoster
import org.symera.source.model.SPlayableItem
import org.symera.source.model.SSeason
import org.symera.source.model.SStream

/**
 * Base contract implemented by every Symera source, regardless of how it obtains content.
 */
interface SymeraSource {
    /** Unique, stable ID for the source. */
    val id: Long

    /** Display name shown to users. */
    val name: String

    /** ISO 639 language code, or an app-defined value such as "all" when mixed. */
    val lang: String

    /** Content families that this source can usually return. */
    val contentTypes: Set<ContentType>

    suspend fun getDetails(content: SContent): SContent

    suspend fun getPlayableItems(content: SContent): List<SPlayableItem>

    suspend fun getHosters(item: SPlayableItem): List<SHoster>

    suspend fun getStreams(hoster: SHoster): List<SStream>

    suspend fun getSeasons(content: SContent): List<SSeason> = emptyList()

    suspend fun getRelated(content: SContent): List<SContent> = emptyList()
}
