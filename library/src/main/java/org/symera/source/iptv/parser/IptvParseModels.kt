package org.symera.source.iptv.parser

import java.io.IOException

enum class IptvParseMode {
    STRICT,
    LENIENT,
}

data class IptvParseLimits(
    val maximumLines: Int = 1_000_000,
    val maximumLineLength: Int = 256 * 1024,
    val maximumEntries: Int = 250_000,
    val maximumAttributesPerEntry: Int = 256,
    val maximumXmlDepth: Int = 64,
    val maximumTextLength: Int = 2 * 1024 * 1024,
) {
    init {
        require(maximumLines > 0) { "Maximum lines must be positive" }
        require(maximumLineLength > 0) { "Maximum line length must be positive" }
        require(maximumEntries > 0) { "Maximum entries must be positive" }
        require(maximumAttributesPerEntry > 0) { "Maximum attributes must be positive" }
        require(maximumXmlDepth > 0) { "Maximum XML depth must be positive" }
        require(maximumTextLength > 0) { "Maximum text length must be positive" }
    }
}

enum class IptvDiagnosticSeverity {
    WARNING,
    ERROR,
}

enum class IptvDiagnosticCode {
    INVALID_HEADER,
    HLS_MANIFEST,
    MALFORMED_DIRECTIVE,
    MISSING_STREAM_URI,
    MISSING_CHANNEL_METADATA,
    INVALID_URI,
    INVALID_ATTRIBUTE,
    INVALID_DATE,
    MISSING_REQUIRED_VALUE,
    UNSUPPORTED_XML_FEATURE,
    MALFORMED_XML,
    RESOURCE_LIMIT_EXCEEDED,
}

data class IptvParseDiagnostic(
    val severity: IptvDiagnosticSeverity,
    val code: IptvDiagnosticCode,
    val message: String,
    val line: Int? = null,
    val column: Int? = null,
) {
    init {
        require(message.isNotBlank()) { "message must not be blank" }
        require(line == null || line > 0) { "line must be positive" }
        require(column == null || column > 0) { "column must be positive" }
    }
}

data class IptvParseResult<T>(
    val value: T,
    val diagnostics: List<IptvParseDiagnostic> = emptyList(),
)

class IptvParseException(
    val diagnostic: IptvParseDiagnostic,
    cause: Throwable? = null,
) : IOException(diagnostic.message, cause)
