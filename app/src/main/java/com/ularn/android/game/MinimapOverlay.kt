package com.ularn.android.game

import android.graphics.Canvas
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import kotlin.math.hypot
import kotlin.math.min

/**
 * The round minimap drawn over a corner of [GameView]'s map, centred on the
 * player. Tapping it opens the full-level overview ([LevelOverviewDialog]).
 *
 * It sits in the top-right corner of the map area, and moves to another
 * corner whenever the player is under it (e.g. near the edge of the level,
 * where the camera stops following). It only ever takes touches that start
 * inside its own circle; everything else goes to the map as usual.
 */
class MinimapOverlay(private val view: GameView) {

    var enabled = true
        set(v) { field = v; view.invalidate() }

    private val density = view.resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    val renderer = MinimapRenderer(density)

    /** Screen strips (from the view's left / right edge) that controls cover. */
    private var avoidLeft = 0f
    private var avoidRight = 0f

    fun setAvoid(left: Float, right: Float) {
        if (left == avoidLeft && right == avoidRight) return
        avoidLeft = left; avoidRight = right
        view.invalidate()
    }

    // Where it was drawn in the last frame (for touches).
    private var shown = false
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f
    private var tracking = false
    private val box = RectF()

    /** Call at the start of every frame; [draw] marks it shown again. */
    fun beginFrame() { shown = false }

    /**
     * Draws the minimap inside [mapRect]. (playerSx, playerSy) is the centre
     * of the player's square on screen and [tilePx] the map's tile size.
     */
    fun draw(canvas: Canvas, s: GameState, mapRect: RectF, playerSx: Float, playerSy: Float, tilePx: Float) {
        if (!enabled || !view.running) return
        val left = maxOf(mapRect.left, avoidLeft)
        val right = minOf(mapRect.right, view.width - avoidRight)
        val w = right - left
        val h = mapRect.height()
        val d = min(dp(MAX_DIAMETER_DP), min(w * 0.34f, h * 0.6f))
        if (d < dp(MIN_DIAMETER_DP)) return
        val m = dp(6f)
        val keepClear = tilePx / 2 + dp(2f)

        // Top-right first, then the other corners; skip any that would cover the player.
        val corners = arrayOf(
            floatArrayOf(right - m - d, mapRect.top + m),
            floatArrayOf(left + m, mapRect.top + m),
            floatArrayOf(right - m - d, mapRect.bottom - m - d),
            floatArrayOf(left + m, mapRect.bottom - m - d),
        )
        var chosen: FloatArray? = null
        for (c in corners) {
            box.set(c[0] - keepClear, c[1] - keepClear, c[0] + d + keepClear, c[1] + d + keepClear)
            if (!box.contains(playerSx, playerSy)) { chosen = c; break }
        }
        val c = chosen ?: return

        radius = d / 2
        cx = c[0] + radius
        cy = c[1] + radius
        renderer.drawRadar(canvas, s, cx, cy, radius, RANGE_TILES)
        shown = true
    }

    /** Returns true if the event belongs to a touch on the minimap. */
    fun onTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tracking = shown && hypot(e.x - cx, e.y - cy) <= radius + dp(4f)
                return tracking
            }
            MotionEvent.ACTION_UP -> {
                if (!tracking) return false
                tracking = false
                if (shown && hypot(e.x - cx, e.y - cy) <= radius + dp(24f)) {
                    if (view.haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    LevelOverviewDialog.show(view.context) { view.state }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                val was = tracking
                tracking = false
                return was
            }
        }
        return tracking
    }

    companion object {
        private const val MAX_DIAMETER_DP = 112f
        private const val MIN_DIAMETER_DP = 56f
        /** Squares shown each way from the player. */
        private const val RANGE_TILES = 10
    }
}
