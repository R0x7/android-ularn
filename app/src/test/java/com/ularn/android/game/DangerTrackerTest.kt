package com.ularn.android.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DangerTrackerTest {

    private val crossed = DangerTracker.CROSSED_BELOW
    private val big = DangerTracker.BIG_HIT

    @Test fun firesOnceUntilRecovered() {
        val t = DangerTracker()
        assertEquals(0, t.update(100, 100))
        assertEquals(0, t.update(30, 100) and crossed)
        assertEquals(crossed, t.update(24, 100) and crossed)
        assertTrue(t.inDanger)
        assertEquals(0, t.update(20, 100) and crossed)
        assertEquals(0, t.update(24, 100) and crossed)
        assertEquals(0, t.update(10, 100) and crossed)
        // recover to the threshold, then fall again
        assertEquals(0, t.update(25, 100))
        assertFalse(t.inDanger)
        assertEquals(crossed, t.update(24, 100) and crossed)
    }

    @Test fun noCueOnFirstReadingOrBeforeStatus() {
        val t = DangerTracker()
        assertEquals(0, t.update(0, 0))
        assertFalse(t.inDanger)
        assertEquals(0, t.update(5, 100))  // resumed already hurt
        assertTrue(t.inDanger)
        assertEquals(0, t.update(4, 100))
        // new game process: struct zeroed, then fresh readings
        assertEquals(0, t.update(0, 0))
        assertFalse(t.inDanger)
        assertEquals(0, t.update(100, 100))
    }

    @Test fun bigHit() {
        val t = DangerTracker()
        t.update(100, 100)
        assertEquals(0, t.update(81, 100) and big)
        assertEquals(big, t.update(61, 100) and big)
        assertEquals(0, t.update(100, 100) and big)
        val ev = t.update(10, 100)
        assertEquals(big or crossed, ev)
    }

    @Test fun thresholdIsConfigurable() {
        val t = DangerTracker().apply { thresholdPct = 50 }
        t.update(100, 100)
        assertEquals(crossed, t.update(49, 100) and crossed)
    }
}
