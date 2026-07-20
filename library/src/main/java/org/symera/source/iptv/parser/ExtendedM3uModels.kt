package org.symera.source.iptv.parser

import org.symera.source.iptv.IptvChannel
import org.symera.source.iptv.IptvGroup
import org.symera.source.iptv.IptvPlaybackRequest

data class IptvPlaylistEntry(
    val channel: IptvChannel,
    val playback: IptvPlaybackRequest,
    val durationSeconds: Long? = null,
    val vlcOptions: Map<String, String> = emptyMap(),
)

data class IptvPlaylist(
    val entries: List<IptvPlaylistEntry>,
    val groups: List<IptvGroup>,
    val attributes: Map<String, String> = emptyMap(),
)
