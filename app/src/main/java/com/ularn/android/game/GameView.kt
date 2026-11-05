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

/**
 * Draws Ularn 1.6.3a's display from [GameState]:
 *
 *  map mode : HP bar, the two status lines, active effects, the tile map
 *             (follows the player; pinch to zoom; tap to step), the message
 *             lines, and tap-to-answer buttons for the current prompt;
 *  text mode: the full 80x25 text page (shops, inventory, help, scores).
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
    var haptics = true

    /** Preferred tile size in dp (0 = automatic). */
    var tileDp = 0f
        set(v) { field = v; invalidate() }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val atlas: Bitmap? = try {
        context.assets.open("ularn_tiles.png").use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })
        }
    } catch (_: Exception) { null }
    private val atlasCols = 16
    private val tileSrc = 32

    private val tilePaint = Paint().apply { isFilterBitmap = false; isDither = false }
    private val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    private val monoBold = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
    private val ui = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val srcRect = Rect()
    private val dstRect = RectF()

    private var reserveLeft = 0f
    private var reserveRight = 0f
    private var reserveBottom = 0f

    fun setReserve(left: Float, right: Float, bottom: Float) {
        if (left == reserveLeft && right == reserveRight && bottom == reserveBottom) return
        reserveLeft = left; reserveRight = right; reserveBottom = bottom
        invalidate()
    }

    // ------------------------------------------------------------ colors

    private val bg = Color.rgb(10, 10, 12)
    private val fgNormal = Color.rgb(214, 214, 208)
    private val fgRed = Color.rgb(255, 110, 100)
    private val fgGreen = Color.rgb(110, 220, 120)
    private val fgBlue = Color.rgb(120, 170, 255)
    private val accent = Color.rgb(255, 190, 60)
    private val panel = Color.rgb(24, 24, 28)

    private fun fmtColor(f: Int) = when (f) {
        GameState.FMT_STANDOUT -> fgRed
        GameState.FMT_STANDOUT2 -> fgGreen
        GameState.FMT_STANDOUT3 -> fgBlue
        GameState.FMT_BOLD -> Color.WHITE
        else -> fgNormal
    }

    // ----------------------------------------------------------- layout

    /** Computed each frame; used for hit-testing. */
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

    override fun onAttachedToWindow() { super.onAttachedToWindow(); postDelayed(blink, 530) }
    override fun onDetachedFromWindow() { removeCallbacks(blink); super.onDetachedFromWindow() }

    // ------------------------------------------------------------- draw

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(bg)
        content.set(reserveLeft, 0f, width - reserveRight, height - reserveBottom)
        if (content.width() < dp(100f) || content.height() < dp(100f)) content.set(0f, 0f, width.toFloat(), height.toFloat())

        val s = state
        if (s == null || !s.refresh()) { drawIdle(canvas); return }

        if (s.beepCount != lastBeep) {
            if (lastBeep >= 0 && haptics) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            lastBeep = s.beepCount
        }

        buildChips(s)
        val chipH = if (chips.isEmpty()) 0f else dp(40f)
        val area = RectF(content.left, content.top, content.right, content.bottom - chipH)

        if (s.displayMode == GameState.DISPLAY_TEXT) drawTextPage(canvas, s, area)
        else drawMapMode(canvas, s, area)

        if (chips.isNotEmpty()) drawChips(canvas, RectF(content.left, content.bottom - chipH, content.right, content.bottom))
    }

    /** Before the first game the start menu covers the screen; nothing to draw. */
    @Suppress("UNUSED_PARAMETER")
    private fun drawIdle(canvas: Canvas) {}

    /** Text size that fits [cols] monospace columns into [w] px. */
    private fun fitMono(cols: Int, w: Float, maxPx: Float): Float {
        mono.textSize = 100f
        val per100 = mono.measureText("M")
        return minOf(maxPx, w / (cols * per100) * 100f)
    }

    private fun drawLine(canvas: Canvas, x: Float, baseline: Float, cellW: Float, lineH: Float,
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
                p.textSize = mono.textSize
                p.color = if (f == GameState.FMT_INVERSE) bg else fmtColor(f)
                one[0] = c
                canvas.drawText(one, 0, 1, cx, baseline, p)
            }
        }
    }

    private fun drawCaret(canvas: Canvas, x: Float, baseline: Float, cellW: Float) {
        if (!caretOn) return
        fill.color = accent
        canvas.drawRect(x, baseline + dp(1f), x + cellW, baseline + dp(3f), fill)
    }

    private fun drawTextPage(canvas: Canvas, s: GameState, area: RectF) {
        val cols = s.lineLen
        val rows = s.textLines
        var size = fitMono(cols, area.width() - dp(8f), dp(22f))
        mono.textSize = size
        var lineH = mono.fontSpacing
        if (lineH * rows > area.height()) {          // also fit vertically
            size *= area.height() / (lineH * rows)
            mono.textSize = size
            lineH = mono.fontSpacing
        }
        val cellW = mono.measureText("M")
        val x0 = area.left + (area.width() - cellW * cols) / 2f
        val y0 = area.top + maxOf(0f, (area.height() - lineH * rows) / 2f)
        for (r in 0 until rows) {
            val base = y0 + r * lineH - mono.fontMetrics.ascent
            drawLine(canvas, x0, base, cellW, lineH, cols, { s.textChar(r, it) }, { s.textFmt(r, it) })
        }
        if (s.caret) drawCaret(canvas, x0 + (s.cursorX - 1) * cellW, y0 + (s.cursorY - 1) * lineH - mono.fontMetrics.ascent, cellW)
    }

    private fun drawMapMode(canvas: Canvas, s: GameState, area: RectF) {
        val pad = dp(4f)
        val cols = s.lineLen
        val size = fitMono(cols, area.width() - 2 * pad, dp(15f))
        mono.textSize = size
        val lineH = mono.fontSpacing
        val cellW = mono.measureText("M")
        val x0 = area.left + pad
        var y = area.top + pad

        // HP bar
        val hpFrac = if (s.hpMax > 0) (s.hp.toFloat() / s.hpMax).coerceIn(0f, 1f) else 0f
        fill.color = panel
        canvas.drawRoundRect(x0, y, area.right - pad, y + dp(5f), dp(3f), dp(3f), fill)
        fill.color = when {
            hpFrac > 0.6f -> Color.rgb(90, 200, 110)
            hpFrac > 0.3f -> Color.rgb(240, 190, 60)
            else -> Color.rgb(235, 80, 70)
        }
        canvas.drawRoundRect(x0, y, x0 + (area.right - pad - x0) * hpFrac, y + dp(5f), dp(3f), dp(3f), fill)
        y += dp(8f)

        // status lines (the original Ularn layout)
        mono.color = fgNormal
        for (line in listOf(s.status1, s.status2)) {
            canvas.drawText(line, x0, y - mono.fontMetrics.ascent, mono)
            y += lineH
        }

        // effects
        val active = GameState.EFFECT_NAMES.filterIndexed { i, _ -> s.effects and (1 shl i) != 0 }
        if (active.isNotEmpty()) {
            ui.textSize = dp(11f)
            var cx = x0
            val h = dp(18f)
            for (name in active) {
                val w = ui.measureText(name) + dp(12f)
                if (cx + w > area.right - pad) break
                fill.color = Color.argb(200, 60, 50, 20)
                canvas.drawRoundRect(cx, y + dp(2f), cx + w, y + dp(2f) + h, h / 2, h / 2, fill)
                ui.color = accent
                canvas.drawText(name, cx + w / 2, y + dp(2f) + h / 2 - (ui.descent() + ui.ascent()) / 2, ui)
                cx += w + dp(4f)
            }
            y += h + dp(4f)
        }

        // message lines at the bottom of the area
        val msgTop = area.bottom - pad - lineH * s.msgLines
        fill.color = panel
        canvas.drawRect(area.left, msgTop - dp(2f), area.right, area.bottom, fill)
        for (r in 0 until s.msgLines) {
            val base = msgTop + r * lineH - mono.fontMetrics.ascent
            drawLine(canvas, x0, base, cellW, lineH, cols, { s.msgChar(r, it) }, { s.msgFmt(r, it) })
        }
        if (s.caret) drawCaret(canvas, x0 + (s.cursorX - 1) * cellW, msgTop + (s.cursorY - 1) * lineH - mono.fontMetrics.ascent, cellW)

        // map between status and messages
        mapRect.set(area.left, y + dp(2f), area.right, msgTop - dp(4f))
        drawMap(canvas, s)
    }

    private fun defaultTilePx(fitAll: Float): Float {
        val pref = if (tileDp > 0f) dp(tileDp) else maxOf(fitAll, dp(26f))
        return pref.coerceIn(fitAll, dp(80f))
    }

    private fun drawMap(canvas: Canvas, s: GameState) {
        val atlas = atlas ?: return
        if (mapRect.height() < dp(20f)) return
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
        for (ty in y0..y1) for (tx in x0..x1) {
            val left = mapRect.left + tx * t - camX
            val top = mapRect.top + ty * t - camY
            dstRect.set(left, top, left + t + 0.5f, top + t + 0.5f)
            drawTile(canvas, atlas, s.tile(tx, ty))
            val o = s.overlay(tx, ty)
            if (o >= 0) drawTile(canvas, atlas, o)
            if (tx == s.playerX && ty == s.playerY && s.playerCursor) drawTile(canvas, atlas, GameState.TILE_CURSOR)
        }
        canvas.restore()
    }

    private fun drawTile(canvas: Canvas, atlas: Bitmap, id: Int) {
        if (id < 0) return
        val sx = (id % atlasCols) * tileSrc
        val sy = (id / atlasCols) * tileSrc
        if (sy + tileSrc > atlas.height) return
        srcRect.set(sx, sy, sx + tileSrc, sy + tileSrc)
        canvas.drawBitmap(atlas, srcRect, dstRect, tilePaint)
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
            GameState.INPUT_DIR -> chips += Chip("Pick a direction", null)
        }
    }

    private fun drawChips(canvas: Canvas, bar: RectF) {
        val gap = dp(4f)
        val n = chips.size
        val w = ((bar.width() - gap * (n + 1)) / n).coerceAtMost(dp(96f))
        val total = n * w + (n - 1) * gap
        var x = bar.centerX() - total / 2
        ui.textSize = dp(13f)
        for (chip in chips) {
            chip.rect.set(x, bar.top + gap, x + w, bar.bottom - gap)
            val enabled = chip.keys != null || chip.action != null
            fill.color = if (enabled) Color.argb(230, 50, 52, 62) else Color.argb(120, 50, 52, 62)
            canvas.drawRoundRect(chip.rect, dp(10f), dp(10f), fill)
            ui.color = if (enabled) Color.WHITE else Color.argb(170, 255, 255, 255)
            var size = dp(13f)
            ui.textSize = size
            val tw = ui.measureText(chip.label)
            if (tw > w - dp(6f)) { size *= (w - dp(6f)) / tw; ui.textSize = size }
            canvas.drawText(chip.label, chip.rect.centerX(), chip.rect.centerY() - (ui.descent() + ui.ascent()) / 2, ui)
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
        // 8 sectors by angle so far taps still pick a sensible direction
        var deg = Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble()))
        if (deg < 0) deg += 360.0
        return "lukyhbjn"[(((deg + 22.5) % 360) / 45).toInt()]
    }

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) pinching = false
        if (event.pointerCount > 1) pinching = true
        gestures.onTouchEvent(event)
        return true
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
