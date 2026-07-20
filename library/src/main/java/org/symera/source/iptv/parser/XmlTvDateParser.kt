package org.symera.source.iptv.parser

import org.symera.source.iptv.IptvInstant
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/**
 * Parses XMLTV compact dates without `java.time`, retaining Android API 24 compatibility. Missing date components use
 * their earliest value. Named zones use the platform tz database and optional aliases such as `BST -> Europe/London`.
 */
class XmlTvDateParser(
    private val defaultTimeZoneId: String = "UTC",
    namedTimeZoneAliases: Map<String, String> = emptyMap(),
) {
    private val availableIds = TimeZone.getAvailableIDs().toSet()
    private val aliases = namedTimeZoneAliases.mapKeys { it.key.uppercase(Locale.ROOT) }

    init {
        require(namedTimeZoneAliases.keys.none { it.isBlank() }) { "Time zone aliases must not be blank" }
        namedTimeZoneAliases.values.forEach { require(isKnownZone(it)) { "Unknown aliased time zone: $it" } }
        require(
            isKnownZone(defaultTimeZoneId) || defaultTimeZoneId.uppercase(Locale.ROOT) in aliases,
        ) { "Unknown default time zone: $defaultTimeZoneId" }
    }

    fun parse(value: String): IptvInstant {
        val match = DATE.matchEntire(value.trim()) ?: throw IllegalArgumentException("Invalid XMLTV date: $value")
        val digits = match.groupValues[1]
        require(digits.length in VALID_LENGTHS) { "Invalid XMLTV date precision: $value" }
        val zone = resolveZone(match.groupValues[2].ifEmpty { defaultTimeZoneId })
        val year = digits.substring(0, 4).toInt()
        val month = digits.component(4, 2, 1)
        val day = digits.component(6, 2, 1)
        val hour = digits.component(8, 2, 0)
        val minute = digits.component(10, 2, 0)
        val second = digits.component(12, 2, 0)

        val calendar = GregorianCalendar(zone).apply {
            isLenient = false
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, 0)
        }
        return try {
            IptvInstant(calendar.timeInMillis)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid XMLTV calendar date: $value", error)
        }
    }

    private fun resolveZone(raw: String): TimeZone {
        val value = raw.trim()
        if (value.equals("Z", true) || value.equals("UTC", true) || value.equals("GMT", true)) {
            return TimeZone.getTimeZone("UTC")
        }
        OFFSET.matchEntire(value)?.let { match ->
            val sign = if (match.groupValues[1] == "-") -1 else 1
            val hours = match.groupValues[2].toInt()
            val minutes = match.groupValues[3].toInt()
            require(hours <= 23 && minutes <= 59) { "Invalid XMLTV UTC offset: $raw" }
            val totalMinutes = sign * (hours * 60 + minutes)
            val signCharacter = if (totalMinutes < 0) '-' else '+'
            val absolute = kotlin.math.abs(totalMinutes)
            return TimeZone.getTimeZone(String.format(Locale.ROOT, "GMT%c%02d:%02d", signCharacter, absolute / 60, absolute % 60))
        }

        val alias = aliases[value.uppercase(Locale.ROOT)] ?: value
        require(isKnownZone(alias)) { "Unknown XMLTV time zone: $raw" }
        return TimeZone.getTimeZone(alias)
    }

    private fun isKnownZone(id: String): Boolean =
        id.equals("UTC", true) || id.equals("GMT", true) || id in availableIds

    private fun String.component(start: Int, length: Int, default: Int): Int =
        if (this.length >= start + length) substring(start, start + length).toInt() else default

    private companion object {
        val VALID_LENGTHS = setOf(4, 6, 8, 10, 12, 14)
        val DATE = Regex("^(\\d{4}(?:\\d{2}){0,5})(?:\\s*(Z|UTC|GMT|[+-]\\d{2}:?\\d{2}|[A-Za-z][A-Za-z0-9_+./-]*))?$", RegexOption.IGNORE_CASE)
        val OFFSET = Regex("^([+-])(\\d{2}):?(\\d{2})$")
    }
}
