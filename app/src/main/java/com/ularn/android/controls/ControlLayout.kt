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

object DefaultLayouts {

    fun portrait(): ControlLayout = ControlLayout(
        mutableListOf(
            ControlSpec("dpad", ControlType.DPAD, "", cx = 0.27f, cy = 0.785f, widthDp = 170f, heightDp = 170f, repeat = true),
            // right-hand action cluster
            btn("esc", "Esc", "\\e", 0.60f, 0.645f),
            btn("ret", "⏎", "\\n", 0.76f, 0.645f),
            btn("spc", "Spc", "\\s", 0.91f, 0.645f),
            btn("inv", "Inv", "i", 0.60f, 0.735f),
            btn("cast", "Cast", "c", 0.76f, 0.735f),
            btn("quaff", "Quaff", "q", 0.91f, 0.735f),
            btn("read", "Read", "r", 0.60f, 0.825f),
            btn("wield", "Wield", "w", 0.76f, 0.825f),
            btn("wear", "Wear", "W", 0.91f, 0.825f),
            btn("drop", "Drop", "d", 0.60f, 0.915f),
            btn("eat", "Eat", "e", 0.76f, 0.915f),
            ControlSpec("run", ControlType.BUTTON, "Run", action = ButtonAction.RUN, cx = 0.91f, cy = 0.915f, widthDp = 52f, heightDp = 46f),
            // strip just below the map
            small("kbd", "⌨", ButtonAction.KEYBOARD, 0.08f, 0.565f),
            btn("help", "?", "?", 0.20f, 0.565f, 44f, 38f),
            btn("disc", "Known", "\\\\", 0.34f, 0.565f, 56f, 38f),
            btn("rest", "Rest", ".", 0.48f, 0.565f, 52f, 38f),
            btn("save", "Save", "S", 0.62f, 0.565f, 52f, 38f),
            btn("tele", "Z", "Z", 0.75f, 0.565f, 44f, 38f),
            small("menu", "☰", ButtonAction.MENU, 0.92f, 0.565f),
        )
    )

    fun landscape(): ControlLayout = ControlLayout(
        mutableListOf(
            ControlSpec("dpad", ControlType.DPAD, "", cx = 0.105f, cy = 0.66f, widthDp = 170f, heightDp = 170f, repeat = true),
            ControlSpec("run", ControlType.BUTTON, "Run", action = ButtonAction.RUN, cx = 0.105f, cy = 0.20f, widthDp = 60f, heightDp = 44f),
            small("kbd", "⌨", ButtonAction.KEYBOARD, 0.045f, 0.07f),
            small("menu", "☰", ButtonAction.MENU, 0.955f, 0.07f),
            btn("esc", "Esc", "\\e", 0.905f, 0.22f, 46f, 44f),
            btn("ret", "⏎", "\\n", 0.97f, 0.22f, 46f, 44f),
            btn("spc", "Spc", "\\s", 0.938f, 0.35f, 92f, 40f),
            btn("inv", "Inv", "i", 0.905f, 0.48f, 46f, 44f),
            btn("cast", "Cast", "c", 0.97f, 0.48f, 46f, 44f),
            btn("quaff", "Quaff", "q", 0.905f, 0.61f, 46f, 44f),
            btn("read", "Read", "r", 0.97f, 0.61f, 46f, 44f),
            btn("wield", "Wield", "w", 0.905f, 0.74f, 46f, 44f),
            btn("wear", "Wear", "W", 0.97f, 0.74f, 46f, 44f),
            btn("drop", "Drop", "d", 0.905f, 0.87f, 46f, 44f),
            btn("eat", "Eat", "e", 0.97f, 0.87f, 46f, 44f),
        ),
        opacity = 0.45f,
    )

    private fun btn(id: String, label: String, keys: String, cx: Float, cy: Float, w: Float = 54f, h: Float = 46f) =
        ControlSpec(id, ControlType.BUTTON, label, keys, ButtonAction.KEYS, cx, cy, w, h)

    private fun small(id: String, label: String, action: ButtonAction, cx: Float, cy: Float) =
        ControlSpec(id, ControlType.BUTTON, label, "", action, cx, cy, 44f, 40f, shape = ButtonShape.CIRCLE)
}
