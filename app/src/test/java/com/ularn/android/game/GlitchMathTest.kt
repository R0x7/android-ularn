package com.ularn.android.game

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GlitchMathTest {

    @Test fun glitchTilesStayInTheReservedRange() {
        val seen = HashSet<Int>()
        for (frame in listOf(0L, 1L, 7L, 123456789L, Long.MAX_VALUE, -5L))
            for (x in -3..70) for (y in -3..20) {
                val t = GlitchMath.glitchTile(frame, x, y)
                assertTrue("tile $t", t in 408..415)
                seen += t
            }
        assertEquals("all eight corruption tiles get used", 8, seen.size)
    }

    @Test fun overlayCellsAreDistinctAndVisible() {
        for (frame in 0L until 500L) {
            val c = GlitchMath.overlayCells(frame, 10, 2, 30, 14)
            val n = c.size / 2
            assertTrue("count $n", n in 3..6)
            val set = HashSet<Pair<Int, Int>>()
            for (i in 0 until n) {
                assertTrue(c[2 * i] in 10..30)
                assertTrue(c[2 * i + 1] in 2..14)
                set += c[2 * i] to c[2 * i + 1]
            }
            assertEquals(n, set.size)
        }
    }

    @Test fun overlayCellsHandleTinyAndEmptyViews() {
        assertEquals(0, GlitchMath.overlayCells(3, 5, 5, 4, 5).size)
        val one = GlitchMath.overlayCells(3, 5, 5, 5, 5)
        assertArrayEquals(intArrayOf(5, 5), one)
        val two = GlitchMath.overlayCells(9, 0, 0, 1, 0)
        assertEquals(4, two.size)
    }

    @Test fun overlayCellsAreStableForAFrame() {
        assertArrayEquals(GlitchMath.overlayCells(42, 0, 0, 66, 16), GlitchMath.overlayCells(42, 0, 0, 66, 16))
    }

    @Test fun rowShiftIsBoundedAndSometimesNonZero() {
        var moved = 0
        for (frame in 0L until 200L) for (row in 0 until 17) {
            val d = GlitchMath.rowShift(frame, row, 4)
            assertTrue(d == 0 || abs(d) == 4)
            if (d != 0) moved++
        }
        assertTrue("some rows slip", moved in 200..1400)
        assertEquals(0, GlitchMath.rowShift(1, 1, 0))
        assertEquals(4, GlitchMath.shiftPx(32f))
        assertEquals(1, GlitchMath.shiftPx(3f))
    }

    @Test fun hueDegreesWrap() {
        for (turn in -400..400) assertTrue(GlitchMath.hueDegrees(turn) in 0f..359f)
        assertEquals(37f, GlitchMath.hueDegrees(1))
        assertEquals((120 * 37 % 360).toFloat(), GlitchMath.hueDegrees(120))
    }

    @Test fun hueRotationKeepsGreyAndIsIdentityAtZero() {
        val id = GlitchMath.hueRotation(0f)
        val expect = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        for (i in expect.indices) assertEquals(expect[i], id[i], 1e-3f)
        for (deg in listOf(37f, 90f, 200f, 359f)) {
            val m = GlitchMath.hueRotation(deg)
            assertEquals(20, m.size)
            for (row in 0 until 3) assertEquals(1f, m[row * 5] + m[row * 5 + 1] + m[row * 5 + 2], 1e-4f)
        }
    }

    @Test fun corruptKeepsLengthSpacesAndMostText() {
        val line = "Lv 12 Valiant Warrior  ·  Exp 123456  ·  Level 7".repeat(4)
        var changed = 0
        var total = 0
        for (frame in 0L until 50L) {
            val out = GlitchMath.corrupt(line, frame)
            assertEquals(line.length, out.length)
            for (i in line.indices) {
                if (line[i] == ' ') assertEquals(' ', out[i])
                if (out[i] != line[i]) { changed++; assertTrue(out[i] in GlitchMath.GLYPHS) }
                if (line[i] != ' ') total++
            }
        }
        val rate = changed.toDouble() / total
        assertTrue("rate $rate", rate in 0.02..0.09)
        assertEquals("", GlitchMath.corrupt("", 1))
    }
}
