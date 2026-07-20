package org.symera.source.iptv

import java.net.URI
import java.util.UUID

enum class IptvRequestKind {
    MANIFEST,
    SEGMENT,
    INITIALIZATION_SEGMENT,
    ENCRYPTION_KEY,
    LICENSE,
    SUBTITLE,
    IMAGE,
}

data class IptvHeaderRule(
    val requestKinds: Set<IptvRequestKind>,
    val allowedOrigins: Set<String>,
    val headers: Map<String, String>,
    val forwardOnCrossOriginRedirect: Boolean = false,
) {
    init {
        require(requestKinds.isNotEmpty()) { "Header rule must target at least one request kind" }
        require(allowedOrigins.isNotEmpty()) { "Header rule must declare allowed origins" }
        allowedOrigins.forEach(IptvUriPolicy::requireCanonicalOrigin)
        require(headers.isNotEmpty()) { "Header rule cannot be empty" }
        IptvHeaderPolicy.requireValid(headers)
    }

    override fun toString(): String =
        "IptvHeaderRule(requestKinds=$requestKinds, allowedOrigins=$allowedOrigins, " +
            "headerNames=${headers.keys}, forwardOnCrossOriginRedirect=$forwardOnCrossOriginRedirect)"
}

data class IptvAuthorizationHandle(
    val value: String,
    val allowedOrigins: Set<String>,
) {
    init {
        require(value.isNotBlank()) { "Authorization handle cannot be blank" }
        require(allowedOrigins.isNotEmpty()) { "Authorization handle needs at least one allowed origin" }
        allowedOrigins.forEach(IptvUriPolicy::requireCanonicalOrigin)
    }

    fun allows(uri: URI): Boolean = runCatching { IptvUriPolicy.origin(uri) in allowedOrigins }.getOrDefault(false)

    override fun toString(): String = "IptvAuthorizationHandle(value=<redacted>, allowedOrigins=$allowedOrigins)"
}

data class IptvLicenseRequest(
    val uri: URI? = null,
    val headers: Map<String, String> = emptyMap(),
    /** Provider-owned exchange identifier for non-standard challenge/response envelopes. */
    val exchangeId: String? = null,
) {
    init {
        if (uri != null) IptvUriPolicy.requireHttpUri(uri, "DRM license URI")
        require(exchangeId == null || exchangeId.isNotBlank()) { "DRM exchange ID cannot be blank" }
        require(uri != null || exchangeId != null) { "License request needs a URI or provider exchange ID" }
        IptvHeaderPolicy.requireValid(headers, "license")
    }

    override fun toString(): String =
        "IptvLicenseRequest(uri=${uri?.redactedForLog()}, headerNames=${headers.keys}, " +
            "exchangeId=${exchangeId?.let { "<redacted>" }})"
}

enum class IptvLicensePolicy {
    MANIFEST,
    FALLBACK,
    OVERRIDE,
}

sealed interface IptvDrm {
    val license: IptvLicenseRequest?
    val licensePolicy: IptvLicensePolicy
    val multiSession: Boolean

    data class Widevine(
        override val license: IptvLicenseRequest? = null,
        override val licensePolicy: IptvLicensePolicy = IptvLicensePolicy.MANIFEST,
        override val multiSession: Boolean = false,
    ) : IptvDrm

    data class ClearKey(
        override val license: IptvLicenseRequest? = null,
        override val licensePolicy: IptvLicensePolicy = IptvLicensePolicy.MANIFEST,
        override val multiSession: Boolean = false,
    ) : IptvDrm

    data class PlayReady(
        override val license: IptvLicenseRequest? = null,
        override val licensePolicy: IptvLicensePolicy = IptvLicensePolicy.MANIFEST,
        override val multiSession: Boolean = false,
    ) : IptvDrm

    data class Custom(
        val schemeUuid: String,
        override val license: IptvLicenseRequest? = null,
        override val licensePolicy: IptvLicensePolicy = IptvLicensePolicy.MANIFEST,
        override val multiSession: Boolean = false,
    ) : IptvDrm {
        init {
            UUID.fromString(schemeUuid)
        }
    }
}

data class IptvRequestContext(
    val uri: URI,
    val kind: IptvRequestKind,
    val failedStatusCode: Int? = null,
) {
    init {
        IptvUriPolicy.requirePlaybackUri(uri, IptvStreamProtocol.AUTO)
        require(failedStatusCode == null || failedStatusCode in 100..599) { "Invalid failed HTTP status" }
    }
}
