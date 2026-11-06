package com.ularn.android.cards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View

/**
 * Shows one rendered card, scaled to fit with nearest-neighbour sampling.
 * Holo cards get an animated diagonal rainbow sheen and a passing glint,
 * drawn over the bitmap each frame while the view is on screen.
 */
class CardView(context: Context) : View(context) {

    private var bitmap: Bitmap? = null
    private var holo = false
    private val dst = RectF()
    private val bmpPaint = Paint().apply { isFilterBitmap = false; isDither = false }
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN) }
    private val glint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN) }
    private val m = Matrix()
    private var shaderFor = 0f

    fun setCard(bmp: Bitmap, holo: Boolean) {
        bitmap = bmp
        this.holo = holo
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Keep the card's aspect ratio: fit the width we are given, unless the height is the tighter limit.
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val hMax = MeasureSpec.getSize(heightMeasureSpec)
        var cw = w
        var ch = w * CardRenderer.H / CardRenderer.W
        if (hMode != MeasureSpec.UNSPECIFIED && ch > hMax) { ch = hMax; cw = ch * CardRenderer.W / CardRenderer.H }
        setMeasuredDimension(cw, ch)
    }

    override fun onDraw(canvas: Canvas) {
        val b = bitmap ?: return
        val sc = minOf(width.toFloat() / b.width, height.toFloat() / b.height)
        val w = b.width * sc; val h = b.height * sc
        dst.set((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
        // Nearest-neighbour keeps pixel art crisp when enlarging; shrinking needs filtering.
        bmpPaint.isFilterBitmap = sc < 1f
        canvas.drawBitmap(b, null, dst, bmpPaint)
        if (!holo) return

        if (shaderFor != w) {
            shaderFor = w
            val band = w * 0.9f
            sheen.shader = LinearGradient(0f, 0f, band, band, RAINBOW, null, Shader.TileMode.MIRROR)
            glint.shader = LinearGradient(0f, 0f, w * 0.25f, w * 0.25f,
                intArrayOf(Color.TRANSPARENT, 0x88FFFFFF.toInt(), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        }
        val t = (SystemClock.uptimeMillis() % 3000L) / 3000f
        canvas.save()
        canvas.clipRect(dst)
        // The rainbow drifts along the diagonal; the glint sweeps corner to corner.
        m.setTranslate(dst.left + t * w * 1.8f, dst.top + t * w * 1.8f)
        sheen.shader.setLocalMatrix(m)
        sheen.alpha = 70
        canvas.drawRect(dst, sheen)
        val g = -0.4f + t * 1.8f
        m.setTranslate(dst.left + g * (w + h) / 2f, dst.top + g * (w + h) / 2f)
        glint.shader.setLocalMatrix(m)
        canvas.drawRect(dst, glint)
        canvas.restore()
        postInvalidateOnAnimation()
    }

    private companion object {
        val RAINBOW = intArrayOf(
            0xFFFF4D4D.toInt(), 0xFFFFC23D.toInt(), 0xFF6CF05A.toInt(),
            0xFF3DD6FF.toInt(), 0xFF7A6CFF.toInt(), 0xFFFF5AD8.toInt(), 0xFFFF4D4D.toInt(),
        )
    }
}
