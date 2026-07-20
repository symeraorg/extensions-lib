package org.symera.source.iptv

import java.net.URI

data class IptvAuthorizationRequest(
    val uri: URI,
    val callbackScheme: String,
    val state: String,
) {
    init {
        IptvUriPolicy.requireHttpUri(uri, "Authorization URI", httpsOnly = true)
        require(callbackScheme.matches(Regex("[A-Za-z][A-Za-z0-9+.-]*"))) { "Invalid callback scheme" }
        require(state.isNotBlank()) { "Authorization state cannot be blank" }
    }
}

/** Optional browser authentication flow implemented by provider adapters, never by WebView secrets. */
interface IptvInteractiveAuthentication {
    fun createAuthorizationRequest(configuration: IptvConfiguration): IptvResult<IptvAuthorizationRequest>

    suspend fun completeAuthorization(
        configuration: IptvConfiguration,
        callbackUri: URI,
        expectedState: String,
    ): IptvResult<IptvCredentials>

    suspend fun refreshAuthorization(
        configuration: IptvConfiguration,
        credentials: IptvCredentials,
    ): IptvResult<IptvCredentials>
}
