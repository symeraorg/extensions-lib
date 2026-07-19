package org.symera.source.local.io

import com.hippo.unifile.UniFile

/** Host-owned local media access. The host retains SAF permissions and chooses the backing store. */
interface LocalSourceFileSystem {
    suspend fun requireBaseDirectory(): UniFile
    suspend fun getFilesInBaseDirectory(): List<UniFile>
    suspend fun getContentDirectories(): List<UniFile>
    suspend fun getContentDirectory(name: String): UniFile?

    suspend fun walk(
        directory: UniFile? = null,
        maximumDepth: Int = LocalMediaDefaults.MAXIMUM_DEPTH,
        maximumEntries: Int = LocalMediaDefaults.MAXIMUM_ENTRIES,
    ): List<UniFile>

    suspend fun getPlayableFiles(
        directory: UniFile? = null,
        supportedExtensions: Set<String> = LocalMediaDefaults.VIDEO_EXTENSIONS,
        maximumDepth: Int = LocalMediaDefaults.MAXIMUM_DEPTH,
        maximumEntries: Int = LocalMediaDefaults.MAXIMUM_ENTRIES,
    ): List<UniFile>

    suspend fun getSidecarSubtitles(video: UniFile): List<UniFile>
}

object LocalMediaDefaults {
    const val MAXIMUM_DEPTH = 8
    const val MAXIMUM_ENTRIES = 100_000

    val VIDEO_EXTENSIONS = setOf(
        "3gp", "avi", "flv", "m2ts", "m4v", "mkv", "mov", "mp4", "mpeg", "mpg",
        "ogm", "ogv", "ts", "vob", "webm", "wmv", "strm",
    )
    val SUBTITLE_EXTENSIONS = setOf("ass", "ssa", "srt", "ttml", "vtt")
}
