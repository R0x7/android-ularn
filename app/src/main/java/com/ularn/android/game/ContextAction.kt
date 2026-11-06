package com.ularn.android.game

import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

/** What a CONTEXT button shows: [label] null = its own neutral label. */
data class ContextUi(val label: String?, val enabled: Boolean) {
    companion object {
        val NEUTRAL = ContextUi(null, false)
    }
}

/**
 * The context-sensitive action button: works out from the shared state
 * (ctx_* fields, filled by compat/android_context.c whenever the game waits
 * for a command) what the player can do where they stand, and does it.
 *
 * Ularn 1.6.3a has no pick-up / climb / enter commands. Every turn the main
 * loop (ularn.c, `lookforobject()` at the top of the `do` loop) asks about the
 * object underfoot (object.c `lookforobject()`), so most actions are "."
 * (ACTION_WAIT: pass a turn, which asks again) plus the answer to that
 * prompt, sent only once the prompt is really there (see [KeyFollowUp]).
 *
 * Threading: [onGameChanged] may be called from any thread; everything else
 * runs on the main thread.
 */
class ContextAction(
    private val state: () -> GameState?,
    private val running: () -> Boolean,
    private val followUp: KeyFollowUp,
    private val onUi: (ContextUi) -> Unit,
) {
    private class Plan(val label: String, val enabled: Boolean, val first: String = "", val then: List<KeyFollowUp.Step> = emptyList())

    private val main = Handler(Looper.getMainLooper())
    private val posted = AtomicBoolean(false)
    private var ui = ContextUi.NEUTRAL

    /** The game published a change (any thread). Coalesced onto the main thread. */
    fun onGameChanged() {
        if (posted.compareAndSet(false, true)) main.post {
            posted.set(false)
            update()
        }
    }

    /** Re-reads the state; answers a pending prompt; refreshes the button. */
    fun update() {
        followUp.check()
        val s = state()
        val next = when {
            s == null || !running() || !s.refresh() || s.displayMode != GameState.DISPLAY_MAP -> ContextUi.NEUTRAL
            // Busy for a moment (thinking, animating): keep what we show, so it doesn't flicker.
            s.inputMode == GameState.INPUT_BUSY -> ui
            else -> plan(s)?.let { ContextUi(it.label, it.enabled && s.inputMode == GameState.INPUT_COMMAND) }
                ?: ContextUi.NEUTRAL
        }
        if (next != ui) { ui = next; onUi(next) }
    }

    /** The button was pressed. Does nothing unless the game waits for a command. */
    fun trigger() {
        val s = state() ?: return
        if (!running() || !s.refresh()) return
        if (s.displayMode != GameState.DISPLAY_MAP || s.inputMode != GameState.INPUT_COMMAND) return
        val p = plan(s) ?: return
        if (!p.enabled || p.first.isEmpty()) return
        followUp.start(p.first, *p.then.toTypedArray())
    }

    // ------------------------------------------------------------------ plan

    private fun plan(s: GameState): Plan? {
        val flags = s.ctxFlags
        val enhanced = flags and CTXF_ENHANCED != 0
        val blocked = flags and CTXF_BLOCKED != 0
        val timeStop = flags and CTXF_TIMESTOP != 0

        // "." re-asks about the object underfoot; then answer with `key`.
        fun look(label: String, key: Char? = null, answers: String? = null) = Plan(
            label, !blocked, ".",
            if (key == null) emptyList()
            else listOf(KeyFollowUp.Step(key.toString(), GameState.INPUT_PROMPT) { a ->
                if (answers != null) a == answers else key in a && 'i' in a
            }),
        )

        when (s.ctxKind) {
            // potions, scrolls, books, items, drugs...: every prompt offers (t) take and (i) ignore
            CTX_TAKE -> return look("Pick up", 't')
            CTX_GOLD -> return look("Gold")
            CTX_STAIRS_UP -> return look("Stairs ▲", 'u', "sudi$ESC")          // ostairs(): "sudi\033"
            CTX_STAIRS_DOWN -> return look("Stairs ▼", 'd', "sudi$ESC")
            CTX_SHAFT_DOWN -> return look("Climb ▼", 'c', "ci$ESC")            // OVOLDOWN: "ci\033"
            CTX_SHAFT_UP -> return look("Climb ▲", 'c', "ci$ESC")              // OVOLUP: "ci\033"
            CTX_ENTER -> return look("Enter", 'g', "gi$ESC")                   // buildings, dungeon entrance: "gi\033"
            CTX_PAD -> return look("Visit", 'c', "ci$ESC")                    // OPAD: "ci\033"
            CTX_CHEST -> return if (enhanced) Plan("Open chest", true, "O")   // ACTION_OPEN_CHEST -> openchest()
                else look("Open chest", 'o', "oti$ESC")                       // ochest(): "oti\033"
            // These open the game's own menu (drink/wash, pray/desecrate, pry/sit, rub/take).
            CTX_FOUNTAIN -> return look("Fountain")
            CTX_ALTAR -> return look("Altar")
            CTX_THRONE -> return look("Throne")
            CTX_LAMP -> return look("Lamp")
            CTX_VENDING -> return look("Vending")                            // Wild Edition: buy / kick
            CTX_COINATM -> return look("ATM")                                // Wild Edition: buy / sell LarnCoin
        }

        // Nothing to do underfoot: a closed door next to us?
        val doors = (0 until 8).filter { s.ctxDoors and (1 shl it) != 0 }
        if (doors.isNotEmpty()) {
            val one = doors.singleOrNull()
            return when {
                timeStop -> Plan("Open door", false)
                enhanced && one != null ->                                    // 'o' asks "In what direction?"
                    Plan("Open " + ARROWS[one], true, "o", listOf(KeyFollowUp.Step(DIR_KEYS[one].toString(), GameState.INPUT_DIR)))
                enhanced -> Plan("Open door", true, "o")                      // pick the door with a swipe
                // Classic interface: walk into the door, then answer "(o) try to open it".
                one != null -> Plan("Open " + ARROWS[one], true, DIR_KEYS[one].toString(),
                    listOf(KeyFollowUp.Step("o", GameState.INPUT_PROMPT) { it == "oi$ESC" }))
                else -> Plan("Doors", false)
            }
        }

        if (s.ctxKind == CTX_INSPECT) return Plan(shortName(s.ctxName), false)
        return null
    }

    /** "a great marble statue" -> "Statue". */
    private fun shortName(name: String): String =
        name.trim().substringAfterLast(' ').replaceFirstChar { it.uppercaseChar() }.ifEmpty { "Act" }

    companion object {
        // shared_state.h US_CTX_* / US_CTXF_*
        const val CTX_NONE = 0
        const val CTX_INSPECT = 1
        const val CTX_TAKE = 2
        const val CTX_GOLD = 3
        const val CTX_STAIRS_UP = 4
        const val CTX_STAIRS_DOWN = 5
        const val CTX_SHAFT_DOWN = 6
        const val CTX_SHAFT_UP = 7
        const val CTX_ENTER = 8
        const val CTX_PAD = 9
        const val CTX_CHEST = 10
        const val CTX_FOUNTAIN = 11
        const val CTX_ALTAR = 12
        const val CTX_THRONE = 13
        const val CTX_LAMP = 14
        const val CTX_VENDING = 15
        const val CTX_COINATM = 16

        const val CTXF_ENHANCED = 1
        const val CTXF_BLOCKED = 2
        const val CTXF_TIMESTOP = 4

        /** Bit i of ctx_doors is the neighbour in direction DIR_KEYS[i]. */
        const val DIR_KEYS = "hjklyubn"
        private val ARROWS = listOf("←", "↓", "↑", "→", "↖", "↗", "↙", "↘")
        private const val ESC = '\u001b'
    }
}
