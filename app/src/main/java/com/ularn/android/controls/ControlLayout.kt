package com.ularn.android.controls

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

enum class ControlType { BUTTON, DPAD }

/** What a button does when pressed. */
enum class ButtonAction(val title: String) {
    KEYS("Send keys"),
    RUN("Run modifier (next D-pad move runs)"),
    KEYBOARD("Show / hide keyboard"),
    MENU("Open app menu"),
    EDIT("Edit controls"),
    CONTEXT("Context action (take, stairs, enter, open…)"),
    CAST("Cast spell (opens the spell list)"),
}

enum class ButtonShape { ROUNDED, CIRCLE }

/**
 * One on-screen control. Position is the centre as a fraction of the overlay
 * (so layouts adapt to different screens); size is in dp (so buttons keep
 * their shape).
 */
data class ControlSpec(
    var id: String,
    var type: ControlType,
    var label: String = "",
    /** Key notation (see [KeySequence]) for BUTTON/KEYS. */
    var keys: String = "",
    var action: ButtonAction = ButtonAction.KEYS,
    var cx: Float = 0.5f,
    var cy: Float = 0.5f,
    var widthDp: Float = 56f,
    var heightDp: Float = 56f,
    /** Auto-repeat while held. */
    var repeat: Boolean = false,
    var shape: ButtonShape = ButtonShape.ROUNDED,
    /**
     * D-pad only: 9 key notations, row-major from top-left:
     * NW, N, NE, W, centre, E, SW, S, SE.  Empty = nothing.
     */
    var dpadKeys: MutableList<String> = DEFAULT_DPAD_KEYS.toMutableList(),
    /** D-pad only: allow diagonals (8-way) or just 4-way. */
    var eightWay: Boolean = true,
) {
    fun deepCopy(): ControlSpec = copy(dpadKeys = dpadKeys.toMutableList())

    companion object {
        val DEFAULT_DPAD_KEYS = listOf("y", "k", "u", "h", ".", "l", "b", "j", "n")
    }
}

/** A full set of controls plus global appearance settings. */
data class ControlLayout(
    val controls: MutableList<ControlSpec> = mutableListOf(),
    var opacity: Float = 0.55f,
    var haptics: Boolean = true,
) {
    fun deepCopy() = ControlLayout(controls.map { it.deepCopy() }.toMutableList(), opacity, haptics)

    fun newId(prefix: String): String {
        var n = controls.size + 1
        while (controls.any { it.id == "$prefix$n" }) n++
        return "$prefix$n"
    }
}

object DpadMath {
    /** Index into [ControlSpec.dpadKeys] (0..8, 4 = centre). */
    fun zoneFor(dx: Float, dy: Float, radius: Float, eightWay: Boolean, deadZone: Float = 0.28f): Int {
        if (hypot(dx, dy) < radius * deadZone) return 4
        if (!eightWay) {
            return if (abs(dx) > abs(dy)) (if (dx > 0) 5 else 3) else (if (dy > 0) 7 else 1)
        }
        // angle 0 = east, counter-clockwise positive (screen y is down, so negate)
        var deg = Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble()))
        if (deg < 0) deg += 360.0
        val sector = (((deg + 22.5) % 360) / 45).toInt() // 0=E,1=NE,2=N,...
        return intArrayOf(5, 2, 1, 0, 3, 6, 7, 8)[sector]
    }

    /** Uppercase run variant of a single-letter move key ("h" -> "H"). */
    fun runVariant(keys: String): String =
        if (keys.length == 1 && keys[0] in 'a'..'z') keys.uppercase() else keys
}

/**
 * Default layouts, computed for the actual screen size (in dp) so every
 * control sits on an 8 dp grid with even gaps. Positions are stored as
 * fractions of the screen, like user-made layouts.
 */
object DefaultLayouts {

    private const val G = 8f      // grid / gap
    private const val M = 12f     // screen margin

    private class Builder(val w: Float, val h: Float) {
        val list = mutableListOf<ControlSpec>()
        fun snap(v: Float) = Math.round(v / G) * G

        fun btn(id: String, label: String, keys: String, cxDp: Float, cyDp: Float, wDp: Float, hDp: Float,
                action: ButtonAction = ButtonAction.KEYS, shape: ButtonShape = ButtonShape.ROUNDED) {
            list += ControlSpec(id, ControlType.BUTTON, label, keys, action, cxDp / w, cyDp / h, wDp, hDp, shape = shape)
        }

        fun dpad(cxDp: Float, cyDp: Float, dia: Float) {
            list += ControlSpec("dpad", ControlType.DPAD, "", cx = cxDp / w, cy = cyDp / h, widthDp = dia, heightDp = dia, repeat = true)
        }

        /** Lays a grid of buttons out from its bottom-right corner. */
        fun grid(items: List<Triple<String, String, String>>, cols: Int, rightDp: Float, bottomDp: Float,
                 bw: Float, bh: Float, actions: Map<String, ButtonAction> = emptyMap()) {
            val rows = (items.size + cols - 1) / cols
            items.forEachIndexed { i, (id, label, keys) ->
                val c = i % cols
                val r = i / cols
                val cx = rightDp - bw / 2 - (cols - 1 - c) * (bw + G)
                val cy = bottomDp - bh / 2 - (rows - 1 - r) * (bh + G)
                btn(id, label, keys, cx, cy, bw, bh, actions[id] ?: ButtonAction.KEYS)
            }
        }

        /** Spreads buttons evenly across [leftDp, rightDp]. */
        fun row(items: List<Triple<String, String, String>>, leftDp: Float, rightDp: Float, cyDp: Float,
                bw: Float, bh: Float, actions: Map<String, ButtonAction> = emptyMap(), round: Set<String> = emptySet()) {
            val n = items.size
            val span = rightDp - leftDp - bw
            items.forEachIndexed { i, (id, label, keys) ->
                val cx = leftDp + bw / 2 + if (n > 1) span * i / (n - 1) else span / 2
                btn(id, label, keys, cx, cyDp, bw, bh, actions[id] ?: ButtonAction.KEYS,
                    if (id in round) ButtonShape.CIRCLE else ButtonShape.ROUNDED)
            }
        }
    }

    private val ACTIONS = listOf(
        Triple("esc", "Esc", "\\e"), Triple("ret", "Enter", "\\n"), Triple("spc", "Space", "\\s"),
        Triple("inv", "Inv", "i"), Triple("cast", "Cast", "c"), Triple("quaff", "Quaff", "q"),
        Triple("read", "Read", "r"), Triple("wield", "Wield", "w"), Triple("wear", "Wear", "W"),
        Triple("drop", "Drop", "d"), Triple("eat", "Eat", "e"), Triple("run", "Run", ""),
    )
    private val UTILITY = listOf(
        Triple("kbd", "⌨", ""), Triple("help", "?", "?"), Triple("disc", "Known", "\\\\"),
        Triple("rest", "Rest", "."), Triple("save", "Save", "S"), Triple("tele", "Tele", "Z"),
        Triple("menu", "☰", ""),
    )
    private val SPECIAL = mapOf("run" to ButtonAction.RUN, "kbd" to ButtonAction.KEYBOARD, "menu" to ButtonAction.MENU,
        "cast" to ButtonAction.CAST)

    /** @param w,h screen size in dp. */
    fun portrait(w: Float, h: Float): ControlLayout {
        val b = Builder(w, h)
        val bw = b.snap(((w - 2 * M - G) * 0.5f - 2 * G) / 3).coerceIn(48f, 72f)   // 3 columns in the right half
        val bh = 48f
        val gridW = 3 * bw + 2 * G
        val gridH = 4 * bh + 3 * G
        val bottom = h - M
        // right: 3x4 action grid
        b.grid(ACTIONS, 3, w - M, bottom, bw, bh, SPECIAL)
        // left: context-action button on top, d-pad below it, as big as the remaining room allows
        val dia = b.snap(minOf(w - 2 * M - gridW - 2 * G, gridH - bh - G, 200f))
        b.btn("ctx", "Act", "", M + dia / 2, bottom - gridH + bh / 2, dia, bh, ButtonAction.CONTEXT)
        b.dpad(M + dia / 2, bottom - (gridH - bh - G) / 2, dia)
        // utility row above both
        val rowCy = bottom - gridH - G - 20f
        val uw = b.snap(((w - 2 * M) - 6 * G) / 7).coerceIn(40f, 60f)
        b.row(UTILITY, M, w - M, rowCy, uw, 40f, SPECIAL, setOf("kbd", "menu"))
        return ControlLayout(b.list)
    }

    fun landscape(w: Float, h: Float): ControlLayout {
        val b = Builder(w, h)
        val bw = 60f
        val bh = b.snap(((h - 2 * M) - 4 * G) / 5).coerceIn(40f, 52f)
        // right: 3x5 grid of actions + utilities
        val items = ACTIONS.filter { it.first != "run" } + UTILITY.filter { it.first in setOf("rest", "save", "disc", "tele") }
        val rows = (items.size + 2) / 3
        val gridH = rows * bh + (rows - 1) * G
        b.grid(items, 3, w - M, h / 2 + gridH / 2, bw, bh, SPECIAL)
        // left: d-pad bottom-left, run above it, small round buttons top-left
        val dia = b.snap(minOf(h - 2 * M - 56f - G, 190f))
        b.dpad(M + dia / 2, h - M - dia / 2, dia)
        b.btn("run", "Run", "", M + 32f, h - M - dia - G - 22f, 64f, 44f, ButtonAction.RUN)
        b.btn("ctx", "Act", "", M + 64f + G + (dia - 64f - G) / 2, h - M - dia - G - 22f, dia - 64f - G, 44f, ButtonAction.CONTEXT)
        b.row(listOf(Triple("kbd", "⌨", ""), Triple("help", "?", "?"), Triple("menu", "☰", "")),
            M, M + dia, M + 20f, 40f, 40f, SPECIAL, setOf("kbd", "menu", "help"))
        return ControlLayout(b.list, opacity = 0.5f)
    }
}
