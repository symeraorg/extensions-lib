package org.symera.source.iptv

/**
 * Infers missing stops from the next programme on the same channel. A final open-ended programme is only closed when
 * [fallbackDurationMillis] is supplied.
 */
fun inferProgrammeStops(
    programmes: List<IptvProgramme>,
    fallbackDurationMillis: Long? = null,
): List<IptvProgramme> {
    require(fallbackDurationMillis == null || fallbackDurationMillis > 0) {
        "fallbackDurationMillis must be positive"
    }

    return programmes
        .groupBy { it.channelId }
        .values
        .flatMap { channelProgrammes ->
            val sorted = channelProgrammes.sortedBy { it.start.epochMillis }
            sorted.mapIndexed { index, programme ->
                if (programme.stop != null) return@mapIndexed programme

                val nextStart = sorted.getOrNull(index + 1)?.start?.takeIf { it > programme.start }
                val inferredStop = nextStart ?: fallbackDurationMillis?.let { programme.start.plusMillis(it) }
                if (inferredStop == null) programme else programme.copy(stop = inferredStop)
            }
        }
        .sortedWith(compareBy<IptvProgramme> { it.channelId }.thenBy { it.start.epochMillis })
}

/** Computes deterministic now/next entries, preferring the most recently started programme when schedules overlap. */
fun computeNowNext(
    programmes: List<IptvProgramme>,
    channelIds: Set<String>,
    at: IptvInstant,
    fallbackDurationMillis: Long? = null,
): List<IptvNowNext> {
    require(channelIds.none { it.isBlank() }) { "channelIds must not contain blanks" }
    val normalized = inferProgrammeStops(programmes, fallbackDurationMillis).groupBy { it.channelId }

    return channelIds.sorted().map { channelId ->
        val schedule = normalized[channelId].orEmpty().sortedBy { it.start.epochMillis }
        val now = schedule
            .filter { it.start <= at && it.stop?.let { stop -> stop > at } == true }
            .maxByOrNull { it.start.epochMillis }
        val after = now?.start ?: at
        val next = schedule.firstOrNull { it.start > after }
        IptvNowNext(channelId, now, next)
    }
}
