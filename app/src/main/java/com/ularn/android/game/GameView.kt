package com.ularn.android.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.InputType
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Draws Ularn 1.6.3a's display from [GameState]:
 *
 *  map mode : a HUD (HP bar, level, gold, stats, active effects), the tile map
 *             (follows the player; pinch to zoom; swipe/tap to move), the
 *             game's message lines reflowed to a readable size, and
 *             tap-to-answer buttons for the current prompt;
 *  text mode: the game's text page (shops, inventory, help, scores), fitted
 *             to the columns it actually uses; drag sideways if it is wider
 *             than the screen.
 *
 * The on-screen controls sit in a separate overlay; [setReserve] tells this
 * view which edges they occupy so nothing important is drawn under them.
 */
class GameView(context: Context) : View(context) {

    var state: GameState? = null
        set(v) { field = v; invalidate() }

    /** Bytes for the game (taps on the map, answer buttons, keyboard). */
    var onInput: ((ByteArray) -> Unit)? = null
    /** Tap when no game is running. */
    var onIdleTap: (() -> Unit)? = null
    var onToggleKeyboard: (() -> Unit)? = null
    var onZoomChanged: ((tileDp: Float) -> Unit)? = null

    var running = false
        set(v) { field = v; invalidate() }

    var arrowsAsMoves = true
    var tapToMove = true
    var swipeToMove = true
    /** A quick flick runs (H/J/K/L...). Off: every swipe is a step, with no delay. */
    var flickToRun = false
    var haptics = true

    /** Preferred tile size in dp (0 = automatic). */
    var tileDp = 0f
        set(v) { field = v; invalidate() }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    /* saves / a11y */
    /** Text size multiplier for the HUD, messages, text pages and answer buttons. */
    var textScale = 1f
        set(v) { field = v; invalidate() }
    /** Opaque outlined panels, pure white text, underlines instead of colours. */
    var highContrast = false
        set(v) { field = v; chipsKey = ""; invalidate() }
    /** A text size (or text-derived height) in dp, scaled by [textScale]. */
    private fun sp(v: Float) = v * density * textScale
    private fun isStandout(f: Int) = f == GameState.FMT_STANDOUT || f == GameState.FMT_STANDOUT2 || f == GameState.FMT_STANDOUT3

    /** Outlines a rounded rect in high-contrast mode. */
    private fun hcOutline(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float) {
        if (!highContrast) return
        val style = fill.style; val w = fill.strokeWidth
        fill.style = Paint.Style.STROKE; fill.strokeWidth = dp(2f); fill.color = Color.WHITE
        canvas.drawRoundRect(l, t, r, b, radius, radius, fill)
        fill.style = style; fill.strokeWidth = w
    }

    // ------------------------------------------------------------- tiles

    private val tileSrc = 32
    private val atlasCols = 16

    /**
     * One bitmap per tile. Drawing from separate bitmaps (rather than
     * sub-rects of the sheet) stops the GPU sampling a neighbouring tile's
     * edge pixels when tiles are scaled, which showed as a grid of seams.
     */
    private val tiles: Array<Bitmap?> = run {
        val sheet = try {
            context.assets.open("ularn_tiles.png").use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })
            }
        } catch (_: Exception) { null }
        if (sheet == null) arrayOfNulls(0)
        else Array(sheet.width / tileSrc * (sheet.height / tileSrc)) { id ->
            val sx = (id % atlasCols) * tileSrc
            val sy = (id / atlasCols) * tileSrc
            Bitmap.createBitmap(sheet, sx, sy, tileSrc, tileSrc)
        }
    }

    private val tilePaint = Paint().apply { isFilterBitmap = false; isDither = false }
    private val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    private val monoBold = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
    private val ui = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.SANS_SERIF }
    private val uiBold = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dstRect = Rect()

    private var reserveLeft = 0f
    private var reserveRight = 0f
    private var reserveBottom = 0f

    fun setReserve(left: Float, right: Float, bottom: Float) {
        if (left == reserveLeft && right == reserveRight && bottom == reserveBottom) return
        reserveLeft = left; reserveRight = right; reserveBottom = bottom
        invalidate()
    }

    // ------------------------------------------------------------ colors

    private val bg get() = if (highContrast) Color.BLACK else Color.rgb(12, 12, 14)
    private val mapBg = Color.BLACK            // same as the "unexplored" tile
    private val fgNormal get() = if (highContrast) Color.WHITE else Color.rgb(222, 222, 216)
    private val fgDim get() = if (highContrast) Color.WHITE else Color.rgb(150, 150, 150)
    private val fgRed = Color.rgb(255, 110, 100)
    private val fgGreen = Color.rgb(110, 220, 120)
    private val fgBlue = Color.rgb(120, 170, 255)
    private val accent = Color.rgb(255, 190, 60)
    private val panel get() = if (highContrast) Color.BLACK else Color.rgb(22, 23, 27)
    private val panelLine = Color.rgb(44, 46, 54)

    private fun fmtColor(f: Int) = if (highContrast) Color.WHITE else when (f) {
        GameState.FMT_STANDOUT -> fgRed
        GameState.FMT_STANDOUT2 -> fgGreen
        GameState.FMT_STANDOUT3 -> fgBlue
        GameState.FMT_BOLD -> Color.WHITE
        else -> fgNormal
    }

    // ----------------------------------------------------------- layout

    private val content = RectF()
    private val mapRect = RectF()
    private var tilePx = 0f
    private var camX = 0f
    private var camY = 0f

    private class Chip(val label: String, val keys: ByteArray?, val action: (() -> Unit)? = null) {
        val rect = RectF()
    }

    private val chips = ArrayList<Chip>()
    private var chipsKey = ""

    private var lastBeep = -1
    private var caretOn = true
    private val blink = object : Runnable {
        override fun run() { caretOn = !caretOn; invalidate(); postDelayed(this, 530) }
    }

    /** Text pages wider than the screen can be dragged sideways. */
    private var textPanX = 0f
    private var textPanMax = 0f
    private var lastDisplayMode = -1

    override fun onAttachedToWindow() { super.onAttachedToWindow(); postDelayed(blink, 530) }
    override fun onDetachedFromWindow() { removeCallbacks(blink); endSwipe(); super.onDetachedFromWindow() }

    // ------------------------------------------------------------- draw

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(bg)
        content.set(reserveLeft, 0f, width - reserveRight, height - reserveBottom)
        if (content.width() < dp(120f) || content.height() < dp(120f)) content.set(0f, 0f, width.toFloat(), height.toFloat())

        val s = state
        if (s == null || !s.refresh() || s.seq == 0) return

        if (s.beepCount != lastBeep) {
            if (lastBeep >= 0 && haptics) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            lastBeep = s.beepCount
        }
        if (s.displayMode != lastDisplayMode) { textPanX = 0f; lastDisplayMode = s.displayMode }

        buildChips(s)
        val chipH = if (chips.isEmpty()) 0f else maxOf(dp(44f), sp(40f))
        val area = RectF(content.left, content.top, content.right, content.bottom - chipH)

        if (s.displayMode == GameState.DISPLAY_TEXT) drawTextPage(canvas, s, area)
        else drawMapMode(canvas, s, area)

        if (chips.isNotEmpty()) drawChips(canvas, RectF(content.left, content.bottom - chipH, content.right, content.bottom))
        if (swipeActive) drawSwipe(canvas)
    }

    private fun drawSwipe(canvas: Canvas) {
        val r = dp(34f)
        fill.style = Paint.Style.STROKE
        fill.strokeWidth = dp(2f)
        fill.color = Color.argb(150, 255, 255, 255)
        canvas.drawCircle(swipeStartX, swipeStartY, r, fill)
        fill.style = Paint.Style.FILL
        val d = swipeDir ?: return
        val (dx, dy) = DIR_VEC.getValue(d)
        val len = hypot(dx.toFloat(), dy.toFloat())
        val ex = swipeStartX + dx / len * r
        val ey = swipeStartY + dy / len * r
        fill.color = Color.argb(220, 255, 190, 60)
        fill.strokeWidth = dp(4f)
        canvas.drawLine(swipeStartX, swipeStartY, ex, ey, fill)
        canvas.drawCircle(ex, ey, dp(7f), fill)
    }

    // -------------------------------------------------------- text page

    private fun drawTextPage(canvas: Canvas, s: GameState, area: RectF) {
        val rows = s.textLines
        // Fit the columns the page actually uses (shops ~75, inventory ~45).
        var usedCols = 40
        for (r in 0 until rows) {
            var n = s.lineLen
            while (n > 0 && s.textChar(r, n - 1) == ' ') n--
            if (n > usedCols) usedCols = n
        }
        val pad = dp(10f)
        mono.textSize = 100f
        val per100 = mono.measureText("M")
        var size = (area.width() - 2 * pad) / (usedCols * per100) * 100f
        size = size.coerceIn(sp(11f), sp(20f))      // never smaller than readable
        mono.textSize = size
        var lineH = mono.fontSpacing
        if (lineH * rows > area.height() - 2 * pad) {   // but do fit vertically
            size = maxOf(dp(8f), size * (area.height() - 2 * pad) / (lineH * rows))
            mono.textSize = size
            lineH = mono.fontSpacing
        }
        monoBold.textSize = size
        val cellW = mono.measureText("M")
        val pageW = cellW * usedCols
        textPanMax = maxOf(0f, pageW - (area.width() - 2 * pad))
        textPanX = textPanX.coerceIn(0f, textPanMax)
        val x0 = area.left + pad + (if (textPanMax > 0) -textPanX else (area.width() - 2 * pad - pageW) / 2f)
        val y0 = area.top + pad

        canvas.save()
        canvas.clipRect(area)
        for (r in 0 until rows) {
            val base = y0 + r * lineH - mono.fontMetrics.ascent
            drawLine(canvas, x0, base, cellW, s.lineLen, { s.textChar(r, it) }, { s.textFmt(r, it) })
        }
        if (s.caret) drawCaret(canvas, x0 + (s.cursorX - 1) * cellW, y0 + (s.cursorY - 1) * lineH - mono.fontMetrics.ascent, cellW)
        canvas.restore()

        if (textPanMax > 0) {   // scroll hint
            val track = area.width() - 2 * pad
            val thumb = track * (track / pageW)
            val tx = area.left + pad + (track - thumb) * (textPanX / textPanMax)
            fill.color = Color.argb(90, 255, 255, 255)
            canvas.drawRoundRect(tx, area.bottom - dp(5f), tx + thumb, area.bottom - dp(2f), dp(2f), dp(2f), fill)
        }
    }

    private fun drawLine(canvas: Canvas, x: Float, baseline: Float, cellW: Float,
                         n: Int, ch: (Int) -> Char, fmt: (Int) -> Int) {
        val one = CharArray(1)
        for (col in 0 until n) {
            val c = ch(col)
            val f = fmt(col)
            val cx = x + col * cellW
            if (f == GameState.FMT_INVERSE) {
                fill.color = fgNormal
                canvas.drawRect(cx, baseline + mono.fontMetrics.ascent, cx + cellW + 0.5f, baseline + mono.fontMetrics.descent, fill)
            }
            if (c != ' ' && c.code != 0) {
                val p = if (f == GameState.FMT_BOLD) monoBold else mono
                p.color = if (f == GameState.FMT_INVERSE) bg else fmtColor(f)
                p.isUnderlineText = highContrast && isStandout(f)
                one[0] = c
                canvas.drawText(one, 0, 1, cx, baseline, p)
                p.isUnderlineText = false
            }
        }
    }

    private fun drawCaret(canvas: Canvas, x: Float, baseline: Float, cellW: Float) {
        if (!caretOn) return
        fill.color = accent
        canvas.drawRect(x, baseline + dp(1f), x + cellW, baseline + dp(3f), fill)
    }

    // --------------------------------------------------------- map mode

    private fun drawMapMode(canvas: Canvas, s: GameState, area: RectF) {
        val pad = dp(8f)
        var y = area.top + pad
        val x0 = area.left + pad
        val x1 = area.right - pad
        val w = x1 - x0

        // --- HUD line 1: HP bar + numbers
        val hpFrac = if (s.hpMax > 0) (s.hp.toFloat() / s.hpMax).coerceIn(0f, 1f) else 0f
        uiBold.textSize = sp(13f); ui.textSize = sp(12f)
        val hpText = "HP ${s.hp}/${s.hpMax}"
        uiBold.color = Color.WHITE
        val lineH = sp(20f)
        canvas.drawText(hpText, x0, y + lineH / 2 - (uiBold.descent() + uiBold.ascent()) / 2, uiBold)
        val barL = x0 + uiBold.measureText("HP 999/999") + dp(6f)
        val right1 = "Gold ${s.gold}"
        ui.color = if (highContrast) Color.WHITE else accent
        val rw = ui.measureText(right1)
        canvas.drawText(right1, x1 - rw, y + lineH / 2 - (ui.descent() + ui.ascent()) / 2, ui)
        val barR = x1 - rw - dp(10f)
        if (barR > barL + dp(30f)) {
            fill.color = panelLine
            canvas.drawRoundRect(barL, y + lineH / 2 - dp(4f), barR, y + lineH / 2 + dp(4f), dp(4f), dp(4f), fill)
            fill.color = when {
                highContrast -> Color.WHITE
                hpFrac > 0.6f -> Color.rgb(90, 200, 110)
                hpFrac > 0.3f -> Color.rgb(240, 190, 60)
                else -> Color.rgb(235, 80, 70)
            }
            canvas.drawRoundRect(barL, y + lineH / 2 - dp(4f), barL + (barR - barL) * hpFrac, y + lineH / 2 + dp(4f), dp(4f), dp(4f), fill)
            hcOutline(canvas, barL, y + lineH / 2 - dp(5f), barR, y + lineH / 2 + dp(5f), dp(5f))
        }
        y += lineH

        // --- HUD line 2: level / title / where
        ui.color = fgNormal
        val where = if (s.levelName == "H") "Home" else "Level ${s.levelName}"
        val line2 = "Lv ${s.playerLevel} ${s.levelTitle}  ·  Exp ${s.exp}  ·  $where"
        drawFitted(canvas, line2, x0, y, w, lineH, ui)
        y += lineH

        // --- HUD line 3: stats
        val stats = (0 until 6).joinToString("  ") { "${GameState.STAT_NAMES[it]} ${s.stat(it)}" } +
            "   AC ${s.ac}  WC ${s.wc}  Sp ${s.spells}/${s.spellMax}"
        ui.color = fgDim
        drawFitted(canvas, stats, x0, y, w, lineH, ui)
        y += lineH

        // --- effects
        val active = GameState.EFFECT_NAMES.filterIndexed { i, _ -> s.effects and (1 shl i) != 0 }
        if (active.isNotEmpty()) {
            ui.textSize = sp(11f)
            var cx = x0
            val h = sp(18f)
            for (name in active) {
                val cw = ui.measureText(name) + dp(12f)
                if (cx + cw > x1) break
                fill.color = if (highContrast) Color.BLACK else Color.argb(200, 60, 50, 20)
                canvas.drawRoundRect(cx, y + dp(1f), cx + cw, y + dp(1f) + h, h / 2, h / 2, fill)
                hcOutline(canvas, cx, y + dp(1f), cx + cw, y + dp(1f) + h, h / 2)
                ui.color = if (highContrast) Color.WHITE else accent
                canvas.drawText(name, cx + dp(6f), y + dp(1f) + h / 2 - (ui.descent() + ui.ascent()) / 2, ui)
                cx += cw + dp(4f)
            }
            y += h + dp(4f)
        }
        y += dp(2f)

        // --- messages: the game's 5x80 message lines, reflowed
        val msgLineH = sp(17f)
        val msgPad = dp(6f)
        val msgLines = 4
        val msgTop = area.bottom - pad - msgLines * msgLineH - 2 * msgPad
        fill.color = panel
        canvas.drawRoundRect(area.left + dp(4f), msgTop, area.right - dp(4f), area.bottom - pad + dp(2f), dp(8f), dp(8f), fill)
        hcOutline(canvas, area.left + dp(4f), msgTop, area.right - dp(4f), area.bottom - pad + dp(2f), dp(8f))
        drawMessages(canvas, s, x0, msgTop + msgPad, w, msgLineH, msgLines)

        // --- map between HUD and messages
        mapRect.set(area.left, y, area.right, msgTop - dp(6f))
        drawMap(canvas, s)
    }

    private fun drawFitted(canvas: Canvas, text: String, x: Float, top: Float, w: Float, h: Float, p: Paint) {
        val base = p.textSize
        val tw = p.measureText(text)
        if (tw > w) p.textSize = maxOf(dp(9f), base * w / tw)
        canvas.drawText(text, x, top + h / 2 - (p.descent() + p.ascent()) / 2, p)
        p.textSize = base
    }

    private fun drawMessages(canvas: Canvas, s: GameState, x: Float, top: Float, w: Float, lineH: Float, maxLines: Int) {
        ui.textSize = sp(13f)
        ui.color = fgNormal
        val caretRow = s.cursorY - 1
        val caretCol = s.cursorX - 1

        // Wrap each source line; remember where the caret lands.
        data class Seg(val text: String, val fmt: Int, val caretAt: Int)
        val out = ArrayList<Seg>()
        for (r in 0 until s.msgLines) {
            var n = s.lineLen
            while (n > 0 && s.msgChar(r, n - 1) == ' ') n--
            val wantCaret = s.caret && r == caretRow
            if (n == 0 && !wantCaret) continue
            val chars = CharArray(n) { s.msgChar(r, it) }
            val fmt = (0 until n).firstOrNull { s.msgFmt(r, it) != GameState.FMT_NORMAL && chars[it] != ' ' }?.let { s.msgFmt(r, it) } ?: GameState.FMT_NORMAL
            var start = 0
            while (start < n || (wantCaret && start == 0 && n == 0)) {
                var end = n
                while (end > start && ui.measureText(chars, start, end - start) > w) {
                    val sp = chars.lastIndexOfSpace(start, end - 1)
                    end = if (sp > start) sp else end - 1
                }
                if (end == start) end = minOf(n, start + 1)
                val caret = if (wantCaret && caretCol >= start && (caretCol < end || end == n)) caretCol - start else -1
                out += Seg(String(chars, start, end - start), fmt, caret)
                start = end
                while (start < n && chars[start] == ' ') start++
                if (n == 0) break
            }
        }
        val shown = if (out.size > maxLines) out.subList(out.size - maxLines, out.size) else out
        var yy = top
        for (seg in shown) {
            ui.color = fmtColor(seg.fmt)
            val base = yy + lineH / 2 - (ui.descent() + ui.ascent()) / 2
            ui.isUnderlineText = highContrast && isStandout(seg.fmt)
            canvas.drawText(seg.text, x, base, ui)
            ui.isUnderlineText = false
            if (seg.caretAt >= 0 && caretOn) {
                val cx = x + ui.measureText(seg.text, 0, minOf(seg.caretAt, seg.text.length))
                fill.color = accent
                canvas.drawRect(cx, base + dp(1f), cx + dp(8f), base + dp(3f), fill)
            }
            yy += lineH
        }
    }

    private fun CharArray.lastIndexOfSpace(from: Int, to: Int): Int {
        var i = to
        while (i >= from) { if (this[i] == ' ') return i; i-- }
        return -1
    }

    private fun defaultTilePx(fitAll: Float): Float {
        val pref = if (tileDp > 0f) dp(tileDp) else maxOf(fitAll, dp(30f))
        return pref.coerceIn(fitAll, dp(80f))
    }

    private fun drawMap(canvas: Canvas, s: GameState) {
        if (tiles.isEmpty() || mapRect.height() < dp(20f)) return
        fill.color = mapBg
        canvas.drawRect(mapRect, fill)

        val fitAll = minOf(mapRect.width() / s.mapW, mapRect.height() / s.mapH)
        tilePx = defaultTilePx(fitAll)
        val t = tilePx
        val mapPxW = t * s.mapW
        val mapPxH = t * s.mapH

        // camera: follow the player, clamp to the map, centre if it fits
        camX = if (mapPxW <= mapRect.width()) (mapPxW - mapRect.width()) / 2f
               else (s.playerX * t + t / 2 - mapRect.width() / 2).coerceIn(0f, mapPxW - mapRect.width())
        camY = if (mapPxH <= mapRect.height()) (mapPxH - mapRect.height()) / 2f
               else (s.playerY * t + t / 2 - mapRect.height() / 2).coerceIn(0f, mapPxH - mapRect.height())

        canvas.save()
        canvas.clipRect(mapRect)
        val x0 = (camX / t).toInt().coerceAtLeast(0)
        val y0 = (camY / t).toInt().coerceAtLeast(0)
        val x1 = minOf(s.mapW - 1, ((camX + mapRect.width()) / t).toInt())
        val y1 = minOf(s.mapH - 1, ((camY + mapRect.height()) / t).toInt())
        // Whole-pixel edges, each tile ending exactly where the next begins: no seams.
        val originX = mapRect.left - camX
        val originY = mapRect.top - camY
        for (ty in y0..y1) {
            val top = (originY + ty * t).roundToInt()
            val bottom = (originY + (ty + 1) * t).roundToInt()
            for (tx in x0..x1) {
                val left = (originX + tx * t).roundToInt()
                val right = (originX + (tx + 1) * t).roundToInt()
                dstRect.set(left, top, right, bottom)
                drawTile(canvas, s.tile(tx, ty))
                val o = s.overlay(tx, ty)
                if (o >= 0) drawTile(canvas, o)
                if (tx == s.playerX && ty == s.playerY && s.playerCursor) drawTile(canvas, GameState.TILE_CURSOR)
            }
        }
        canvas.restore()
    }

    private fun drawTile(canvas: Canvas, id: Int) {
        val bmp = tiles.getOrNull(id) ?: return
        canvas.drawBitmap(bmp, null, dstRect, tilePaint)
    }

    // ---------------------------------------------------------- answers

    private fun keyLabel(c: Char): String = when (c) {
        '\r' -> "Enter"
        '\u001b' -> "Esc"
        ' ' -> "Space"
        '*' -> "All (*)"
        else -> c.toString()
    }

    private fun buildChips(s: GameState) {
        val key = "${s.inputMode}|${s.answers}|${s.displayMode}"
        if (key == chipsKey) return
        chipsKey = key
        chips.clear()
        when (s.inputMode) {
            GameState.INPUT_PROMPT -> {
                val keys = s.answers.filter { it.code in 0x20..0x7e || it == '\r' || it == '\u001b' }.toList().distinct()
                if (keys.size in 1..14) {
                    // Esc / Enter go last so letters stay in reading order.
                    val ordered = keys.filter { it != '\u001b' && it != '\r' } + keys.filter { it == '\r' } + keys.filter { it == '\u001b' }
                    for (c in ordered) chips += Chip(keyLabel(c), byteArrayOf(c.code.toByte()))
                }
            }
            GameState.INPUT_NUMBER -> {
                chips += Chip("All", byteArrayOf('*'.code.toByte()))
                for (d in '0'..'9') chips += Chip(d.toString(), byteArrayOf(d.code.toByte()))
                chips += Chip("⌫", byteArrayOf(8))
                chips += Chip("Enter", byteArrayOf(13))
            }
            GameState.INPUT_PASSWORD -> {
                chips += Chip("⌨ Type", null) { onToggleKeyboard?.invoke() }
                chips += Chip("Enter", byteArrayOf(13))
            }
            GameState.INPUT_DIR -> chips += Chip("Swipe or press a direction", null)
        }
    }

    private fun drawChips(canvas: Canvas, bar: RectF) {
        val gap = dp(6f)
        val n = chips.size
        val w = ((bar.width() - gap * (n + 1)) / n).coerceAtMost(dp(110f))
        val total = n * w + (n - 1) * gap
        var x = bar.centerX() - total / 2
        for (chip in chips) {
            chip.rect.set(x, bar.top + gap, x + w, bar.bottom - gap)
            val enabled = chip.keys != null || chip.action != null
            fill.color = if (enabled) (if (highContrast) Color.BLACK else Color.argb(235, 52, 54, 64)) else Color.argb(0, 0, 0, 0)
            canvas.drawRoundRect(chip.rect, dp(10f), dp(10f), fill)
            if (enabled) {
                fill.style = Paint.Style.STROKE; fill.strokeWidth = dp(if (highContrast) 2f else 1f); fill.color = if (highContrast) Color.WHITE else Color.argb(80, 255, 255, 255)
                canvas.drawRoundRect(chip.rect, dp(10f), dp(10f), fill)
                fill.style = Paint.Style.FILL
            }
            uiBold.color = if (enabled || highContrast) Color.WHITE else Color.argb(170, 255, 255, 255)
            var size = sp(14f)
            uiBold.textSize = size
            val tw = uiBold.measureText(chip.label)
            if (tw > w - dp(8f)) { size *= (w - dp(8f)) / tw; uiBold.textSize = size }
            canvas.drawText(chip.label, chip.rect.centerX() - uiBold.measureText(chip.label) / 2,
                chip.rect.centerY() - (uiBold.descent() + uiBold.ascent()) / 2, uiBold)
            x += w + gap
        }
    }

    // ------------------------------------------------------------ touch

    private fun send(b: ByteArray) {
        if (haptics) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        onInput?.invoke(b)
    }

    private var pinching = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
            val s = state ?: return false
            if (s.displayMode != GameState.DISPLAY_MAP || !mapRect.contains(d.focusX, d.focusY)) return false
            pinching = true
            return true
        }
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val s = state ?: return false
            val fitAll = minOf(mapRect.width() / s.mapW, mapRect.height() / s.mapH)
            val newPx = (tilePx * d.scaleFactor).coerceIn(fitAll, dp(80f))
            tileDp = newPx / density
            return true
        }
        override fun onScaleEnd(d: ScaleGestureDetector) { onZoomChanged?.invoke(tileDp) }
    })

    /** Direction key from the player towards a map point, or null. */
    private fun directionTo(s: GameState, x: Float, y: Float): Char? {
        val tx = ((x - mapRect.left + camX) / tilePx).toInt()
        val ty = ((y - mapRect.top + camY) / tilePx).toInt()
        val dx = tx - s.playerX
        val dy = ty - s.playerY
        if (dx == 0 && dy == 0) return null
        return sectorDir(dx.toFloat(), dy.toFloat())
    }

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            val s = state ?: return false
            if (s.displayMode == GameState.DISPLAY_TEXT && textPanMax > 0f) {
                textPanX = (textPanX + dx).coerceIn(0f, textPanMax)
                invalidate()
                return true
            }
            return false
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (pinching) return true
            for (chip in chips) if (chip.rect.contains(e.x, e.y)) {
                chip.keys?.let { send(it) }
                chip.action?.invoke()
                return true
            }
            val s = state
            if (!running || s == null) { onIdleTap?.invoke(); return true }
            if (s.displayMode == GameState.DISPLAY_TEXT || !mapRect.contains(e.x, e.y)) {
                // Tap the page to continue ("press space / return to continue").
                if (s.inputMode == GameState.INPUT_PROMPT) {
                    val a = s.answers
                    val k = when {
                        ' ' in a -> ' '
                        '\r' in a -> '\r'
                        a.length == 1 -> a[0]
                        else -> null
                    }
                    if (k != null) send(byteArrayOf(k.code.toByte()))
                }
                return true
            }
            if (tapToMove && (s.inputMode == GameState.INPUT_COMMAND || s.inputMode == GameState.INPUT_DIR)) {
                directionTo(s, e.x, e.y)?.let { send(byteArrayOf(it.code.toByte())) }
            }
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            if (pinching) return
            val s = state ?: return
            if (!running || !tapToMove || s.displayMode != GameState.DISPLAY_MAP || !mapRect.contains(e.x, e.y)) return
            if (s.inputMode != GameState.INPUT_COMMAND) return
            directionTo(s, e.x, e.y)?.let { send(byteArrayOf(it.uppercaseChar().code.toByte())) }   // run
        }
    })

    // ------------------------------------------------------------ swipes
    //
    // Swipe on the map: one step in that direction. Keep the finger down to
    // keep walking (steer by moving it around the starting point, like a
    // joystick). With flickToRun, a quick flick runs instead. In a direction
    // prompt a swipe answers it.

    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeDownTime = 0L
    private var swipeActive = false
    private var swipeDir: Char? = null
    private var swipeStepped = false

    private val swipeRepeat = object : Runnable {
        override fun run() {
            val s = state
            val d = swipeDir
            if (swipeActive && d != null && s != null && s.inputMode == GameState.INPUT_COMMAND) {
                send(byteArrayOf(d.code.toByte()))
                swipeStepped = true
            }
            if (swipeActive) postDelayed(this, SWIPE_REPEAT_MS)
        }
    }

    private fun swipeAllowed(s: GameState?): Boolean =
        swipeToMove && running && s != null && s.displayMode == GameState.DISPLAY_MAP &&
            (s.inputMode == GameState.INPUT_COMMAND || s.inputMode == GameState.INPUT_DIR)

    private fun inSwipeArea(x: Float, y: Float): Boolean =
        mapRect.contains(x, y) && chips.none { it.rect.contains(x, y) }

    private fun sectorDir(dx: Float, dy: Float): Char {
        var deg = Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble()))
        if (deg < 0) deg += 360.0
        return "lukyhbjn"[(((deg + 22.5) % 360) / 45).toInt()]
    }

    /** Returns true if the event was consumed as part of a swipe. */
    private fun handleSwipe(e: MotionEvent): Boolean {
        val s = state
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeActive = false; swipeDir = null; swipeStepped = false
                swipeStartX = e.x; swipeStartY = e.y; swipeDownTime = e.eventTime
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (pinching || e.pointerCount > 1) { endSwipe(); return false }
                if (!swipeActive && (!swipeAllowed(s) || !inSwipeArea(swipeStartX, swipeStartY))) return false
                val dx = e.x - swipeStartX
                val dy = e.y - swipeStartY
                if (hypot(dx, dy) < dp(SWIPE_THRESHOLD_DP)) return swipeActive
                val d = sectorDir(dx, dy)
                if (!swipeActive) {
                    swipeActive = true
                    swipeDir = d
                    removeCallbacks(swipeRepeat)
                    if (flickToRun) {
                        // Wait briefly before the first step so a flick can become a run.
                        postDelayed(firstStep, FLICK_MS)
                    } else {
                        firstStep.run()
                    }
                    invalidate()
                } else if (d != swipeDir) {
                    swipeDir = d
                    if (swipeStepped && s?.inputMode == GameState.INPUT_COMMAND) {
                        send(byteArrayOf(d.code.toByte()))
                        removeCallbacks(swipeRepeat)
                        postDelayed(swipeRepeat, SWIPE_HOLD_MS)
                    }
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!swipeActive) return false
                val d = swipeDir
                val quick = flickToRun && e.eventTime - swipeDownTime < FLICK_MS
                if (!swipeStepped && d != null && s != null) {
                    if (s.inputMode == GameState.INPUT_DIR) send(byteArrayOf(d.code.toByte()))
                    else if (quick) send(byteArrayOf(d.uppercaseChar().code.toByte()))   // flick: run
                    else send(byteArrayOf(d.code.toByte()))
                }
                endSwipe()
                return true
            }
            MotionEvent.ACTION_CANCEL -> { val was = swipeActive; endSwipe(); return was }
        }
        return swipeActive
    }

    /** First step of a held swipe (fires unless the gesture ended as a flick). */
    private val firstStep = Runnable {
        val s = state
        val d = swipeDir
        if (swipeActive && d != null && s != null && !swipeStepped) {
            send(byteArrayOf(d.code.toByte()))
            swipeStepped = true
            if (s.inputMode == GameState.INPUT_COMMAND) postDelayed(swipeRepeat, SWIPE_HOLD_MS)
        }
    }

    private fun endSwipe() {
        removeCallbacks(firstStep)
        removeCallbacks(swipeRepeat)
        if (swipeActive) { swipeActive = false; swipeDir = null; invalidate() }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) pinching = false
        if (event.pointerCount > 1) pinching = true
        if (handleSwipe(event)) {
            // Keep the gesture detector from also seeing a tap / long-press.
            val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
            gestures.onTouchEvent(cancel)
            cancel.recycle()
            return true
        }
        gestures.onTouchEvent(event)
        return true
    }

    companion object {
        private const val SWIPE_THRESHOLD_DP = 28f
        /** A swipe that ends within this time is a flick (= run). */
        private const val FLICK_MS = 180L
        /** Hold this long after the first step before walking continuously. */
        private const val SWIPE_HOLD_MS = 320L
        private const val SWIPE_REPEAT_MS = 150L
        private val DIR_VEC = mapOf(
            'l' to (1 to 0), 'h' to (-1 to 0), 'k' to (0 to -1), 'j' to (0 to 1),
            'u' to (1 to -1), 'y' to (-1 to -1), 'n' to (1 to 1), 'b' to (-1 to 1),
        )
    }

    // --------------------------------------------------------- keyboard

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun showKeyboard(show: Boolean) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (show) { requestFocus(); imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT) }
        else imm.hideSoftInputFromWindow(windowToken, 0)
    }

    override fun onCheckIsTextEditor() = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_NULL
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                sendText(text.toString()); return true
            }
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(maxOf(1, beforeLength)) { onInput?.invoke(byteArrayOf(8)) }
                return true
            }
            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) handleKey(event.keyCode, event)
                return true
            }
        }
    }

    private fun sendText(s: String) {
        val bytes = s.map { if (it == '\n') 13.toByte() else it.code.coerceAtMost(0x7f).toByte() }
        onInput?.invoke(bytes.toByteArray())
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        handleKey(keyCode, event) || super.onKeyDown(keyCode, event)

    /** Hardware / soft keyboard key. Returns true if consumed. */
    fun handleKey(keyCode: Int, event: KeyEvent): Boolean {
        val shift = event.isShiftPressed
        fun mv(c: Char) = (if (shift) c.uppercaseChar() else c).code.toByte()
        val bytes: ByteArray? = when (keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> byteArrayOf(13)
            KeyEvent.KEYCODE_DEL -> byteArrayOf(8)
            KeyEvent.KEYCODE_ESCAPE -> byteArrayOf(27)
            KeyEvent.KEYCODE_DPAD_LEFT -> if (arrowsAsMoves) byteArrayOf(mv('h')) else null
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (arrowsAsMoves) byteArrayOf(mv('l')) else null
            KeyEvent.KEYCODE_DPAD_UP -> if (arrowsAsMoves) byteArrayOf(mv('k')) else null
            KeyEvent.KEYCODE_DPAD_DOWN -> if (arrowsAsMoves) byteArrayOf(mv('j')) else null
            KeyEvent.KEYCODE_MOVE_HOME -> byteArrayOf(mv('y'))
            KeyEvent.KEYCODE_PAGE_UP -> byteArrayOf(mv('u'))
            KeyEvent.KEYCODE_MOVE_END -> byteArrayOf(mv('b'))
            KeyEvent.KEYCODE_PAGE_DOWN -> byteArrayOf(mv('n'))
            else -> null
        }
        if (bytes != null) { onInput?.invoke(bytes); return true }

        if (arrowsAsMoves && !event.isNumLockOn) {
            val m = when (keyCode) {
                KeyEvent.KEYCODE_NUMPAD_7 -> 'y'; KeyEvent.KEYCODE_NUMPAD_8 -> 'k'; KeyEvent.KEYCODE_NUMPAD_9 -> 'u'
                KeyEvent.KEYCODE_NUMPAD_4 -> 'h'; KeyEvent.KEYCODE_NUMPAD_5 -> '.'; KeyEvent.KEYCODE_NUMPAD_6 -> 'l'
                KeyEvent.KEYCODE_NUMPAD_1 -> 'b'; KeyEvent.KEYCODE_NUMPAD_2 -> 'j'; KeyEvent.KEYCODE_NUMPAD_3 -> 'n'
                else -> null
            }
            if (m != null) { onInput?.invoke(byteArrayOf(mv(m))); return true }
        }

        if (event.isCtrlPressed) {
            val base = event.getUnicodeChar(0)
            if (base in 'a'.code..'z'.code) { onInput?.invoke(byteArrayOf((base - 'a'.code + 1).toByte())); return true }
        }
        val uc = event.unicodeChar
        if (uc in 0x20..0x7e) { onInput?.invoke(byteArrayOf(uc.toByte())); return true }
        return false
    }
}
