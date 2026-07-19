package org.symera.source

import okhttp3.OkHttpClient
import org.symera.source.challenge.WebChallengeInterceptorFactory
import org.symera.source.local.io.LocalSourceFileSystem
import org.symera.source.network.JavaScriptEngineFactory

interface SourceLogger {
    fun debug(message: String)
    fun warning(message: String, cause: Throwable? = null)
    fun error(message: String, cause: Throwable? = null)
}

data class HostAppInfo(
    val versionCode: Long,
    val versionName: String,
    val sdkVersion: Int,
) {
    init {
        require(versionCode >= 0) { "Host version code cannot be negative" }
        require(versionName.isNotBlank()) { "Host version name cannot be blank" }
        require(sdkVersion > 0) { "Host SDK version must be positive" }
    }
}

/** Dependencies supplied and isolated by the Symera host for one extension process. */
interface SourceEnvironment {
    val httpClient: OkHttpClient

    val userAgent: String
    val appInfo: HostAppInfo
    val logger: SourceLogger

    /** Host-owned bridge for browser challenge detection and coordination. */
    val webChallengeInterceptorFactory: WebChallengeInterceptorFactory?
        get() = null

    /** Optional SAF-backed access controlled and supplied by the host. */
    val localFileSystem: LocalSourceFileSystem?
        get() = null

    /** Optional host-owned JavaScript executor; browser challenge UI remains a separate host concern. */
    val javaScriptEngineFactory: JavaScriptEngineFactory?
        get() = null

    /** Returns storage isolated to this extension and namespace. */
    fun preferencesFor(namespace: String): SourcePreferenceValues
}
