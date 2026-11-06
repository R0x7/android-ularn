package com.ularn.android.a11y

import android.content.Context
import android.content.res.Configuration

/**
 * Accessibility preferences, stored with the other settings (the same
 * "settings" SharedPreferences file) but kept here so Settings.kt stays small.
 */
class AccessibilityPrefs(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Text size for the HUD, messages, text pages and button labels (0.85..1.5). */
    var textScale: Float
        get() = p.getFloat("a11yTextScale", 1f).coerceIn(MIN_SCALE, MAX_SCALE)
        set(v) = p.edit().putFloat("a11yTextScale", v.coerceIn(MIN_SCALE, MAX_SCALE)).apply()

    /** Opaque, outlined HUD and buttons, pure white text, no colour-only cues. */
    var highContrast: Boolean
        get() = p.getBoolean("a11yHighContrast", false)
        set(v) = p.edit().putBoolean("a11yHighContrast", v).apply()

    /** Send new message-area lines to TalkBack. */
    var announceMessages: Boolean
        get() = p.getBoolean("a11yAnnounce", false)
        set(v) = p.edit().putBoolean("a11yAnnounce", v).apply()

    /** Also scale game text by the system font size (Android Settings > Display). */
    var followSystemFontScale: Boolean
        get() = p.getBoolean("a11yFollowFont", true)
        set(v) = p.edit().putBoolean("a11yFollowFont", v).apply()

    /**
     * The scale to draw game text at. The system font scale is followed, but
     * clamped: the HUD and the 80-column text pages are fixed layouts, and
     * the largest system sizes (up to 2x) would leave no room for the map.
     */
    fun effectiveScale(config: Configuration): Float {
        val system = if (followSystemFontScale) config.fontScale.coerceIn(0.85f, 1.3f) else 1f
        return (textScale * system).coerceIn(0.75f, 1.9f)
    }

    companion object {
        const val MIN_SCALE = 0.85f
        const val MAX_SCALE = 1.5f
    }
}
