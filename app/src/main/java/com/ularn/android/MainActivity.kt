package com.ularn.android

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.ularn.android.controls.ButtonAction
import com.ularn.android.controls.ControlEditorDialog
import com.ularn.android.controls.ControlLayout
import com.ularn.android.controls.ControlSpec
import com.ularn.android.controls.ControlType
import com.ularn.android.controls.ControlsOverlay
import com.ularn.android.controls.LayoutStore
import com.ularn.android.term.TerminalEmulator
import com.ularn.android.term.TerminalView

class MainActivity : Activity(), ControlsOverlay.Listener {

    private lateinit var settings: Settings
    private lateinit var store: LayoutStore
    private lateinit var emulator: TerminalEmulator
    private lateinit var session: GameSession

    private lateinit var terminal: TerminalView
    private lateinit var overlay: ControlsOverlay
    private lateinit var editBar: LinearLayout
    private lateinit var editBarScroll: HorizontalScrollView
    private lateinit var startPanel: LinearLayout
    private lateinit var startScroll: ScrollView
    private lateinit var startTitle: TextView

    private var landscape = false
    private var keyboardShown = false
    private var quitAfterExit = false
    private var editBarAtTop = true
    /** Layout snapshot taken when entering edit mode (for Cancel). */
    private var editBackup: ControlLayout? = null

    private val density get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    // ------------------------------------------------------------ lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        settings = Settings(this)
        store = LayoutStore(this)
        emulator = TerminalEmulator(80, 24)

        terminal = TerminalView(this).apply {
            emulator = this@MainActivity.emulator
            onInput = { session.send(it) }
            onTap = { if (!session.isRunning) showStartPanel(true) }
        }
        overlay = ControlsOverlay(this).apply { listener = this@MainActivity }

        session = GameSession(
            this, emulator,
            onOutput = { terminal.postInvalidateOnAnimation() },
            onExit = { onGameExit() },
        )

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(terminal, FrameLayout.LayoutParams(-1, -1))
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        buildEditBar(root)
        buildStartPanel(root)
        setContentView(root)

        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 28) {
                val cut = insets.displayCutout
                v.setPadding(cut?.safeInsetLeft ?: 0, cut?.safeInsetTop ?: 0, cut?.safeInsetRight ?: 0, cut?.safeInsetBottom ?: 0)
            }
            insets
        }

        applySettings()
        applyOrientation(resources.configuration)
        showStartPanel(true)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (overlay.editMode) finishEditing(save = true)
        applyOrientation(newConfig)
    }

    override fun onDestroy() {
        // A hang-up makes Ularn write its checkpoint (if enabled) and exit.
        if (isFinishing) session.hangUp()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() = handleBack()

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
            if (keyCode == KeyEvent.KEYCODE_MENU) { showMenu(); return true }
            return super.onKeyDown(keyCode, event)
        }
        if (session.isRunning && !overlay.editMode && terminal.handleKey(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }

    private fun handleBack() {
        when {
            overlay.editMode -> finishEditing(save = true)
            keyboardShown -> setKeyboard(false)
            else -> showMenu()
        }
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }

    private fun applyOrientation(cfg: Configuration) {
        landscape = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE
        terminal.alignTop = !landscape
        overlay.layout = store.load(landscape)
        updateMapReserve()
    }

    /**
     * In landscape, measure the controls hugging the left and right edges and
     * shrink the map to fit between them (capped so the map stays readable).
     */
    private fun updateMapReserve() {
        overlay.post {
            val w = overlay.width.toFloat()
            if (!landscape || !settings.fitMapBesideControls || w == 0f) {
                terminal.setHorizontalReserve(0f, 0f); return@post
            }
            var left = 0f; var right = 0f
            val r = android.graphics.RectF()
            for (c in overlay.layout.controls) {
                overlay.rectFor(c, r)
                if (r.centerX() < w * 0.25f) left = maxOf(left, r.right)
                else if (r.centerX() > w * 0.75f) right = maxOf(right, w - r.left)
            }
            terminal.setHorizontalReserve(minOf(left, w * 0.3f), minOf(right, w * 0.3f))
        }
    }

    private fun applySettings() {
        terminal.theme = TerminalView.THEMES.getOrElse(settings.theme) { TerminalView.THEMES[0] }
        terminal.showCursor = settings.showCursor
        terminal.arrowsAsMoves = settings.arrowsAsMoves
        if (::overlay.isInitialized) updateMapReserve()
    }

    // --------------------------------------------------------------- game

    private fun startGame(args: List<String> = emptyList()) {
        showStartPanel(false)
        quitAfterExit = false
        session.start(settings, args)
        terminal.requestFocus()
    }

    private fun onGameExit() {
        setKeyboard(false)
        if (quitAfterExit) { finish(); return }
        // Leave the final screen (scores, tombstone...) readable; tap for the menu.
        toast("Tap the screen for the menu")
    }

    // ---------------------------------------------- ControlsOverlay.Listener

    override fun onKeys(bytes: ByteArray) {
        if (session.isRunning) session.send(bytes)
    }

    override fun onAction(action: ButtonAction) {
        when (action) {
            ButtonAction.KEYBOARD -> setKeyboard(!keyboardShown)
            ButtonAction.MENU -> showMenu()
            ButtonAction.EDIT -> startEditing()
            else -> {}
        }
    }

    override fun onEditControl(spec: ControlSpec) = openControlEditor(spec)

    override fun onLayoutEdited() {}

    override fun onSelectionChanged(spec: ControlSpec?) = refreshEditBar()

    private fun setKeyboard(show: Boolean) {
        keyboardShown = show
        terminal.showKeyboard(show)
    }

    // ------------------------------------------------------------ editing

    private fun startEditing() {
        showStartPanel(false)
        setKeyboard(false)
        editBackup = overlay.layout.deepCopy()
        overlay.editMode = true
        editBarScroll.visibility = View.VISIBLE
        refreshEditBar()
        Toast.makeText(this, "Drag to move · corner handle to resize · tap a selected control to edit it", Toast.LENGTH_LONG).show()
    }

    private fun finishEditing(save: Boolean) {
        if (save) store.save(landscape, overlay.layout)
        else editBackup?.let { overlay.layout = it }
        editBackup = null
        overlay.editMode = false
        updateMapReserve()
        editBarScroll.visibility = View.GONE
        if (!session.isRunning) showStartPanel(true)
    }

    private fun pill(text: String, accent: Boolean = false, onClick: () -> Unit) =
        Button(this, null, android.R.attr.buttonStyleSmall).apply {
            this.text = text
            isAllCaps = false
            setTextColor(if (accent) Color.BLACK else Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(if (accent) Color.rgb(255, 190, 60) else Color.argb(220, 45, 47, 55))
            }
            minHeight = dp(36); minimumHeight = dp(36)
            setPadding(dp(14), 0, dp(14), 0)
            setOnClickListener { onClick() }
        }

    private lateinit var editSelectedBtn: Button
    private lateinit var snapBtn: Button

    private fun buildEditBar(root: FrameLayout) {
        editBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            gravity = Gravity.CENTER_VERTICAL
        }
        fun add(v: View) = editBar.addView(v, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })

        add(pill("Done", accent = true) { finishEditing(save = true) })
        add(pill("+ Button") { addControl(ControlType.BUTTON) })
        add(pill("+ D-pad") { addControl(ControlType.DPAD) })
        editSelectedBtn = pill("Edit selected") { overlay.selected?.let { openControlEditor(it) } }
        add(editSelectedBtn)
        snapBtn = pill("Snap: on") {
            overlay.snapToGrid = !overlay.snapToGrid; refreshEditBar()
        }
        add(snapBtn)
        add(pill("Look") { showLookDialog() })
        add(pill("Profiles") { showProfilesDialog() })
        add(pill("Reset") {
            AlertDialog.Builder(this)
                .setMessage("Reset the ${if (landscape) "landscape" else "portrait"} layout to the default?")
                .setPositiveButton("Reset") { _, _ -> overlay.layout = store.reset(landscape); refreshEditBar() }
                .setNegativeButton("Cancel", null).show()
        })
        add(pill("⇅") { editBarAtTop = !editBarAtTop; positionEditBar() })
        add(pill("Cancel") { finishEditing(save = false) })

        editBarScroll = HorizontalScrollView(this).apply {
            addView(editBar)
            setBackgroundColor(Color.argb(200, 10, 10, 12))
            visibility = View.GONE
            isHorizontalScrollBarEnabled = false
        }
        root.addView(editBarScroll, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
    }

    private fun positionEditBar() {
        (editBarScroll.layoutParams as FrameLayout.LayoutParams).gravity = if (editBarAtTop) Gravity.TOP else Gravity.BOTTOM
        editBarScroll.requestLayout()
    }

    private fun refreshEditBar() {
        editSelectedBtn.isEnabled = overlay.selected != null
        editSelectedBtn.alpha = if (overlay.selected != null) 1f else 0.4f
        snapBtn.text = if (overlay.snapToGrid) "Snap: on" else "Snap: off"
    }

    private fun addControl(type: ControlType) {
        val l = overlay.layout
        val spec = if (type == ControlType.DPAD)
            ControlSpec(l.newId("dpad"), ControlType.DPAD, cx = 0.5f, cy = 0.6f, widthDp = 160f, heightDp = 160f, repeat = true)
        else
            ControlSpec(l.newId("btn"), ControlType.BUTTON, "New", "", cx = 0.5f, cy = 0.6f, widthDp = 56f, heightDp = 46f)
        l.controls += spec
        overlay.select(spec.id)
        openControlEditor(spec)
    }

    private fun openControlEditor(spec: ControlSpec) {
        ControlEditorDialog.show(
            this, spec,
            onSave = { edited ->
                val list = overlay.layout.controls
                val i = list.indexOfFirst { it.id == spec.id }
                if (i >= 0) list[i] = edited
                overlay.invalidate(); refreshEditBar()
            },
            onDelete = {
                overlay.layout.controls.removeAll { it.id == spec.id }
                overlay.select(null)
            },
            onDuplicate = {
                val copy = spec.deepCopy().apply {
                    id = overlay.layout.newId(if (type == ControlType.DPAD) "dpad" else "btn")
                    cx = (cx + 0.06f).coerceAtMost(1f); cy = (cy + 0.04f).coerceAtMost(1f)
                }
                overlay.layout.controls += copy
                overlay.select(copy.id)
            },
        )
    }

    private fun showLookDialog() {
        val l = overlay.layout
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val lbl = TextView(this)
        fun refresh() { lbl.text = "Opacity: ${(l.opacity * 100).toInt()}%" }
        refresh()
        box.addView(lbl)
        box.addView(SeekBar(this).apply {
            max = 90; progress = ((l.opacity - 0.1f) * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { l.opacity = 0.1f + p / 100f; refresh(); this@MainActivity.overlay.invalidate() }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        box.addView(CheckBox(this).apply {
            text = "Vibrate on press"; isChecked = l.haptics
            setOnCheckedChangeListener { _, v -> l.haptics = v }
        })
        AlertDialog.Builder(this).setTitle("Controls appearance").setView(box).setPositiveButton("OK", null).show()
    }

    private fun showProfilesDialog() {
        val names = store.profileNames()
        val items = mutableListOf("Save current layout as…")
        items += names.map { "Load: $it" }
        items += listOf("Copy layout to clipboard (JSON)", "Paste layout from clipboard")
        if (names.isNotEmpty()) items += "Delete a profile…"
        AlertDialog.Builder(this)
            .setTitle("Layout profiles")
            .setItems(items.toTypedArray()) { _, which ->
                val item = items[which]
                when {
                    which == 0 -> {
                        val e = EditText(this).apply { hint = "Profile name"; isSingleLine = true }
                        AlertDialog.Builder(this).setTitle("Save profile").setView(e)
                            .setPositiveButton("Save") { _, _ ->
                                val n = e.text.toString().trim()
                                if (n.isNotEmpty()) { store.saveProfile(n, overlay.layout); toast("Saved \"$n\"") }
                            }.setNegativeButton("Cancel", null).show()
                    }
                    item.startsWith("Load: ") -> store.loadProfile(item.removePrefix("Load: "))?.let {
                        overlay.layout = it; refreshEditBar(); toast("Loaded — tap Done to keep it")
                    }
                    item.startsWith("Copy") -> {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Ularn controls", LayoutStore.toJson(overlay.layout)))
                        toast("Layout copied")
                    }
                    item.startsWith("Paste") -> {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val txt = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
                        val parsed = txt?.let { runCatching { LayoutStore.fromJson(it) }.getOrNull() }
                        if (parsed == null) toast("Clipboard doesn't contain a layout")
                        else { overlay.layout = parsed; refreshEditBar(); toast("Pasted — tap Done to keep it") }
                    }
                    item.startsWith("Delete") -> AlertDialog.Builder(this).setTitle("Delete profile")
                        .setItems(names.toTypedArray()) { _, i -> store.deleteProfile(names[i]); toast("Deleted") }
                        .show()
                }
            }.show()
    }

    // ------------------------------------------------------- start panel

    private fun buildStartPanel(root: FrameLayout) {
        startPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(20), dp(24), dp(20))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.argb(235, 22, 22, 26))
                setStroke(dp(1), Color.argb(90, 255, 255, 255))
            }
            elevation = dp(8).toFloat()
        }
        startTitle = TextView(this).apply {
            text = "ULARN"
            textSize = 30f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(Color.rgb(255, 190, 60))
            gravity = Gravity.CENTER
        }
        startScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setPadding(0, dp(12), 0, dp(12))
            addView(startPanel)
        }
        root.addView(startScroll, FrameLayout.LayoutParams(dp(300), -2, Gravity.CENTER))
    }

    private fun showStartPanel(show: Boolean) {
        if (!show) { startScroll.visibility = View.GONE; return }
        startPanel.removeAllViews()
        startPanel.addView(startTitle)
        startPanel.addView(TextView(this).apply {
            text = "The Caverns of Ularn"
            setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(14))
        })
        fun item(label: String, accent: Boolean = false, action: () -> Unit) {
            startPanel.addView(pill(label, accent, action), LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
        }
        when {
            session.hasSave() -> {
                item("Continue saved game", accent = true) { startGame() }
            }
            session.hasCheckpoint() -> {
                item("Resume from checkpoint", accent = true) { startGame(listOf("-r")) }
                item("New game") { confirmNewGameOverCheckpoint() }
            }
            else -> item("New game", accent = true) { startGame() }
        }
        if (session.hasSave()) item("New game (discard save)") { confirmDiscardSave() }
        item("Scoreboard") { startGame(listOf("-i")) }
        item("Edit controls") { startEditing() }
        item("Settings") { showSettings() }
        item("Quit") { finish() }
        startScroll.visibility = View.VISIBLE
        startScroll.scrollTo(0, 0)
    }

    private fun confirmNewGameOverCheckpoint() {
        AlertDialog.Builder(this)
            .setMessage("Start a new game? The checkpoint will be discarded.")
            .setPositiveButton("New game") { _, _ -> java.io.File(session.workDir, "Ularn.ckp").delete(); startGame() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun confirmDiscardSave() {
        AlertDialog.Builder(this)
            .setMessage("Delete your saved game and start over?")
            .setPositiveButton("Delete & start") { _, _ ->
                java.io.File(session.workDir, "Ularn.sav").delete()
                java.io.File(session.workDir, "Ularn.ckp").delete()
                startGame()
            }
            .setNegativeButton("Cancel", null).show()
    }

    // --------------------------------------------------------------- menu

    private fun showMenu() {
        if (!session.isRunning) {
            if (startScroll.visibility != View.VISIBLE) showStartPanel(true) else finish()
            return
        }
        val items = arrayOf(
            if (keyboardShown) "Hide keyboard" else "Show keyboard",
            "Edit controls",
            "Settings",
            "Game help (?)",
            "Save game & quit",
            "Quit without saving",
        )
        AlertDialog.Builder(this).setItems(items) { _, which ->
            when (which) {
                0 -> setKeyboard(!keyboardShown)
                1 -> startEditing()
                2 -> showSettings()
                3 -> session.send(byteArrayOf('?'.code.toByte()))
                4 -> { quitAfterExit = true; session.send(byteArrayOf('S'.code.toByte())) }
                5 -> AlertDialog.Builder(this)
                    .setMessage(
                        if (settings.checkpointing) "Leave now? You can resume from the last checkpoint next time."
                        else "Leave now? Progress since your last save will be lost."
                    )
                    .setPositiveButton("Quit") { _, _ -> quitAfterExit = true; session.hangUp() }
                    .setNegativeButton("Cancel", null).show()
            }
        }.show()
    }

    // ----------------------------------------------------------- settings

    private fun showSettings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        fun header(t: String) = box.addView(TextView(this).apply {
            text = t; setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(14), 0, dp(2))
        })
        fun check(t: String, v: Boolean, set: (Boolean) -> Unit) = box.addView(CheckBox(this).apply {
            text = t; isChecked = v; setOnCheckedChangeListener { _, b -> set(b) }
        })

        header("Character (applies to new games)")
        val name = EditText(this).apply { setText(settings.playerName); isSingleLine = true; hint = "Name" }
        box.addView(name)
        check("Female character", settings.female) { settings.female = it }
        check("Skip introduction", settings.skipIntro) { settings.skipIntro = it }
        check("Auto-checkpoint (resume after the app is closed)", settings.checkpointing) { settings.checkpointing = it }
        check("No delays (no-nap)", settings.noNap) { settings.noNap = it }
        check("No beeping", settings.noBeep) { settings.noBeep = it }

        header("Display")
        val themeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, TerminalView.THEMES.map { it.name })
            setSelection(settings.theme.coerceIn(0, TerminalView.THEMES.size - 1))
        }
        box.addView(themeSpinner)
        check("Show cursor", settings.showCursor) { settings.showCursor = it }
        check("Landscape: fit map beside the controls", settings.fitMapBesideControls) { settings.fitMapBesideControls = it }
        box.addView(TextView(this).apply { text = "Pinch the map to zoom, double-tap to reset."; textSize = 12f; alpha = 0.7f })

        header("Keyboard")
        check("Arrow keys / numpad move (hardware keyboard)", settings.arrowsAsMoves) { settings.arrowsAsMoves = it }

        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("OK") { _, _ ->
                settings.playerName = name.text.toString()
                settings.theme = themeSpinner.selectedItemPosition
                applySettings()
            }
            .show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
