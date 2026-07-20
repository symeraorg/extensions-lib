package org.symera.source.iptv

import java.net.URI
import java.util.TimeZone

data class IptvConfiguration(
    val id: String,
    val name: String,
    val catalog: IptvCatalogConfiguration,
    val epg: List<IptvEpgLocation> = emptyList(),
    val timeZoneId: String = "UTC",
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(name.isNotBlank()) { "name must not be blank" }
        require(timeZoneId in TimeZone.getAvailableIDs()) { "Unknown IPTV time zone: $timeZoneId" }
    }
}

sealed interface IptvCatalogConfiguration {
    /** One or more Extended M3U documents, each of which can produce many channels. */
    data class Playlists(val locations: List<IptvPlaylistLocation>) : IptvCatalogConfiguration {
        init {
            require(locations.isNotEmpty()) { "At least one playlist is required" }
        }
    }

    /** Fully described channels for extensions that do not expose a playlist document. */
    data class Channels(
        val entries: List<IptvConfiguredChannel>,
        val groups: List<IptvGroup> = emptyList(),
    ) : IptvCatalogConfiguration {
        init {
            require(entries.isNotEmpty()) { "At least one channel is required" }
            require(entries.map { it.channel.id }.distinct().size == entries.size) { "Channel IDs must be unique" }
            require(groups.map(IptvGroup::id).distinct().size == groups.size) { "Group IDs must be unique" }
            val groupIds = groups.mapTo(mutableSetOf(), IptvGroup::id)
            require(entries.flatMap { it.channel.groupIds }.all(groupIds::contains)) {
                "Every configured channel group must have explicit group metadata"
            }
        }
    }

    /**
     * Extension-specific provider adapter. `adapterId` names code implemented by the extension; no endpoint shape is
     * implied because IPTV provider APIs, including so-called Xtream APIs, are not standards.
     */
    data class Provider(
        val adapterId: String,
        val baseUri: URI? = null,
        val parameters: Map<String, String> = emptyMap(),
    ) : IptvCatalogConfiguration {
        init {
            require(adapterId.isNotBlank()) { "adapterId must not be blank" }
            if (baseUri != null) IptvUriPolicy.requireHttpUri(baseUri, "Provider base URI")
            require(parameters.keys.none { it.isBlank() }) { "Parameter keys must not be blank" }
        }

        override fun toString(): String =
            "Provider(adapterId=$adapterId, baseUri=${baseUri?.redactedForLog()}, parameterKeys=${parameters.keys})"
    }
}

data class IptvPlaylistLocation(
    val uri: URI,
    val headers: Map<String, String> = emptyMap(),
    val secretHeaderReferences: Map<String, String> = emptyMap(),
) {
    init {
        IptvUriPolicy.requireHttpUri(uri, "Playlist URI")
        IptvHeaderPolicy.requireValid(headers)
        validateSecretHeaders(secretHeaderReferences)
        IptvHeaderPolicy.requireDisjoint(headers, secretHeaderReferences)
    }

    override fun toString(): String =
        "IptvPlaylistLocation(uri=${uri.redactedForLog()}, headerNames=${headers.keys}, " +
            "secretHeaderNames=${secretHeaderReferences.keys})"
}

data class IptvEpgLocation(
    val uri: URI,
    val headers: Map<String, String> = emptyMap(),
    val secretHeaderReferences: Map<String, String> = emptyMap(),
) {
    init {
        IptvUriPolicy.requireHttpUri(uri, "EPG URI")
        IptvHeaderPolicy.requireValid(headers)
        validateSecretHeaders(secretHeaderReferences)
        IptvHeaderPolicy.requireDisjoint(headers, secretHeaderReferences)
    }

    override fun toString(): String =
        "IptvEpgLocation(uri=${uri.redactedForLog()}, headerNames=${headers.keys}, " +
            "secretHeaderNames=${secretHeaderReferences.keys})"
}

data class IptvConfiguredChannel(
    val channel: IptvChannel,
    val playback: IptvPlaybackRequest,
)

private fun validateSecretHeaders(references: Map<String, String>) {
    IptvHeaderPolicy.requireValid(references.mapValues { "secret" }, "secret")
    require(references.values.none(String::isBlank)) { "Secret credential references cannot be blank" }
}
