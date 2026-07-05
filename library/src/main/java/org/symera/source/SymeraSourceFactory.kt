package org.symera.source

/** Factory used by extension APKs that expose more than one source. */
interface SymeraSourceFactory {
    fun createSources(): List<SymeraSource>
}
