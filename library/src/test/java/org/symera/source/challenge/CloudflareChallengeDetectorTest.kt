package org.symera.source.challenge

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class CloudflareChallengeDetectorTest {
    @Test
    fun detectsModernMitigationHeader() {
        val response = response(
            code = 200,
            headers = mapOf("cf-mitigated" to "challenge", "Content-Type" to "text/html"),
        )
        assertTrue(CloudflareChallengeDetector.isChallenge(response))
    }

    @Test
    fun doesNotTreatEveryCloudflareErrorAsChallenge() {
        val response = response(
            code = 503,
            headers = mapOf("Server" to "cloudflare", "Content-Type" to "text/html"),
            body = "Origin unavailable",
        )
        assertFalse(CloudflareChallengeDetector.isChallenge(response))
    }

    @Test
    fun detectsLegacyChallengeWithBoundedBodyInspection() {
        val response = response(
            code = 503,
            headers = mapOf("Server" to "cloudflare", "Content-Type" to "text/html"),
            body = "<script>window._cf_chl_opt = {}</script>",
        )
        assertTrue(CloudflareChallengeDetector.isChallenge(response))
    }

    @Test
    fun challengePolicyRejectsBrowserControlledHeaders() {
        assertThrows(IllegalArgumentException::class.java) {
            WebChallengePolicy(initialHeaders = mapOf("Cookie" to "cf_clearance=secret"))
        }
    }

    private fun response(
        code: Int,
        headers: Map<String, String>,
        body: String = "",
    ): Response = Response.Builder()
        .request(Request.Builder().url("https://example.com/protected").build())
        .protocol(Protocol.HTTP_2)
        .code(code)
        .message("Test")
        .headers(okhttp3.Headers.Builder().apply { headers.forEach(::add) }.build())
        .body(body.toResponseBody("text/html".toMediaType()))
        .build()
}
