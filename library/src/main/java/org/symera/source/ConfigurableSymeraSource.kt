package org.symera.source

import org.symera.source.model.SourcePreference
import org.symera.source.model.validatePreferenceSchema

/** Source that exposes app-rendered preferences without depending on Android UI classes. */
interface ConfigurableSymeraSource : SymeraSource {
    fun getSourcePreferences(): List<SourcePreference<*>> = emptyList()

    fun validatedSourcePreferences(): List<SourcePreference<*>> =
        getSourcePreferences().also(List<SourcePreference<*>>::validatePreferenceSchema)
}

/** Optional preference action handler, required only when the schema contains an Action control. */
interface ActionableSymeraSource : ConfigurableSymeraSource {
    suspend fun onPreferenceAction(key: String)
}
