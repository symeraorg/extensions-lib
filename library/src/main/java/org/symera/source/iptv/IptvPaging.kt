package org.symera.source.iptv

data class IptvPageRequest(
    val cursor: String? = null,
    val limit: Int = 100,
) {
    init {
        require(limit in 1..MAX_PAGE_SIZE) { "IPTV page limit must be between 1 and $MAX_PAGE_SIZE" }
    }

    companion object {
        const val MAX_PAGE_SIZE = 1_000
    }
}

data class IptvPage<T>(
    val items: List<T>,
    val nextCursor: String? = null,
    val totalCount: Long? = null,
) {
    init {
        require(totalCount == null || totalCount >= 0) { "Total count cannot be negative" }
    }
}
