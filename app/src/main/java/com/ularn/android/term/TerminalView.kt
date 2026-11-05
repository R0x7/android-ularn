package com.ularn.android.term

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.InputType
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

/**
 * Draws a [TerminalEmulator] scaled so all 80x24 cells fit, and turns
 * keyboard input (hardware or soft) into bytes for the game.
 *
 * Pinch to zoom, drag to pan while zoomed, double-tap to reset zoom.
 */
class TerminalView(context: Context) : View(context) {

    var emulator: TerminalEmulator? = null
        set(v) { field = v; invalidate() }

    /** Called with bytes the user typed. */
    var onInput: ((ByteArray) -> Unit)? = null

    /** Map arrow keys / numpad to Ularn's hjklyubn. */
    var arrowsAsMoves = true

    var showCursor = true

    /** In portrait the map is pinned to the top so controls fit below it. */
    var alignTop = true
        set(v) { field = v; computeMetrics(); invalidate() }

    data class Theme(val name: String, val bg: Int, val fg: Int, val bold: Int, val cursor: Int)

    var theme: Theme = THEMES[0]
        set(v) { field = v; invalidate() }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
    }
    private val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val bgPaint = Paint()
    private val tmpRect = RectF()

    private var cellW = 0f
    private var cellH = 0f
    private var baseline = 0f
    private var originX = 0f
    private var originY = 0f

    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f

    private var reserveLeft = 0f
    private var reserveRight = 0f

    /** Keep the map clear of side controls (px from each edge). */
    fun setHorizontalReserve(left: Float, right: Float) {
        if (left == reserveLeft && right == reserveRight) return
        reserveLeft = left; reserveRight = right
        computeMetrics(); invalidate()
    }

    /** Bottom edge (px, unzoomed) of the drawn terminal; used for layout hints. */
    var terminalBottom = 0f
        private set

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val old = zoom
            zoom = (zoom * d.scaleFactor).coerceIn(1f, 4f)
            // keep the focal point stable
            val f = zoom / old
            panX = d.focusX - (d.focusX - panX) * f
            panY = d.focusY - (d.focusY - panY) * f
            clampPan()
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (zoom > 1f) { panX -= dx; panY -= dy; clampPan(); invalidate() }
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            zoom = 1f; panX = 0f; panY = 0f; invalidate(); return true
        }
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onTap?.invoke(); return true
        }
    })

    var onTap: (() -> Unit)? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        computeMetrics()
    }

    private fun computeMetrics() {
        val emu = emulator ?: return
        if (width == 0 || height == 0) return
        // Find the largest text size where cols*charWidth <= width and rows*lineHeight <= height.
        textPaint.textSize = 100f
        val wPer100 = textPaint.measureText("M")
        val fm = textPaint.fontMetrics
        val hPer100 = fm.descent - fm.ascent
        // Fit between side reserves; ignore them if they'd leave too little room.
        val reserved = width - reserveLeft - reserveRight
        val useReserve = reserved >= width * 0.4f
        val availW = if (useReserve) reserved else width.toFloat()
        val startX = if (useReserve) reserveLeft else 0f
        val byWidth = availW / (emu.columns * wPer100) * 100f
        val byHeight = height / (emu.rows * hPer100) * 100f
        val size = minOf(byWidth, byHeight)
        textPaint.textSize = size
        boldPaint.textSize = size
        cellW = textPaint.measureText("M")
        val m = textPaint.fontMetrics
        cellH = m.descent - m.ascent
        baseline = -m.ascent
        originX = startX + (availW - cellW * emu.columns) / 2f
        originY = if (alignTop) 0f else (height - cellH * emu.rows) / 2f
        terminalBottom = originY + cellH * emu.rows
    }

    private fun clampPan() {
        panX = panX.coerceIn(width * (1 - zoom), 0f)
        panY = panY.coerceIn(height * (1 - zoom), 0f)
    }

    override fun onDraw(canvas: Canvas) {
        val emu = emulator ?: return
        if (cellW == 0f) computeMetrics()
        canvas.drawColor(theme.bg)
        canvas.save()
        canvas.translate(panX, panY)
        canvas.scale(zoom, zoom)
        textPaint.color = theme.fg
        boldPaint.color = theme.bold
        synchronized(emu.lock) {
            val cols = emu.columns
            val one = CharArray(1)
            for (r in 0 until emu.rows) {
                val y = originY + r * cellH
                for (c in 0 until cols) {
                    val idx = r * cols + c
                    val ch = emu.chars[idx]
                    val a = emu.attrs[idx].toInt()
                    val inverse = a and TerminalEmulator.ATTR_INVERSE != 0
                    val x = originX + c * cellW
                    if (inverse) {
                        bgPaint.color = theme.fg
                        tmpRect.set(x, y, x + cellW + 0.5f, y + cellH + 0.5f)
                        canvas.drawRect(tmpRect, bgPaint)
                    }
                    if (ch != ' ') {
                        val paint = if (a and TerminalEmulator.ATTR_BOLD != 0) boldPaint else textPaint
                        val saved = paint.color
                        if (inverse) paint.color = theme.bg
                        one[0] = ch
                        canvas.drawText(one, 0, 1, x, y + baseline, paint)
                        paint.color = saved
                    }
                }
            }
            if (showCursor) {
                val cc = minOf(emu.cursorCol, cols - 1)
                val x = originX + cc * cellW
                val y = originY + emu.cursorRow * cellH
                bgPaint.color = theme.cursor
                tmpRect.set(x, y + cellH * 0.82f, x + cellW, y + cellH)
                canvas.drawRect(tmpRect, bgPaint)
            }
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) gestureDetector.onTouchEvent(event)
        return true
    }

    // ------------------------------------------------------------ keyboard

    fun showKeyboard(show: Boolean) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (show) {
            requestFocus()
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        } else {
            imm.hideSoftInputFromWindow(windowToken, 0)
        }
    }

    override fun onCheckIsTextEditor() = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        // TYPE_NULL makes most keyboards send raw key events with no
        // autocorrect / composition, which is what a terminal wants.
        outAttrs.inputType = InputType.TYPE_NULL
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                sendText(text.toString()); return true
            }
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(maxOf(1, beforeLength)) { onInput?.invoke(byteArrayOf(0x7f)) }
                return true
            }
            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) handleKey(event.keyCode, event)
                return true
            }
        }
    }

    private fun sendText(s: String) {
        val bytes = s.map { if (it == '\n') 0x0d.toByte() else it.code.coerceAtMost(0x7f).toByte() }
        onInput?.invoke(bytes.toByteArray())
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        handleKey(keyCode, event) || super.onKeyDown(keyCode, event)

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean {
        val chars = event.characters
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN && chars != null) { sendText(chars); return true }
        return super.onKeyMultiple(keyCode, repeatCount, event)
    }

    /** Returns true if the key was consumed. */
    fun handleKey(keyCode: Int, event: KeyEvent): Boolean {
        val shift = event.isShiftPressed
        fun mv(c: Char) = (if (shift) c.uppercaseChar() else c).code.toByte()
        val bytes: ByteArray? = when (keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> byteArrayOf(0x0d)
            KeyEvent.KEYCODE_DEL -> byteArrayOf(0x7f)
            KeyEvent.KEYCODE_ESCAPE -> byteArrayOf(0x1b)
            KeyEvent.KEYCODE_TAB -> byteArrayOf(0x09)
            KeyEvent.KEYCODE_DPAD_LEFT -> if (arrowsAsMoves) byteArrayOf(mv('h')) else "\u001b[D".toByteArray()
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (arrowsAsMoves) byteArrayOf(mv('l')) else "\u001b[C".toByteArray()
            KeyEvent.KEYCODE_DPAD_UP -> if (arrowsAsMoves) byteArrayOf(mv('k')) else "\u001b[A".toByteArray()
            KeyEvent.KEYCODE_DPAD_DOWN -> if (arrowsAsMoves) byteArrayOf(mv('j')) else "\u001b[B".toByteArray()
            KeyEvent.KEYCODE_MOVE_HOME -> byteArrayOf(mv('y'))
            KeyEvent.KEYCODE_PAGE_UP -> byteArrayOf(mv('u'))
            KeyEvent.KEYCODE_MOVE_END -> byteArrayOf(mv('b'))
            KeyEvent.KEYCODE_PAGE_DOWN -> byteArrayOf(mv('n'))
            else -> null
        }
        if (bytes != null) { onInput?.invoke(bytes); return true }

        // Numpad digits as movement when NumLock is off
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

    companion object {
        val THEMES = listOf(
            Theme("Classic", Color.rgb(12, 12, 14), Color.rgb(210, 210, 205), Color.WHITE, Color.rgb(255, 200, 60)),
            Theme("Green phosphor", Color.rgb(4, 14, 6), Color.rgb(60, 230, 110), Color.rgb(170, 255, 190), Color.rgb(170, 255, 190)),
            Theme("Amber", Color.rgb(16, 10, 2), Color.rgb(255, 176, 40), Color.rgb(255, 220, 140), Color.rgb(255, 230, 170)),
            Theme("Paper", Color.rgb(244, 240, 228), Color.rgb(40, 36, 30), Color.BLACK, Color.rgb(200, 70, 40)),
        )
    }
}
