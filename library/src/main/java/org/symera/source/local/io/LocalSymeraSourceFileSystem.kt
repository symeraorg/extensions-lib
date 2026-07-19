package org.symera.source.local.io

import com.hippo.unifile.UniFile
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Storage facade injected by the host, which remains responsible for SAF permissions. */
class LocalSymeraSourceFileSystem(
    private val baseDirectoryProvider: () -> UniFile?,
) : LocalSourceFileSystem {
    override suspend fun requireBaseDirectory(): UniFile = withContext(Dispatchers.IO) { requireBaseDirectoryBlocking() }

    override suspend fun getFilesInBaseDirectory(): List<UniFile> = withContext(Dispatchers.IO) {
        requireBaseDirectoryBlocking().listFilesChecked()
    }

    override suspend fun getContentDirectories(): List<UniFile> = withContext(Dispatchers.IO) {
        contentDirectoriesBlocking()
    }

    override suspend fun getContentDirectory(name: String): UniFile? = withContext(Dispatchers.IO) {
        contentDirectoriesBlocking().firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    override suspend fun walk(
        directory: UniFile?,
        maximumDepth: Int,
        maximumEntries: Int,
    ): List<UniFile> = withContext(Dispatchers.IO) {
        val root = directory ?: requireBaseDirectoryBlocking()
        validateWalk(root, maximumDepth, maximumEntries)
        walkBlocking(root, depth = 0, maximumDepth, maximumEntries, intArrayOf(0)).toList()
    }

    override suspend fun getPlayableFiles(
        directory: UniFile?,
        supportedExtensions: Set<String>,
        maximumDepth: Int,
        maximumEntries: Int,
    ): List<UniFile> = withContext(Dispatchers.IO) {
        val root = directory ?: requireBaseDirectoryBlocking()
        validateWalk(root, maximumDepth, maximumEntries)
        val extensions = supportedExtensions.mapTo(mutableSetOf()) { it.lowercase(Locale.ROOT) }
        walkBlocking(root, 0, maximumDepth, maximumEntries, intArrayOf(0))
            .filter { file -> file.isFile && file.extension() in extensions }
            .sortedBy { it.name.orEmpty().lowercase(Locale.ROOT) }
            .toList()
    }

    override suspend fun getSidecarSubtitles(video: UniFile): List<UniFile> = withContext(Dispatchers.IO) {
        val parent = video.parentFile ?: return@withContext emptyList()
        val stem = video.name?.substringBeforeLast('.') ?: return@withContext emptyList()
        parent.listFilesChecked()
            .filter { file ->
                val candidateStem = file.name.orEmpty().substringBeforeLast('.')
                file.isFile &&
                    file.extension() in LocalMediaDefaults.SUBTITLE_EXTENSIONS &&
                    (candidateStem.equals(stem, ignoreCase = true) ||
                        candidateStem.startsWith("$stem.", ignoreCase = true))
            }
            .sortedBy { it.name.orEmpty().lowercase(Locale.ROOT) }
    }

    private fun requireBaseDirectoryBlocking(): UniFile =
        requireNotNull(baseDirectoryProvider()) { "The host did not provide a local media directory" }
            .also { require(it.isDirectory) { "The local media root is not a directory" } }

    private fun contentDirectoriesBlocking(): List<UniFile> = requireBaseDirectoryBlocking()
        .listFilesChecked()
        .filter(UniFile::isDirectory)
        .sortedBy { it.name.orEmpty().lowercase(Locale.ROOT) }

    private fun validateWalk(directory: UniFile, maximumDepth: Int, maximumEntries: Int) {
        require(maximumDepth >= 0) { "Maximum depth cannot be negative" }
        require(maximumEntries > 0) { "Maximum entries must be positive" }
        require(directory.isDirectory) { "Walk root must be a directory" }
    }

    private fun walkBlocking(
        directory: UniFile,
        depth: Int,
        maximumDepth: Int,
        maximumEntries: Int,
        count: IntArray,
    ): Sequence<UniFile> = sequence {
        directory.listFilesChecked().forEach { child ->
            require(count[0] < maximumEntries) { "Local media entry limit exceeded" }
            count[0]++
            yield(child)
            if (child.isDirectory && depth < maximumDepth) {
                yieldAll(walkBlocking(child, depth + 1, maximumDepth, maximumEntries, count))
            }
        }
    }

}

private fun UniFile.extension(): String = name
    ?.substringAfterLast('.', missingDelimiterValue = "")
    ?.lowercase(Locale.ROOT)
    .orEmpty()

private fun UniFile.listFilesChecked(): List<UniFile> {
    require(isDirectory) { "Cannot list a non-directory UniFile" }
    return listFiles()?.toList().orEmpty()
}
