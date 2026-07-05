package org.symera.source

import org.symera.source.model.SourcePreference

/** Source that exposes app-rendered preferences without depending on Android UI classes. */
interface ConfigurableSymeraSource : SymeraSource {
    fun getSourcePreferences(): List<SourcePreference<*>> = emptyList()
}
