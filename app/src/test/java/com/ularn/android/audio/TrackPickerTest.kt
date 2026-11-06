package com.ularn.android.audio

import com.ularn.android.audio.TrackPicker.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackPickerTest {
    private val none = emptySet<Int>()
    private val all = Songs.BONUS.indices.toSet()

    @Test fun byLevel() {
        val p = TrackPicker()
        assertEquals(Songs.TOWN, p.pick(Scene(0), none, 0))
        assertEquals(Songs.SHALLOWS, p.pick(Scene(1), none, 0))
        assertEquals(Songs.SHALLOWS, p.pick(Scene(4), none, 0))
        assertEquals(Songs.DEPTHS, p.pick(Scene(5), none, 0))
        assertEquals(Songs.DEPTHS, p.pick(Scene(15), none, 0))
        assertEquals(Songs.VOLCANO, p.pick(Scene(16), none, 0))
        assertEquals(Songs.VOLCANO, p.pick(Scene(20), none, 0))
    }

    @Test fun shopStaysUntilTheMapReturns() {
        val p = TrackPicker()
        assertEquals(Songs.TOWN, p.pick(Scene(0, textPage = true), none, 0))   // inventory in town
        assertEquals(Songs.SHOP, p.pick(Scene(0, textPage = true, shopText = true), none, 0))
        assertEquals(Songs.SHOP, p.pick(Scene(0, textPage = true), none, 0))   // next page of the store
        assertEquals(Songs.TOWN, p.pick(Scene(0), none, 0))
    }

    @Test fun bankPlaysElevatorOnceUnlocked() {
        val p = TrackPicker()
        assertEquals(Songs.SHOP, p.pick(Scene(0, textPage = true, bankText = true), none, 0))
        assertEquals(Songs.ELEVATOR, p.pick(Scene(0, textPage = true, bankText = true), all, 0))
        assertEquals(Songs.ELEVATOR, p.pick(Scene(8, textPage = true, bankText = true), all, 0))   // the branch office
    }

    @Test fun bossAndFishingNeedTheirTapes() {
        val p = TrackPicker()
        assertEquals(Songs.DEPTHS, p.pick(Scene(14, bossVisible = true), none, 0))
        assertEquals(Songs.BOSS, p.pick(Scene(14, bossVisible = true), setOf(Songs.BONUS_BOSS), 0))
        p.fishing(1000)
        assertEquals(Songs.TOWN, p.pick(Scene(0), none, 2000))
        assertEquals(Songs.SURF, p.pick(Scene(0), setOf(Songs.BONUS_SURF), 2000))
        assertEquals(Songs.TOWN, p.pick(Scene(0), setOf(Songs.BONUS_SURF), 1000 + TrackPicker.FISHING_MS))
    }

    @Test fun endOfGame() {
        val p = TrackPicker()
        assertEquals(Songs.GAME_OVER, p.pick(Scene(3, endState = 1), all, 0))
        assertEquals(Songs.VICTORY, p.pick(Scene(0, endState = 2), all, 0))
        assertNull(p.pick(Scene(3, endState = 3), all, 0))
    }
}
