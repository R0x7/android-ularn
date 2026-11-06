package com.ularn.android.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolaroidMathTest {

    @Test fun frameWrapsThePhotoWithAThickChin() {
        val l = PolaroidMath.layout(1000, 600)
        assertEquals(1000, l.photoR - l.photoL)
        assertEquals(600, l.photoB - l.photoT)
        assertEquals(l.photoL, l.photoT)                      // equal side and top borders
        assertEquals(l.frameW - l.photoR, l.photoL)           // and right
        assertTrue("chin ${l.bottom} vs side ${l.side}", l.bottom >= 4 * l.side)
        assertEquals(l.frameH, l.photoB + l.bottom)
        assertEquals(50, l.side)                              // 5% of the longer side
    }

    @Test fun captionAndDateSitInTheChin() {
        for ((w, h) in listOf(1000 to 600, 64 to 64, 2560 to 640, 1 to 1, 0 to 0)) {
            val l = PolaroidMath.layout(w, h)
            assertTrue(l.captionY > l.photoB && l.captionY < l.frameH)
            assertTrue(l.dateY > l.captionY && l.dateY < l.frameH)
            assertTrue(l.dateSize < l.captionSize)
            assertTrue("caption above the date", l.captionY + l.dateSize <= l.dateY + l.captionSize)
            assertTrue(l.side >= PolaroidMath.MIN_SIDE)
        }
    }

    @Test fun captionShrinksToFit() {
        assertEquals(30f, PolaroidMath.fitSize(30f, 400f, 500), 0f)
        assertEquals(15f, PolaroidMath.fitSize(30f, 1000f, 500), 0.001f)
        assertEquals(15f, PolaroidMath.fitSize(30f, 100000f, 500), 0.001f)   // never below half
    }

    @Test fun captionWords() {
        assertEquals("Ann the novice explorer — home, turn 0", PolaroidMath.caption("Ann", "novice explorer", "H", 0))
        assertEquals("Bob the knight — level 7, turn 1234", PolaroidMath.caption(" Bob ", "knight", "7", 1234))
        assertEquals("An adventurer — the volcano, V2, turn 5", PolaroidMath.caption("", "", "V2", 5))
    }

    @Test fun nameComesFromTheOptionsFile() {
        assertEquals("Zed", PolaroidMath.nameFromOpts("# x\nOPTION name:Zed,gender:male,nonap\n"))
        assertEquals("Mary Sue", PolaroidMath.nameFromOpts("OPTION name:Mary Sue\n"))
        assertNull(PolaroidMath.nameFromOpts(null))
        assertNull(PolaroidMath.nameFromOpts("OPTION gender:female"))
    }

    @Test fun oneToThreeStickersOnDifferentCorners() {
        val l = PolaroidMath.layout(1200, 500)
        val counts = HashSet<Int>()
        for (seed in 0L until 300L) {
            val st = PolaroidMath.stickers(l, seed, 181)
            assertTrue(st.size in 1..3)
            counts += st.size
            assertEquals("distinct corners", st.size, st.map { it.cx to it.cy }.toSet().size)
            for (s in st) {
                assertTrue(s.tile == 181 || s.tile in PolaroidMath.STICKER_TILES)
                assertTrue(s.degrees in -15f..15f)
                assertTrue(s.cx in 0..l.frameW && s.cy in 0..l.frameH)
                assertTrue(s.size >= 32)
            }
        }
        assertEquals(setOf(1, 2, 3), counts)
        assertEquals(PolaroidMath.stickers(l, 42, -1), PolaroidMath.stickers(l, 42, -1))
    }

    @Test fun fileNames() {
        assertEquals("ularn-wild-17.png", PolaroidMath.fileName(17))
    }
}
