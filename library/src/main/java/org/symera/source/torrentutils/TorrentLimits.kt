package org.symera.source.torrentutils

data class TorrentLimits(
    val maxInputBytes: Int = 16 * 1024 * 1024,
    val maxDepth: Int = 64,
    val maxElements: Int = 200_000,
    val maxByteStringLength: Int = 16 * 1024 * 1024,
    val maxTextLength: Int = 16 * 1024,
    val maxFiles: Int = 100_000,
    val maxPathSegments: Int = 256,
    val maxFileSize: Long = Long.MAX_VALUE,
    val maxTotalSize: Long = Long.MAX_VALUE,
    val maxMagnetLength: Int = 32 * 1024,
    val maxMagnetParameters: Int = 1_024,
) {
    init {
        require(maxInputBytes > 0) { "maxInputBytes must be positive" }
        require(maxDepth > 0) { "maxDepth must be positive" }
        require(maxElements > 0) { "maxElements must be positive" }
        require(maxByteStringLength > 0) { "maxByteStringLength must be positive" }
        require(maxByteStringLength <= maxInputBytes) { "maxByteStringLength cannot exceed maxInputBytes" }
        require(maxTextLength > 0) { "maxTextLength must be positive" }
        require(maxFiles > 0) { "maxFiles must be positive" }
        require(maxPathSegments > 0) { "maxPathSegments must be positive" }
        require(maxFileSize >= 0) { "maxFileSize cannot be negative" }
        require(maxTotalSize >= 0) { "maxTotalSize cannot be negative" }
        require(maxMagnetLength > 0) { "maxMagnetLength must be positive" }
        require(maxMagnetParameters > 0) { "maxMagnetParameters must be positive" }
    }
}
