package com.ularn.android.audio

/**
 * Chooses Dungeon Radio's track from where the player is. Pure logic (no
 * Android), fed a [Scene] snapshot by Radio.kt; unit-tested in TrackPickerTest.
 */
class TrackPicker {
    /** What the screen shows right now, boiled down to what the music cares about. */
    data class Scene(
        /** Dungeon level index: 0 town, 1..15 dungeon, 16..20 volcano. */
        val dlevel: Int,
        /** A full text page (store, inventory, help...) instead of the map. */
        val textPage: Boolean = false,
        /** The text page is a store's ("Welcome to the Ularn Thrift Shoppe" ...). */
        val shopText: Boolean = false,
        /** The text page is the bank's. */
        val bankText: Boolean = false,
        /** A demon lord (tiles 56..63) is on the map. */
        val bossVisible: Boolean = false,
        /** GameState.END_*: 0 none, 1 died, 2 won, 3 quit. */
        val endState: Int = 0,
    )

    private var inShop = false
    private var inBank = false
    private var fishingUntil = 0L

    /** A fishing event (US_EV_WILD_FISHING) arrived at [now] ms: surf's up for a while. */
    fun fishing(now: Long) { fishingUntil = now + FISHING_MS }

    /**
     * The track for [s] at time [now] (ms), given the unlocked cassette
     * tracks (indices into [Songs.BONUS]); null = silence.
     */
    fun pick(s: Scene, unlocked: Set<Int>, now: Long): Song? {
        // A store spreads over several pages and not all of them repeat its
        // name, so once a store page is seen it stays "the shop" until the
        // map comes back.
        if (!s.textPage) { inShop = false; inBank = false }
        else {
            if (s.bankText) inBank = true
            else if (s.shopText) inShop = true
        }
        return when {
            s.endState == END_DIED -> Songs.GAME_OVER
            s.endState == END_WON -> Songs.VICTORY
            s.endState != 0 -> null
            s.bossVisible && Songs.BONUS_BOSS in unlocked -> Songs.BOSS
            now < fishingUntil && Songs.BONUS_SURF in unlocked -> Songs.SURF
            inBank && Songs.BONUS_ELEVATOR in unlocked -> Songs.ELEVATOR
            inBank || inShop -> Songs.SHOP
            else -> forLevel(s.dlevel)
        }
    }

    companion object {
        const val FISHING_MS = 20_000L
        const val END_DIED = 1
        const val END_WON = 2

        fun forLevel(dlevel: Int): Song = when {
            dlevel <= 0 -> Songs.TOWN
            dlevel <= 4 -> Songs.SHALLOWS
            dlevel <= 15 -> Songs.DEPTHS
            else -> Songs.VOLCANO
        }

        /** Phrases that only a store's text page shows (see ularn/store.c). */
        val SHOP_WORDS = listOf("Thrift Shoppe", "College of Ularn", "Trading Post", "Revenue Service", "McDope")
        const val BANK_WORDS = "Bank of Ularn"
    }
}
