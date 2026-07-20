package org.symera.source

import org.symera.source.model.SourcePreference
import org.symera.source.model.validatePreferenceSchema

/** Source that exposes app-rendered preferences without depending on Android UI classes. */
interface ConfigurableSymeraSource : SymeraSource {
    /** Stable extension-local namespace that binds this schema to host-owned storage. */
    val sourcePreferenceNamespace: String
        get() = "source.$id"

    fun getSourcePreferences(): List<SourcePreference<*>> = emptyList()

    fun validatedSourcePreferences(): List<SourcePreference<*>> =
        getSourcePreferences().also {
            SourcePreferenceNamespace.requireValid(sourcePreferenceNamespace)
            it.validatePreferenceSchema()
        }
}

object SourcePreferenceNamespace {
    const val MAXIMUM_LENGTH = 128

    fun requireValid(namespace: String): String {
        require(namespace.length <= MAXIMUM_LENGTH && namespace.matches(PATTERN)) {
            "Source preference namespace is invalid"
        }
        return namespace
    }

    private val PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,${MAXIMUM_LENGTH - 1}}")
}

/** Optional preference action handler, required only when the schema contains an Action control. */
interface ActionableSymeraSource : ConfigurableSymeraSource {
    suspend fun onPreferenceAction(key: String)
}
