package org.symera.source.model

data class ExternalId(
    val provider: String,
    val value: String,
) {
    init {
        require(provider.isNotBlank()) { "External ID provider cannot be blank" }
        require(value.isNotBlank()) { "External ID value cannot be blank" }
    }
}

/** A rating whose scale and provider remain explicit instead of being inferred by the host. */
data class ContentRating(
    val value: Double,
    val maximum: Double? = null,
    val voteCount: Long? = null,
    val provider: String? = null,
) {
    init {
        require(value.isFinite()) { "Rating must be finite" }
        require(maximum == null || maximum.isFinite() && maximum > 0) { "Rating maximum must be positive" }
        require(maximum == null || value in 0.0..maximum) { "Rating must be within its declared scale" }
        require(voteCount == null || voteCount >= 0) { "Vote count cannot be negative" }
    }
}

data class ContentCredits(
    val directors: List<String> = emptyList(),
    val writers: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val studios: List<String> = emptyList(),
    val networks: List<String> = emptyList(),
)

data class ContentRelease(
    val date: SourceDate? = null,
    val year: Int? = date?.year,
) {
    init {
        require(year == null || year > 0) { "Release year must be positive" }
        require(date == null || year == null || date.year == year) { "Release date and year disagree" }
    }
}
