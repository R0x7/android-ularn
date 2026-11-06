package com.ularn.android

import android.content.Context

/** App + game preferences. */
class Settings(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var playerName: String
        get() = p.getString("name", "Adventurer")!!
        set(v) = p.edit().putString("name", v).apply()

    var female: Boolean
        get() = p.getBoolean("female", false)
        set(v) = p.edit().putBoolean("female", v).apply()

    var skipIntro: Boolean
        get() = p.getBoolean("skipIntro", false)
        set(v) = p.edit().putBoolean("skipIntro", v).apply()

    var noNap: Boolean
        get() = p.getBoolean("noNap", false)
        set(v) = p.edit().putBoolean("noNap", v).apply()

    var noBeep: Boolean
        get() = p.getBoolean("noBeep", false)
        set(v) = p.edit().putBoolean("noBeep", v).apply()

    /**
     * Ularn 1.6's "enhanced interface": doors open with 'o' + direction
     * instead of by walking into them, and 'O' opens a chest you stand on.
     */
    var enhancedInterface: Boolean
        get() = p.getBoolean("enhanced", false)
        set(v) = p.edit().putBoolean("enhanced", v).apply()

    /** Tap a square on the map to step towards it (long-press to run). */
    var tapToMove: Boolean
        get() = p.getBoolean("tapMove", true)
        set(v) = p.edit().putBoolean("tapMove", v).apply()

    /** Swipe on the map to step; hold to keep walking. */
    var swipeToMove: Boolean
        get() = p.getBoolean("swipeMove", true)
        set(v) = p.edit().putBoolean("swipeMove", v).apply()

    /** A quick flick on the map runs instead of stepping. */
    var flickToRun: Boolean
        get() = p.getBoolean("flickRun", false)
        set(v) = p.edit().putBoolean("flickRun", v).apply()

    /** See [ORIENTATIONS]. */
    var orientation: Int
        get() = p.getInt("orientation", 0)
        set(v) = p.edit().putInt("orientation", v).apply()

    /** Landscape: keep the map clear of the side controls. */
    var fitMapBesideControls: Boolean
        get() = p.getBoolean("fitBeside", true)
        set(v) = p.edit().putBoolean("fitBeside", v).apply()

    /** Hardware/Bluetooth keyboard arrow keys map to hjkl. */
    var arrowsAsMoves: Boolean
        get() = p.getBoolean("arrows", true)
        set(v) = p.edit().putBoolean("arrows", v).apply()

    /** Map zoom, as tile size in dp; 0 = automatic. */
    var tileDp: Float
        get() = p.getFloat("tileDp", 0f)
        set(v) = p.edit().putFloat("tileDp", v).apply()

    // danger cues / stats
    /** Low-HP cues start below this percentage of max HP. */
    var dangerThresholdPct: Int
        get() = p.getInt("dangerPct", 25)
        set(v) = p.edit().putInt("dangerPct", v).apply()

    /** Low HP: the HP bar pulses red. */
    var dangerPulse: Boolean
        get() = p.getBoolean("dangerPulse", true)
        set(v) = p.edit().putBoolean("dangerPulse", v).apply()

    /** Low HP: a red vignette around the map. */
    var dangerVignette: Boolean
        get() = p.getBoolean("dangerVignette", true)
        set(v) = p.edit().putBoolean("dangerVignette", v).apply()

    /** Vibrate once when HP falls below the threshold. */
    var dangerVibrate: Boolean
        get() = p.getBoolean("dangerVibrate", true)
        set(v) = p.edit().putBoolean("dangerVibrate", v).apply()

    /** Red flash (and a short buzz) when one hit takes 20% of max HP or more. */
    var bigHitCue: Boolean
        get() = p.getBoolean("bigHitCue", true)
        set(v) = p.edit().putBoolean("bigHitCue", v).apply()

    /** Show a toast when an achievement unlocks. */
    var achievementToasts: Boolean
        get() = p.getBoolean("achToasts", true)
        set(v) = p.edit().putBoolean("achToasts", v).apply()

    companion object {
        /** Rotation choices: label to ActivityInfo.screenOrientation. */
        val ORIENTATIONS = listOf(
            "Follow system rotation setting" to android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER,
            "Always auto-rotate" to android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,
            "Portrait" to android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
            "Landscape" to android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
        )
        const val ORIENT_PORTRAIT = 2
        const val ORIENT_LANDSCAPE = 3
    }

    /** Builds Ularn 1.6.3a's ularn.opt: "OPTION name:X,gender:male,...". */
    fun buildOptsFile(): String {
        // ',' and ':' separate options in ularn.opt.
        val name = playerName.replace(Regex("[,:\\r\\n]"), " ").trim().take(30).ifBlank { "Adventurer" }
        val opts = mutableListOf("name:$name", "gender:" + if (female) "female" else "male")
        if (skipIntro) opts += "nowelcome"
        if (noNap) opts += "nonap"
        if (noBeep) opts += "nobeep"
        opts += if (enhancedInterface) "enhanced_interface" else "noenhanced_interface"
        return "# Written by the Ularn app; edit in Settings.\nOPTION " + opts.joinToString(",") + "\n"
    }
}
