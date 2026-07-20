package org.symera.source.browser

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InteractiveBrowserRequestTest {
    @Test
    fun normalizesEntryUrlAndCopiesAllowedOrigins() {
        val additional = mutableSetOf("https://login.example.com/".toHttpUrl())
        val request = InteractiveBrowserRequest(
            entryUrl = "https://example.com/sign-in?return=%2Fbrowse".toHttpUrl(),
            additionalAllowedTopLevelOrigins = additional,
        )
        additional.clear()

        assertEquals(
            setOf("https://example.com/", "https://login.example.com/"),
            request.allowedTopLevelOrigins.mapTo(mutableSetOf(), Any::toString),
        )
    }

    @Test
    fun rejectsInsecureCredentialedAndNonOriginUrls() {
        assertThrows(IllegalArgumentException::class.java) {
            InteractiveBrowserRequest("http://example.com/".toHttpUrl())
        }
        assertThrows(IllegalArgumentException::class.java) {
            InteractiveBrowserRequest("https://user:secret@example.com/".toHttpUrl())
        }
        assertThrows(IllegalArgumentException::class.java) {
            InteractiveBrowserRequest(
                "https://example.com/".toHttpUrl(),
                setOf("https://login.example.com/path".toHttpUrl()),
            )
        }
    }
}
