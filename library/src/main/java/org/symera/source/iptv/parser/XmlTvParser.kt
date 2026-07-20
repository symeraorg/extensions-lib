package org.symera.source.iptv.parser

import org.symera.source.iptv.IptvEpg
import org.symera.source.iptv.IptvEpgChannel
import org.symera.source.iptv.IptvEpisodeNumber
import org.symera.source.iptv.IptvImage
import org.symera.source.iptv.IptvInstant
import org.symera.source.iptv.IptvLocalizedText
import org.symera.source.iptv.IptvProgramme
import org.symera.source.iptv.IptvRating
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.Locator
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import org.xml.sax.ext.DefaultHandler2
import java.io.InputStream
import java.io.Reader
import java.net.URI
import javax.xml.XMLConstants
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory

data class XmlTvParserOptions(
    val mode: IptvParseMode = IptvParseMode.LENIENT,
    val defaultTimeZoneId: String = "UTC",
    val namedTimeZoneAliases: Map<String, String> = emptyMap(),
    val baseUri: URI? = null,
    val limits: IptvParseLimits = IptvParseLimits(),
    val maximumChannels: Int = limits.maximumEntries,
    val maximumProgrammes: Int = limits.maximumEntries,
) {
    init {
        require(baseUri == null || baseUri.isAbsolute) { "baseUri must be absolute" }
        require(maximumChannels > 0) { "maximumChannels must be positive" }
        require(maximumProgrammes > 0) { "maximumProgrammes must be positive" }
    }
}

interface XmlTvConsumer {
    fun onChannel(channel: IptvEpgChannel)
    fun onProgramme(programme: IptvProgramme)
}

data class XmlTvParseReport(
    val channelCount: Int,
    val programmeCount: Int,
    val diagnostics: List<IptvParseDiagnostic>,
)

/** Secure, streaming XMLTV parser. Inputs remain owned by the caller. */
class XmlTvParser(
    private val options: XmlTvParserOptions = XmlTvParserOptions(),
) {
    private val dateParser = XmlTvDateParser(options.defaultTimeZoneId, options.namedTimeZoneAliases)

    fun parse(input: InputStream): IptvParseResult<IptvEpg> = parseDocument(InputSource(input))

    fun parse(reader: Reader): IptvParseResult<IptvEpg> = parseDocument(InputSource(reader))

    fun parse(input: InputStream, consumer: XmlTvConsumer): XmlTvParseReport = parseStream(InputSource(input), consumer)

    fun parse(reader: Reader, consumer: XmlTvConsumer): XmlTvParseReport = parseStream(InputSource(reader), consumer)

    private fun parseDocument(source: InputSource): IptvParseResult<IptvEpg> {
        val channels = mutableListOf<IptvEpgChannel>()
        val programmes = mutableListOf<IptvProgramme>()
        val report = parseStream(source, object : XmlTvConsumer {
            override fun onChannel(channel: IptvEpgChannel) {
                channels += channel
            }

            override fun onProgramme(programme: IptvProgramme) {
                programmes += programme
            }
        })
        return IptvParseResult(IptvEpg(channels, programmes), report.diagnostics)
    }

    private fun parseStream(source: InputSource, consumer: XmlTvConsumer): XmlTvParseReport {
        val diagnostics = mutableListOf<IptvParseDiagnostic>()
        val handler = Handler(consumer, diagnostics)
        val reader = createSecureFactory(diagnostics).newSAXParser().xmlReader
        reader.contentHandler = handler
        reader.errorHandler = handler
        reader.entityResolver = handler

        try {
            reader.parse(source)
        } catch (error: SAXException) {
            val typed = findParseException(error)
            if (typed != null) throw typed
            val location = error as? SAXParseException
            throw IptvParseException(
                IptvParseDiagnostic(
                    IptvDiagnosticSeverity.ERROR,
                    IptvDiagnosticCode.MALFORMED_XML,
                    error.message ?: "Malformed XMLTV document",
                    location?.lineNumber?.takeIf { it > 0 },
                    location?.columnNumber?.takeIf { it > 0 },
                ),
                error,
            )
        }
        return XmlTvParseReport(handler.channelCount, handler.programmeCount, diagnostics)
    }

    private fun createSecureFactory(diagnostics: MutableList<IptvParseDiagnostic>): SAXParserFactory {
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            isValidating = false
        }
        val requiredFeatures = mapOf(
            XMLConstants.FEATURE_SECURE_PROCESSING to true,
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
            "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
        )
        requiredFeatures.forEach { (feature, enabled) ->
            try {
                factory.setFeature(feature, enabled)
            } catch (error: Exception) {
                if (error !is ParserConfigurationException && error !is SAXException) throw error
                val diagnostic = IptvParseDiagnostic(
                    IptvDiagnosticSeverity.ERROR,
                    IptvDiagnosticCode.UNSUPPORTED_XML_FEATURE,
                    "XML parser cannot enforce required security feature $feature",
                )
                diagnostics += diagnostic
                throw IptvParseException(diagnostic, error)
            }
        }
        return factory
    }

    private fun findParseException(error: Throwable?): IptvParseException? {
        var current = error
        while (current != null) {
            if (current is IptvParseException) return current
            current = current.cause
        }
        return null
    }

    private inner class Handler(
        private val consumer: XmlTvConsumer,
        private val diagnostics: MutableList<IptvParseDiagnostic>,
    ) : DefaultHandler2() {
        private var locator: Locator? = null
        private var channel: ChannelBuilder? = null
        private var programme: ProgrammeBuilder? = null
        private var rating: RatingBuilder? = null
        private var textTarget: TextTarget? = null
        private var textLanguage: String? = null
        private val text = StringBuilder()
        private val elements = mutableListOf<String>()
        var channelCount: Int = 0
            private set
        var programmeCount: Int = 0
            private set

        override fun setDocumentLocator(locator: Locator) {
            this.locator = locator
        }

        override fun resolveEntity(publicId: String?, systemId: String?): InputSource {
            throw SAXException("External XML entities are forbidden")
        }

        override fun startDTD(name: String?, publicId: String?, systemId: String?) {
            throw SAXException("DOCTYPE is forbidden in XMLTV")
        }

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            val element = (localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty()).lowercase()
            val parent = elements.lastOrNull()
            elements += element
            if (elements.size > options.limits.maximumXmlDepth) {
                limitExceeded("XMLTV exceeds element depth limit")
            }
            when (element) {
                "channel" -> channel = ChannelBuilder(attributes.getValue("id").orEmpty())
                "programme" -> {
                    val channelId = attributes.getValue("channel").orEmpty()
                    val start = parseDate(attributes.getValue("start"), "programme start")
                    val stop = parseDate(attributes.getValue("stop"), "programme stop", required = false)
                    programme = ProgrammeBuilder(channelId, start, stop)
                }
                "display-name" -> if (channel != null) beginText(TextTarget.CHANNEL_NAME, attributes)
                "title" -> if (programme != null) beginText(TextTarget.TITLE, attributes)
                "sub-title" -> if (programme != null) beginText(TextTarget.SUBTITLE, attributes)
                "desc" -> if (programme != null) beginText(TextTarget.DESCRIPTION, attributes)
                "category" -> if (programme != null) beginText(TextTarget.CATEGORY, attributes)
                "episode-num" -> if (programme != null) {
                    beginText(TextTarget.EPISODE, attributes)
                    textLanguage = attributes.getValue("system")
                }
                "url" -> if (channel != null) beginText(TextTarget.CHANNEL_URL, attributes)
                "rating" -> if (programme != null) rating = RatingBuilder(attributes.getValue("system"))
                "value" -> if (rating != null && parent == "rating") beginText(TextTarget.RATING_VALUE, attributes)
                "icon" -> addIcon(attributes, parent)
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (textTarget != null) {
                if (text.length + length > options.limits.maximumTextLength) {
                    limitExceeded("XMLTV text exceeds length limit")
                }
                text.append(ch, start, length)
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val element = (localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty()).lowercase()
            when (element) {
                "display-name", "title", "sub-title", "desc", "category", "episode-num", "url", "value" -> finishText()
                "rating" -> finishRating()
                "channel" -> finishChannel()
                "programme" -> finishProgramme()
            }
            if (elements.isNotEmpty()) elements.removeAt(elements.lastIndex)
        }

        private fun beginText(target: TextTarget, attributes: Attributes) {
            textTarget = target
            textLanguage = attributes.getValue(XML_NAMESPACE, "lang")
                ?: attributes.getValue("xml:lang")
                ?: attributes.getValue("lang")
            text.setLength(0)
        }

        private fun finishText() {
            val target = textTarget ?: return
            val value = text.toString().trim()
            if (value.isNotEmpty()) {
                when (target) {
                    TextTarget.CHANNEL_NAME -> channel?.names?.add(IptvLocalizedText(value, textLanguage))
                    TextTarget.TITLE -> programme?.titles?.add(IptvLocalizedText(value, textLanguage))
                    TextTarget.SUBTITLE -> programme?.subtitles?.add(IptvLocalizedText(value, textLanguage))
                    TextTarget.DESCRIPTION -> programme?.descriptions?.add(IptvLocalizedText(value, textLanguage))
                    TextTarget.CATEGORY -> programme?.categories?.add(IptvLocalizedText(value, textLanguage))
                    TextTarget.EPISODE -> programme?.episodes?.add(IptvEpisodeNumber(value, textLanguage))
                    TextTarget.RATING_VALUE -> rating?.value = value
                    TextTarget.CHANNEL_URL -> resolveUri(value)?.let { channel?.urls?.add(it) }
                }
            }
            textTarget = null
            textLanguage = null
            text.setLength(0)
        }

        private fun addIcon(attributes: Attributes, parent: String?) {
            val source = attributes.getValue("src")
            if (source.isNullOrBlank()) {
                issue(IptvDiagnosticCode.MISSING_REQUIRED_VALUE, "XMLTV icon has no src")
                return
            }
            val uri = resolveUri(source) ?: return
            val width = positiveInt(attributes.getValue("width"), "icon width")
            val height = positiveInt(attributes.getValue("height"), "icon height")
            val icon = IptvImage(uri, width, height)
            when {
                parent == "rating" && rating != null -> rating?.icon = icon
                programme != null -> programme?.icon = icon
                channel != null -> channel?.icons?.add(icon)
            }
        }

        private fun finishRating() {
            val current = rating ?: return
            current.value?.takeIf { it.isNotBlank() }?.let {
                programme?.ratings?.add(IptvRating(it, current.system?.takeIf(String::isNotBlank), current.icon))
            } ?: issue(IptvDiagnosticCode.MISSING_REQUIRED_VALUE, "XMLTV rating has no value")
            rating = null
        }

        private fun finishChannel() {
            val current = channel ?: return
            if (current.id.isBlank()) {
                issue(IptvDiagnosticCode.MISSING_REQUIRED_VALUE, "XMLTV channel has no id")
                channel = null
                return
            }
            if (current.names.isEmpty()) {
                issue(IptvDiagnosticCode.MISSING_REQUIRED_VALUE, "XMLTV channel ${current.id} has no display-name")
                if (options.mode == IptvParseMode.LENIENT) current.names += IptvLocalizedText(current.id)
            }
            if (current.names.isNotEmpty()) {
                if (channelCount >= options.maximumChannels) {
                    limitExceeded("XMLTV exceeds channel limit")
                }
                consumer.onChannel(IptvEpgChannel(current.id, current.names, current.icons, current.urls))
                channelCount++
            }
            channel = null
        }

        private fun finishProgramme() {
            val current = programme ?: return
            when {
                current.channelId.isBlank() -> issue(IptvDiagnosticCode.MISSING_REQUIRED_VALUE, "XMLTV programme has no channel")
                current.start == null -> Unit
                current.titles.isEmpty() -> issue(IptvDiagnosticCode.MISSING_REQUIRED_VALUE, "XMLTV programme has no title")
                current.stop?.let { it <= current.start } == true -> {
                    issue(IptvDiagnosticCode.INVALID_DATE, "XMLTV programme stop is not after start")
                    current.stop = null
                    emitProgramme(current)
                }
                else -> {
                    emitProgramme(current)
                }
            }
            programme = null
        }

        private fun emitProgramme(programme: ProgrammeBuilder) {
            val start = programme.start ?: return
            if (programmeCount >= options.maximumProgrammes) {
                limitExceeded("XMLTV exceeds programme limit")
            }
            consumer.onProgramme(
                IptvProgramme(
                    channelId = programme.channelId,
                    start = start,
                    stop = programme.stop,
                    titles = programme.titles,
                    subtitles = programme.subtitles,
                    descriptions = programme.descriptions,
                    categories = programme.categories,
                    icon = programme.icon,
                    episodeNumbers = programme.episodes,
                    ratings = programme.ratings,
                ),
            )
            programmeCount++
        }

        private fun limitExceeded(message: String): Nothing {
            val diagnostic = IptvParseDiagnostic(
                severity = IptvDiagnosticSeverity.ERROR,
                code = IptvDiagnosticCode.RESOURCE_LIMIT_EXCEEDED,
                message = message,
                line = locator?.lineNumber?.takeIf { it > 0 },
                column = locator?.columnNumber?.takeIf { it > 0 },
            )
            addDiagnostic(diagnostic)
            throw IptvParseException(diagnostic)
        }

        private fun parseDate(value: String?, label: String, required: Boolean = true): IptvInstant? {
            if (value.isNullOrBlank()) {
                if (required) issue(IptvDiagnosticCode.MISSING_REQUIRED_VALUE, "$label is missing")
                return null
            }
            return try {
                dateParser.parse(value)
            } catch (error: IllegalArgumentException) {
                issue(IptvDiagnosticCode.INVALID_DATE, "$label is invalid: $value", error)
                null
            }
        }

        private fun resolveUri(value: String): URI? = try {
            val parsed = URI(value)
            val resolved = if (parsed.isAbsolute) parsed else options.baseUri?.resolve(parsed)
            if (
                resolved == null || !resolved.isAbsolute || resolved.host == null || resolved.userInfo != null ||
                !(resolved.scheme.equals("http", true) || resolved.scheme.equals("https", true))
            ) {
                issue(IptvDiagnosticCode.INVALID_URI, "Relative XMLTV URI requires baseUri: $value")
                null
            } else {
                resolved
            }
        } catch (error: Exception) {
            issue(IptvDiagnosticCode.INVALID_URI, "Invalid XMLTV URI: $value", error)
            null
        }

        private fun positiveInt(value: String?, label: String): Int? {
            if (value.isNullOrBlank()) return null
            val parsed = value.toIntOrNull()
            if (parsed == null || parsed <= 0) issue(IptvDiagnosticCode.INVALID_ATTRIBUTE, "Invalid $label: $value")
            return parsed?.takeIf { it > 0 }
        }

        private fun issue(code: IptvDiagnosticCode, message: String, cause: Throwable? = null) {
            val diagnostic = IptvParseDiagnostic(
                if (options.mode == IptvParseMode.STRICT) IptvDiagnosticSeverity.ERROR else IptvDiagnosticSeverity.WARNING,
                code,
                message,
                locator?.lineNumber?.takeIf { it > 0 },
                locator?.columnNumber?.takeIf { it > 0 },
            )
            addDiagnostic(diagnostic)
            if (options.mode == IptvParseMode.STRICT) throw SAXException(IptvParseException(diagnostic, cause))
        }

        private fun addDiagnostic(diagnostic: IptvParseDiagnostic) {
            if (diagnostics.size >= 1_024) {
                throw IptvParseException(
                    IptvParseDiagnostic(
                        severity = IptvDiagnosticSeverity.ERROR,
                        code = IptvDiagnosticCode.RESOURCE_LIMIT_EXCEEDED,
                        message = "XMLTV exceeds diagnostic limit",
                        line = locator?.lineNumber?.takeIf { it > 0 },
                        column = locator?.columnNumber?.takeIf { it > 0 },
                    ),
                )
            }
            diagnostics += diagnostic
        }
    }

    private data class ChannelBuilder(
        val id: String,
        val names: MutableList<IptvLocalizedText> = mutableListOf(),
        val icons: MutableList<IptvImage> = mutableListOf(),
        val urls: MutableList<URI> = mutableListOf(),
    )

    private data class ProgrammeBuilder(
        val channelId: String,
        val start: IptvInstant?,
        var stop: IptvInstant?,
        val titles: MutableList<IptvLocalizedText> = mutableListOf(),
        val subtitles: MutableList<IptvLocalizedText> = mutableListOf(),
        val descriptions: MutableList<IptvLocalizedText> = mutableListOf(),
        val categories: MutableList<IptvLocalizedText> = mutableListOf(),
        val episodes: MutableList<IptvEpisodeNumber> = mutableListOf(),
        val ratings: MutableList<IptvRating> = mutableListOf(),
        var icon: IptvImage? = null,
    )

    private data class RatingBuilder(
        val system: String?,
        var value: String? = null,
        var icon: IptvImage? = null,
    )

    private enum class TextTarget {
        CHANNEL_NAME,
        CHANNEL_URL,
        TITLE,
        SUBTITLE,
        DESCRIPTION,
        CATEGORY,
        EPISODE,
        RATING_VALUE,
    }

    private companion object {
        const val XML_NAMESPACE = "http://www.w3.org/XML/1998/namespace"
    }
}
