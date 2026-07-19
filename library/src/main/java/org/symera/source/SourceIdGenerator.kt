package org.symera.source

import java.security.MessageDigest
import java.util.Locale

/** Generates persisted source IDs using the historical Symera/Aniyomi-compatible algorithm. */
object SourceIdGenerator {
    fun generate(name: String, lang: String, versionId: Int): Long {
        require(name.isNotBlank()) { "Source name cannot be blank" }
        require(lang.isNotBlank()) { "Source language cannot be blank" }
        require(versionId > 0) { "Source version ID must be positive" }
        val key = "${name.lowercase(Locale.ROOT)}/$lang/$versionId"
        // MD5 is part of a persisted identity format and is not used for a security decision.
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7)
            .map { (bytes[it].toLong() and 0xff) shl (8 * (7 - it)) }
            .reduce(Long::or) and Long.MAX_VALUE
    }
}
