package org.symera.source.iptv

import java.net.URI

/** Describes authentication UI and transport semantics without depending on Android widgets. */
data class IptvAuthentication(
    val scheme: IptvAuthenticationScheme,
    val fields: List<IptvCredentialField> = emptyList(),
    val helpUrl: String? = null,
) {
    init {
        require(fields.map { it.key }.distinct().size == fields.size) { "Credential field keys must be unique" }
        require(scheme != IptvAuthenticationScheme.NONE || fields.isEmpty()) { "NONE authentication cannot have fields" }
        if (helpUrl != null) {
            val helpUri = runCatching { URI(helpUrl) }
                .getOrElse { throw IllegalArgumentException("Invalid authentication help URL", it) }
            IptvUriPolicy.requireHttpUri(helpUri, "Authentication help URL")
        }
    }

    companion object {
        val NONE = IptvAuthentication(IptvAuthenticationScheme.NONE)
    }
}

enum class IptvAuthenticationScheme {
    NONE,
    HTTP_BASIC,
    BEARER_TOKEN,
    API_KEY,
    USERNAME_PASSWORD,
    OAUTH2_AUTHORIZATION_CODE,
    CUSTOM_BROWSER,
    CUSTOM,
}

enum class IptvCredentialKind {
    TEXT,
    SECRET,
}

data class IptvCredentialField(
    val key: String,
    val label: String,
    val kind: IptvCredentialKind = IptvCredentialKind.TEXT,
    val required: Boolean = true,
    val hint: String? = null,
) {
    init {
        require(key.matches(Regex("[A-Za-z][A-Za-z0-9_.-]*"))) { "Invalid credential key: $key" }
        require(label.isNotBlank()) { "label must not be blank" }
    }
}

/** Secret values supplied from host encrypted storage. */
class IptvCredentials(values: Map<String, String>) {
    private val values: Map<String, String> = values.toMap()

    init {
        require(values.keys.none { it.isBlank() }) { "Credential keys must not be blank" }
    }

    operator fun get(key: String): String? = values[key]
    fun contains(key: String): Boolean = key in values
    override fun toString(): String = "IptvCredentials(keys=${values.keys})"

    companion object {
        val EMPTY = IptvCredentials(emptyMap())
    }
}
