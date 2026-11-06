package com.ularn.android.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import com.ularn.android.Settings
import kotlin.math.PI
import kotlin.math.sin

/**
 * Low-HP cues for [GameView]: a pulsing red HP bar and a red vignette around
 * the map while HP is below the threshold, one vibration when it falls below
 * (again only after recovering), and a brief red flash on a big hit.
 *
 * Only animates while a cue is showing; otherwise the view redraws only when
 * the game changes.
 */
class DangerCues(private val view: View) {

    val tracker = DangerTracker()

    var pulseHpBar = true
    var vignette = true
    var vibrate = true
    var bigHitCue = true

    private var flashStart = 0L
    private var mapMode = false

    fun apply(s: Settings) {
        tracker.thresholdPct = s.dangerThresholdPct
        pulseHpBar = s.dangerPulse
        vignette = s.dangerVignette
        vibrate = s.dangerVibrate
        bigHitCue = s.bigHitCue
        view.invalidate()
    }

    /**
     * Call once per frame with fresh state. [haptics] is the app's existing
     * vibration setting; the cue's own toggle has to be on as well.
     */
    fun onFrame(s: GameState, haptics: Boolean) {
        mapMode = s.displayMode == GameState.DISPLAY_MAP
        val ev = tracker.update(s.hp, s.hpMax)
        val now = SystemClock.uptimeMillis()
        val buzz = haptics && vibrate
        if (ev and DangerTracker.CROSSED_BELOW != 0 && buzz) vibrate(longArrayOf(0, 70, 90, 70))
        if (ev and DangerTracker.BIG_HIT != 0 && bigHitCue) {
            flashStart = now
            if (buzz && ev and DangerTracker.CROSSED_BELOW == 0) vibrate(longArrayOf(0, 45))
        }
        if (animating(now)) view.postInvalidateOnAnimation()
    }

    private fun flashing(now: Long) = flashStart > 0 && now - flashStart < FLASH_MS

    private fun animating(now: Long) =
        mapMode && ((tracker.inDanger && (pulseHpBar || vignette)) || flashing(now))

    /** 0..1, a slow heartbeat-like pulse. */
    private fun pulse(now: Long) = (0.5 + 0.5 * sin(2 * PI * (now % PULSE_MS) / PULSE_MS)).toFloat()

    /** The HP bar's fill colour: [normal], or a pulsing red while in danger. */
    fun hpBarColor(normal: Int): Int {
        if (!tracker.inDanger || !pulseHpBar) return normal
        val p = pulse(SystemClock.uptimeMillis())
        return Color.rgb(lerp(140, 255, p), lerp(20, 70, p), lerp(20, 60, p))
    }

    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flashPaint = Paint()
    private val shaderMatrix = Matrix()
    private var shaderW = -1f
    private var shaderH = -1f

    /** Draws the vignette / hit flash over the map area. */
    fun drawMapOverlay(canvas: Canvas, map: RectF) {
        if (map.width() <= 0f || map.height() <= 0f) return
        val now = SystemClock.uptimeMillis()
        if (tracker.inDanger && vignette) {
            if (map.width() != shaderW || map.height() != shaderH || vignettePaint.shader == null) {
                shaderW = map.width(); shaderH = map.height()
                vignettePaint.shader = RadialGradient(
                    0f, 0f, 100f,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(150, 200, 0, 0)),
                    floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP,
                )
            }
            // An ellipse touching the middle of each edge; corners are reddest.
            shaderMatrix.setScale(map.width() / 200f, map.height() / 200f)
            shaderMatrix.postTranslate(map.centerX(), map.centerY())
            vignettePaint.shader.setLocalMatrix(shaderMatrix)
            vignettePaint.alpha = if (pulseHpBar) (150 + 105 * pulse(now)).toInt() else 220
            canvas.drawRect(map, vignettePaint)
        }
        if (flashing(now)) {
            val f = 1f - (now - flashStart).toFloat() / FLASH_MS
            flashPaint.color = Color.argb((90 * f).toInt().coerceIn(0, 255), 255, 30, 20)
            canvas.drawRect(map, flashPaint)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(pattern: LongArray) {
        val v = view.context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            else v.vibrate(pattern, -1)
        } catch (_: Exception) {
        }
    }

    private fun lerp(a: Int, b: Int, t: Float) = (a + (b - a) * t).toInt()

    companion object {
        private const val FLASH_MS = 350L
        private const val PULSE_MS = 1100L
    }
}
