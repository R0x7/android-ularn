package com.ularn.android.controls

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Transparent layer over the terminal that draws and runs the on-screen
 * controls. In edit mode, controls can be dragged, resized (bottom-right
 * handle) and tapped to open their properties.
 */
@SuppressLint("ViewConstructor")
class ControlsOverlay(context: Context) : View(context) {

    interface Listener {
        fun onKeys(bytes: ByteArray)
        fun onAction(action: ButtonAction)
        /** Edit mode: user tapped an already-selected control. */
        fun onEditControl(spec: ControlSpec)
        /** Edit mode: geometry changed (call [layout] to read it). */
        fun onLayoutEdited()
        fun onSelectionChanged(spec: ControlSpec?) {}
    }

    var listener: Listener? = null

    var layout: ControlLayout = ControlLayout()
        set(v) { field = v; selectedId = null; presses.clear(); invalidate() }

    var editMode = false
        set(v) {
            field = v
            selectedId = null
            presses.clear()
            mainHandler.removeCallbacksAndMessages(null)
            listener?.onSelectionChanged(null)
            invalidate()
        }

    var snapToGrid = true

    var selectedId: String? = null
        private set

    val selected: ControlSpec? get() = layout.controls.firstOrNull { it.id == selectedId }

    /** Run modifier state. */
    private var runArmed = false
    private var runLocked = false

    private val density = resources.displayMetrics.density
    private val mainHandler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }
    private val rect = RectF()
    private val path = Path()

    private val accent = Color.rgb(255, 190, 60)

    // ------------------------------------------------------------- geometry

    fun rectFor(c: ControlSpec, out: RectF = RectF()): RectF {
        val w = c.widthDp * density
        val h = c.heightDp * density
        val x = (c.cx * width).coerceIn(w / 2, maxOf(w / 2, width - w / 2))
        val y = (c.cy * height).coerceIn(h / 2, maxOf(h / 2, height - h / 2))
        out.set(x - w / 2, y - h / 2, x + w / 2, y + h / 2)
        return out
    }

    private fun hit(x: Float, y: Float, slop: Float = 6f * density): ControlSpec? {
        // topmost = last drawn
        for (i in layout.controls.indices.reversed()) {
            val c = layout.controls[i]
            rectFor(c, rect)
            rect.inset(-slop, -slop)
            if (c.type == ControlType.DPAD || c.shape == ButtonShape.CIRCLE) {
                val r = minOf(rect.width(), rect.height()) / 2
                if (hypot(x - rect.centerX(), y - rect.centerY()) <= r) return c
            } else if (rect.contains(x, y)) return c
        }
        return null
    }

    // --------------------------------------------------------------- play

    private class Press(val spec: ControlSpec, var zone: Int, val downX: Float, val downY: Float) {
        var inside = true
        var repeater: Runnable? = null
    }

    private val presses = HashMap<Int, Press>()

    private fun haptic() {
        if (layout.haptics) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun sendKeys(notation: String) {
        val b = KeySequence.parse(notation)
        if (b.isNotEmpty()) listener?.onKeys(b)
    }

    private fun dpadZone(spec: ControlSpec, x: Float, y: Float): Int {
        rectFor(spec, rect)
        return DpadMath.zoneFor(x - rect.centerX(), y - rect.centerY(), rect.width() / 2, spec.eightWay)
    }

    private fun fireDpad(spec: ControlSpec, zone: Int) {
        var k = spec.dpadKeys.getOrElse(zone) { "" }
        if (k.isEmpty()) return
        if (zone != 4 && (runArmed || runLocked)) {
            k = DpadMath.runVariant(k)
            if (!runLocked) runArmed = false
        }
        haptic()
        sendKeys(k)
    }

    private fun startRepeat(p: Press, action: () -> Unit) {
        stopRepeat(p)
        val r = object : Runnable {
            override fun run() { action(); mainHandler.postDelayed(this, 110) }
        }
        p.repeater = r
        mainHandler.postDelayed(r, 320)
    }

    private fun stopRepeat(p: Press) {
        p.repeater?.let { mainHandler.removeCallbacks(it) }
        p.repeater = null
    }

    private val runLockRunnable = Runnable {
        runLocked = true; runArmed = false; haptic(); invalidate()
    }

    private fun pressDown(pointerId: Int, x: Float, y: Float): Boolean {
        val spec = hit(x, y) ?: return false
        val p = Press(spec, -1, x, y)
        presses[pointerId] = p
        when (spec.type) {
            ControlType.DPAD -> {
                p.zone = dpadZone(spec, x, y)
                fireDpad(spec, p.zone)
                if (spec.repeat && p.zone != 4) startRepeat(p) { fireDpad(spec, p.zone) }
            }
            ControlType.BUTTON -> when (spec.action) {
                ButtonAction.KEYS -> {
                    haptic(); sendKeys(spec.keys)
                    if (spec.repeat) startRepeat(p) { sendKeys(spec.keys) }
                }
                ButtonAction.RUN -> {
                    haptic()
                    when {
                        runLocked -> { runLocked = false; runArmed = false }
                        runArmed -> runArmed = false
                        else -> { runArmed = true; mainHandler.postDelayed(runLockRunnable, 550) }
                    }
                }
                else -> haptic() // fires on release
            }
        }
        invalidate()
        return true
    }

    private fun pressMove(pointerId: Int, x: Float, y: Float) {
        val p = presses[pointerId] ?: return
        if (p.spec.type == ControlType.DPAD) {
            val z = dpadZone(p.spec, x, y)
            if (z != p.zone) {
                p.zone = z
                stopRepeat(p)
                if (z != 4) {           // sliding into the centre just pauses
                    fireDpad(p.spec, z)
                    if (p.spec.repeat) startRepeat(p) { fireDpad(p.spec, p.zone) }
                }
                invalidate()
            }
        } else {
            rectFor(p.spec, rect)
            rect.inset(-24 * density, -24 * density)
            val inside = rect.contains(x, y)
            if (inside != p.inside) { p.inside = inside; if (!inside) stopRepeat(p); invalidate() }
        }
    }

    private fun pressUp(pointerId: Int, cancelled: Boolean) {
        val p = presses.remove(pointerId) ?: return
        stopRepeat(p)
        if (p.spec.action == ButtonAction.RUN) mainHandler.removeCallbacks(runLockRunnable)
        if (!cancelled && p.inside && p.spec.type == ControlType.BUTTON &&
            p.spec.action != ButtonAction.KEYS && p.spec.action != ButtonAction.RUN
        ) listener?.onAction(p.spec.action)
        invalidate()
    }

    // --------------------------------------------------------------- edit

    private enum class Drag { NONE, MOVE, RESIZE }

    private var drag = Drag.NONE
    private var dragOffX = 0f
    private var dragOffY = 0f
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragMoved = false
    private var wasSelected = false
    private var dragDownTime = 0L

    private fun handleRadius() = 14f * density

    private fun onHandle(c: ControlSpec, x: Float, y: Float): Boolean {
        rectFor(c, rect)
        return hypot(x - rect.right, y - rect.bottom) <= handleRadius() * 1.8f
    }

    private fun snap(px: Float): Float {
        if (!snapToGrid) return px
        val g = 8f * density
        return (px / g).roundToInt() * g
    }

    private fun editTouch(e: MotionEvent): Boolean {
        val x = e.x; val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = x; dragStartY = y; dragMoved = false
                dragDownTime = SystemClock.uptimeMillis()
                val sel = selected
                if (sel != null && onHandle(sel, x, y)) {
                    drag = Drag.RESIZE; wasSelected = true
                } else {
                    val c = hit(x, y, 0f)
                    if (c == null) {
                        drag = Drag.NONE
                        if (selectedId != null) { selectedId = null; listener?.onSelectionChanged(null) }
                    } else {
                        wasSelected = c.id == selectedId
                        selectedId = c.id
                        if (!wasSelected) listener?.onSelectionChanged(c)
                        // bring to front
                        layout.controls.remove(c); layout.controls.add(c)
                        rectFor(c, rect)
                        dragOffX = x - rect.centerX(); dragOffY = y - rect.centerY()
                        drag = Drag.MOVE
                    }
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(x - dragStartX) > touchSlop || abs(y - dragStartY) > touchSlop) dragMoved = true
                if (!dragMoved) return true
                val c = selected ?: return true
                when (drag) {
                    Drag.MOVE -> {
                        c.cx = (snap(x - dragOffX) / width).coerceIn(0f, 1f)
                        c.cy = (snap(y - dragOffY) / height).coerceIn(0f, 1f)
                    }
                    Drag.RESIZE -> {
                        rectFor(c, rect)
                        val left = rect.left; val top = rect.top
                        var w = (snap(x) - left).coerceAtLeast(28 * density)
                        var h = (snap(y) - top).coerceAtLeast(28 * density)
                        if (c.type == ControlType.DPAD || c.shape == ButtonShape.CIRCLE) { val s = maxOf(w, h); w = s; h = s }
                        w = minOf(w, width.toFloat()); h = minOf(h, height.toFloat())
                        c.widthDp = w / density; c.heightDp = h / density
                        c.cx = ((left + w / 2) / width).coerceIn(0f, 1f)
                        c.cy = ((top + h / 2) / height).coerceIn(0f, 1f)
                    }
                    Drag.NONE -> {}
                }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val c = selected
                if (c != null) {
                    if (dragMoved) listener?.onLayoutEdited()
                    else if (wasSelected && drag == Drag.MOVE &&
                        SystemClock.uptimeMillis() - dragDownTime < 500
                    ) listener?.onEditControl(c)
                }
                drag = Drag.NONE
            }
            MotionEvent.ACTION_CANCEL -> drag = Drag.NONE
        }
        return true
    }

    /** Nudge the selected control by whole pixels (for fine placement). */
    fun nudgeSelected(dxDp: Float, dyDp: Float) {
        val c = selected ?: return
        c.cx = (c.cx + dxDp * density / width).coerceIn(0f, 1f)
        c.cy = (c.cy + dyDp * density / height).coerceIn(0f, 1f)
        invalidate(); listener?.onLayoutEdited()
    }

    fun select(id: String?) {
        selectedId = id; listener?.onSelectionChanged(selected); invalidate()
    }

    // ------------------------------------------------------------- dispatch

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (editMode) return editTouch(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> return pressDown(e.getPointerId(0), e.x, e.y)
            MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                pressDown(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) pressMove(e.getPointerId(i), e.getX(i), e.getY(i))
            MotionEvent.ACTION_POINTER_UP -> pressUp(e.getPointerId(e.actionIndex), false)
            MotionEvent.ACTION_UP -> pressUp(e.getPointerId(0), false)
            MotionEvent.ACTION_CANCEL -> { presses.keys.toList().forEach { pressUp(it, true) } }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mainHandler.removeCallbacksAndMessages(null)
    }

    // ---------------------------------------------------------------- draw

    override fun onDraw(canvas: Canvas) {
        if (editMode) drawGrid(canvas)
        val alpha = if (editMode) maxOf(layout.opacity, 0.75f) else layout.opacity
        for (c in layout.controls) {
            val pressed = presses.values.any { it.spec === c && it.inside }
            when (c.type) {
                ControlType.DPAD -> drawDpad(canvas, c, alpha)
                ControlType.BUTTON -> drawButton(canvas, c, alpha, pressed)
            }
            if (editMode) drawEditDecor(canvas, c)
        }
    }

    private fun a(color: Int, alpha: Float) =
        Color.argb((Color.alpha(color) * alpha).roundToInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun drawGrid(canvas: Canvas) {
        canvas.drawColor(Color.argb(70, 0, 0, 0))
        stroke.color = Color.argb(28, 255, 255, 255)
        stroke.strokeWidth = 1f
        val g = 24f * density
        var x = 0f
        while (x < width) { canvas.drawLine(x, 0f, x, height.toFloat(), stroke); x += g }
        var y = 0f
        while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, stroke); y += g }
    }

    private fun drawButton(canvas: Canvas, c: ControlSpec, alpha: Float, pressed: Boolean) {
        rectFor(c, rect)
        val isRun = c.action == ButtonAction.RUN
        val active = isRun && (runArmed || runLocked)
        val lit = pressed || (isRun && runLocked)
        val r = if (c.shape == ButtonShape.CIRCLE) minOf(rect.width(), rect.height()) / 2 else 10f * density

        // body
        fill.color = a(
            when {
                lit -> accent
                active -> Color.argb(230, 120, 90, 30)
                else -> Color.argb(215, 38, 40, 48)
            }, alpha
        )
        if (c.shape == ButtonShape.CIRCLE) canvas.drawCircle(rect.centerX(), rect.centerY(), r, fill)
        else canvas.drawRoundRect(rect, r, r, fill)
        // top highlight + outline
        stroke.strokeWidth = 1f * density
        stroke.color = a(if (lit) Color.argb(120, 0, 0, 0) else Color.argb(if (active) 230 else 70, 255, 255, 255), alpha)
        if (c.shape == ButtonShape.CIRCLE) canvas.drawCircle(rect.centerX(), rect.centerY(), r - 0.5f * density, stroke)
        else { rect.inset(0.5f * density, 0.5f * density); canvas.drawRoundRect(rect, r, r, stroke); rect.inset(-0.5f * density, -0.5f * density) }

        val label = c.label.ifEmpty { KeySequence.describe(KeySequence.parse(c.keys)) }
        text.color = a(if (lit) Color.rgb(30, 22, 0) else Color.WHITE, minOf(1f, alpha + 0.35f))
        fitText(label, rect.width() * 0.82f, minOf(rect.height() * 0.42f, 16f * density))
        val fm = text.fontMetrics
        canvas.drawText(label, rect.centerX(), rect.centerY() - (fm.ascent + fm.descent) / 2, text)
    }

    private fun fitText(s: String, maxW: Float, maxH: Float) {
        text.textSize = maxH
        val w = text.measureText(s)
        if (w > maxW && w > 0) text.textSize = maxH * maxW / w
    }

    private fun drawDpad(canvas: Canvas, c: ControlSpec, alpha: Float) {
        rectFor(c, rect)
        val cx = rect.centerX(); val cy = rect.centerY()
        val r = minOf(rect.width(), rect.height()) / 2
        val activeZone = presses.values.firstOrNull { it.spec === c }?.zone ?: -1
        val running = runArmed || runLocked

        // base
        fill.color = a(Color.argb(205, 34, 36, 44), alpha)
        canvas.drawCircle(cx, cy, r, fill)
        stroke.strokeWidth = 1f * density
        stroke.color = a(Color.argb(70, 255, 255, 255), alpha)
        canvas.drawCircle(cx, cy, r - 0.5f * density, stroke)

        // zone index -> angle (degrees, 0 = east, CCW)
        val angles = mapOf(5 to 0.0, 2 to 45.0, 1 to 90.0, 0 to 135.0, 3 to 180.0, 6 to 225.0, 7 to 270.0, 8 to 315.0)
        val inner = r * 0.3f
        for ((zone, deg) in angles) {
            if (!c.eightWay && zone in intArrayOf(0, 2, 6, 8)) continue
            val k = c.dpadKeys.getOrElse(zone) { "" }
            if (k.isEmpty()) continue
            val on = zone == activeZone
            // wedge highlight
            if (on) {
                fill.color = a(accent, alpha)
                path.reset()
                path.addArc(cx - r, cy - r, cx + r, cy + r, -(deg + 22.5).toFloat(), 45f)
                path.arcTo(cx - inner, cy - inner, cx + inner, cy + inner, -(deg - 22.5).toFloat(), -45f, false)
                path.close()
                canvas.drawPath(path, fill)
            }
            // chevron
            val rad = Math.toRadians(deg)
            val dx = cos(rad).toFloat(); val dy = -sin(rad).toFloat()
            val tipX = cx + dx * r * 0.80f; val tipY = cy + dy * r * 0.80f
            val sz = r * 0.13f
            path.reset()
            path.moveTo(tipX, tipY)
            path.lineTo(tipX - dx * sz * 1.5f - dy * sz, tipY - dy * sz * 1.5f + dx * sz)
            path.lineTo(tipX - dx * sz * 1.5f + dy * sz, tipY - dy * sz * 1.5f - dx * sz)
            path.close()
            fill.color = a(if (on) Color.rgb(30, 22, 0) else if (running) accent else Color.argb(235, 235, 235, 240), alpha)
            canvas.drawPath(path, fill)
            // key label: only while editing or when remapped away from the defaults
            if (editMode || k != ControlSpec.DEFAULT_DPAD_KEYS[zone]) {
                val lbl = KeySequence.describe(KeySequence.parse(k))
                text.color = a(if (on) Color.rgb(30, 22, 0) else Color.argb(200, 255, 255, 255), minOf(1f, alpha + 0.3f))
                fitText(lbl, r * 0.3f, r * 0.16f)
                val fm = text.fontMetrics
                canvas.drawText(lbl, cx + dx * r * 0.52f, cy + dy * r * 0.52f - (fm.ascent + fm.descent) / 2, text)
            }
        }
        // centre
        val centreKey = c.dpadKeys.getOrElse(4) { "" }
        if (centreKey.isNotEmpty()) {
            val on = activeZone == 4
            fill.color = a(if (on) accent else Color.argb(200, 55, 58, 68), alpha)
            canvas.drawCircle(cx, cy, inner * 0.85f, fill)
            val lbl = if (centreKey == ".") "•" else KeySequence.describe(KeySequence.parse(centreKey))
            text.color = a(if (on) Color.rgb(30, 22, 0) else Color.WHITE, minOf(1f, alpha + 0.3f))
            fitText(lbl, inner * 1.3f, inner * 0.8f)
            val fm = text.fontMetrics
            canvas.drawText(lbl, cx, cy - (fm.ascent + fm.descent) / 2, text)
        }
        if (running) {
            text.color = a(accent, alpha)
            fitText("RUN", r * 0.5f, r * 0.14f)
            val fm = text.fontMetrics
            canvas.drawText("RUN", cx, cy + r * 0.38f - (fm.ascent + fm.descent) / 2, text)
        }
    }

    private fun drawEditDecor(canvas: Canvas, c: ControlSpec) {
        rectFor(c, rect)
        val sel = c.id == selectedId
        dash.color = if (sel) accent else Color.argb(160, 255, 255, 255)
        dash.strokeWidth = (if (sel) 2.5f else 1.2f) * density
        canvas.drawRect(rect, dash)
        if (sel) {
            fill.color = accent
            canvas.drawCircle(rect.right, rect.bottom, handleRadius(), fill)
            stroke.color = Color.BLACK; stroke.strokeWidth = 2f * density
            val h = handleRadius() * 0.45f
            canvas.drawLine(rect.right - h, rect.bottom + h, rect.right + h, rect.bottom - h, stroke)
        }
    }
}
