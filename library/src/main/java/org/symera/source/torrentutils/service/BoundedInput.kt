package org.symera.source.torrentutils.service

import java.io.ByteArrayOutputStream
import java.io.InputStream
import org.symera.source.torrentutils.model.DeadTorrentException

internal fun InputStream.readBytesBounded(maxBytes: Int): ByteArray {
    val initialCapacity = minOf(DEFAULT_INITIAL_CAPACITY, maxBytes)
    val output = ByteArrayOutputStream(initialCapacity)
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        if (read == 0) continue
        if (read > maxBytes - total) throw DeadTorrentException("Torrent input exceeds the configured byte limit")
        output.write(buffer, 0, read)
        total += read
    }
    return output.toByteArray()
}

private const val DEFAULT_INITIAL_CAPACITY = 32 * 1024
