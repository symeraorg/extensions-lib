package org.symera.source.challenge

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WebChallengePolicyTest {
    @Test
    fun rejectsBrowserControlledHeaders() {
        assertThrows(IllegalArgumentException::class.java) {
            WebChallengePolicy(initialHeaders = mapOf("Cookie" to "cf_clearance=secret"))
        }
    }

    @Test
    fun rejectsUnsafeBrowserBoundaries() {
        assertThrows(IllegalArgumentException::class.java) {
            WebChallengePolicy(entryUrl = "https://user:secret@example.com/".toHttpUrl())
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebChallengePolicy(
                additionalAllowedTopLevelOrigins = setOf("https://login.example.com/path".toHttpUrl()),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebChallengePolicy(timeoutMillis = WebChallengePolicy.MAXIMUM_TIMEOUT_MILLIS + 1)
        }
    }

    @Test
    fun copiesExtensionOwnedCollections() {
        val origins = mutableSetOf("https://login.example.com/".toHttpUrl())
        val headers = mutableMapOf("Referer" to "https://example.com/")
        val policy = WebChallengePolicy(
            additionalAllowedTopLevelOrigins = origins,
            initialHeaders = headers,
        )

        origins.clear()
        headers.clear()

        assertEquals(1, policy.additionalAllowedTopLevelOrigins.size)
        assertEquals(1, policy.initialHeaders.size)
    }
}
