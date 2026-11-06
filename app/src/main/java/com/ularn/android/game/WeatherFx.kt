package com.ularn.android.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import com.ularn.android.Settings
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Wild Edition, atmosphere: weather and day/night on the surface. The game
 * (compat/wild_weather.c) keeps the hour and the weather in [GameState.weatherHour]
 * / [GameState.weatherState]; on the home level [GameView] draws the map
 * through this pass:
 *  - a day/night tint, multiplied over the map (warm at dawn and dusk, deep
 *    blue at night, never more than 40% darker so the map stays readable);
 *  - rain streaks / snow flakes (tiles 400-403) over some floor cells,
 *    drifting at 10 fps while it rains or snows;
 *  - stars (404-405) in the black unexplored cells at night;
 *  - a white flash for storm lightning (a strike from the game, or now and
 *    then on its own while the storm lasts);
 *  - the sun or the moon (406 / 407) in the HUD, next to the level name.
 *
 * [reduce] ("Reduce weather effects") stops the drifting and the flashes and
 * thins the rain and snow; the tint stays, it never moves.
 */
class WeatherFx(private val view: View) {

    /** Settings: "Reduce weather effects". */
    var reduce = false
        set(v) { field = v; view.invalidate() }

    private var on = false
    private var hour = 12
    private var state = WeatherMath.CLEAR
    private var minute = 0f
    private var frame = 0L
    private var flashStart = 0L
    private var nextRandomFlash = 0L

    private val multiply = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY) }
    private val flashPaint = Paint()
    val overlayPaint = Paint().apply { isFilterBitmap = false; isDither = false }
    private val dst = Rect()

    private var pending = false
    private val tick = Runnable { pending = false; view.invalidate() }

    /** Weather is drawn this frame (on the surface, feature on, map shown). */
    val active get() = on

    /** Call once per drawn frame. */
    fun onFrame(s: GameState) {
        on = s.dlevel == 0 && s.displayMode == GameState.DISPLAY_MAP && s.wildOn(Settings.WILDF_WEATHER)
        if (!on) return
        hour = s.weatherHour.coerceIn(0, 23)
        state = s.weatherState
        // S->game_time only moves when the status line is repainted: blend
        // within the hour only while it agrees with the hour the game sent.
        minute = if (WeatherMath.hourAt(s.gameTime.toLong()) == hour)
            Math.floorMod(s.gameTime, WeatherMath.TURNS_PER_HOUR).toFloat() / WeatherMath.TURNS_PER_HOUR else 0f
        val now = SystemClock.uptimeMillis()
        // Reduced: one look per game turn, nothing drifts on its own.
        frame = if (reduce) s.gameTime.toLong() else now / WeatherMath.FRAME_MS
        if (state == WeatherMath.STORM && !reduce) {
            if (nextRandomFlash == 0L) nextRandomFlash = now + WeatherMath.randomFlashDelay(now)
            else if (now >= nextRandomFlash) { flash(); nextRandomFlash = now + WeatherMath.randomFlashDelay(now) }
        } else nextRandomFlash = 0L
        val animating = !reduce && (WeatherMath.precipitates(state) || state == WeatherMath.STORM || flashing(now))
        if (animating && !pending) {
            pending = true
            view.postDelayed(tick, WeatherMath.FRAME_MS)
        }
    }

    /** Lightning: a short white flash (not in reduced mode). */
    fun flash() {
        if (reduce) return
        flashStart = SystemClock.uptimeMillis()
        view.postInvalidate()
    }

    private fun flashing(now: Long) = flashStart != 0L && now - flashStart < WeatherMath.FLASH_MS

    /**
     * Draws the weather of cell ([tx],[ty]) into [cell] (its screen rect),
     * after its tiles. [mm] is the cell's minimap class ([GameState.mmCat]).
     */
    fun drawCell(canvas: Canvas, tiles: Array<Bitmap?>, cell: Rect, tx: Int, ty: Int, mm: Int) {
        if (!on) return
        val cls = mm and GameState.MM_CLASS_MASK
        if (cls == GameState.MM_UNKNOWN) {
            if (!WeatherMath.isNight(hour)) return
            val star = WeatherMath.starTile(tx, ty, if (reduce) 0L else frame / 10) ?: return
            tiles.getOrNull(star)?.let { canvas.drawBitmap(it, null, cell, overlayPaint) }
            return
        }
        if (cls != GameState.MM_FLOOR) return
        val id = WeatherMath.precipTile(state, tx, ty, frame, reduce) ?: return
        val bmp = tiles.getOrNull(id) ?: return
        val size = cell.height()
        val (ox, oy) = WeatherMath.drift(state, tx, ty, frame, size)
        // The pattern scrolls through the cell: draw it, and the copy that
        // wraps in from above.
        dst.set(cell); dst.offset(ox, oy)
        canvas.drawBitmap(bmp, null, dst, overlayPaint)
        dst.offset(-WeatherMath.wrapDx(state, size), -size)
        canvas.drawBitmap(bmp, null, dst, overlayPaint)
    }

    /** Tints the whole map (call after the tiles, still clipped to [map]). */
    fun drawTint(canvas: Canvas, map: RectF) {
        if (!on) return
        val c = WeatherMath.tint(hour, minute)
        if (c == Color.WHITE) return
        multiply.color = c
        canvas.drawRect(map, multiply)
    }

    /** The lightning flash over the whole view (call last). */
    fun drawFlash(canvas: Canvas, w: Int, h: Int) {
        if (flashStart == 0L) return
        val age = SystemClock.uptimeMillis() - flashStart
        val a = WeatherMath.flashAlpha(age)
        if (a <= 0) { flashStart = 0L; return }
        flashPaint.color = Color.argb(a, 255, 255, 255)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), flashPaint)
        view.postInvalidateOnAnimation()
    }

    /** Sun (406) by day, moon (407) by night; -1 when weather isn't shown. */
    fun hudTile(): Int = if (!on) -1 else if (WeatherMath.isNight(hour)) WeatherMath.TILE_MOON else WeatherMath.TILE_SUN

    /** Weather for a photo taken now: the same tint and precipitation, frozen. */
    fun snapshotFrame() = frame
}

/**
 * The arithmetic of [WeatherFx] (and the packing of S->weather), free of
 * Android classes so it can be unit tested on the JVM. Colours are plain ARGB
 * ints. Every "random" choice is a hash of the frame and the cell, so a frame
 * redrawn for another reason looks the same.
 */
object WeatherMath {
    // weather states (compat/wild_weather.c: WX_*)
    const val CLEAR = 0
    const val RAIN = 1
    const val SNOW = 2
    const val STORM = 3

    const val TURNS_PER_HOUR = 50
    const val NIGHT_START = 21
    const val NIGHT_END = 6

    const val TILE_RAIN = 400
    const val TILE_SNOW = 402
    const val TILE_STARS = 404
    const val TILE_SUN = 406
    const val TILE_MOON = 407

    const val FRAME_MS = 100L           // 10 fps
    const val FLASH_MS = 260L
    const val FLASH_MAX_ALPHA = 200
    /** The tint never takes more than 40% off a channel. */
    const val MAX_DARKNESS = 0.40f

    /** S->weather as the game packs it: hour | state << 8. */
    fun pack(hour: Int, state: Int) = (hour and 0xff) or ((state and 0xff) shl 8)
    fun hourOf(packed: Int) = packed and 0xff
    fun stateOf(packed: Int) = (packed ushr 8) and 0xff

    /** The hour the game computes from its clock (gtime). */
    fun hourAt(gameTime: Long) = ((gameTime / TURNS_PER_HOUR) % 24).toInt()

    fun isNight(hour: Int) = hour >= NIGHT_START || hour < NIGHT_END

    fun precipitates(state: Int) = state == RAIN || state == SNOW || state == STORM

    /** A well-mixed non-negative hash of a few ints (same as GlitchMath's). */
    fun hash(a: Long, b: Int = 0, c: Int = 0): Int = GlitchMath.hash(a, b, c)

    // ---- tint

    private fun rgb(r: Int, g: Int, b: Int) = (0xff shl 24) or (r shl 16) or (g shl 8) or b

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(c: Int, s: Int) = (c shr s) and 0xff
        fun m(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * t).roundToInt().coerceIn(0, 255)
        return rgb(m(16), m(8), m(0))
    }

    private val DAY = rgb(255, 255, 255)
    private val NIGHT = rgb(153, 160, 214)
    private val DAWN = rgb(255, 212, 178)
    private val DUSK = rgb(250, 186, 160)

    /** Key colours per hour (index = hour); the tint blends to the next one. */
    private val HOURS = intArrayOf(
        NIGHT, NIGHT, NIGHT, NIGHT, NIGHT, mix(NIGHT, DAWN, 0.5f),   // 0-5
        DAWN, mix(DAWN, DAY, 0.5f), DAY, DAY, DAY, DAY,              // 6-11
        DAY, DAY, DAY, DAY, DAY, mix(DAY, DUSK, 0.4f),               // 12-17
        DUSK, mix(DUSK, NIGHT, 0.4f), mix(DUSK, NIGHT, 0.8f), NIGHT, NIGHT, NIGHT, // 18-23
    )

    /**
     * The colour multiplied over the map at [hour] + [minute] (0..1 of the
     * hour). Opaque; every channel is at least (1 - [MAX_DARKNESS]) * 255.
     */
    fun tint(hour: Int, minute: Float): Int {
        val h = Math.floorMod(hour, 24)
        val c = mix(HOURS[h], HOURS[(h + 1) % 24], minute.coerceIn(0f, 1f))
        val floor = ((1f - MAX_DARKNESS) * 255f).roundToInt()
        fun clamp(s: Int) = maxOf(floor, (c shr s) and 0xff)
        return rgb(clamp(16), clamp(8), clamp(0))
    }

    // ---- precipitation

    /** Rain or snow tile for a floor cell this frame, or null (most cells stay dry). */
    fun precipTile(state: Int, x: Int, y: Int, frame: Long, reduce: Boolean): Int? {
        if (!precipitates(state)) return null
        val odds = when {
            reduce -> 5
            state == STORM -> 2
            else -> 3
        }
        val h = hash(7L, x * 131 + 3, y)
        if (h % odds != 0) return null
        val base = if (state == SNOW) TILE_SNOW else TILE_RAIN
        return base + ((h / odds + (frame / 4).toInt()) and 1)
    }

    /**
     * Offset in px of the drifting pattern in a cell of [size] px: rain falls
     * a quarter cell per frame with a slant, storms faster; snow drifts down
     * slowly and sways.
     */
    fun drift(state: Int, x: Int, y: Int, frame: Long, size: Int): Pair<Int, Int> {
        if (size <= 0) return 0 to 0
        val phase = hash(11L, x, y) % size
        return when (state) {
            SNOW -> {
                val dy = ((frame * size / 24 + phase) % size).toInt()
                val sway = (sin((frame + phase) / 6.0) * size / 10.0).roundToInt()
                sway to dy
            }
            RAIN, STORM -> {
                val step = if (state == STORM) size / 3 else size / 4
                val dy = ((frame * maxOf(1, step) + phase) % size).toInt()
                dy / 3 to dy
            }
            else -> 0 to 0
        }
    }

    /** Horizontal offset between a pattern and its wrapped copy above it. */
    fun wrapDx(state: Int, size: Int) = if (state == RAIN || state == STORM) size / 3 else 0

    // ---- stars

    /** A star tile for an unexplored cell at night (about 1 in 4), or null; twinkles with [slowFrame]. */
    fun starTile(x: Int, y: Int, slowFrame: Long): Int? {
        val h = hash(13L, x * 31 + 1, y)
        if (h % 4 != 0) return null
        return TILE_STARS + ((h / 4 + (slowFrame + h % 7).toInt() / 3) and 1)
    }

    // ---- lightning

    /** Flash alpha [age] ms after the strike: a hard flash, then a fast fade. */
    fun flashAlpha(age: Long): Int {
        if (age < 0 || age >= FLASH_MS) return 0
        if (age < 60) return FLASH_MAX_ALPHA
        return (FLASH_MAX_ALPHA * (FLASH_MS - age) / (FLASH_MS - 60)).toInt()
    }

    /** Milliseconds until the next ambient flash in a storm: 6 to 20 s. */
    fun randomFlashDelay(seed: Long) = 6000L + hash(seed, 17) % 14000
}
