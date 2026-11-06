package com.ularn.android.a11y

import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import com.ularn.android.controls.ButtonAction
import com.ularn.android.controls.ControlSpec
import com.ularn.android.controls.ControlType
import com.ularn.android.controls.ControlsOverlay
import com.ularn.android.controls.DpadMath
import com.ularn.android.controls.KeySequence
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * TalkBack support for [ControlsOverlay], which draws its buttons itself:
 * each button, and each direction of each D-pad, is exposed as a virtual
 * view with a spoken label ("Quaff potion", "Move north"). Explore-by-touch
 * hovers are mapped to them, and double-tap (ACTION_CLICK) presses them.
 *
 * Virtual view ids: control index * 16 + part, where part is the D-pad
 * zone (0..8, row-major from north-west, 4 = centre) or [BUTTON_PART].
 */
@Suppress("DEPRECATION") // AccessibilityNodeInfo.obtain / setBoundsInParent: needed for API 24..29
class ControlsAccessibility(private val view: ControlsOverlay) : AccessibilityNodeProvider() {

    private class Item(val id: Int, val spec: ControlSpec, val part: Int, val bounds: RectF, val label: String)

    private var focusedId = INVALID
    private var hoveredId = INVALID
    private val rect = RectF()

    private val manager get() = view.context.getSystemService(AccessibilityManager::class.java)
    val isTouchExplorationOn get() = manager?.isTouchExplorationEnabled == true

    /** The current, pressable items, topmost (last drawn) last. */
    private fun items(): List<Item> {
        if (view.editMode) return emptyList()
        val out = ArrayList<Item>()
        view.layout.controls.forEachIndexed { index, c ->
            view.rectFor(c, rect)
            when (c.type) {
                ControlType.BUTTON -> out += Item(index * 16 + BUTTON_PART, c, BUTTON_PART, RectF(rect), buttonLabel(c))
                ControlType.DPAD -> {
                    val cx = rect.centerX(); val cy = rect.centerY()
                    val r = minOf(rect.width(), rect.height()) / 2
                    for (zone in 0..8) {
                        val keys = c.dpadKeys.getOrElse(zone) { "" }
                        if (keys.isEmpty()) continue
                        if (!c.eightWay && zone in intArrayOf(0, 2, 6, 8)) continue
                        val b = if (zone == 4) {
                            val s = r * 0.3f
                            RectF(cx - s, cy - s, cx + s, cy + s)
                        } else {
                            val deg = ZONE_ANGLE.getValue(zone)
                            val rad = Math.toRadians(deg)
                            val px = cx + cos(rad).toFloat() * r * 0.62f
                            val py = cy - sin(rad).toFloat() * r * 0.62f
                            val s = r * 0.3f
                            RectF(px - s, py - s, px + s, py + s)
                        }
                        out += Item(index * 16 + zone, c, zone, b, dpadLabel(c, zone))
                    }
                }
            }
        }
        return out
    }

    private fun find(id: Int) = items().firstOrNull { it.id == id }

    /** Which item a point is over (same hit rules as touches). */
    private fun itemAt(x: Float, y: Float): Item? {
        val all = items()
        for (i in view.layout.controls.indices.reversed()) {
            val c = view.layout.controls[i]
            view.rectFor(c, rect)
            if (c.type == ControlType.DPAD) {
                val r = minOf(rect.width(), rect.height()) / 2
                if (hypot(x - rect.centerX(), y - rect.centerY()) > r) continue
                val zone = DpadMath.zoneFor(x - rect.centerX(), y - rect.centerY(), r, c.eightWay)
                return all.firstOrNull { it.id == i * 16 + zone }
            } else if (rect.contains(x, y)) {
                return all.firstOrNull { it.id == i * 16 + BUTTON_PART }
            }
        }
        return null
    }

    // ---------------------------------------------------------------- labels

    private fun buttonLabel(c: ControlSpec): String = when (c.action) {
        ButtonAction.KEYBOARD -> "Show or hide keyboard"
        ButtonAction.MENU -> "Menu"
        ButtonAction.EDIT -> "Edit controls"
        ButtonAction.RUN -> "Run modifier"
        ButtonAction.CAST -> "Cast a spell"
        ButtonAction.PHOTO -> "Take a photo"
        ButtonAction.CONTEXT -> view.contextUi.let { ui ->
            val what = ui.label ?: c.label.ifBlank { "Action" }
            if (ui.enabled) what else "$what, unavailable"
        }
        ButtonAction.KEYS -> describeKeys(c.keys, run = false) ?: c.label.takeIf { it.isNotBlank() }?.let { spokenLabel(it) }
            ?: KeySequence.describe(KeySequence.parse(c.keys)).ifEmpty { "Button" }
    }

    private fun dpadLabel(c: ControlSpec, zone: Int): String {
        val keys = c.dpadKeys.getOrElse(zone) { "" }
        val run = zone != 4 && view.isRunModifierOn
        val k = if (run) DpadMath.runVariant(keys) else keys
        return describeKeys(k, run) ?: KeySequence.describe(KeySequence.parse(k))
    }

    private fun spokenLabel(label: String) = when (label) {
        "⌨" -> "Keyboard"
        "☰" -> "Menu"
        "?" -> "Help"
        else -> label
    }

    /** Spoken name for what a key sequence does in Ularn, or null if unknown. */
    private fun describeKeys(notation: String, run: Boolean): String? {
        val b = KeySequence.parse(notation)
        if (b.size != 1) return null
        val ch = (b[0].toInt() and 0xff).toChar()
        MOVES[ch.lowercaseChar()]?.let { dir ->
            return if (ch.isUpperCase() || run) "Run $dir" else "Move $dir"
        }
        return COMMANDS[ch]
    }

    // ----------------------------------------------------------- the provider

    override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? {
        if (virtualViewId == View.NO_ID) {
            val info = AccessibilityNodeInfo.obtain(view)
            view.onInitializeAccessibilityNodeInfo(info)
            for (it in items()) info.addChild(view, it.id)
            info.className = View::class.java.name
            info.contentDescription = "Game controls"
            return info
        }
        val item = find(virtualViewId) ?: return null
        val info = AccessibilityNodeInfo.obtain(view, virtualViewId)
        info.setSource(view, virtualViewId)
        info.setParent(view)
        info.packageName = view.context.packageName
        info.className = android.widget.Button::class.java.name
        info.contentDescription = item.label
        if (item.spec.action == ButtonAction.RUN && item.part == BUTTON_PART) {
            val state = if (view.isRunModifierOn) "on" else "off"
            if (Build.VERSION.SDK_INT >= 30) info.stateDescription = state
            else info.contentDescription = "${item.label}, $state"
        }
        info.isEnabled = true
        info.isClickable = true
        info.isFocusable = true
        info.isVisibleToUser = true
        info.isAccessibilityFocused = focusedId == virtualViewId
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
        info.addAction(
            if (focusedId == virtualViewId) AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS
            else AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS
        )
        val parentBounds = Rect()
        item.bounds.roundOut(parentBounds)
        parentBounds.intersect(0, 0, view.width, view.height)
        info.setBoundsInParent(parentBounds)
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val screen = Rect(parentBounds)
        screen.offset(loc[0], loc[1])
        info.setBoundsInScreen(screen)
        return info
    }

    override fun performAction(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
        if (virtualViewId == View.NO_ID) return view.performAccessibilityAction(action, arguments)
        val item = find(virtualViewId) ?: return false
        return when (action) {
            AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> {
                if (focusedId == virtualViewId) return false
                val old = focusedId
                focusedId = virtualViewId
                if (old != INVALID) send(old, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                send(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                true
            }
            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> {
                if (focusedId != virtualViewId) return false
                focusedId = INVALID
                send(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                true
            }
            AccessibilityNodeInfo.ACTION_CLICK -> {
                view.a11yActivate(item.spec, if (item.part == BUTTON_PART) -1 else item.part)
                send(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
                true
            }
            else -> false
        }
    }

    private fun send(virtualViewId: Int, type: Int) {
        val parent = view.parent ?: return
        if (manager?.isEnabled != true) return
        val e = AccessibilityEvent.obtain(type)
        e.packageName = view.context.packageName
        e.className = android.widget.Button::class.java.name
        e.setSource(view, virtualViewId)
        find(virtualViewId)?.let { e.contentDescription = it.label }
        parent.requestSendAccessibilityEvent(view, e)
    }

    /** From View.dispatchHoverEvent: explore-by-touch over the controls. */
    fun onHover(e: MotionEvent): Boolean {
        if (!isTouchExplorationOn) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                val id = itemAt(e.x, e.y)?.id ?: INVALID
                setHovered(id)
                return id != INVALID
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                val had = hoveredId != INVALID
                setHovered(INVALID)
                return had
            }
        }
        return false
    }

    private fun setHovered(id: Int) {
        if (id == hoveredId) return
        val old = hoveredId
        hoveredId = id
        if (id != INVALID) send(id, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
        if (old != INVALID) send(old, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
    }

    /** The controls changed (new layout, edit mode, run modifier...). */
    fun invalidateAll(clearFocus: Boolean = true) {
        if (clearFocus) { focusedId = INVALID; hoveredId = INVALID }
        val parent = view.parent ?: return
        if (manager?.isEnabled != true) return
        val e = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        e.contentChangeTypes = AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
        e.setSource(view)
        e.packageName = view.context.packageName
        parent.requestSendAccessibilityEvent(view, e)
    }

    companion object {
        private const val INVALID = Int.MIN_VALUE
        private const val BUTTON_PART = 15

        /** D-pad zone -> angle in degrees (0 = east, counter-clockwise). */
        private val ZONE_ANGLE = mapOf(5 to 0.0, 2 to 45.0, 1 to 90.0, 0 to 135.0, 3 to 180.0, 6 to 225.0, 7 to 270.0, 8 to 315.0)

        private val MOVES = mapOf(
            'k' to "north", 'j' to "south", 'h' to "west", 'l' to "east",
            'y' to "north-west", 'u' to "north-east", 'b' to "south-west", 'n' to "south-east",
        )

        /** Ularn 1.6 commands (see the README's key list). */
        private val COMMANDS = mapOf(
            '.' to "Rest", 'i' to "Inventory", 'c' to "Cast spell", 'q' to "Quaff potion",
            'r' to "Read scroll", 'w' to "Wield weapon", 'W' to "Wear armor", 'T' to "Take off armor",
            'd' to "Drop", 'e' to "Eat cookie", '\\' to "List known spells and items",
            'o' to "Open door", 'C' to "Close door", 'O' to "Open chest", 'Z' to "Teleport",
            '^' to "Identify trap", 'g' to "Pack weight", 'P' to "Show taxes", 'S' to "Save game",
            '?' to "Help", ',' to "List known spells and items", 'D' to "List known spells and items",
            'v' to "Version", 'Q' to "Quit game",
            '\u001b' to "Escape", '\r' to "Enter", ' ' to "Space", '\u007f' to "Backspace",
        )
    }
}
