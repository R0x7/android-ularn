package com.ularn.android.saves

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.text.InputType
import android.text.format.DateUtils
import android.util.Log
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.ularn.android.GameSession
import com.ularn.android.Settings
import java.util.concurrent.Executors

/**
 * The start screen's save slots: lists them, and runs continue / resume /
 * new game / rename / delete / export / import. MainActivity calls
 * [populate] from its start panel, [startScoreboard]-style runs go through
 * [useNoSlot], and progress is recorded with [recordProgress].
 */
class SlotsController(
    private val activity: Activity,
    private val session: GameSession,
    private val settings: Settings,
    /** MainActivity.startGame. */
    private val startGame: (List<String>) -> Unit,
    /** Rebuild the start panel. */
    private val refresh: () -> Unit,
) {
    val slots = SaveSlots(session.workDir)

    /** The slot the current / last game runs in (null: scoreboard etc.). */
    var activeSlot: Int? = null
        private set

    /** Slot being exported or imported; no game may start in it meanwhile. */
    private var busySlot: Int? = null
    private val io = Executors.newSingleThreadExecutor()
    private val prefs = activity.getSharedPreferences("saves", Context.MODE_PRIVATE)

    private val density = activity.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()
    private fun toast(s: String) = Toast.makeText(activity, s, Toast.LENGTH_LONG).show()

    /** Call once at startup, before anything reads the slots. */
    fun migrate() {
        try {
            val slot = slots.migrateLegacy(settings.playerName, settings.female)
            if (slot != null) Log.i(TAG, "moved the existing game into slot $slot")
        } catch (e: Exception) {
            // Never fatal: the old files stay where they were.
            Log.e(TAG, "migration failed", e)
        }
    }

    // ----------------------------------------------------------- start panel

    /**
     * Adds the slot list to the start panel. [item] adds one of the panel's
     * standard buttons (label, accent, action).
     */
    fun populate(panel: LinearLayout, item: (String, Boolean, () -> Unit) -> Unit) {
        val list = slots.list()
        val recent = list.filter { !it.isEmpty }.maxByOrNull { it.meta?.lastPlayed ?: 0L }
        if (recent != null && busySlot != recent.id) {
            val verb = if (recent.hasSave) "Continue" else "Resume"
            item("$verb: ${recent.title}", true) { play(recent) }
        } else if (recent == null) {
            item("New game", true) { slots.firstEmpty()?.let { askNewGame(it) } ?: toast("No empty slot") }
        }

        panel.addView(TextView(activity).apply {
            text = "Save slots"
            setTextColor(Color.LTGRAY)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(4), dp(16), 0, dp(2))
            if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        })
        for (info in list) panel.addView(slotCard(info), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        item("Import a saved game…", false) { pickImport(0) }
    }

    private fun slotCard(info: SaveSlots.SlotInfo): View {
        val m = info.meta
        val busy = busySlot == info.id
        val line1: String
        val line2: String
        val line3: String?
        if (info.isEmpty) {
            line1 = "Slot ${info.id} · Empty"
            line2 = if (busy) "Importing…" else "Tap to start a new game or import one"
            line3 = null
        } else {
            line1 = "Slot ${info.id} · ${info.title}"
            val parts = mutableListOf<String>()
            if (m?.label != null && m.character != null && m.label != m.character) parts += m.character
            parts += if (m != null && m.level >= 0) "Level ${m.level}" else "Level ?"
            m?.depthDescription?.let { parts += it }
            line2 = parts.joinToString(" · ")
            val state = when {
                busy -> "Exporting…"
                info.hasSave -> "Saved game"
                else -> "Checkpoint"
            }
            val whenText = m?.lastPlayed?.takeIf { it > 0 }?.let {
                DateUtils.formatDateTime(activity, it,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH)
            }
            line3 = if (whenText != null) "$state · last played $whenText" else state
        }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            minimumHeight = dp(48)
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), rounded(Color.argb(240, 80, 70, 40), Color.rgb(255, 190, 60)))
                addState(intArrayOf(android.R.attr.state_focused), rounded(Color.argb(230, 45, 47, 55), Color.rgb(255, 190, 60)))
                addState(intArrayOf(), rounded(Color.argb(220, 45, 47, 55), Color.argb(70, 255, 255, 255)))
            }
            isClickable = true
            isFocusable = true
            fun tv(t: String, size: Float, color: Int, bold: Boolean = false) = addView(TextView(activity).apply {
                text = t; textSize = size; setTextColor(color)
                if (bold) setTypeface(typeface, Typeface.BOLD)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            tv(line1, 15f, if (info.isEmpty) Color.LTGRAY else Color.WHITE, bold = !info.isEmpty)
            tv(line2, 13f, Color.rgb(210, 210, 210))
            line3?.let { tv(it, 12f, Color.rgb(190, 190, 190)) }
            contentDescription = listOfNotNull(line1, line2, line3).joinToString(". ")
            setOnClickListener { slotActions(slots.info(info.id)) }
        }
    }

    private fun rounded(fill: Int, stroke: Int) = GradientDrawable().apply {
        cornerRadius = dp(12).toFloat(); setColor(fill); setStroke(dp(1), stroke)
    }

    /** Refuses slot operations while a game runs or the slot is busy. */
    private fun canTouch(id: Int): Boolean {
        if (session.isRunning) { toast("Leave the current game first"); return false }
        if (busySlot != null) { toast(if (busySlot == id) "Slot $id is busy — please wait" else "Please wait for the current export or import"); return false }
        return true
    }

    private fun slotActions(info: SaveSlots.SlotInfo) {
        if (!canTouch(info.id)) return
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (info.isEmpty) {
            actions += "New game" to { askNewGame(info.id) }
            actions += "Import a saved game into this slot" to { pickImport(info.id) }
        } else {
            if (info.hasSave) actions += "Continue saved game" to { play(info) }
            else actions += "Resume from checkpoint" to { play(info) }
            actions += "New game in this slot…" to {
                confirm("Start a new game in slot ${info.id}? ${info.title}'s game will be deleted.", "Delete & start") {
                    askNewGame(info.id)
                }
            }
            actions += "Rename…" to { askRename(info) }
            actions += "Export to a file…" to { pickExport(info) }
            actions += "Delete…" to {
                confirm("Delete slot ${info.id} (${info.title})? Its saved game and checkpoint will be permanently deleted.", "Delete") {
                    if (canTouch(info.id)) { slots.delete(info.id); toast("Slot ${info.id} deleted"); refresh() }
                }
            }
        }
        AlertDialog.Builder(activity)
            .setTitle(if (info.isEmpty) "Slot ${info.id} (empty)" else "Slot ${info.id}: ${info.title}")
            .setItems(actions.map { it.first }.toTypedArray()) { _, i -> actions[i].second() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirm(message: String, ok: String, action: () -> Unit) {
        AlertDialog.Builder(activity).setMessage(message)
            .setPositiveButton(ok) { _, _ -> action() }
            .setNegativeButton("Cancel", null).show()
    }

    // ------------------------------------------------------------ playing

    /** Continue the slot's save, or resume its checkpoint if there is no save. */
    private fun play(info: SaveSlots.SlotInfo) {
        if (!canTouch(info.id)) return
        val now = slots.info(info.id)
        when {
            // Like the game itself: a save wins over a checkpoint (restoring
            // the checkpoint would leave a stale save that wins next time).
            now.hasSave -> start(info.id, emptyList())
            now.hasCheckpoint -> start(info.id, listOf("-r"))
            else -> { toast("Slot ${info.id} has no game any more"); refresh() }
        }
    }

    private fun askNewGame(id: Int) {
        if (!canTouch(id)) return
        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val name = EditText(activity).apply {
            this.id = View.generateViewId()
            setText(settings.playerName); isSingleLine = true; hint = "Name"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        box.addView(TextView(activity).apply { text = "Character name"; labelFor = name.id })
        box.addView(name)
        val female = CheckBox(activity).apply { text = "Female character"; isChecked = settings.female }
        box.addView(female)

        // Difficulty: the game's -d option. Applies to new games only; a
        // restored game keeps the difficulty it was started with.
        val diffLabel = TextView(activity).apply { setPadding(0, dp(14), 0, 0) }
        val diffInfo = TextView(activity).apply { textSize = 12f; alpha = 0.75f }
        val diff = SeekBar(activity).apply {
            max = Settings.MAX_DIFFICULTY
            progress = settings.difficulty
            this.id = View.generateViewId()
        }
        diffLabel.labelFor = diff.id
        fun showDiff(k: Int) {
            diffLabel.text = "Difficulty: $k" + if (k == 0) " (normal)" else ""
            diffInfo.text = Settings.describeDifficulty(k)
            diff.stateDescriptionCompat("Level $k")
        }
        diff.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) = showDiff(p)
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        showDiff(diff.progress)
        box.addView(diffLabel)
        box.addView(diff)
        box.addView(diffInfo)
        box.addView(TextView(activity).apply {
            text = "Once you have won, the game sets your next game at least one level harder."
            textSize = 12f; alpha = 0.6f; setPadding(0, dp(6), 0, 0)
        })
        AlertDialog.Builder(activity)
            .setTitle("New game in slot $id")
            .setView(box)
            .setPositiveButton("Start") { _, _ ->
                if (!canTouch(id)) return@setPositiveButton
                val n = name.text.toString().replace(Regex("[,:\\r\\n]"), " ").trim().take(30).ifBlank { "Adventurer" }
                try {
                    slots.clearGame(id)
                    slots.writeMeta(id, SlotMeta(character = n, female = female.isChecked, lastPlayed = System.currentTimeMillis()))
                } catch (e: Exception) {
                    toast("Couldn't prepare slot $id: ${e.message}"); return@setPositiveButton
                }
                settings.difficulty = diff.progress
                start(id, Settings.difficultyArgs(diff.progress))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun SeekBar.stateDescriptionCompat(text: String) {
        if (Build.VERSION.SDK_INT >= 30) stateDescription = text
    }

    private fun start(id: Int, args: List<String>) {
        val meta = slots.readMeta(id) ?: SlotMeta(character = settings.playerName, female = settings.female)
        session.homeDir = slots.dir(id)
        session.optsText = settings.buildOptsFile(meta.character ?: settings.playerName, meta.female)
        activeSlot = id
        try {
            slots.writeMeta(id, meta.copy(lastPlayed = System.currentTimeMillis()))
        } catch (e: Exception) { Log.w(TAG, "can't write slot metadata", e) }
        startGame(args)
    }

    /** Before a run that isn't a slot's game (the scoreboard). */
    fun useNoSlot() {
        session.homeDir = null
        session.optsText = null
        activeSlot = null
    }

    /**
     * Stores the running game's level and depth in its slot's metadata.
     * Call when the app goes to the background and when the game exits.
     */
    fun recordProgress() {
        val id = activeSlot ?: return
        val s = session.state ?: return
        try {
            if (!s.refresh()) return
            val m = slots.readMeta(id) ?: SlotMeta(character = settings.playerName, female = settings.female)
            val now = System.currentTimeMillis()
            val updated = if (s.classNum >= 0 && s.hpMax > 0)
                m.copy(level = s.playerLevel, levelTitle = s.levelTitle.trim().ifEmpty { null },
                    depth = s.levelName.ifEmpty { null }, lastPlayed = now)
            else m.copy(lastPlayed = now)
            if (slots.dir(id).exists()) slots.writeMeta(id, updated)
        } catch (e: Exception) {
            Log.w(TAG, "can't record progress", e)
        }
    }

    // -------------------------------------------------------------- rename

    private fun askRename(info: SaveSlots.SlotInfo) {
        val e = EditText(activity).apply {
            setText(info.meta?.label ?: info.meta?.character ?: ""); isSingleLine = true
            hint = "Slot name"; contentDescription = "Slot name"
            setSelection(text.length)
        }
        AlertDialog.Builder(activity)
            .setTitle("Rename slot ${info.id}")
            .setMessage("Leave empty to show the character's name.")
            .setView(LinearLayout(activity).apply { setPadding(dp(20), 0, dp(20), 0); addView(e, LinearLayout.LayoutParams(-1, -2)) })
            .setPositiveButton("Rename") { _, _ ->
                try {
                    val label = e.text.toString().trim()
                    slots.rename(info.id, if (label == info.meta?.character) "" else label)
                } catch (ex: Exception) { toast("Couldn't rename: ${ex.message}") }
                refresh()
            }
            .setNegativeButton("Cancel", null).show()
    }

    // ------------------------------------------------------ export / import

    private fun pickExport(info: SaveSlots.SlotInfo) {
        if (!canTouch(info.id)) return
        prefs.edit().putInt(KEY_EXPORT, info.id).apply()
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = SaveTransfer.MIME
            putExtra(Intent.EXTRA_TITLE, SaveTransfer.fileName(info))
        }
        try { activity.startActivityForResult(i, REQ_EXPORT) } catch (e: Exception) { toast("No file picker available") }
    }

    private fun pickImport(target: Int) {
        if (session.isRunning) { toast("Leave the current game first"); return }
        if (busySlot != null) { toast("Please wait for the current export or import"); return }
        if (target == 0 && slots.firstEmpty() == null) { toast("No empty slot: delete a slot first"); return }
        prefs.edit().putInt(KEY_IMPORT, target).apply()
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"   // some providers don't label zips as application/zip
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(SaveTransfer.MIME, "application/x-zip-compressed", "application/octet-stream"))
        }
        try { activity.startActivityForResult(i, REQ_IMPORT) } catch (e: Exception) { toast("No file picker available") }
    }

    /** Forwarded from Activity.onActivityResult. Returns true if handled. */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQ_EXPORT && requestCode != REQ_IMPORT) return false
        val uri = data?.data
        if (resultCode != Activity.RESULT_OK || uri == null) return true
        if (requestCode == REQ_EXPORT) doExport(prefs.getInt(KEY_EXPORT, 0), uri)
        else doImport(prefs.getInt(KEY_IMPORT, 0), uri)
        return true
    }

    private fun doExport(id: Int, uri: Uri) {
        if (id !in 1..SaveSlots.MAX_SLOTS || slots.info(id).isEmpty) { toast("Nothing to export"); return }
        if (!canTouch(id)) return
        busySlot = id
        refresh()
        val resolver = activity.contentResolver
        io.execute {
            val error = try {
                (resolver.openOutputStream(uri, "w") ?: throw java.io.IOException("can't open the file")).use {
                    SaveTransfer.export(slots, id, it)
                }
                null
            } catch (e: Exception) {
                Log.w(TAG, "export failed", e)
                try { DocumentsContract.deleteDocument(resolver, uri) } catch (_: Exception) {}
                e.message ?: "error"
            }
            activity.runOnUiThread {
                busySlot = null
                if (activity.isDestroyed) return@runOnUiThread
                toast(if (error == null) "Slot $id exported" else "Export failed: $error")
                refresh()
            }
        }
    }

    private fun doImport(requested: Int, uri: Uri) {
        if (session.isRunning) { toast("Leave the current game first"); return }
        if (busySlot != null) { toast("Please wait for the current export or import"); return }
        val target = if (requested in 1..SaveSlots.MAX_SLOTS && slots.info(requested).isEmpty) requested else slots.firstEmpty()
        if (target == null) { toast("No empty slot: delete a slot first"); return }
        busySlot = target
        refresh()
        val resolver = activity.contentResolver
        io.execute {
            val error = try {
                (resolver.openInputStream(uri) ?: throw java.io.IOException("can't open the file")).use {
                    SaveTransfer.import(slots, it, target)
                }
                null
            } catch (e: SaveTransfer.ImportException) {
                e.message
            } catch (e: Exception) {
                Log.w(TAG, "import failed", e)
                "couldn't read the file (${e.javaClass.simpleName})"
            }
            activity.runOnUiThread {
                busySlot = null
                if (activity.isDestroyed) return@runOnUiThread
                if (error == null) toast("Imported into slot $target")
                else AlertDialog.Builder(activity).setTitle("Import failed").setMessage(error)
                    .setPositiveButton("OK", null).show()
                refresh()
            }
        }
    }

    /** True while a slot is being exported or imported. */
    fun isBusy(id: Int) = busySlot == id

    companion object {
        private const val TAG = "SlotsController"
        const val REQ_EXPORT = 0x5a01
        const val REQ_IMPORT = 0x5a02
        private const val KEY_EXPORT = "pendingExport"
        private const val KEY_IMPORT = "pendingImport"
    }
}
