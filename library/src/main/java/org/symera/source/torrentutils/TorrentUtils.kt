package org.symera.source.torrentutils

import okhttp3.OkHttpClient
import okhttp3.Request
import org.symera.source.torrentutils.model.TorrentInfo
import org.symera.source.torrentutils.parser.TorrentMetainfoParser
import org.symera.source.torrentutils.service.OkHttpTorrentFetcher
import org.symera.source.torrentutils.service.TorrentInputLoader
import org.symera.source.torrentutils.service.TorrentService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TorrentUtils {
    fun parseTorrent(
        bytes: ByteArray,
        fallbackTitle: String = "",
        limits: TorrentLimits = TorrentLimits(),
    ): TorrentInfo = TorrentMetainfoParser.parse(bytes, fallbackTitle, limits)

    fun parseMagnet(
        uri: String,
        fallbackTitle: String = "",
        limits: TorrentLimits = TorrentLimits(),
    ): TorrentInfo = TorrentService(limits).parseMagnet(uri, fallbackTitle)

    suspend fun fetchTorrent(
        url: String,
        client: OkHttpClient,
        fallbackTitle: String = "",
        limits: TorrentLimits = TorrentLimits(),
    ): TorrentInfo {
        val bytes = OkHttpTorrentFetcher(client).fetch(url, limits)
        return withContext(Dispatchers.Default) {
            TorrentMetainfoParser.parse(bytes, fallbackTitle, limits)
        }
    }

    suspend fun fetchTorrent(
        request: Request,
        client: OkHttpClient,
        fallbackTitle: String = "",
        limits: TorrentLimits = TorrentLimits(),
    ): TorrentInfo {
        val bytes = OkHttpTorrentFetcher(client).fetch(request, limits)
        return withContext(Dispatchers.Default) {
            TorrentMetainfoParser.parse(bytes, fallbackTitle, limits)
        }
    }

    suspend fun loadTorrent(
        loader: TorrentInputLoader,
        fallbackTitle: String = "",
        limits: TorrentLimits = TorrentLimits(),
    ): TorrentInfo = TorrentService(limits).load(loader, fallbackTitle)

    suspend fun getTorrentInfo(
        url: String,
        title: String,
        client: OkHttpClient,
        limits: TorrentLimits = TorrentLimits(),
    ): TorrentInfo {
        val source = url.trim()
        return if (source.startsWith("magnet:", ignoreCase = true)) {
            parseMagnet(source, title, limits)
        } else {
            fetchTorrent(source, client, title, limits)
        }
    }
}
