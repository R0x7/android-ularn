package com.ularn.android.game

import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Wild Edition, the Glitch Realm: while [GameState.glitch] > 0 (a scroll of
 * segfault was read), [GameView] draws the map through this corruption pass:
 * a few cells get a corruption tile (408-415) blended on top, random
 * horizontal slices are shifted sideways, the whole map's hue rotates with
 * the turn, and the HUD lines lose the odd character to block glyphs. It
 * animates at 8 fps. The game side (compat/wild_glitch.c) does the rest:
 * items drawn as other items, shuffled monster names, the null pointer.
 *
 * [reduce] ("Reduce glitch effects", for motion-sensitive players) drops the
 * slicing and the hue rotation and stops the animation: the corruption tiles
 * and HUD glyphs then change only once per game turn.
 */
class GlitchFx(private val view: View) {

    /** Settings: "Reduce glitch effects". */
    var reduce = false
        set(v) { field = v; view.invalidate() }

    private var turnsLeft = 0
    private var frame = 0L
    private var x0 = 0; private var y0 = 0; private var x1 = -1; private var y1 = -1
    private var cells = IntArray(0)
    private var shiftPx = 0

    private val hueMatrix = ColorMatrix()
    private val layerPaint = Paint()
    val overlayPaint = Paint().apply { isFilterBitmap = false; isDither = false; alpha = GlitchMath.OVERLAY_ALPHA }
    private var layered = false

    private var pending = false
    private val tick = Runnable { pending = false; view.invalidate() }

    val active get() = turnsLeft > 0

    /** Call once per drawn frame; keeps the 8 fps cadence going while glitched. */
    fun onFrame(s: GameState) {
        turnsLeft = if (s.displayMode == GameState.DISPLAY_MAP) s.glitch.coerceAtLeast(0) else 0
        // Reduced: one look per game turn, so nothing moves while you think.
        frame = if (reduce) turnsLeft.toLong() else SystemClock.uptimeMillis() / GlitchMath.FRAME_MS
        if (active && !reduce && !pending) {
            pending = true
            view.postDelayed(tick, GlitchMath.FRAME_MS)
        }
    }

    /**
     * Starts the map pass for the visible cells [vx0]..[vx1] x [vy0]..[vy1]
     * (call after clipping to [map], before drawing tiles); [tilePx] sizes
     * the slice shift.
     */
    fun begin(canvas: Canvas, map: RectF, vx0: Int, vy0: Int, vx1: Int, vy1: Int, tilePx: Float) {
        layered = false
        if (!active) return
        x0 = vx0; y0 = vy0; x1 = vx1; y1 = vy1
        cells = GlitchMath.overlayCells(frame, vx0, vy0, vx1, vy1)
        shiftPx = GlitchMath.shiftPx(tilePx)
        if (!reduce) {
            hueMatrix.set(GlitchMath.hueRotation(GlitchMath.hueDegrees(turnsLeft)))
            layerPaint.colorFilter = ColorMatrixColorFilter(hueMatrix)
            canvas.saveLayer(map, layerPaint)
            layered = true
        }
    }

    /** Shifts a tile's destination when its row is in a slipped slice. */
    fun shift(dst: Rect, ty: Int) {
        if (!active || reduce) return
        val dx = GlitchMath.rowShift(frame, ty, shiftPx)
        if (dx != 0) dst.offset(dx, 0)
    }

    /** The corruption tile drawn over cell ([tx],[ty]) this frame, or -1. */
    fun overlayAt(tx: Int, ty: Int): Int {
        if (!active) return -1
        var i = 0
        while (i + 1 < cells.size) {
            if (cells[i] == tx && cells[i + 1] == ty) return GlitchMath.glitchTile(frame, tx, ty)
            i += 2
        }
        return -1
    }

    /** Ends the map pass (applies the hue rotation). */
    fun end(canvas: Canvas) {
        if (layered) canvas.restore()
        layered = false
    }

    /** A HUD line, with the odd character corrupted while glitched. */
    fun text(line: String): String = if (active) GlitchMath.corrupt(line, frame) else line
}

/**
 * The arithmetic of [GlitchFx], free of Android classes so it can be unit
 * tested on the JVM. Every "random" choice is a hash of the frame number and
 * the cell, so a frame redrawn for another reason looks the same.
 */
object GlitchMath {
    const val FIRST_TILE = 408
    const val TILE_COUNT = 8
    const val FRAME_MS = 125L          // 8 fps
    const val MIN_CELLS = 3
    const val MAX_CELLS = 6
    const val OVERLAY_ALPHA = 200
    const val TEXT_ODDS = 20           // 1 in 20 characters
    val GLYPHS = charArrayOf('▓', '█', '▒', '░')

    /** A well-mixed non-negative hash of a few ints. */
    fun hash(a: Long, b: Int = 0, c: Int = 0): Int {
        var h = a * -0x61c8864680b583ebL + b * 0x5bd1e995L + c * 0x27d4eb2dL
        h = h xor (h ushr 31)
        h *= 0x7fb5d329728ea185L
        h = h xor (h ushr 27)
        return ((h ushr 33).toInt()) and 0x7fffffff
    }

    /** A corruption tile id for this frame and cell, always in 408..415. */
    fun glitchTile(frame: Long, x: Int, y: Int) = FIRST_TILE + hash(frame, x * 131 + 7, y) % TILE_COUNT

    /**
     * 3..6 distinct cells inside the visible range, as x,y pairs. Empty when
     * the range is empty.
     */
    fun overlayCells(frame: Long, x0: Int, y0: Int, x1: Int, y1: Int): IntArray {
        val w = x1 - x0 + 1
        val h = y1 - y0 + 1
        if (w <= 0 || h <= 0) return IntArray(0)
        val want = minOf(MIN_CELLS + hash(frame, 1) % (MAX_CELLS - MIN_CELLS + 1), w * h)
        val out = IntArray(want * 2)
        var n = 0
        var k = 0
        while (n < want && k < want * 8) {
            val r = hash(frame, 2, k++)
            val x = x0 + r % w
            val y = y0 + (r / w) % h
            var dup = false
            for (i in 0 until n) if (out[2 * i] == x && out[2 * i + 1] == y) { dup = true; break }
            if (dup) continue
            out[2 * n] = x; out[2 * n + 1] = y
            n++
        }
        return if (n == want) out else out.copyOf(n * 2)
    }

    /** The slice shift in px for a tile size: 4 px at 32 px tiles, at least 1. */
    fun shiftPx(tilePx: Float) = maxOf(1, (tilePx / 8f).toInt())

    /** About one row in five slips this frame, by +-[maxPx]. */
    fun rowShift(frame: Long, row: Int, maxPx: Int): Int {
        if (maxPx <= 0) return 0
        val r = hash(frame, 3, row)
        if (r % 5 != 0) return 0
        return if ((r / 5) % 2 == 0) maxPx else -maxPx
    }

    /** Hue rotation in degrees for the turns left: (turn * 37) mod 360. */
    fun hueDegrees(turn: Int) = Math.floorMod(turn * 37, 360).toFloat()

    /**
     * A 4x5 colour matrix (android.graphics.ColorMatrix layout) rotating hue
     * by [degrees] while keeping luminance: the standard luminance-preserving
     * hue rotation. Identity at 0 degrees.
     */
    fun hueRotation(degrees: Float): FloatArray {
        val a = Math.toRadians(degrees.toDouble())
        val c = cos(a).toFloat()
        val s = sin(a).toFloat()
        val lr = 0.213f; val lg = 0.715f; val lb = 0.072f
        return floatArrayOf(
            lr + c * (1 - lr) + s * (-lr), lg + c * (-lg) + s * (-lg), lb + c * (-lb) + s * (1 - lb), 0f, 0f,
            lr + c * (-lr) + s * 0.143f, lg + c * (1 - lg) + s * 0.140f, lb + c * (-lb) + s * (-0.283f), 0f, 0f,
            lr + c * (-lr) + s * (-(1 - lr)), lg + c * (-lg) + s * lg, lb + c * (1 - lb) + s * lb, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** Replaces about 1 in [TEXT_ODDS] non-space characters with block glyphs. */
    fun corrupt(line: String, frame: Long): String {
        if (line.isEmpty()) return line
        val out = line.toCharArray()
        for (i in out.indices) {
            if (out[i] == ' ') continue
            val r = hash(frame, 4, i)
            if (r % TEXT_ODDS == 0) out[i] = GLYPHS[(r / TEXT_ODDS) % GLYPHS.size]
        }
        return String(out)
    }
}
