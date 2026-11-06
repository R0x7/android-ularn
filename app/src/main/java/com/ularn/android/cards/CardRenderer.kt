package com.ularn.android.cards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import android.util.LruCache

/**
 * Draws trading cards as pixel art.
 *
 * A card is laid out on a [W]x[H] grid of "card pixels" and rendered at an
 * integer [scale] so frames stay crisp; the monster's 32x32 tile is blown up
 * 4x with nearest-neighbour sampling. Text is drawn at the final resolution
 * (anti-aliased), so it stays readable. Holo cards get a static foil pattern
 * here; the animated sheen is [CardView]'s job.
 */
class CardRenderer(private val context: Context) {

    companion object {
        const val W = 160
        const val H = 232
        private const val TILE_SRC = 32
        private const val SHEET_COLS = 16
        private const val PANEL = 0xFF1B1A21.toInt()
        private const val CELL = 0xFF2A2833.toInt()
        private val UNKNOWN_FRAME = intArrayOf(0xFF55545C.toInt(), 0xFF7E7D88.toInt(), 0xFF2C2B31.toInt())
        private val RAINBOW = intArrayOf(
            0xFFFF5A5A.toInt(), 0xFFFFC94A.toInt(), 0xFF7CF06A.toInt(),
            0xFF4AD8FF.toInt(), 0xFF8A7CFF.toInt(), 0xFFFF6AE0.toInt(),
        )
    }

    // Monster tiles cut from the same sheet GameView uses (assets/ularn_tiles.png).
    private val tiles = HashMap<Int, Bitmap?>()
    private var sheet: Bitmap? = null
    private var sheetTried = false

    private fun tile(id: Int): Bitmap? = tiles.getOrPut(id) {
        if (!sheetTried) {
            sheetTried = true
            sheet = try {
                context.assets.open("ularn_tiles.png").use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })
                }
            } catch (_: Exception) { null }
        }
        val s = sheet ?: return@getOrPut null
        val sx = (id % SHEET_COLS) * TILE_SRC
        val sy = (id / SHEET_COLS) * TILE_SRC
        if (sx + TILE_SRC > s.width || sy + TILE_SRC > s.height) null
        else Bitmap.createBitmap(s, sx, sy, TILE_SRC, TILE_SRC)
    }

    // Rendered cards: a few MB at most, recreated on demand.
    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /**
     * The card for [monst] as [entry] (null = not found yet: a silhouette)
     * at [scale] device pixels per card pixel.
     */
    fun render(monst: Int, entry: CardEntry?, scale: Int): Bitmap {
        val s = scale.coerceIn(1, 8)
        val holo = entry?.holo == true
        val key = "$monst/${entry != null}/$holo/$s"
        cache.get(key)?.let { return it }
        val bmp = draw(monst, entry != null, holo, s)
        cache.put(key, bmp)
        return bmp
    }

    /**
     * Smallest integer scale that makes the card at least [widthPx] wide, so
     * views only ever shrink it a little (smoothly) and never blow up text.
     */
    fun scaleFor(widthPx: Int) = ((widthPx + W - 1) / W).coerceIn(1, 8)

    private fun draw(monst: Int, known: Boolean, holo: Boolean, s: Int): Bitmap {
        val fact = MonsterFacts.get(monst)
        val rarity = MonsterFacts.rarityOf(monst)
        val (frame, light, dark) = if (known) Triple(rarity.frame, rarity.frameLight, rarity.frameDark)
            else Triple(UNKNOWN_FRAME[0], UNKNOWN_FRAME[1], UNKNOWN_FRAME[2])
        val bmp = Bitmap.createBitmap(W * s, H * s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint()
        fun rect(l: Int, t: Int, r: Int, b: Int, color: Int) {
            p.color = color
            c.drawRect((l * s).toFloat(), (t * s).toFloat(), (r * s).toFloat(), (b * s).toFloat(), p)
        }

        // Outline with clipped corners, bevelled frame, dark inner panel.
        rect(1, 0, W - 1, H, Color.BLACK)
        rect(0, 1, W, H - 1, Color.BLACK)
        rect(2, 2, W - 2, H - 2, frame)
        rect(2, 2, W - 2, 4, light); rect(2, 2, 4, H - 2, light)
        rect(2, H - 4, W - 2, H - 2, dark); rect(W - 4, 2, W - 2, H - 2, dark)
        rect(7, 7, W - 7, H - 7, Color.BLACK)
        rect(8, 8, W - 8, H - 8, PANEL)

        // Name bar.
        rect(10, 10, W - 10, 27, dark)
        rect(10, 26, W - 10, 27, frame)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
        val lvText = if (known && fact != null) "LV${fact.level}" else "LV?"
        text.textSize = 8f * s
        text.color = light
        text.textAlign = Paint.Align.RIGHT
        c.drawText(lvText, (W - 13f) * s, 21f * s, text)
        val lvWidth = text.measureText(lvText)
        val name = if (known && fact != null) fact.title else "???"
        text.color = Color.WHITE
        text.textAlign = Paint.Align.LEFT
        text.textSize = 10f * s
        val nameRoom = (W - 30f) * s - lvWidth
        while (text.textSize > 6f * s && text.measureText(name) > nameRoom) text.textSize -= 0.5f * s
        c.drawText(name, 13f * s, 22f * s, text)

        // Art box: scanline backdrop in the rarity colour, the monster 4x.
        val artL = 16; val artT = 30; val artR = artL + 128; val artB = artT + 128
        rect(artL - 1, artT - 1, artR + 1, artB + 1, light)
        val bgA = blend(dark, Color.BLACK, 0.35f)
        val bgB = blend(dark, Color.BLACK, 0.55f)
        for (y in artT until artB step 2) {
            rect(artL, y, artR, y + 1, bgA)
            rect(artL, y + 1, artR, y + 2, bgB)
        }
        if (holo) {
            // Static foil: thin rainbow diagonals.
            val foil = Paint().apply { alpha = 255 }
            c.save()
            c.clipRect((artL * s).toFloat(), (artT * s).toFloat(), (artR * s).toFloat(), (artB * s).toFloat())
            var i = 0
            for (d in -128..128 step 8) {
                foil.color = (RAINBOW[i++ % RAINBOW.size] and 0x00FFFFFF) or 0x38000000
                foil.strokeWidth = 3f * s
                val x0 = (artL + d) * s.toFloat()
                c.drawLine(x0, (artB * s).toFloat(), x0 + 128f * s, (artT * s).toFloat(), foil)
            }
            c.restore()
        }
        tile(MonsterFacts.tileOf(monst))?.let { t ->
            val tp = Paint().apply {
                isFilterBitmap = false; isDither = false
                if (!known) colorFilter = PorterDuffColorFilter(0xFF09090C.toInt(), PorterDuff.Mode.SRC_IN)
            }
            c.drawBitmap(t, null, RectF((artL * s).toFloat(), (artT * s).toFloat(), (artR * s).toFloat(), (artB * s).toFloat()), tp)
        }
        if (holo) {
            rect(artR - 34, artT + 3, artR - 3, artT + 14, Color.BLACK)
            text.textSize = 7f * s
            text.textAlign = Paint.Align.CENTER
            text.color = 0xFFFFE070.toInt()
            c.drawText("★HOLO", (artR - 18.5f) * s, (artT + 11f) * s, text)
        }

        // Rarity line: pips + label, collector number on the right.
        val y0 = 161
        val pips = if (known) rarity.ordinal + 1 else 0
        for (i in 0 until 5) rect(12 + i * 6, y0 + 2, 16 + i * 6, y0 + 6, if (i < pips) light else CELL)
        text.textSize = 7f * s
        text.textAlign = Paint.Align.LEFT
        text.color = if (known) light else UNKNOWN_FRAME[1]
        c.drawText(if (known) rarity.label.uppercase() else "???", 44f * s, (y0 + 7f) * s, text)
        text.textAlign = Paint.Align.RIGHT
        text.color = 0xFF9A98A6.toInt()
        c.drawText("No.%02d/%d".format(monst, MonsterFacts.COUNT), (W - 12f) * s, (y0 + 7f) * s, text)

        // Stats: four cells.
        val labels = arrayOf("AC", "DAM", "HP", "XP")
        val values = if (known && fact != null) arrayOf(fact.ac.toString(), fact.dam.toString(), compact(fact.hp), compact(fact.exp))
            else arrayOf("?", "?", "?", "?")
        for (i in 0 until 4) {
            val l = 12 + i * 34
            rect(l, 172, l + 32, 196, CELL)
            rect(l, 172, l + 32, 173, dark)
            text.textAlign = Paint.Align.CENTER
            text.textSize = 6f * s
            text.color = 0xFF8D8B99.toInt()
            c.drawText(labels[i], (l + 16f) * s, 180f * s, text)
            text.textSize = 9f * s
            text.color = Color.WHITE
            c.drawText(values[i], (l + 16f) * s, 192f * s, text)
        }

        // Lore, up to three lines.
        val lore = if (known && fact != null) fact.lore else "Not found yet. Go and hit things."
        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            textSize = 7f * s
            color = 0xFFC9C6D3.toInt()
            textAlign = Paint.Align.CENTER
        }
        val lines = wrap(lore, (W - 24f) * s) { lp.measureText(it) }.take(3)
        val top = 204f + (3 - lines.size) * 3.5f
        lines.forEachIndexed { i, line -> c.drawText(line, W / 2f * s, (top + i * 8f) * s, lp) }
        return bmp
    }

    private fun compact(v: Int): String = when {
        v >= 1_000_000 -> "${v / 1_000_000}M"
        v >= 10_000 -> "${v / 1000}k"
        else -> v.toString()
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(sh: Int) = (((a shr sh) and 255) * (1 - t) + ((b shr sh) and 255) * t).toInt()
        return Color.rgb(ch(16), ch(8), ch(0))
    }
}

/** Greedy word wrap; [width] measures a string in the same units as [max]. */
internal fun wrap(s: String, max: Float, width: (String) -> Float): List<String> {
    val out = mutableListOf<String>()
    var line = ""
    for (w in s.split(' ')) {
        val t = if (line.isEmpty()) w else "$line $w"
        if (line.isNotEmpty() && width(t) > max) { out += line; line = w } else line = t
    }
    if (line.isNotEmpty()) out += line
    return out
}
