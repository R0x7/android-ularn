package com.ularn.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DifficultyTest {
    @Test fun normalDifficultyPassesNoArgs() {
        assertEquals(emptyList<String>(), Settings.difficultyArgs(0))
    }

    @Test fun higherDifficultyPassesDashD() {
        assertEquals(listOf("-d", "3"), Settings.difficultyArgs(3))
        assertEquals(listOf("-d", "${Settings.MAX_DIFFICULTY}"), Settings.difficultyArgs(99))
    }

    @Test fun descriptionMirrorsSethard() {
        val d = Settings.describeDifficulty(6)
        assertTrue(d, d.contains("200% hit points"))
        assertTrue(d, d.contains("240% damage"))
        assertTrue(d, d.contains("armour class -6"))
        assertTrue(d, d.contains("63% gold"))
        assertTrue(d, d.contains("54% experience"))
    }
}
