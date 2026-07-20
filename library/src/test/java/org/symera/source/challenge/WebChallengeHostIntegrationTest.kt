package org.symera.source.challenge

import okhttp3.OkHttpClient
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.symera.source.CatalogCapability
import org.symera.source.HostAppInfo
import org.symera.source.SourceEnvironment
import org.symera.source.SourceLogger
import org.symera.source.SourcePreferenceValues
import org.symera.source.model.ContentType
import org.symera.source.model.SContent
import org.symera.source.online.SymeraHttpSource

class WebChallengeHostIntegrationTest {
    @Test
    fun httpSourceUsesOnlyHostProvidedChallengeFactory() {
        var requestedSourceId: Long? = null
        val factory = WebChallengeInterceptorFactory { sourceId, _ ->
            requestedSourceId = sourceId
            okhttp3.Interceptor { chain -> chain.proceed(chain.request()) }
        }
        val source = TestSource(environment(factory))

        assertTrue(source.buildClient().interceptors.isNotEmpty())
        assertEquals(source.id, requestedSourceId)

        requestedSourceId = null
        TestSource(environment(null)).buildClient()
        assertNull(requestedSourceId)
    }

    private fun environment(factory: WebChallengeInterceptorFactory?) = object : SourceEnvironment {
        override val httpClient = OkHttpClient()
        override val userAgent = "Symera Test"
        override val appInfo = HostAppInfo(1, "1", 3)
        override val logger = object : SourceLogger {
            override fun debug(message: String) = Unit
            override fun warning(message: String, cause: Throwable?) = Unit
            override fun error(message: String, cause: Throwable?) = Unit
        }
        override val webChallengeInterceptorFactory = factory
        override fun preferencesFor(namespace: String): SourcePreferenceValues = error("Not used")
    }

    private class TestSource(environment: SourceEnvironment) : SymeraHttpSource(environment), WebChallengeSource {
        override val name = "Challenge"
        override val lang = "en"
        override val contentTypes = setOf(ContentType.MOVIE)
        override val catalogCapabilities = emptySet<CatalogCapability>()
        override val baseUrl = "https://example.com"

        override fun contentDetailsParse(response: Response): SContent = error("Not used")
        fun buildClient(): OkHttpClient = client
    }
}
