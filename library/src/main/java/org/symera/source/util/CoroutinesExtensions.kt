package org.symera.source.util

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

suspend fun <T> withIO(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

suspend fun <T, R> Iterable<T>.parallelMap(
    concurrency: Int = DEFAULT_PARALLELISM,
    transform: suspend (T) -> R,
): List<R> = coroutineScope {
    require(concurrency > 0) { "concurrency must be greater than 0" }
    val semaphore = Semaphore(concurrency)
    map { item ->
        async(Dispatchers.IO) {
            semaphore.withPermit { transform(item) }
        }
    }.awaitAll()
}

const val DEFAULT_PARALLELISM = 4
