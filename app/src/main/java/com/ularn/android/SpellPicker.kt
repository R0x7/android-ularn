package com.ularn.android

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.ularn.android.game.GameState
import com.ularn.android.game.KeyFollowUp

/**
 * The Cast button: lists the spells learned this run (spell_known[] in the
 * shared state, from the game's spelknow[]) and casts the chosen one by
 * sending `c`, then its three-letter code once the game asks for it
 * (spell.c `get_spell_code()`). A following "In what direction?" is
 * answered with the usual swipe / direction UI.
 */
class SpellPicker(
    private val activity: Activity,
    private val state: () -> GameState?,
    private val running: () -> Boolean,
    private val followUp: KeyFollowUp,
    private val send: (ByteArray) -> Unit,
) {
    class Spell(val code: String, val name: String, val description: String)

    private val density get() = activity.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    /** Opens the picker in command mode; otherwise sends `c` like a plain key button. */
    fun onCastButton() {
        if (!running()) return
        val s = state()
        if (s == null || !s.refresh() || s.inputMode != GameState.INPUT_COMMAND) {
            send(byteArrayOf('c'.code.toByte()))
            return
        }
        show(s)
    }

    private fun show(s: GameState) {
        val points = s.spells
        val known = (0 until s.spellCount).filter { s.spellKnown(it) }.map { i ->
            val code = s.spellCode(i)
            BY_CODE[code] ?: Spell(code, code, "")
        }
        val usable = points > 0

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
            addView(TextView(activity).apply {
                text = "Cast a spell"
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(activity).apply {
                text = "Spell points: $points / ${s.spellMax}"
                alpha = 0.8f
            })
            if (!usable) addView(TextView(activity).apply {
                text = "You have no spell points left. They regenerate over time."
                setTextColor(0xFFFFBE3C.toInt())
                setPadding(0, dp(6), 0, 0)
            })
            if (known.isEmpty()) addView(TextView(activity).apply {
                text = "You haven't learned any spells yet. Reading books teaches them."
                setPadding(0, dp(6), 0, 0)
            })
        }

        val adapter = object : BaseAdapter() {
            override fun getCount() = known.size
            override fun getItem(position: Int) = known[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun areAllItemsEnabled() = usable
            override fun isEnabled(position: Int) = usable
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val sp = known[position]
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(20), dp(10), dp(20), dp(10))
                    alpha = if (usable) 1f else 0.4f
                }
                val top = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                top.addView(TextView(activity).apply {
                    text = sp.name
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                top.addView(TextView(activity).apply {
                    text = sp.code
                    textSize = 15f
                    typeface = Typeface.MONOSPACE
                    setTextColor(0xFFFFBE3C.toInt())
                })
                row.addView(top)
                if (sp.description.isNotEmpty()) row.addView(TextView(activity).apply {
                    text = sp.description
                    textSize = 13f
                    alpha = 0.75f
                })
                return row
            }
        }

        AlertDialog.Builder(activity)
            .setCustomTitle(header)
            .setAdapter(adapter) { _, which -> cast(known[which].code) }
            .setNeutralButton("Type code…") { _, _ -> typeCode() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun typeCode() {
        val edit = EditText(activity).apply {
            hint = "e.g. pro"
            isSingleLine = true
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(3))
        }
        val box = LinearLayout(activity).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(edit, LinearLayout.LayoutParams(-1, -2))
        }
        AlertDialog.Builder(activity)
            .setTitle("Spell code")
            .setView(box)
            .setPositiveButton("Cast") { _, _ ->
                val code = edit.text.toString().trim().lowercase()
                if (code.length == 3 && code.all { it in 'a'..'z' }) cast(code)
                else Toast.makeText(activity, "A spell code is three letters, e.g. pro", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
        edit.requestFocus()
    }

    /**
     * `c`, then the code once get_spell_code() prompts. If it doesn't (no
     * spell points: "You don't have any spells!"), the letters are not sent,
     * so they can't be mistaken for commands.
     */
    private fun cast(code: String) {
        val s = state() ?: return
        if (!running() || !s.refresh() || s.inputMode != GameState.INPUT_COMMAND) return
        followUp.start("c", KeyFollowUp.Step(code, GameState.INPUT_PROMPT) { it.startsWith(SPELL_PROMPT) })
    }

    companion object {
        /** get_spell_code()'s accepted answers start with this. */
        private const val SPELL_PROMPT = "abcdefghijklmnopqrstuvwxyz"

        /** Names and codes from spell.c (spelcode[], spelname[]), in game order. */
        val SPELLS = listOf(
            Spell("pro", "Protection", "Raises your armor class by 2 for a while."),
            Spell("mle", "Magic missile", "Fires a magic missile in a chosen direction."),
            Spell("dex", "Dexterity", "Raises your dexterity by 3 for a while."),
            Spell("sle", "Sleep", "Puts an adjacent monster to sleep; you hit it 2–4 times."),
            Spell("chm", "Charm monster", "Monsters may be awed by you and leave you alone for a while."),
            Spell("ssp", "Sonic spear", "Hurls a screeching sound blast in a chosen direction."),
            Spell("web", "Web", "Entangles an adjacent monster; you hit it 3–5 times."),
            Spell("str", "Strength", "Raises your strength by 3 for a while."),
            Spell("enl", "Enlightenment", "Reveals the map in a wide area around you."),
            Spell("hel", "Healing", "Restores 20 + 2 × your level hit points."),
            Spell("cbl", "Cure blindness", "Cures blindness."),
            Spell("cre", "Create monster", "Creates a monster next to you."),
            Spell("pha", "Phantasmal forces", "An illusion that kills an adjacent monster if it believes it (needs wisdom)."),
            Spell("inv", "Invisibility", "Makes you invisible for a while (longer with an amulet of invisibility)."),
            Spell("bal", "Fireball", "Hurls a ball of fire in a chosen direction."),
            Spell("cld", "Cold", "Sends a cone of cold in a chosen direction."),
            Spell("ply", "Polymorph", "Turns an adjacent monster into a random other monster."),
            Spell("can", "Cancellation", "For a few turns, monsters can't use their special attacks."),
            Spell("has", "Haste self", "Speeds up your movements for a few turns."),
            Spell("ckl", "Cloud kill", "A cloud of poison gas hurts every monster around you."),
            Spell("vpr", "Vaporize rock", "Turns the walls around you into open floor."),
            Spell("dry", "Dehydration", "Dries up an adjacent monster for heavy damage."),
            Spell("lit", "Lightning", "Shoots a lightning bolt in a chosen direction."),
            Spell("drl", "Drain life", "Costs you up to half your max HP to deal double that to an adjacent monster."),
            Spell("glo", "Invulnerability", "A globe that raises your armor class by 10 for a while (costs 1 INT)."),
            Spell("flo", "Flood", "A deluge of water hurts every monster around you."),
            Spell("fgr", "Finger of death", "Kills an adjacent monster if your wisdom holds (rarely kills you)."),
            Spell("sca", "Scare monster", "Frightens monsters so they may not hit you (longer with the Hand of Fear)."),
            Spell("hld", "Hold monster", "Freezes monsters in their tracks for a few turns."),
            Spell("stp", "Time stop", "Stops time for a while: monsters can't act."),
            Spell("tel", "Teleport away", "Teleports an adjacent monster elsewhere on the level."),
            Spell("mfi", "Magic fire", "A curtain of fire burns every monster around you."),
            Spell("mkw", "Make a wall", "Builds a wall in a chosen direction."),
            Spell("sph", "Sphere of annihilation", "Sends a sphere that destroys whatever it meets (costs 1 INT; risky)."),
            Spell("gen", "Genocide", "Wipes out one kind of monster (costs 1 INT; forgotten after use)."),
            Spell("sum", "Summon demon", "A demon strikes an adjacent monster, but may turn on you."),
            Spell("wtw", "Walk through walls", "Lets you walk through walls for a few turns."),
            Spell("alt", "Alter reality", "Rebuilds the level, scattering its items and monsters (costs 1 INT; forgotten after use)."),
            Spell("per", "Permanence", "Makes your active spell effects last almost forever (costs 1 INT; forgotten after use)."),
        )
        private val BY_CODE = SPELLS.associateBy { it.code }
    }
}
