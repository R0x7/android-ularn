package com.ularn.android.game

/**
 * Wild Edition: game events from the C side (wild_event() in compat/wild.c).
 *
 * The game appends {type, a, b, c} to a ring buffer in shared memory and bumps
 * a sequence number. [poll] reads everything since the last call and hands
 * each event to the listeners, on the main thread. Call it whenever the game
 * reports a change (GameSession.onChanged, which runs on the notify thread);
 * a fresh game or a lost race just drops events, which is fine - they only
 * drive sound, toasts and the like.
 */
class GameEvents(private val state: () -> GameState?) {
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    data class Event(val type: Int, val a: Int, val b: Int, val c: Int)

    fun interface Listener { fun onEvent(e: Event) }

    private val listeners = mutableListOf<Listener>()
    private var lastSeq = -1

    fun addListener(l: Listener) { listeners += l }
    fun removeListener(l: Listener) { listeners -= l }

    /** Forget what was consumed: the next poll starts from "now" (new game). */
    fun reset() { lastSeq = -1 }

    /** Listeners always run on the main thread: a poll from the notify thread is re-posted. */
    fun poll() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) { main.post { poll() }; return }
        val s = state() ?: return
        val seq = s.liveEventSeq()
        val n = s.eventSlots
        if (lastSeq < 0 || seq < lastSeq) { lastSeq = seq; return }
        if (seq == lastSeq) return
        // Too far behind: only the last ring's worth is still intact.
        var from = lastSeq
        if (seq - from > n) from = seq - n
        for (i in from until seq) {
            val e = Event(s.liveEvent(i, 0), s.liveEvent(i, 1), s.liveEvent(i, 2), s.liveEvent(i, 3))
            for (l in listeners.toList()) l.onEvent(e)
        }
        lastSeq = seq
    }

    companion object {
        // US_EV_* in shared_state.h
        const val KILL = 1; const val LEVEL_UP = 2; const val NEW_LEVEL = 3; const val TAKE = 4
        const val PLAYER_HIT = 5; const val GOLD = 6; const val SPELL = 7; const val DIED = 8; const val WON = 9
        const val WILD_RADIO = 100; const val WILD_CARD = 110; const val WILD_VENDING = 120; const val WILD_HAT = 130
        const val WILD_COIN = 140; const val WILD_GLITCH = 150; const val WILD_FISHING = 160; const val WILD_PET = 170
        const val WILD_PERK = 180; const val WILD_WEATHER = 190
    }
}
