package org.symera.source.model

data class PageRequest(
    val page: Int = 1,
    val cursor: String? = null,
    val pageSize: Int? = null,
) {
    init {
        require(page > 0) { "Page number starts at one" }
        require(pageSize == null || pageSize > 0) { "Page size must be positive" }
    }
}

data class ContentPage(
    val contents: List<SContent>,
    val hasNextPage: Boolean,
    val nextCursor: String? = null,
    val totalCount: Long? = null,
) {
    init {
        require(totalCount == null || totalCount >= 0) { "Total count cannot be negative" }
    }

    companion object {
        val Empty = ContentPage(emptyList(), false)
    }
}

data class HomeSection(
    val id: String,
    val title: String,
    val contentTypes: Set<ContentType> = emptySet(),
) {
    init {
        require(id.isNotBlank()) { "Home section ID cannot be blank" }
        require(title.isNotBlank()) { "Home section title cannot be blank" }
    }
}
