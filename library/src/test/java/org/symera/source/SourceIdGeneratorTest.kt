package org.symera.source

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceIdGeneratorTest {
    @Test
    fun preservesCaseOfBcp47LanguageForLegacyIdentity() {
        assertEquals(6_481_945_042_803_036_576L, SourceIdGenerator.generate("Source", "pt-BR", 1))
    }
}
