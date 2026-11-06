package com.ularn.android.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherMathTest {

    @Test fun packingRoundTrips() {
        for (hour in 0..23) for (state in 0..3) {
            val p = WeatherMath.pack(hour, state)
            assertEquals(hour, WeatherMath.hourOf(p))
            assertEquals(state, WeatherMath.stateOf(p))
        }
        // the layout compat/wild_weather.c writes: hour | state << 8
        assertEquals(0x0315, WeatherMath.pack(21, WeatherMath.STORM))
        assertEquals(0, WeatherMath.pack(0, WeatherMath.CLEAR))
    }

    @Test fun hourFollowsTheGameClock() {
        assertEquals(0, WeatherMath.hourAt(0))
        assertEquals(0, WeatherMath.hourAt(49))
        assertEquals(1, WeatherMath.hourAt(50))
        assertEquals(23, WeatherMath.hourAt(1199))
        assertEquals(0, WeatherMath.hourAt(1200))       // a day is 1200 turns
        assertEquals(6, WeatherMath.hourAt(1200 + 300))
    }

    @Test fun nightIsNinePmToSixAm() {
        val night = (0..23).filter { WeatherMath.isNight(it) }
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 21, 22, 23), night)
    }

    @Test fun tintNeverDarkensMoreThanFortyPercent() {
        val floor = (0.6f * 255f).toInt()
        for (hour in 0..23) for (m in 0..10) {
            val c = WeatherMath.tint(hour, m / 10f)
            assertEquals("opaque", 0xff, (c ushr 24) and 0xff)
            for (shift in intArrayOf(16, 8, 0)) assertTrue("hour $hour", ((c shr shift) and 0xff) >= floor)
        }
    }

    @Test fun tintIsClearAtNoonAndBlueAtMidnight() {
        assertEquals(0xffffffff.toInt(), WeatherMath.tint(12, 0f))
        val night = WeatherMath.tint(0, 0f)
        val r = (night shr 16) and 0xff; val b = night and 0xff
        assertTrue("blue at night", b > r)
        val dawn = WeatherMath.tint(6, 0f)
        assertTrue("warm at dawn", ((dawn shr 16) and 0xff) > (dawn and 0xff))
    }

    @Test fun precipitationOnlyWhenItRainsOrSnows() {
        for (x in 0..66) for (y in 0..16) assertNull(WeatherMath.precipTile(WeatherMath.CLEAR, x, y, 5, false))
        var rain = 0; var snow = 0
        for (x in 0..66) for (y in 0..16) {
            WeatherMath.precipTile(WeatherMath.RAIN, x, y, 5, false)?.let { assertTrue(it in 400..401); rain++ }
            WeatherMath.precipTile(WeatherMath.SNOW, x, y, 5, false)?.let { assertTrue(it in 402..403); snow++ }
            WeatherMath.precipTile(WeatherMath.STORM, x, y, 5, false)?.let { assertTrue(it in 400..401) }
        }
        val cells = 67 * 17
        assertTrue("some rain: $rain", rain in cells / 6..cells / 2)
        assertTrue("some snow: $snow", snow in cells / 6..cells / 2)
        var reduced = 0
        for (x in 0..66) for (y in 0..16) if (WeatherMath.precipTile(WeatherMath.RAIN, x, y, 5, true) != null) reduced++
        assertTrue("reduced is thinner", reduced < rain)
    }

    @Test fun driftStaysInsideOneCell() {
        for (state in 0..3) for (frame in 0L..200L) {
            val (dx, dy) = WeatherMath.drift(state, 3, 4, frame, 64)
            assertTrue(dy in 0 until 64)
            assertTrue(dx in -64..64)
        }
        assertEquals(0 to 0, WeatherMath.drift(WeatherMath.RAIN, 1, 1, 9, 0))
    }

    @Test fun starsAreSparseAndInRange() {
        var n = 0
        for (x in 0..66) for (y in 0..16) WeatherMath.starTile(x, y, 3)?.let { assertTrue(it in 404..405); n++ }
        assertTrue("stars $n", n in 67 * 17 / 8..67 * 17 / 2)
    }

    @Test fun flashFades() {
        assertEquals(WeatherMath.FLASH_MAX_ALPHA, WeatherMath.flashAlpha(0))
        assertTrue(WeatherMath.flashAlpha(200) in 1 until WeatherMath.FLASH_MAX_ALPHA)
        assertEquals(0, WeatherMath.flashAlpha(WeatherMath.FLASH_MS))
        assertEquals(0, WeatherMath.flashAlpha(-1))
        for (seed in 0L..100L) assertTrue(WeatherMath.randomFlashDelay(seed) in 6000L until 20000L)
        assertFalse(WeatherMath.precipitates(WeatherMath.CLEAR))
    }
}
