package org.symera.source.iptv

import java.net.URI

enum class IptvChannelKind {
    TV,
    RADIO,
}

data class IptvGroup(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val logo: URI? = null,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(name.isNotBlank()) { "name must not be blank" }
        require(parentId != id) { "A group cannot be its own parent" }
        if (logo != null) IptvUriPolicy.requireHttpUri(logo, "Group logo URI")
    }

    override fun toString(): String =
        "IptvGroup(id=$id, name=$name, parentId=$parentId, logo=${logo?.redactedForLog()})"
}

data class IptvChannel(
    val id: String,
    val name: String,
    val kind: IptvChannelKind = IptvChannelKind.TV,
    val groupIds: List<String> = emptyList(),
    val logo: URI? = null,
    val channelNumber: String? = null,
    val epgId: String? = null,
    val language: String? = null,
    val country: String? = null,
    val catchUp: IptvCatchUp? = null,
    val timeshift: IptvTimeshift? = null,
    val attributes: Map<String, String> = emptyMap(),
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(name.isNotBlank()) { "name must not be blank" }
        require(groupIds.none { it.isBlank() }) { "groupIds must not contain blanks" }
        require(groupIds.distinct().size == groupIds.size) { "groupIds must be unique" }
        require(channelNumber == null || channelNumber.isNotBlank()) { "channelNumber must be null or non-blank" }
        if (logo != null) IptvUriPolicy.requireHttpUri(logo, "Channel logo URI")
    }

    override fun toString(): String =
        "IptvChannel(id=$id, name=$name, kind=$kind, groupIds=$groupIds, logo=${logo?.redactedForLog()}, " +
            "channelNumber=$channelNumber, epgId=$epgId, language=$language, country=$country, " +
            "catchUp=$catchUp, timeshift=$timeshift, attributeKeys=${attributes.keys})"
}

data class IptvChannelQuery(
    val groupId: String? = null,
    val kind: IptvChannelKind? = null,
    val search: String? = null,
) {
    init {
        require(groupId == null || groupId.isNotBlank()) { "groupId must be null or non-blank" }
        require(search == null || search.isNotBlank()) { "search must be null or non-blank" }
    }
}
