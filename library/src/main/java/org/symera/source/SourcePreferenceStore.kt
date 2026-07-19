package org.symera.source

import kotlinx.coroutines.flow.Flow

/** Host-owned storage already scoped to one extension-defined namespace. */
interface SourcePreferenceStore {
    fun getString(key: String, default: String): String
    fun getSecret(key: String, default: String): String
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getLong(key: String, default: Long): Long
    fun getStringSet(key: String, default: Set<String>): Set<String>

    suspend fun putString(key: String, value: String)
    suspend fun putSecret(key: String, value: String)
    suspend fun putBoolean(key: String, value: Boolean)
    suspend fun putLong(key: String, value: Long)
    suspend fun putStringSet(key: String, value: Set<String>)
    suspend fun remove(key: String)

    fun observeChanges(): Flow<String>
}

class SourcePreferenceValues(
    private val store: SourcePreferenceStore,
) {
    fun getString(key: String, default: String = ""): String = store.getString(key, default)
    fun getSecret(key: String, default: String = ""): String = store.getSecret(key, default)
    fun getBoolean(key: String, default: Boolean = false): Boolean = store.getBoolean(key, default)
    fun getLong(key: String, default: Long = 0): Long = store.getLong(key, default)
    fun getStringSet(key: String, default: Set<String> = emptySet()): Set<String> = store.getStringSet(key, default)

    suspend fun putString(key: String, value: String) = store.putString(key, value)
    suspend fun putSecret(key: String, value: String) = store.putSecret(key, value)
    suspend fun putBoolean(key: String, value: Boolean) = store.putBoolean(key, value)
    suspend fun putLong(key: String, value: Long) = store.putLong(key, value)
    suspend fun putStringSet(key: String, value: Set<String>) = store.putStringSet(key, value)
    suspend fun remove(key: String) = store.remove(key)
    fun observeChanges(): Flow<String> = store.observeChanges()
}
