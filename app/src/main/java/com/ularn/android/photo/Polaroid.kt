package com.ularn.android.photo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.text.DateFormat
import java.util.Date

/**
 * Wild Edition photo mode: turns a picture of the map into an instant photo.
 * Off-white frame with the thick chin, the caption in a handwritten-ish
 * italic serif, the date, a soft vignette and a few pixel-art stickers from
 * the tile sheet. The geometry is in [PolaroidMath].
 */
object Polaroid {
    private val PAPER = Color.rgb(244, 241, 232)
    private val INK = Color.rgb(43, 43, 58)
    private val INK_DIM = Color.rgb(110, 108, 118)

    /**
     * Composes the photo. [tile] gives a sheet tile for the stickers;
     * [seed] picks them. The returned bitmap is new; [photo] is not recycled.
     */
    fun compose(
        photo: Bitmap, caption: String, date: Date, seed: Long,
        playerTile: Int, tile: (Int) -> Bitmap?,
    ): Bitmap {
        val l = PolaroidMath.layout(photo.width, photo.height)
        val out = Bitmap.createBitmap(l.frameW, l.frameH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(PAPER)

        // the photo, pixel-sharp
        val photoRect = Rect(l.photoL, l.photoT, l.photoR, l.photoB)
        c.drawBitmap(photo, null, photoRect, Paint().apply { isFilterBitmap = false })

        // vignette: clear in the middle, darker towards the corners
        val cx = (l.photoL + l.photoR) / 2f
        val cy = (l.photoT + l.photoB) / 2f
        val radius = Math.hypot((l.photoR - l.photoL) / 2.0, (l.photoB - l.photoT) / 2.0).toFloat()
        val vignette = Paint().apply {
            shader = RadialGradient(cx, cy, radius, intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(120, 0, 0, 0)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawRect(RectF(photoRect), vignette)
        // a thin inner edge, like the print's border
        val edge = Paint().apply { style = Paint.Style.STROKE; strokeWidth = maxOf(1f, l.side / 16f); color = Color.argb(60, 0, 0, 0) }
        c.drawRect(RectF(photoRect), edge)

        // caption + date
        val hand = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = hand; color = INK; textSize = l.captionSize }
        text.textSize = PolaroidMath.fitSize(l.captionSize, text.measureText(caption), l.captionMaxW)
        c.drawText(caption, l.captionX.toFloat(), l.captionY.toFloat(), text)
        text.color = INK_DIM
        text.textSize = l.dateSize
        c.drawText(DateFormat.getDateInstance(DateFormat.LONG).format(date), l.captionX.toFloat(), l.dateY.toFloat(), text)

        // stickers
        val px = Paint().apply { isFilterBitmap = false }
        val shadow = Paint().apply { color = Color.argb(70, 0, 0, 0) }
        val back = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        for (st in PolaroidMath.stickers(l, seed, playerTile)) {
            val bmp = tile(st.tile) ?: continue
            val half = st.size / 2f
            val pad = st.size / 10f
            c.save()
            c.rotate(st.degrees, st.cx.toFloat(), st.cy.toFloat())
            val r = RectF(st.cx - half, st.cy - half, st.cx + half, st.cy + half)
            c.drawRoundRect(RectF(r).apply { offset(pad / 3, pad / 3); inset(-pad, -pad) }, pad, pad, shadow)
            c.drawRoundRect(RectF(r).apply { inset(-pad, -pad) }, pad, pad, back)
            c.drawBitmap(bmp, null, r, px)
            c.restore()
        }
        return out
    }
}
