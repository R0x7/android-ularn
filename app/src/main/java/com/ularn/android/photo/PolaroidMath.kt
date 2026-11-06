package com.ularn.android.photo

import kotlin.math.roundToInt

/**
 * The geometry and words of a Polaroid ([Polaroid]), free of Android classes
 * so it can be unit tested on the JVM. All sizes are in px of the final
 * bitmap; rectangles are (left, top, right, bottom).
 */
object PolaroidMath {

    /** Where everything goes in a frame around a photo of [photoW] x [photoH] px. */
    data class Layout(
        val frameW: Int, val frameH: Int,
        val photoL: Int, val photoT: Int, val photoR: Int, val photoB: Int,
        /** Baselines and sizes of the caption and the date, in the bottom border. */
        val captionX: Int, val captionY: Int, val captionSize: Float,
        val dateY: Int, val dateSize: Float,
        /** Room the caption may take before it has to shrink. */
        val captionMaxW: Int,
    ) {
        val side get() = photoL
        val bottom get() = frameH - photoB
    }

    const val SIDE_FRACTION = 0.05f
    const val MIN_SIDE = 16
    /** The bottom border is this many times the side border (the Polaroid chin). */
    const val CHIN = 4.2f

    fun layout(photoW: Int, photoH: Int): Layout {
        val w = photoW.coerceAtLeast(1)
        val h = photoH.coerceAtLeast(1)
        val side = maxOf(MIN_SIDE, (maxOf(w, h) * SIDE_FRACTION).roundToInt())
        val bottom = (side * CHIN).roundToInt()
        val frameW = w + 2 * side
        val frameH = h + side + bottom
        val captionSize = bottom * 0.30f
        val dateSize = bottom * 0.20f
        val captionY = h + side + (bottom * 0.45f).roundToInt()
        val dateY = h + side + (bottom * 0.78f).roundToInt()
        return Layout(
            frameW, frameH, side, side, side + w, side + h,
            captionX = side + side / 2, captionY = captionY, captionSize = captionSize,
            dateY = dateY, dateSize = dateSize, captionMaxW = w - side,
        )
    }

    /** The caption's text size so that text [measured] px wide at [size] fits [maxW]. */
    fun fitSize(size: Float, measured: Float, maxW: Int, minSize: Float = size * 0.5f): Float =
        if (measured <= maxW || measured <= 0f) size else maxOf(minSize, size * maxW / measured)

    /** "home" for the town, "the volcano, V2", else "level 7". */
    fun where(levelName: String): String {
        val n = levelName.trim()
        return when {
            n.isEmpty() || n == "H" -> "home"
            n.startsWith("V") -> "the volcano, $n"
            else -> "level $n"
        }
    }

    /** "<name> the <title> — level <n>, turn <t>". */
    fun caption(name: String, title: String, levelName: String, turn: Int): String {
        val who = name.trim().ifEmpty { "An adventurer" }
        val t = title.trim()
        val head = if (t.isEmpty()) who else "$who the $t"
        return "$head — ${where(levelName)}, turn $turn"
    }

    /** The character's name from the game's ularn.opt text ("OPTION name:X,gender:..."). */
    fun nameFromOpts(opts: String?): String? {
        if (opts == null) return null
        val m = Regex("""name:([^,\r\n]*)""").find(opts) ?: return null
        return m.groupValues[1].trim().ifEmpty { null }
    }

    /** A sticker: tile id, centre, size (px) and rotation (degrees). */
    data class Sticker(val tile: Int, val cx: Int, val cy: Int, val size: Int, val degrees: Float)

    /** Stickers come from these tiles (gems, a potion, a scroll, gold) plus the player. */
    val STICKER_TILES = intArrayOf(113, 114, 115, 116, 105, 104, 81)

    /**
     * 1 to 3 stickers for [seed], straddling the edges of the photo in
     * different corners. [playerTile] (or -1) may be one of them.
     */
    fun stickers(l: Layout, seed: Long, playerTile: Int): List<Sticker> {
        var s = seed xor 0x5DEECE66DL
        fun next(n: Int): Int {
            s = s * 6364136223846793005L + 1442695040888963407L
            return ((s ushr 33) % n).toInt().let { if (it < 0) it + n else it }
        }
        val count = 1 + next(3)
        val size = maxOf(32, l.side * 2)
        // corners of the photo: top-left, top-right, bottom-right, bottom-left
        val corners = listOf(
            l.photoL to l.photoT, l.photoR to l.photoT, l.photoR to l.photoB, l.photoL to l.photoB,
        )
        val first = next(4)
        val out = ArrayList<Sticker>()
        for (i in 0 until count) {
            val (x, y) = corners[(first + i) % 4]
            // nudge inwards a little so the sticker straddles the photo's edge
            val cx = x + (if (x == l.photoL) size / 4 else -size / 4)
            val cy = y + (if (y == l.photoT) size / 4 else -size / 4)
            val tile = if (i == 0 && playerTile >= 0 && next(2) == 0) playerTile
                       else STICKER_TILES[next(STICKER_TILES.size)]
            val deg = (next(31) - 15).toFloat()
            out += Sticker(tile, cx, cy, size, deg)
        }
        return out
    }

    /** File name for a photo taken at [millis]: "ularn-wild-<millis>.png". */
    fun fileName(millis: Long) = "ularn-wild-$millis.png"
}
