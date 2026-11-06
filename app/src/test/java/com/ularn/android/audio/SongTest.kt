package com.ularn.android.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SongTest {

    @Test fun noteNames() {
        assertEquals(60, Notes.midi("c4"))
        assertEquals(69, Notes.midi("A4"))
        assertEquals(61, Notes.midi("c#4"))
        assertEquals(70, Notes.midi("bb4"))
        assertEquals(58, Notes.midi("Bb3"))
        assertEquals(12, Notes.midi("c0"))
        assertEquals(59, Notes.midi("cb4"))
        assertNull(Notes.midi("h4"))
        assertNull(Notes.midi("c"))
        assertNull(Notes.midi("c9"))
        assertNull(Notes.midi("c#x"))
    }

    @Test fun noteFrequencies() {
        assertEquals(440.0, Notes.freq(69), 1e-9)
        assertEquals(880.0, Notes.freq(81), 1e-9)
        assertEquals(220.0, Notes.freq(57), 1e-9)
        assertEquals(261.626, Notes.freq(Notes.midi("c4")!!), 1e-3)
        assertEquals(82.407, Notes.freq(Notes.midi("e2")!!), 1e-3)
    }

    @Test fun parsesNotesRestsDrumsAndLengths() {
        val p = Pattern.parse("c4 d4:3 - r:2 l4 e4 k s:1 h | g#3")
        assertEquals(listOf(
            Step(60, 1), Step(62, 3), Step(Notes.REST, 1), Step(Notes.REST, 2),
            Step(64, 4), Step(Notes.KICK, 4), Step(Notes.SNARE, 1), Step(Notes.HAT, 4), Step(56, 4),
        ), p.steps)
        assertEquals(1 + 3 + 1 + 2 + 4 + 4 + 1 + 4 + 4, p.length)
    }

    @Test fun repeatsAndNesting() {
        assertEquals(listOf(60, 62, 60, 62, 60, 62), Pattern.parse("[c4 d4]3").steps.map { it.note })
        assertEquals(4, Pattern.parse("[c4 d4]").length)   // "]" alone = twice
        assertEquals(listOf(60, 62, 62, 60, 62, 62), Pattern.parse("[c4 [d4]2]2").steps.map { it.note })
        assertEquals(16, Pattern.parse("l2 [c3 g3]4").length)
        // brackets glued to notes
        assertEquals(4, Pattern.parse("[c4 d4]2").steps.size)
    }

    @Test fun rejectsBadPatterns() {
        for (bad in listOf("c4 x4", "c4:0", "c4:z", "[c4", "c4]2", "l0 c4", "[c4]0")) {
            try { Pattern.parse(bad); fail("accepted \"$bad\"") } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun songLength() {
        val s = Song("t", 120, listOf(v(Wave.PULSE50, "l4 c4 d4 e4 f4"), v(Wave.NOISE, "k:4 s:4")))
        assertEquals(16, s.lengthSteps)
        assertEquals(0.125, s.stepSeconds, 1e-9)   // a sixteenth at 120 bpm
        assertEquals(2.0, s.seconds, 1e-9)
    }

    @Test fun everySongIsWellFormed() {
        assertTrue(Songs.ALL.size >= 10)
        for (s in Songs.ALL) {
            assertTrue("${s.name} is empty", s.lengthSteps > 0)
            for (v in s.voices) {
                // Looping voices must line up with the whole song, or the parts drift apart.
                if (s.loop) assertEquals("${s.name}: voice length ${v.pattern.length} vs ${s.lengthSteps}",
                    0, s.lengthSteps % v.pattern.length)
            }
            if (s.loop) assertEquals("${s.name} is not whole bars", 0, s.lengthSteps % 16)
            assertTrue("${s.name} too long", s.seconds < 60)
        }
        // Stingers are short and don't loop.
        for (s in listOf(Songs.GAME_OVER, Songs.VICTORY)) {
            assertTrue(!s.loop)
            assertTrue(s.seconds < 5)
        }
    }

    @Test fun bonusTracksMatchTheCSide() {
        // compat/wild_radio.c: 0 Elevator Music, 1 Surf's Up, 2 Boss Fight.
        assertEquals(listOf("Elevator Music", "Surf's Up", "Boss Fight"), Songs.BONUS.map { it.name })
    }
}
