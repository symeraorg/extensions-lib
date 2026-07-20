package org.symera.source.model

import org.junit.Assert.assertThrows
import org.junit.Test

class SourcePreferenceTest {
    @Test
    fun validatesTextDefaults() {
        val preferences = listOf(
            SourcePreference.Text(
                key = "domain",
                title = "Domain",
                defaultValue = "invalid",
                validation = TextValidation(pattern = "https://.*"),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) { preferences.validatePreferenceSchema() }
    }

    @Test
    fun validatesDependencyTypesAndCycles() {
        val wrongType = listOf(
            SourcePreference.Text("text", "Text"),
            SourcePreference.Switch(
                key = "switch",
                title = "Switch",
                enabledWhen = PreferenceCondition.BooleanValue("text", true),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) { wrongType.validatePreferenceSchema() }

        val cycle = listOf(
            SourcePreference.Switch("one", "One", enabledWhen = PreferenceCondition.BooleanValue("two", true)),
            SourcePreference.Switch("two", "Two", enabledWhen = PreferenceCondition.BooleanValue("one", true)),
        )
        assertThrows(IllegalArgumentException::class.java) { cycle.validatePreferenceSchema() }
    }

    @Test
    fun supportsSecretAndCompositeTypedConditions() {
        val preferences = listOf(
            SourcePreference.Secret("token", "Token"),
            SourcePreference.MultiSelect(
                "features",
                "Features",
                values = listOf(SourcePreference.Option("premium")),
            ),
            SourcePreference.Number("retries", "Retries", defaultValue = 1, minimum = 0),
            SourcePreference.Text(
                "endpoint",
                "Endpoint",
                enabledWhen = PreferenceCondition.All(
                    listOf(
                        PreferenceCondition.IsNotBlank("token"),
                        PreferenceCondition.StringSetContains("features", "premium"),
                        PreferenceCondition.LongValue("retries", NumberOperator.GREATER_THAN, 0),
                    ),
                ),
            ),
        )

        preferences.validatePreferenceSchema()
    }
}
