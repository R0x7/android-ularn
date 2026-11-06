package com.ularn.android.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the level in the "dots on a map" style used by the minimap, the
 * full-level overview and the side panel: revealed terrain as a muted
 * silhouette (walls dark, floors lighter), landmarks as small coloured
 * markers, red dots for items and gold, yellow dots for monsters and a white
 * dot for the player.
 *
 * Everything comes from [GameState.mmCat], which the game writes together
 * with the map tiles, so only squares the player has revealed (and monsters
 * the map is actually showing) ever appear. Call [GameState.refresh] first.
 */
class MinimapRenderer(private val density: Float) {

    private fun dp(v: Float) = v * density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flat = Paint()                       // terrain: no AA, no seams
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.SANS_SERIF }
    private val path = Path()
    private val tmp = RectF()

    // ------------------------------------------------------------ colours

    private fun terrainColor(cls: Int): Int = when (cls) {
        GameState.MM_UNKNOWN -> 0
        GameState.MM_WALL -> Color.rgb(58, 56, 52)
        GameState.MM_DOOR -> Color.rgb(140, 100, 60)
        else -> Color.rgb(128, 126, 116)            // floor and anything on it
    }

    private fun markerColor(cls: Int): Int = when (cls) {
        GameState.MM_UP -> Color.rgb(100, 230, 110)
        GameState.MM_DOWN -> Color.rgb(70, 210, 255)
        GameState.MM_VOLCANO -> Color.rgb(255, 110, 30)
        GameState.MM_ENTRANCE -> Color.rgb(230, 100, 255)
        GameState.MM_BUILDING -> Color.rgb(80, 140, 255)
        GameState.MM_FOUNTAIN -> Color.rgb(150, 215, 255)
        GameState.MM_ALTAR -> Color.rgb(255, 160, 210)
        GameState.MM_THRONE -> Color.rgb(255, 205, 60)
        GameState.MM_TRAP -> Color.rgb(185, 110, 255)
        GameState.MM_FEATURE -> Color.rgb(185, 185, 185)
        GameState.MM_ITEM, GameState.MM_GOLD -> ITEM
        else -> 0
    }

    private fun isLandmark(cls: Int) = cls >= GameState.MM_UP && cls <= GameState.MM_FEATURE

    // -------------------------------------------------------------- level

    /**
     * Draws squares [x0..x1] x [y0..y1] (clamped to the map), square (x, y)
     * covering ox + x*cell .. ox + (x+1)*cell. [alpha] scales every colour.
     */
    fun drawLevel(canvas: Canvas, s: GameState, ox: Float, oy: Float, cell: Float,
                  x0: Int, y0: Int, x1: Int, y1: Int, alpha: Int = 255) {
        val ax0 = max(0, x0); val ay0 = max(0, y0)
        val ax1 = min(s.mapW - 1, x1); val ay1 = min(s.mapH - 1, y1)
        if (ax0 > ax1 || ay0 > ay1 || cell <= 0f) return

        // 1. terrain silhouette (whole-pixel edges so neighbours meet exactly)
        for (y in ay0..ay1) {
            val top = Math.round(oy + y * cell).toFloat()
            val bottom = Math.round(oy + (y + 1) * cell).toFloat()
            for (x in ax0..ax1) {
                val c = terrainColor(s.mmCat(x, y) and GameState.MM_CLASS_MASK)
                if (c == 0) continue
                flat.color = withAlpha(c, alpha)
                canvas.drawRect(Math.round(ox + x * cell).toFloat(), top, Math.round(ox + (x + 1) * cell).toFloat(), bottom, flat)
            }
        }

        val markR = max(cell * 0.75f, dp(2.6f))
        val dotR = max(cell * 0.42f, dp(1.7f))
        // 2. landmarks, 3. items and gold, 4. monsters - each layer on top of the last
        for (pass in 0..2) {
            for (y in ay0..ay1) {
                val cy = oy + (y + 0.5f) * cell
                for (x in ax0..ax1) {
                    val v = s.mmCat(x, y)
                    val cls = v and GameState.MM_CLASS_MASK
                    val cx = ox + (x + 0.5f) * cell
                    when (pass) {
                        0 -> if (isLandmark(cls)) drawMarker(canvas, cls, cx, cy, markR, alpha)
                        1 -> if (cls == GameState.MM_ITEM || cls == GameState.MM_GOLD) dot(canvas, cx, cy, dotR, ITEM, alpha)
                        2 -> if (v and GameState.MM_MONSTER != 0) dot(canvas, cx, cy, dotR * 1.1f, MONSTER, alpha)
                    }
                }
            }
        }
    }

    /** The player: a white dot with a dark outline. */
    fun drawPlayer(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        fill.color = Color.argb(230, 0, 0, 0)
        canvas.drawCircle(cx, cy, r + dp(1f), fill)
        fill.color = Color.WHITE
        canvas.drawCircle(cx, cy, r, fill)
    }

    private fun dot(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, alpha: Int) {
        fill.color = withAlpha(Color.argb(200, 0, 0, 0), alpha)
        canvas.drawCircle(cx, cy, r + dp(0.6f), fill)
        fill.color = withAlpha(color, alpha)
        canvas.drawCircle(cx, cy, r, fill)
    }

    /** A landmark's marker, centred on (cx, cy), about 2r across. */
    fun drawMarker(canvas: Canvas, cls: Int, cx: Float, cy: Float, r: Float, alpha: Int = 255) {
        val color = withAlpha(markerColor(cls), alpha)
        val outline = withAlpha(Color.argb(210, 0, 0, 0), alpha)
        fun shape(p: Paint) {
            when (cls) {
                GameState.MM_UP -> { tri(cx, cy, r, up = true); canvas.drawPath(path, p) }
                GameState.MM_DOWN, GameState.MM_ENTRANCE -> { tri(cx, cy, r, up = false); canvas.drawPath(path, p) }
                GameState.MM_VOLCANO, GameState.MM_THRONE -> { diamond(cx, cy, r); canvas.drawPath(path, p) }
                GameState.MM_BUILDING -> canvas.drawRect(cx - r * 0.8f, cy - r * 0.8f, cx + r * 0.8f, cy + r * 0.8f, p)
                GameState.MM_FEATURE -> canvas.drawRect(cx - r * 0.5f, cy - r * 0.5f, cx + r * 0.5f, cy + r * 0.5f, p)
                GameState.MM_FOUNTAIN -> canvas.drawCircle(cx, cy, r * 0.75f, p)
                GameState.MM_ALTAR -> {
                    val t = r * 0.32f
                    canvas.drawRect(cx - t, cy - r, cx + t, cy + r, p)
                    canvas.drawRect(cx - r * 0.8f, cy - r * 0.45f - t, cx + r * 0.8f, cy - r * 0.45f + t, p)
                }
                GameState.MM_TRAP -> {
                    path.reset()
                    val k = r * 0.75f
                    path.moveTo(cx - k, cy - k); path.lineTo(cx + k, cy + k)
                    path.moveTo(cx + k, cy - k); path.lineTo(cx - k, cy + k)
                    canvas.drawPath(path, p)
                }
                GameState.MM_ITEM, GameState.MM_GOLD -> canvas.drawCircle(cx, cy, r * 0.6f, p)
            }
        }
        if (cls == GameState.MM_TRAP) {
            stroke.strokeCap = Paint.Cap.ROUND
            stroke.color = outline; stroke.strokeWidth = r * 0.55f + dp(1.2f); shape(stroke)
            stroke.color = color; stroke.strokeWidth = r * 0.55f; shape(stroke)
            return
        }
        if (cls == GameState.MM_FOUNTAIN) {
            stroke.color = outline; stroke.strokeWidth = r * 0.5f + dp(1.2f); shape(stroke)
            stroke.color = color; stroke.strokeWidth = r * 0.5f; shape(stroke)
            return
        }
        stroke.strokeCap = Paint.Cap.BUTT
        stroke.color = outline; stroke.strokeWidth = dp(1.2f); shape(stroke)
        fill.color = color; shape(fill)
    }

    private fun tri(cx: Float, cy: Float, r: Float, up: Boolean) {
        path.reset()
        val d = if (up) 1f else -1f
        path.moveTo(cx, cy - d * r)
        path.lineTo(cx + r, cy + d * r * 0.8f)
        path.lineTo(cx - r, cy + d * r * 0.8f)
        path.close()
    }

    private fun diamond(cx: Float, cy: Float, r: Float) {
        path.reset()
        path.moveTo(cx, cy - r); path.lineTo(cx + r, cy); path.lineTo(cx, cy + r); path.lineTo(cx - r, cy)
        path.close()
    }

    // -------------------------------------------------------------- radar

    /**
     * The round minimap: [rangeTiles] squares each way around the player,
     * in a semi-transparent disc of radius [radius] centred on (cx, cy).
     */
    fun drawRadar(canvas: Canvas, s: GameState, cx: Float, cy: Float, radius: Float, rangeTiles: Int) {
        val px = s.playerX.coerceIn(0, s.mapW - 1)
        val py = s.playerY.coerceIn(0, s.mapH - 1)
        val cell = 2 * radius / (2 * rangeTiles + 1)

        fill.color = Color.argb(150, 8, 8, 12)
        canvas.drawCircle(cx, cy, radius, fill)

        canvas.save()
        path.reset()
        path.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.clipPath(path)
        val ox = cx - (px + 0.5f) * cell
        val oy = cy - (py + 0.5f) * cell
        drawLevel(canvas, s, ox, oy, cell, px - rangeTiles - 1, py - rangeTiles - 1,
            px + rangeTiles + 1, py + rangeTiles + 1, alpha = 205)
        canvas.restore()

        drawPlayer(canvas, cx, cy, max(cell * 0.55f, dp(2.6f)))

        stroke.strokeCap = Paint.Cap.BUTT
        stroke.color = Color.argb(160, 0, 0, 0)
        stroke.strokeWidth = dp(3f)
        canvas.drawCircle(cx, cy, radius + dp(1f), stroke)
        stroke.color = Color.argb(210, 200, 180, 120)
        stroke.strokeWidth = dp(1.5f)
        canvas.drawCircle(cx, cy, radius, stroke)
    }

    // ----------------------------------------------------------- overview

    /**
     * The whole level, fitted (square cells, centred) into [area].
     * Returns the rectangle actually covered by the map in [out].
     */
    fun drawOverview(canvas: Canvas, s: GameState, area: RectF, out: RectF = tmp): RectF {
        val cell = min(area.width() / s.mapW, area.height() / s.mapH)
        val w = cell * s.mapW
        val h = cell * s.mapH
        val left = area.left + (area.width() - w) / 2
        val top = area.top + (area.height() - h) / 2
        out.set(left, top, left + w, top + h)
        if (cell <= 0f) return out
        fill.color = Color.rgb(8, 8, 12)
        canvas.drawRect(out, fill)
        drawLevel(canvas, s, left, top, cell, 0, 0, s.mapW - 1, s.mapH - 1)
        val px = s.playerX
        val py = s.playerY
        if (px in 0 until s.mapW && py in 0 until s.mapH) {
            drawPlayer(canvas, left + (px + 0.5f) * cell, top + (py + 0.5f) * cell, max(cell * 0.6f, dp(2.6f)))
        }
        stroke.strokeCap = Paint.Cap.BUTT
        stroke.color = Color.argb(120, 200, 180, 120)
        stroke.strokeWidth = dp(1f)
        canvas.drawRect(out, stroke)
        return out
    }

    // ------------------------------------------------------------- legend

    private class Entry(val label: String, val kind: Int)

    private val legend = listOf(
        Entry("You", LEGEND_PLAYER), Entry("Monster", LEGEND_MONSTER), Entry("Item / gold", LEGEND_ITEM),
        Entry("Stairs up", GameState.MM_UP), Entry("Stairs down", GameState.MM_DOWN),
        Entry("Volcanic shaft", GameState.MM_VOLCANO), Entry("Dungeon entrance", GameState.MM_ENTRANCE),
        Entry("Building", GameState.MM_BUILDING), Entry("Fountain", GameState.MM_FOUNTAIN),
        Entry("Altar", GameState.MM_ALTAR), Entry("Throne", GameState.MM_THRONE),
        Entry("Trap", GameState.MM_TRAP), Entry("Statue / mirror", GameState.MM_FEATURE),
        Entry("Door", LEGEND_DOOR),
    )

    /**
     * Lays the legend out in rows from (left, top), wrapping at [width].
     * Draws only if [canvas] is non-null; returns the height used.
     */
    fun drawLegend(canvas: Canvas?, left: Float, top: Float, width: Float, textPx: Float): Float {
        text.textSize = textPx
        text.color = Color.rgb(200, 200, 195)
        val r = textPx * 0.42f
        val rowH = textPx * 1.6f
        val gap = textPx * 1.1f
        var x = left
        var y = top
        for (e in legend) {
            val w = 2 * r + textPx * 0.4f + text.measureText(e.label)
            if (x > left && x + w > left + width) { x = left; y += rowH }
            if (canvas != null) {
                val cx = x + r
                val cy = y + rowH / 2
                when (e.kind) {
                    LEGEND_PLAYER -> drawPlayer(canvas, cx, cy, r * 0.8f)
                    LEGEND_MONSTER -> dot(canvas, cx, cy, r * 0.8f, MONSTER, 255)
                    LEGEND_ITEM -> dot(canvas, cx, cy, r * 0.75f, ITEM, 255)
                    LEGEND_DOOR -> { flat.color = terrainColor(GameState.MM_DOOR); canvas.drawRect(cx - r * 0.7f, cy - r * 0.7f, cx + r * 0.7f, cy + r * 0.7f, flat) }
                    else -> drawMarker(canvas, e.kind, cx, cy, r)
                }
                canvas.drawText(e.label, x + 2 * r + textPx * 0.4f, cy - (text.descent() + text.ascent()) / 2, text)
            }
            x += w + gap
        }
        return y + rowH - top
    }

    private fun withAlpha(c: Int, alpha: Int): Int =
        if (alpha >= 255) c else Color.argb(Color.alpha(c) * alpha / 255, Color.red(c), Color.green(c), Color.blue(c))

    companion object {
        private val ITEM = Color.rgb(235, 55, 50)
        private val MONSTER = Color.rgb(255, 225, 40)
        private const val LEGEND_PLAYER = 100
        private const val LEGEND_MONSTER = 101
        private const val LEGEND_ITEM = 102
        private const val LEGEND_DOOR = 103
    }
}
