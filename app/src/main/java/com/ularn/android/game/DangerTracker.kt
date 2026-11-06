package com.ularn.android.game

/**
 * Low-HP state and its one-shot events, from successive (hp, hpMax) readings.
 * Pure logic (unit-tested); [DangerCues] turns it into pixels and vibration.
 *
 *  - [inDanger]: HP is below [thresholdPct] % of max.
 *  - [CROSSED_BELOW]: HP just fell below the threshold. Fires once, then not
 *    again until HP has been back at or above the threshold.
 *  - [BIG_HIT]: HP dropped by at least [bigHitPct] % of max since the last
 *    reading.
 */
class DangerTracker {
    var thresholdPct = 25
    var bigHitPct = 20

    var inDanger = false
        private set

    /** Fell below the threshold and hasn't recovered yet (the vibration fired). */
    private var latched = false
    private var lastHp = -1

    /** Returns a mask of [CROSSED_BELOW] / [BIG_HIT]. */
    fun update(hp: Int, hpMax: Int): Int {
        if (hpMax <= 0) { reset(); return 0 }
        val below = hp.toLong() * 100 < thresholdPct.toLong() * hpMax
        inDanger = below
        if (lastHp < 0) {
            // First reading of a game (new, or resumed already hurt): show the
            // state, but there was no crossing to announce.
            latched = below
            lastHp = hp
            return 0
        }
        var ev = 0
        if (below) {
            if (!latched) { latched = true; ev = ev or CROSSED_BELOW }
        } else latched = false
        if (bigHitPct > 0 && hp < lastHp && (lastHp - hp).toLong() * 100 >= bigHitPct.toLong() * hpMax) ev = ev or BIG_HIT
        lastHp = hp
        return ev
    }

    fun reset() {
        inDanger = false
        latched = false
        lastHp = -1
    }

    companion object {
        const val CROSSED_BELOW = 1
        const val BIG_HIT = 2
    }
}
