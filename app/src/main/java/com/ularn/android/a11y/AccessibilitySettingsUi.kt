package com.ularn.android.a11y

import android.graphics.Typeface
import android.os.Build
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/** The "Accessibility" section of the settings dialog. Changes apply at once. */
object AccessibilitySettingsUi {

    fun addTo(box: LinearLayout, prefs: AccessibilityPrefs, onChanged: () -> Unit) {
        val ctx = box.context
        val density = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        box.addView(TextView(ctx).apply {
            text = "Accessibility"
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(14), 0, dp(2))
            if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        })

        // Text size, 85%..150% in 5% steps.
        val steps = ((AccessibilityPrefs.MAX_SCALE - AccessibilityPrefs.MIN_SCALE) * 20).toInt()
        val label = TextView(ctx)
        fun pct(scale: Float) = "${Math.round(scale * 100)}%"
        fun showLabel(scale: Float) { label.text = "Game text size: ${pct(scale)}" }
        showLabel(prefs.textScale)
        val seek = SeekBar(ctx).apply {
            id = View.generateViewId()
            max = steps
            progress = Math.round((prefs.textScale - AccessibilityPrefs.MIN_SCALE) * 20)
            contentDescription = "Game text size"
            if (Build.VERSION.SDK_INT >= 30) stateDescription = pct(prefs.textScale)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    val v = AccessibilityPrefs.MIN_SCALE + p / 20f
                    showLabel(v)
                    if (Build.VERSION.SDK_INT >= 30) s.stateDescription = pct(v)
                    if (fromUser) { prefs.textScale = v; onChanged() }
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        label.labelFor = seek.id
        box.addView(label)
        box.addView(seek)
        box.addView(TextView(ctx).apply {
            text = "HUD, messages, text pages and button labels."
            textSize = 12f; alpha = 0.7f
        })

        fun check(t: String, v: Boolean, set: (Boolean) -> Unit) = box.addView(CheckBox(ctx).apply {
            text = t; isChecked = v
            setOnCheckedChangeListener { _, b -> set(b); onChanged() }
        })
        check("Also follow the system font size", prefs.followSystemFontScale) { prefs.followSystemFontScale = it }
        check("High contrast (solid outlined HUD and buttons, white text, underlined highlights)", prefs.highContrast) { prefs.highContrast = it }
        check("Announce new game messages (TalkBack)", prefs.announceMessages) { prefs.announceMessages = it }
    }
}
