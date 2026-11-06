package com.ularn.android.game

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import kotlin.math.min

/**
 * Shows [SidePanelView] along the right edge of [root] when the window is
 * wide enough (tablets, unfolded foldables, wide multi-window), narrowing
 * [mainViews] (the map and the controls overlay) to make room. It follows
 * window size changes (fold / unfold, multi-window, rotation) as they
 * happen; on phone-sized windows nothing changes.
 *
 * [changed] may be called from any thread.
 */
class SidePanelController(
    private val root: FrameLayout,
    private val mainViews: List<View>,
    stateOf: () -> GameState?,
) {
    private val density = root.resources.displayMetrics.density

    val panel = SidePanelView(root.context, stateOf)

    /** User setting; when false the panel never shows. */
    var enabled = true
        set(v) { if (field != v) { field = v; update() } }

    init {
        panel.visibility = View.GONE
        // Above the map and controls, below the start menu / edit bar.
        val above = mainViews.maxOf { root.indexOfChild(it) }
        root.addView(panel, above + 1, FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.END))

        // First decision from the configuration, so the first layout is already right.
        val cfg = root.resources.configuration
        apply(cfg.screenWidthDp.toFloat(), cfg.screenHeightDp.toFloat())

        root.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob ->
            if (r - l != or_ - ol || b - t != ob - ot) root.post { update() }
        }
    }

    fun changed() = panel.postInvalidateOnAnimation()

    /** Re-evaluates from the root's current size. */
    fun update() {
        val w = root.width - root.paddingLeft - root.paddingRight
        val h = root.height - root.paddingTop - root.paddingBottom
        if (w <= 0 || h <= 0) {
            val cfg = root.resources.configuration
            apply(cfg.screenWidthDp.toFloat(), cfg.screenHeightDp.toFloat())
        } else {
            apply(w / density, h / density)
        }
    }

    private fun apply(wDp: Float, hDp: Float) {
        val show = enabled && isWide(wDp, hDp)
        val panelPx = if (show) (panelWidthDp(wDp) * density).toInt() else 0
        for (v in mainViews) {
            val lp = v.layoutParams as? FrameLayout.LayoutParams ?: continue
            if (lp.rightMargin != panelPx) {
                lp.rightMargin = panelPx
                v.layoutParams = lp
            }
        }
        val plp = panel.layoutParams as FrameLayout.LayoutParams
        if (plp.width != panelPx) { plp.width = panelPx; panel.layoutParams = plp }
        val vis = if (show) View.VISIBLE else View.GONE
        if (panel.visibility != vis) panel.visibility = vis
        if (show) panel.invalidate()
    }

    companion object {
        /**
         * Tablet-class window: smallest side at least 600 dp, or a landscape
         * window that is both very wide and reasonably tall (not a phone on
         * its side, whose height is ~360-440 dp).
         */
        fun isWide(wDp: Float, hDp: Float): Boolean =
            (min(wDp, hDp) >= 600f && wDp >= 600f) || (wDp >= 900f && hDp >= 480f)

        fun panelWidthDp(wDp: Float): Float = (wDp * 0.28f).coerceIn(240f, 340f)
    }
}
