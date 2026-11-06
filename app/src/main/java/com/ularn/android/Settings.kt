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

    // ---- Wild Edition ----
    /** Feature mask passed to the game as ULARN_WILD (WILDF_* bits); default all on. */
    var wildFlags: Int
        get() = p.getInt("wildFlags", WILDF_ALL)
        set(v) = p.edit().putInt("wildFlags", v and WILDF_ALL).apply()

    /** Trading cards: pop up the card when one drops. */
    var cardToasts: Boolean
        get() = p.getBoolean("cardToasts", true)
        set(v) = p.edit().putBoolean("cardToasts", v).apply()

    fun wild(bit: Int) = (wildFlags and bit) != 0
    fun setWild(bit: Int, on: Boolean) { wildFlags = if (on) wildFlags or bit else wildFlags and bit.inv() }

    /** Dungeon Radio: music volume, 0..100 (Radio entry of the in-game menu). */
    var radioMusicVolume: Int
        get() = p.getInt("radioMusic", 60)
        set(v) = p.edit().putInt("radioMusic", v.coerceIn(0, 100)).apply()

    /** Dungeon Radio: sound effects volume, 0..100. */
    var radioSfxVolume: Int
        get() = p.getInt("radioSfx", 80)
        set(v) = p.edit().putInt("radioSfx", v.coerceIn(0, 100)).apply()

    companion object {
        // Feature bits: keep in sync with compat/wild.h (WILDF_*).
        const val WILDF_RADIO = 1 shl 0
        const val WILDF_CARDS = 1 shl 1
        const val WILDF_VENDING = 1 shl 2
        const val WILDF_HATS = 1 shl 3
        const val WILDF_COIN = 1 shl 4
        const val WILDF_GLITCH = 1 shl 5
        const val WILDF_FISHING = 1 shl 6
        const val WILDF_PETS = 1 shl 7
        const val WILDF_PERKS = 1 shl 8
        const val WILDF_WEATHER = 1 shl 9
        const val WILDF_PHOTO = 1 shl 10
        const val WILDF_ALL = 0x7ff

        /** Checkbox labels for the Settings "Wild features" section, in bit order. */
        val WILD_FEATURES = listOf(
            WILDF_RADIO to "Dungeon Radio: chiptune music and sound effects",
            WILDF_CARDS to "Monster trading cards drop from kills",
            WILDF_VENDING to "Vending machines, junk food, the rubber duck and the Roomba",
            WILDF_HATS to "Hats",
            WILDF_COIN to "LarnCoin at the bank (and the Tax Auditor)",
            WILDF_GLITCH to "The Glitch Realm (scroll of segfault)",
            WILDF_FISHING to "Fishing at fountains",
            WILDF_PETS to "Mysterious eggs hatch into pets",
            WILDF_PERKS to "Choose a perk on every level-up",
            WILDF_WEATHER to "Weather and day/night on the surface",
            WILDF_PHOTO to "Polaroid photo mode",
        )

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
