package org.symera.source

interface SourcePreferenceProvider {
    fun getString(sourceId: Long, key: String, default: String): String = default
    fun getBoolean(sourceId: Long, key: String, default: Boolean): Boolean = default
    fun getStringSet(sourceId: Long, key: String, default: Set<String>): Set<String> = default
}

object SourcePreferenceStore {
    @Volatile
    var provider: SourcePreferenceProvider = object : SourcePreferenceProvider {}

    fun forSource(source: SymeraSource): SourcePreferenceValues = SourcePreferenceValues(source.id)
}

class SourcePreferenceValues internal constructor(
    private val sourceId: Long,
) {
    fun getString(key: String, default: String): String = SourcePreferenceStore.provider.getString(sourceId, key, default)

    fun getBoolean(key: String, default: Boolean): Boolean = SourcePreferenceStore.provider.getBoolean(sourceId, key, default)

    fun getStringSet(key: String, default: Set<String>): Set<String> = SourcePreferenceStore.provider.getStringSet(sourceId, key, default)
}

fun SymeraSource.preferenceValues(): SourcePreferenceValues = SourcePreferenceStore.forSource(this)
