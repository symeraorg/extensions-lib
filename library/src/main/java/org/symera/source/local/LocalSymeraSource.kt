package org.symera.source.local

import com.hippo.unifile.UniFile
import java.util.Locale
import org.symera.source.SymeraSource
import org.symera.source.local.io.LocalMediaDefaults
import org.symera.source.local.io.LocalSourceFileSystem

/** Marker contract for sources that read media from Symera's local content directory. */
interface LocalSymeraSource : SymeraSource {
    val fileSystem: LocalSourceFileSystem

    val supportedFileExtensions: Set<String>
        get() = LocalMediaDefaults.VIDEO_EXTENSIONS

    fun isSupportedLocalFile(file: UniFile): Boolean {
        val extension = file.name
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase(Locale.ROOT)
            .orEmpty()

        return file.isFile && extension in supportedFileExtensions.map { it.lowercase(Locale.ROOT) }
    }
}
