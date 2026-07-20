package org.symera.source.torrentutils.service

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.symera.source.torrentutils.TorrentLimits
import org.symera.source.torrentutils.model.DeadTorrentException
import org.symera.source.torrentutils.model.TorrentInfo
import org.symera.source.torrentutils.parser.MagnetParser
import org.symera.source.torrentutils.parser.TorrentMetainfoParser

class TorrentService(
    private val limits: TorrentLimits = TorrentLimits(),
) {
    fun parse(bytes: ByteArray, fallbackTitle: String = ""): TorrentInfo =
        TorrentMetainfoParser.parse(bytes, fallbackTitle, limits)

    fun parseMagnet(uri: String, fallbackTitle: String = ""): TorrentInfo {
        val magnet = MagnetParser.parse(uri, limits)
        return TorrentInfo(
            title = magnet.displayName ?: fallbackTitle.takeIf(String::isNotBlank),
            files = emptyList(),
            hashes = magnet.hashes,
            size = null,
            trackers = magnet.trackers,
            webSeeds = magnet.webSeeds,
            selectedFiles = magnet.selectedFiles,
        )
    }

    suspend fun load(loader: TorrentInputLoader, fallbackTitle: String = ""): TorrentInfo {
        val stream = try {
            loader.open()
        } catch (exception: IOException) {
            throw DeadTorrentException("Unable to open torrent input", exception)
        } catch (exception: SecurityException) {
            throw DeadTorrentException("Access to torrent input was denied", exception)
        }
        val bytes = try {
            runInterruptible(Dispatchers.IO) {
                stream.use { input -> input.readBytesBounded(limits.maxInputBytes) }
            }
        } catch (exception: IOException) {
            throw DeadTorrentException("Unable to read torrent input", exception)
        }
        return withContext(Dispatchers.Default) { parse(bytes, fallbackTitle) }
    }

    suspend fun fetch(
        url: String,
        client: okhttp3.OkHttpClient,
        fallbackTitle: String = "",
    ): TorrentInfo = withContext(Dispatchers.Default) {
        parse(OkHttpTorrentFetcher(client).fetch(url, limits), fallbackTitle)
    }
}
