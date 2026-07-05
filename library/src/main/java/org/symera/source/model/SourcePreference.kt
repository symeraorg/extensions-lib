package org.symera.source.model

sealed class SourcePreference<T>(
    val key: String,
    val title: String,
    val summary: String? = null,
    val defaultValue: T,
) {
    class Header(title: String) : SourcePreference<Unit>(
        key = title,
        title = title,
        defaultValue = Unit,
    )

    class Separator(key: String = "") : SourcePreference<Unit>(
        key = key,
        title = "",
        defaultValue = Unit,
    )

    class Text(
        key: String,
        title: String,
        summary: String? = null,
        defaultValue: String = "",
    ) : SourcePreference<String>(key, title, summary, defaultValue)

    class Switch(
        key: String,
        title: String,
        summary: String? = null,
        defaultValue: Boolean = false,
    ) : SourcePreference<Boolean>(key, title, summary, defaultValue)

    class Select(
        key: String,
        title: String,
        values: List<Option>,
        summary: String? = null,
        defaultValue: String = values.firstOrNull()?.value.orEmpty(),
    ) : SourcePreference<String>(key, title, summary, defaultValue) {
        val values: List<Option> = values
    }

    class MultiSelect(
        key: String,
        title: String,
        values: List<Option>,
        summary: String? = null,
        defaultValue: Set<String> = emptySet(),
    ) : SourcePreference<Set<String>>(key, title, summary, defaultValue) {
        val values: List<Option> = values
    }

    data class Option(
        val value: String,
        val label: String = value,
    )
}
