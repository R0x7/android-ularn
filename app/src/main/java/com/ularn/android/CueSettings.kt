package com.ularn.android

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView

/** The "Danger cues" and "Statistics" sections of the settings dialog. */
object CueSettings {

    private val THRESHOLDS = listOf(10, 15, 20, 25, 30, 35, 40, 50)

    fun addTo(context: Context, box: LinearLayout, settings: Settings) {
        val d = context.resources.displayMetrics.density
        fun header(t: String) = box.addView(TextView(context).apply {
            text = t; setTypeface(typeface, Typeface.BOLD); setPadding(0, (14 * d).toInt(), 0, (2 * d).toInt())
        })
        fun check(t: String, v: Boolean, set: (Boolean) -> Unit) = box.addView(CheckBox(context).apply {
            text = t; isChecked = v; setOnCheckedChangeListener { _, b -> set(b) }
        })

        header("Danger cues (low HP)")
        box.addView(TextView(context).apply { text = "Warn when HP is below" })
        box.addView(Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, THRESHOLDS.map { "$it% of max HP" })
            val cur = THRESHOLDS.indexOf(settings.dangerThresholdPct)
            setSelection(if (cur >= 0) cur else THRESHOLDS.indexOf(25))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    settings.dangerThresholdPct = THRESHOLDS[pos]
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        })
        check("Pulse the HP bar red", settings.dangerPulse) { settings.dangerPulse = it }
        check("Red vignette around the map", settings.dangerVignette) { settings.dangerVignette = it }
        check("Vibrate when HP falls below it", settings.dangerVibrate) { settings.dangerVibrate = it }
        check("Flash red on a big hit (20% of max HP or more)", settings.bigHitCue) { settings.bigHitCue = it }
        box.addView(TextView(context).apply {
            text = "Vibration also needs \"Vibrate on press\" (Edit controls → Look) to be on."
            textSize = 12f; alpha = 0.7f
        })

        header("Statistics")
        check("Show a message when an achievement unlocks", settings.achievementToasts) { settings.achievementToasts = it }
    }
}
