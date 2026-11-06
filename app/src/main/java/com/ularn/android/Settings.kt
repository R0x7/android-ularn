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

    /** Difficulty for the next new game (the game's `-d` option); see [MAX_DIFFICULTY]. */
    var difficulty: Int
        get() = p.getInt("difficulty", 0).coerceIn(0, MAX_DIFFICULTY)
        set(v) = p.edit().putInt("difficulty", v.coerceIn(0, MAX_DIFFICULTY)).apply()

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

    /** Round minimap in a corner of the map (tap it for the whole level). */
    var showMinimap: Boolean
        get() = p.getBoolean("minimap", true)
        set(v) = p.edit().putBoolean("minimap", v).apply()

    /** Wide windows (tablets, foldables): stats / map / inventory panel beside the map. */
    var sidePanel: Boolean
        get() = p.getBoolean("sidePanel", true)
        set(v) = p.edit().putBoolean("sidePanel", v).apply()

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
        /** Highest difficulty the slider offers (the game itself accepts up to 100). */
        const val MAX_DIFFICULTY = 10

        /** Game arguments for a new game at difficulty [level]. */
        fun difficultyArgs(level: Int): List<String> =
            if (level > 0) listOf("-d", level.coerceAtMost(MAX_DIFFICULTY).toString()) else emptyList()

        /**
         * What [level] does to monsters, mirroring the game's sethard():
         * hit points x(6+k)/6, damage x(6+k)/5, gold x10/(10+k),
         * armour class -k, experience x7/(7+k).
         */
        fun describeDifficulty(level: Int): String {
            val k = level.coerceIn(0, MAX_DIFFICULTY)
            if (k == 0) return "Standard game: monsters at their normal strength."
            fun pct(f: Double) = Math.round(f * 100).toInt()
            return "Monsters: ${pct((6 + k) / 6.0)}% hit points, ${pct((6 + k) / 5.0)}% damage, " +
                "armour class -$k. They drop ${pct(10.0 / (10 + k))}% gold and give " +
                "${pct(7.0 / (7 + k))}% experience."
        }

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
    fun buildOptsFile(playerName: String = this.playerName, female: Boolean = this.female): String {
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
