package com.ularn.android.controls

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saves one layout per orientation, plus named user profiles that can be
 * loaded into either orientation. Layouts are also exportable as JSON text so
 * they can be shared or backed up.
 */
class LayoutStore(context: Context) {
    private val prefs = context.getSharedPreferences("controls", Context.MODE_PRIVATE)

    /** @param wDp,hDp the screen size, used to build the default layout. */
    fun load(landscape: Boolean, wDp: Float, hDp: Float): ControlLayout {
        val json = prefs.getString(key(landscape), null)
        if (json != null) runCatching { return fromJson(json) }
        return if (landscape) DefaultLayouts.landscape(wDp, hDp) else DefaultLayouts.portrait(wDp, hDp)
    }

    fun save(landscape: Boolean, layout: ControlLayout) {
        prefs.edit().putString(key(landscape), toJson(layout)).apply()
    }

    fun reset(landscape: Boolean, wDp: Float, hDp: Float): ControlLayout {
        prefs.edit().remove(key(landscape)).apply()
        return load(landscape, wDp, hDp)
    }

    // ---- named profiles

    fun profileNames(): List<String> =
        prefs.getStringSet("profiles", emptySet())!!.sorted()

    fun saveProfile(name: String, layout: ControlLayout) {
        val names = prefs.getStringSet("profiles", emptySet())!!.toMutableSet()
        names += name
        prefs.edit().putStringSet("profiles", names).putString("profile:$name", toJson(layout)).apply()
    }

    fun loadProfile(name: String): ControlLayout? =
        prefs.getString("profile:$name", null)?.let { runCatching { fromJson(it) }.getOrNull() }

    fun deleteProfile(name: String) {
        val names = prefs.getStringSet("profiles", emptySet())!!.toMutableSet()
        names -= name
        prefs.edit().putStringSet("profiles", names).remove("profile:$name").apply()
    }

    private fun key(landscape: Boolean) = if (landscape) "layout_land" else "layout_port"

    companion object {
        fun toJson(layout: ControlLayout): String {
            val arr = JSONArray()
            for (c in layout.controls) {
                arr.put(JSONObject().apply {
                    put("id", c.id)
                    put("type", c.type.name)
                    put("label", c.label)
                    put("keys", c.keys)
                    put("action", c.action.name)
                    put("cx", c.cx.toDouble())
                    put("cy", c.cy.toDouble())
                    put("w", c.widthDp.toDouble())
                    put("h", c.heightDp.toDouble())
                    put("repeat", c.repeat)
                    put("shape", c.shape.name)
                    if (c.type == ControlType.DPAD) {
                        put("dpad", JSONArray(c.dpadKeys))
                        put("eightWay", c.eightWay)
                    }
                })
            }
            return JSONObject().apply {
                put("version", 1)
                put("opacity", layout.opacity.toDouble())
                put("haptics", layout.haptics)
                put("controls", arr)
            }.toString(2)
        }

        fun fromJson(json: String): ControlLayout {
            val o = JSONObject(json)
            val arr = o.getJSONArray("controls")
            val list = mutableListOf<ControlSpec>()
            for (i in 0 until arr.length()) {
                val j = arr.getJSONObject(i)
                val type = enumOr(j.optString("type"), ControlType.BUTTON)
                val spec = ControlSpec(
                    id = j.optString("id", "c$i"),
                    type = type,
                    label = j.optString("label", ""),
                    keys = j.optString("keys", ""),
                    action = enumOr(j.optString("action"), ButtonAction.KEYS),
                    cx = j.optDouble("cx", 0.5).toFloat().coerceIn(0f, 1f),
                    cy = j.optDouble("cy", 0.5).toFloat().coerceIn(0f, 1f),
                    widthDp = j.optDouble("w", 56.0).toFloat().coerceIn(24f, 400f),
                    heightDp = j.optDouble("h", 56.0).toFloat().coerceIn(24f, 400f),
                    repeat = j.optBoolean("repeat", false),
                    shape = enumOr(j.optString("shape"), ButtonShape.ROUNDED),
                    eightWay = j.optBoolean("eightWay", true),
                )
                j.optJSONArray("dpad")?.let { d ->
                    if (d.length() == 9) spec.dpadKeys = MutableList(9) { d.optString(it, "") }
                }
                list += spec
            }
            return ControlLayout(
                list,
                o.optDouble("opacity", 0.55).toFloat().coerceIn(0.1f, 1f),
                o.optBoolean("haptics", true),
            )
        }

        private inline fun <reified T : Enum<T>> enumOr(name: String?, def: T): T =
            runCatching { enumValueOf<T>(name!!) }.getOrDefault(def)
    }
}
