package org.symera.source.model

sealed interface PreferenceCondition {
    data class BooleanValue(val key: String, val expected: Boolean) : PreferenceCondition
    data class StringValue(val key: String, val expected: String) : PreferenceCondition
    data class StringSetContains(val key: String, val value: String) : PreferenceCondition
    data class LongValue(val key: String, val operator: NumberOperator, val value: Long) : PreferenceCondition
    data class IsNotBlank(val key: String) : PreferenceCondition
    data class All(val conditions: List<PreferenceCondition>) : PreferenceCondition {
        init {
            require(conditions.isNotEmpty()) { "All condition requires at least one child" }
        }
    }
    data class Any(val conditions: List<PreferenceCondition>) : PreferenceCondition {
        init {
            require(conditions.isNotEmpty()) { "Any condition requires at least one child" }
        }
    }
    data class Not(val condition: PreferenceCondition) : PreferenceCondition
}

enum class NumberOperator {
    EQUAL,
    NOT_EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
}

enum class TextInputType {
    TEXT,
    URL,
}

data class TextValidation(
    val minimumLength: Int = 0,
    val maximumLength: Int? = null,
    val pattern: String? = null,
) {
    init {
        require(minimumLength >= 0) { "Minimum text length cannot be negative" }
        require(maximumLength == null || maximumLength >= minimumLength) { "Maximum text length is invalid" }
        pattern?.toRegex()
    }

    fun validate(value: String): Boolean =
        value.length >= minimumLength &&
            (maximumLength == null || value.length <= maximumLength) &&
            (pattern == null || pattern.toRegex().matches(value))
}

sealed class SourcePreference<T>(
    open val key: String?,
    open val title: String?,
    open val summary: String? = null,
    open val defaultValue: T,
    open val enabledWhen: PreferenceCondition? = null,
) {
    data class Header(
        override val title: String,
    ) : SourcePreference<Unit>(null, title, defaultValue = Unit)

    data object Separator : SourcePreference<Unit>(null, null, defaultValue = Unit)

    data class Text(
        override val key: String,
        override val title: String,
        override val summary: String? = null,
        override val defaultValue: String = "",
        val inputType: TextInputType = TextInputType.TEXT,
        val validation: TextValidation = TextValidation(),
        override val enabledWhen: PreferenceCondition? = null,
    ) : SourcePreference<String>(key, title, summary, defaultValue, enabledWhen)

    /** A text value that the host must read and persist through secret storage operations. */
    data class Secret(
        override val key: String,
        override val title: String,
        override val summary: String? = null,
        override val defaultValue: String = "",
        val validation: TextValidation = TextValidation(),
        override val enabledWhen: PreferenceCondition? = null,
    ) : SourcePreference<String>(key, title, summary, defaultValue, enabledWhen) {
        init {
            require(defaultValue.isEmpty()) { "Secret preferences cannot embed a default credential" }
        }
    }

    data class Switch(
        override val key: String,
        override val title: String,
        override val summary: String? = null,
        override val defaultValue: Boolean = false,
        override val enabledWhen: PreferenceCondition? = null,
    ) : SourcePreference<Boolean>(key, title, summary, defaultValue, enabledWhen)

    data class Number(
        override val key: String,
        override val title: String,
        override val summary: String? = null,
        override val defaultValue: Long,
        val minimum: Long? = null,
        val maximum: Long? = null,
        override val enabledWhen: PreferenceCondition? = null,
    ) : SourcePreference<Long>(key, title, summary, defaultValue, enabledWhen) {
        init {
            require(minimum == null || maximum == null || minimum <= maximum) { "Preference minimum exceeds maximum" }
            require(minimum == null || defaultValue >= minimum) { "Preference default is below minimum" }
            require(maximum == null || defaultValue <= maximum) { "Preference default is above maximum" }
        }
    }

    data class Select(
        override val key: String,
        override val title: String,
        val values: List<Option>,
        override val summary: String? = null,
        override val defaultValue: String = values.firstOrNull()?.value.orEmpty(),
        override val enabledWhen: PreferenceCondition? = null,
    ) : SourcePreference<String>(key, title, summary, defaultValue, enabledWhen) {
        init {
            require(values.isNotEmpty()) { "Select preference requires at least one option" }
            require(values.map(Option::value).distinct().size == values.size) { "Select option values must be unique" }
            require(values.any { it.value == defaultValue }) { "Select default must be one of its options" }
        }
    }

    data class MultiSelect(
        override val key: String,
        override val title: String,
        val values: List<Option>,
        override val summary: String? = null,
        override val defaultValue: Set<String> = emptySet(),
        override val enabledWhen: PreferenceCondition? = null,
    ) : SourcePreference<Set<String>>(key, title, summary, defaultValue, enabledWhen) {
        init {
            val optionValues = values.map(Option::value)
            require(optionValues.distinct().size == optionValues.size) { "Multi-select option values must be unique" }
            require(defaultValue.all(optionValues::contains)) { "Multi-select default contains an unknown option" }
        }
    }

    data class Action(
        override val key: String,
        override val title: String,
        override val summary: String? = null,
        override val enabledWhen: PreferenceCondition? = null,
    ) : SourcePreference<Unit>(key, title, summary, Unit, enabledWhen)

    data class Option(
        val value: String,
        val label: String = value,
    ) {
        init {
            require(value.isNotBlank()) { "Preference option value cannot be blank" }
            require(label.isNotBlank()) { "Preference option label cannot be blank" }
        }
    }
}

fun List<SourcePreference<*>>.validatePreferenceSchema() {
    val keys = mapNotNull(SourcePreference<*>::key)
    require(keys.all(String::isNotBlank)) { "Preference keys cannot be blank" }
    require(keys.distinct().size == keys.size) { "Preference keys must be unique" }

    val byKey = associateBy { it.key }
    forEach { preference ->
        when (preference) {
            is SourcePreference.Text -> require(preference.validation.validate(preference.defaultValue)) {
                "Default value is invalid for preference ${preference.key}"
            }
            is SourcePreference.Secret -> Unit
            else -> Unit
        }
        preference.enabledWhen?.validate(byKey)
    }

    fun visit(key: String, path: MutableSet<String>, complete: MutableSet<String>) {
        if (key in complete) return
        require(path.add(key)) { "Preference dependency cycle includes $key" }
        byKey[key]?.enabledWhen?.referencedKeys().orEmpty().forEach { visit(it, path, complete) }
        path.remove(key)
        complete += key
    }
    val complete = mutableSetOf<String>()
    keys.forEach { visit(it, mutableSetOf(), complete) }
}

private fun PreferenceCondition.validate(byKey: Map<String?, SourcePreference<*>>) {
    when (this) {
        is PreferenceCondition.BooleanValue -> requireTypedTarget<SourcePreference.Switch>(byKey, key)
        is PreferenceCondition.StringValue -> {
            val target = requireTarget(byKey, key)
            require(target is SourcePreference.Text || target is SourcePreference.Secret || target is SourcePreference.Select) {
                "Preference dependency type does not match $key"
            }
        }
        is PreferenceCondition.StringSetContains -> requireTypedTarget<SourcePreference.MultiSelect>(byKey, key)
        is PreferenceCondition.LongValue -> requireTypedTarget<SourcePreference.Number>(byKey, key)
        is PreferenceCondition.IsNotBlank -> {
            val target = requireTarget(byKey, key)
            require(target is SourcePreference.Text || target is SourcePreference.Secret || target is SourcePreference.Select) {
                "Preference dependency type does not match $key"
            }
        }
        is PreferenceCondition.All -> conditions.forEach { it.validate(byKey) }
        is PreferenceCondition.Any -> conditions.forEach { it.validate(byKey) }
        is PreferenceCondition.Not -> condition.validate(byKey)
    }
}

private inline fun <reified T : SourcePreference<*>> requireTypedTarget(
    byKey: Map<String?, SourcePreference<*>>,
    key: String,
): T {
    val target = requireTarget(byKey, key)
    require(target is T) { "Preference dependency type does not match $key" }
    return target
}

private fun requireTarget(byKey: Map<String?, SourcePreference<*>>, key: String): SourcePreference<*> =
    requireNotNull(byKey[key]) { "Unknown preference dependency: $key" }

private fun PreferenceCondition.referencedKeys(): Set<String> = when (this) {
    is PreferenceCondition.BooleanValue -> setOf(key)
    is PreferenceCondition.StringValue -> setOf(key)
    is PreferenceCondition.StringSetContains -> setOf(key)
    is PreferenceCondition.LongValue -> setOf(key)
    is PreferenceCondition.IsNotBlank -> setOf(key)
    is PreferenceCondition.All -> conditions.flatMapTo(mutableSetOf()) { it.referencedKeys() }
    is PreferenceCondition.Any -> conditions.flatMapTo(mutableSetOf()) { it.referencedKeys() }
    is PreferenceCondition.Not -> condition.referencedKeys()
}
