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

    var checkpointing: Boolean
        get() = p.getBoolean("checkpoint", true)
        set(v) = p.edit().putBoolean("checkpoint", v).apply()

    var noNap: Boolean
        get() = p.getBoolean("noNap", false)
        set(v) = p.edit().putBoolean("noNap", v).apply()

    var noBeep: Boolean
        get() = p.getBoolean("noBeep", false)
        set(v) = p.edit().putBoolean("noBeep", v).apply()

    var theme: Int
        get() = p.getInt("theme", 0)
        set(v) = p.edit().putInt("theme", v).apply()

    var showCursor: Boolean
        get() = p.getBoolean("cursor", true)
        set(v) = p.edit().putBoolean("cursor", v).apply()

    /** Landscape: shrink the map so it sits between the side controls. */
    var fitMapBesideControls: Boolean
        get() = p.getBoolean("fitBeside", true)
        set(v) = p.edit().putBoolean("fitBeside", v).apply()

    /** Hardware/Bluetooth keyboard arrow keys map to hjkl. */
    var arrowsAsMoves: Boolean
        get() = p.getBoolean("arrows", true)
        set(v) = p.edit().putBoolean("arrows", v).apply()

    fun buildOptsFile(): String = buildString {
        val name = playerName.replace("\"", "").take(34).ifBlank { "Adventurer" }
        appendLine("name: \"$name\"")
        appendLine(if (female) "female" else "male")
        appendLine("no-mail")
        if (checkpointing) appendLine("enable-checkpointing")
        if (skipIntro) appendLine("no-introduction")
        if (noNap) appendLine("no-nap")
        if (noBeep) appendLine("no-beep")
    }
}
