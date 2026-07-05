package org.symera.source.local.io

import com.hippo.unifile.UniFile

/**
 * File-system facade for local Symera sources.
 *
 * The host app owns storage permissions and sets [defaultBaseDirectoryProvider]. Extensions can
 * then browse the exposed directory without knowing where or how Symera stores local content.
 */
class LocalSymeraSourceFileSystem(
    private val baseDirectoryProvider: () -> UniFile? = defaultBaseDirectoryProvider,
) {
    fun getBaseDirectory(): UniFile? = baseDirectoryProvider()

    fun getFilesInBaseDirectory(): List<UniFile> = getBaseDirectory().listFilesSafely()

    fun getContentDirectories(): List<UniFile> {
        return getFilesInBaseDirectory()
            .filter { it.isDirectory }
            .sortedBy { it.name.orEmpty().lowercase() }
    }

    fun getContentDirectory(title: String): UniFile? {
        return getContentDirectories().firstOrNull { it.name.equals(title, ignoreCase = true) }
    }

    fun getFilesInContentDirectory(title: String): List<UniFile> {
        return getContentDirectory(title).listFilesSafely()
    }

    fun getPlayableFilesInContentDirectory(
        title: String,
        supportedExtensions: Set<String> = DEFAULT_VIDEO_EXTENSIONS,
    ): List<UniFile> {
        return getFilesInContentDirectory(title)
            .filter { file ->
                val extension = file.name
                    ?.substringAfterLast('.', missingDelimiterValue = "")
                    ?.lowercase()
                    .orEmpty()

                file.isFile && extension in supportedExtensions
            }
            .sortedBy { it.name.orEmpty().lowercase() }
    }

    companion object {
        val DEFAULT_VIDEO_EXTENSIONS = setOf(
            "3gp",
            "avi",
            "flv",
            "m4v",
            "mkv",
            "mov",
            "mp4",
            "mpeg",
            "mpg",
            "ogm",
            "ogv",
            "ts",
            "webm",
            "wmv",
        )

        @Volatile
        var defaultBaseDirectoryProvider: () -> UniFile? = { null }
    }
}

private fun UniFile?.listFilesSafely(): List<UniFile> {
    return this
        ?.takeIf { it.isDirectory }
        ?.listFiles()
        ?.toList()
        .orEmpty()
}
