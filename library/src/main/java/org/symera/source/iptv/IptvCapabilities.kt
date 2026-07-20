package org.symera.source.iptv

/** Features a source or an opened session can actually serve. */
enum class IptvCapability {
    GROUPS,
    TV,
    RADIO,
    EPG,
    NOW_NEXT,
    CATCH_UP,
    TIMESHIFT,
    SEARCH,
    REFRESH,
    DYNAMIC_HEADERS,
    LICENSE_EXCHANGE,
}

data class IptvCapabilities(
    val values: Set<IptvCapability>,
) {
    operator fun contains(capability: IptvCapability): Boolean = capability in values

    fun require(capability: IptvCapability) {
        check(capability in values) { "Capability $capability is not available" }
    }

    companion object {
        val NONE = IptvCapabilities(emptySet())
    }
}
