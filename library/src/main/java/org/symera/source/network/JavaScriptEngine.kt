package org.symera.source.network

import java.io.Closeable

/** Host-provided JavaScript executor. The SDK does not prescribe a browser implementation. */
interface JavaScriptEngine : Closeable {
    suspend fun loadUrl(
        url: String,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    ): String

    suspend fun loadHtml(
        html: String,
        baseUrl: String? = null,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    ): String

    suspend fun executeScript(
        script: String,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    ): String?

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000L
    }
}

fun interface JavaScriptEngineFactory {
    fun create(
        allowNetworkLoads: Boolean,
        navigationPolicy: (String) -> Boolean,
    ): JavaScriptEngine
}
