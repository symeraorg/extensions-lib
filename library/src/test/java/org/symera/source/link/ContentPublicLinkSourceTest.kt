package org.symera.source.link

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ContentPublicLinkSourceTest {
    @Test
    fun acceptsPublicHttpsLinkWithoutSensitiveState() {
        val link = PublicContentLink("https://example.com/title?id=42".toHttpUrl())

        assertEquals("https://example.com/title?id=42", link.url.toString())
    }

    @Test
    fun rejectsUnsafeLinks() {
        listOf(
            "http://example.com/title",
            "https://user:secret@example.com/title",
            "https://example.com/title?token=secret",
            "https://example.com/title?Signature=secret",
        ).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { PublicContentLink(value.toHttpUrl()) }
        }
    }
}
