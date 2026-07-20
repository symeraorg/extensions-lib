package org.symera.source.iptv.playlist

import java.net.URI
import java.util.Locale
import okhttp3.Credentials
import org.symera.source.iptv.IptvAuthentication
import org.symera.source.iptv.IptvAuthenticationScheme
import org.symera.source.iptv.IptvCatalogConfiguration
import org.symera.source.iptv.IptvConfiguration
import org.symera.source.iptv.IptvCredentialField
import org.symera.source.iptv.IptvCredentialKind
import org.symera.source.iptv.IptvCredentials
import org.symera.source.iptv.IptvError
import org.symera.source.iptv.IptvResult

enum class ConfiguredIptvResourceKind {
    PLAYLIST,
    EPG,
}

fun interface ConfiguredIptvHeaderProvider {
    fun headers(uri: URI, kind: ConfiguredIptvResourceKind): Map<String, String>
}

interface ConfiguredIptvAuthenticator {
    val authentication: IptvAuthentication

    suspend fun authenticate(
        configuration: IptvConfiguration,
        credentials: IptvCredentials,
    ): IptvResult<ConfiguredIptvHeaderProvider>
}

object ConfiguredIptvAuthenticators {
    fun none(): ConfiguredIptvAuthenticator = headerAuthenticator(IptvAuthentication.NONE) { emptyMap() }

    fun httpBasic(
        usernameKey: String = "username",
        passwordKey: String = "password",
        allowedOrigins: Set<String>? = null,
    ): ConfiguredIptvAuthenticator = headerAuthenticator(
        authentication = IptvAuthentication(
            scheme = IptvAuthenticationScheme.HTTP_BASIC,
            fields = listOf(
                IptvCredentialField(usernameKey, "Username"),
                IptvCredentialField(passwordKey, "Password", IptvCredentialKind.SECRET),
            ),
        ),
        allowedOrigins = allowedOrigins,
    ) { credentials ->
        mapOf("Authorization" to Credentials.basic(credentials.require(usernameKey), credentials.require(passwordKey)))
    }

    fun bearerToken(
        tokenKey: String = "token",
        allowedOrigins: Set<String>? = null,
    ): ConfiguredIptvAuthenticator = headerAuthenticator(
        authentication = IptvAuthentication(
            scheme = IptvAuthenticationScheme.BEARER_TOKEN,
            fields = listOf(IptvCredentialField(tokenKey, "Token", IptvCredentialKind.SECRET)),
        ),
        allowedOrigins = allowedOrigins,
    ) { credentials -> mapOf("Authorization" to "Bearer ${credentials.require(tokenKey)}") }

    fun apiKey(
        headerName: String,
        credentialKey: String = "apiKey",
        allowedOrigins: Set<String>? = null,
    ): ConfiguredIptvAuthenticator {
        require(headerName.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+"))) { "Invalid API key header name" }
        return headerAuthenticator(
            authentication = IptvAuthentication(
                scheme = IptvAuthenticationScheme.API_KEY,
                fields = listOf(IptvCredentialField(credentialKey, "API key", IptvCredentialKind.SECRET)),
            ),
            allowedOrigins = allowedOrigins,
        ) { credentials -> mapOf(headerName to credentials.require(credentialKey)) }
    }

    private fun headerAuthenticator(
        authentication: IptvAuthentication,
        allowedOrigins: Set<String>? = null,
        headers: (IptvCredentials) -> Map<String, String>,
    ): ConfiguredIptvAuthenticator {
        val normalizedOrigins = allowedOrigins?.mapTo(linkedSetOf()) { it.requireCanonicalOrigin() }
        return object : ConfiguredIptvAuthenticator {
            override val authentication = authentication

            override suspend fun authenticate(
                configuration: IptvConfiguration,
                credentials: IptvCredentials,
            ): IptvResult<ConfiguredIptvHeaderProvider> {
                val generated = runCatching { headers(credentials) }.getOrElse {
                    return IptvResult.Failure(IptvError.AuthenticationRequired(it.message.orEmpty()))
                }
                val origins = normalizedOrigins ?: configuration.resourceOrigins()
                return IptvResult.Success(
                    ConfiguredIptvHeaderProvider { uri, _ ->
                        if (uri.canonicalOrigin() in origins) generated else emptyMap()
                    },
                )
            }
        }
    }
}

private fun IptvCredentials.require(key: String): String =
    requireNotNull(this[key]?.takeIf(String::isNotBlank)) { "Missing IPTV credential: $key" }

private fun IptvConfiguration.resourceOrigins(): Set<String> = buildSet {
    when (val configuredCatalog = catalog) {
        is IptvCatalogConfiguration.Playlists -> configuredCatalog.locations.forEach { add(it.uri.canonicalOrigin()) }
        is IptvCatalogConfiguration.Provider -> configuredCatalog.baseUri?.let { add(it.canonicalOrigin()) }
        is IptvCatalogConfiguration.Channels -> Unit
    }
    epg.forEach { add(it.uri.canonicalOrigin()) }
}

private fun URI.canonicalOrigin(): String {
    val defaultPort = if (scheme.equals("https", true)) 443 else 80
    return buildString {
        append(scheme.lowercase(Locale.ROOT)).append("://").append(host.lowercase(Locale.ROOT))
        if (port >= 0 && port != defaultPort) append(':').append(port)
    }
}

private fun String.requireCanonicalOrigin(): String {
    val uri = URI(this)
    require(
        uri.isAbsolute && uri.host != null && uri.userInfo == null &&
            (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) &&
            uri.rawPath.orEmpty().isEmpty() && uri.rawQuery == null && uri.rawFragment == null
    ) { "Allowed authentication origins must contain only HTTP(S) scheme, host, and port" }
    return uri.canonicalOrigin()
}
