package org.symera.source.torrentutils.service

import java.io.InputStream

fun interface TorrentInputLoader {
    suspend fun open(): InputStream
}
