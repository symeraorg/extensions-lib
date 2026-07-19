package org.symera.source

import org.symera.source.iptv.IptvSource

/**
 * Single host-discovered extension entry point capable of exposing VOD, IPTV, or both.
 * Implementations must be a Kotlin object or expose a public no-argument constructor.
 */
interface SymeraExtensionFactory {
    fun createVodSources(environment: SourceEnvironment): List<SymeraSource> = emptyList()
    fun createIptvSources(environment: SourceEnvironment): List<IptvSource> = emptyList()
}
