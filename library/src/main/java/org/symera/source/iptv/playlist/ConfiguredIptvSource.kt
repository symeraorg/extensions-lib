package org.symera.source.iptv.playlist

import kotlinx.coroutines.CancellationException
import org.symera.source.iptv.IptvCapabilities
import org.symera.source.iptv.IptvCapability
import org.symera.source.iptv.IptvAuthentication
import org.symera.source.iptv.IptvCatalogConfiguration
import org.symera.source.iptv.IptvConfiguration
import org.symera.source.iptv.IptvCredentials
import org.symera.source.iptv.IptvError
import org.symera.source.iptv.IptvResult
import org.symera.source.iptv.IptvSession
import org.symera.source.iptv.IptvSource

/** Ready-to-use source for playlist URLs or individually configured channels. */
class ConfiguredIptvSource(
    override val id: Long,
    override val name: String,
    private val resourceLoader: IptvResourceLoader,
    presets: List<IptvConfiguration> = emptyList(),
    private val authenticator: ConfiguredIptvAuthenticator = ConfiguredIptvAuthenticators.none(),
    private val limits: ConfiguredSessionLimits = ConfiguredSessionLimits(),
    private val components: ConfiguredIptvComponents = ConfiguredIptvComponents(),
) : IptvSource {
    private val presets = presets.toList()

    init {
        require(name.isNotBlank()) { "IPTV source name cannot be blank" }
        require(this.presets.map(IptvConfiguration::id).distinct().size == this.presets.size) {
            "IPTV preset IDs must be unique"
        }
        require(presets.none { it.catalog is IptvCatalogConfiguration.Provider }) {
            "ConfiguredIptvSource does not implement provider-specific adapters"
        }
    }

    override val capabilities: IptvCapabilities = IptvCapabilities(buildSet {
        add(IptvCapability.GROUPS)
        add(IptvCapability.TV)
        add(IptvCapability.RADIO)
        add(IptvCapability.EPG)
        add(IptvCapability.NOW_NEXT)
        add(IptvCapability.SEARCH)
        add(IptvCapability.REFRESH)
        if (components.catchUpResolver != null) add(IptvCapability.CATCH_UP)
        if (components.timeshiftResolver != null) add(IptvCapability.TIMESHIFT)
    })

    override val authentication: IptvAuthentication
        get() = authenticator.authentication

    override fun configurations(): List<IptvConfiguration> = presets

    override suspend fun openSession(
        configuration: IptvConfiguration,
        credentials: IptvCredentials,
    ): IptvResult<IptvSession> {
        if (configuration.catalog is IptvCatalogConfiguration.Provider) {
            return IptvResult.Failure(IptvError.InvalidConfiguration("Provider configuration requires its extension adapter"))
        }
        val requiredCredentialKeys = buildSet {
            authentication.fields.filter { it.required }.mapTo(this) { it.key }
            val catalog = configuration.catalog
            if (catalog is IptvCatalogConfiguration.Playlists) {
                catalog.locations.flatMapTo(this) { it.secretHeaderReferences.values }
            }
            configuration.epg.flatMapTo(this) { it.secretHeaderReferences.values }
        }
        val missingCredential = requiredCredentialKeys.firstOrNull { credentials[it].isNullOrBlank() }
        if (missingCredential != null) {
            return IptvResult.Failure(IptvError.AuthenticationRequired("Missing IPTV credential: $missingCredential"))
        }
        val authenticationResult = try {
            authenticator.authenticate(configuration, credentials)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            return IptvResult.Failure(IptvError.Unexpected("IPTV authentication failed", exception))
        }
        val headerProvider = when (val authenticated = authenticationResult) {
            is IptvResult.Success -> authenticated.value
            is IptvResult.Failure -> return authenticated
        }
        val session = ConfiguredIptvSession(
            configuration,
            resourceLoader,
            credentials,
            limits,
            headerProvider,
            components,
        )
        return when (val initialized = session.initialize()) {
            is IptvResult.Success -> IptvResult.Success(session)
            is IptvResult.Failure -> {
                session.close()
                initialized
            }
        }
    }
}

data class ConfiguredSessionLimits(
    val maximumPlaylistBytes: Long = 64L * 1024L * 1024L,
    val maximumEpgBytes: Long = 256L * 1024L * 1024L,
    val maximumLocations: Int = 32,
    val maximumChannels: Int = 250_000,
    val maximumProgrammes: Int = 1_000_000,
) {
    init {
        require(maximumPlaylistBytes > 0) { "Playlist byte limit must be positive" }
        require(maximumEpgBytes > 0) { "EPG byte limit must be positive" }
        require(maximumLocations > 0) { "Location limit must be positive" }
        require(maximumChannels > 0) { "Channel limit must be positive" }
        require(maximumProgrammes > 0) { "Programme limit must be positive" }
    }
}
