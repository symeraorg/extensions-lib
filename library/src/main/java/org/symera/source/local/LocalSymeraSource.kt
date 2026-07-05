package org.symera.source.local

import com.hippo.unifile.UniFile
import org.symera.source.SymeraSource
import org.symera.source.local.io.LocalSymeraSourceFileSystem

/** Marker contract for sources that read media from Symera's local content directory. */
interface LocalSymeraSource : SymeraSource {
    val fileSystem: LocalSymeraSourceFileSystem
        get() = LocalSymeraSourceFileSystem()

    val supportedFileExtensions: Set<String>
        get() = LocalSymeraSourceFileSystem.DEFAULT_VIDEO_EXTENSIONS

    fun isSupportedLocalFile(file: UniFile): Boolean {
        val extension = file.name
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            .orEmpty()

        return file.isFile && extension in supportedFileExtensions
    }
}
