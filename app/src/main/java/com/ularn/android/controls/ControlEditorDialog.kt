package com.ularn.android.controls

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView

/** Builds the property sheet for one control. Works on a copy until Save. */
object ControlEditorDialog {

    fun show(
        context: Context,
        original: ControlSpec,
        onSave: (ControlSpec) -> Unit,
        onDelete: () -> Unit,
        onDuplicate: () -> Unit,
    ) {
        val c = original.deepCopy()
        val d = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        fun header(t: String) = TextView(context).apply {
            text = t
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(2))
        }.also { root.addView(it) }
        fun note(t: String) = TextView(context).apply {
            text = t; textSize = 12f; alpha = 0.75f
        }.also { root.addView(it) }

        // ---- label
        header("Label")
        val labelEdit = EditText(context).apply {
            setText(c.label)
            hint = "Shown on the button (blank = show keys)"
            isSingleLine = true
        }
        root.addView(labelEdit)

        // ---- action + keys (buttons only)
        val keysEdit = EditText(context).apply {
            setText(c.keys)
            isSingleLine = true
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        val preview = TextView(context).apply { textSize = 12f }
        fun updatePreview() {
            val b = KeySequence.parse(keysEdit.text.toString())
            preview.text = if (b.isEmpty()) "Sends: (nothing)" else "Sends: " + KeySequence.describe(b)
        }
        keysEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, cnt: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, cnt: Int) {}
            override fun afterTextChanged(s: Editable?) = updatePreview()
        })
        updatePreview()

        val keysSection = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        if (c.type == ControlType.BUTTON) {
            header("Action")
            val actions = ButtonAction.values()
            val spinner = Spinner(context)
            spinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, actions.map { it.title })
            spinner.setSelection(actions.indexOf(c.action))
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    c.action = actions[pos]
                    keysSection.visibility = if (c.action == ButtonAction.KEYS) View.VISIBLE else View.GONE
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
            root.addView(spinner)

            root.addView(keysSection)
            keysSection.addView(TextView(context).apply {
                text = "Keys to send"; setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(12), 0, dp(2))
            })
            keysSection.addView(keysEdit)
            keysSection.addView(preview)
            // quick inserts
            val chips = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for ((lbl, ins) in listOf("Esc" to "\\e", "Enter" to "\\n", "Space" to "\\s", "Ctrl" to "^", "Bksp" to "\\b")) {
                chips.addView(Button(context, null, android.R.attr.buttonStyleSmall).apply {
                    text = lbl; isAllCaps = false
                    setOnClickListener {
                        val s = keysEdit.selectionStart.coerceAtLeast(0)
                        keysEdit.text.insert(s, ins)
                    }
                })
            }
            keysSection.addView(android.widget.HorizontalScrollView(context).apply { addView(chips) })
            keysSection.addView(TextView(context).apply {
                textSize = 12f; alpha = 0.75f
                text = "Type the keys the game should receive, in order. e.g.  q  (quaff)  ·  c\\ea  ·  ^R\n" +
                    "\\e Esc   \\n Enter   \\s Space   \\b Backspace   ^X Ctrl+X   \\\\ backslash"
            })
            keysSection.visibility = if (c.action == ButtonAction.KEYS) View.VISIBLE else View.GONE
        }

        // ---- D-pad keys
        val dpadEdits = ArrayList<EditText>()
        if (c.type == ControlType.DPAD) {
            header("Direction keys")
            note("NW  N  NE / W  centre  E / SW  S  SE. Same notation as buttons. Leave the centre blank to disable it.")
            val grid = GridLayout(context).apply { columnCount = 3; rowCount = 3 }
            for (i in 0 until 9) {
                val e = EditText(context).apply {
                    setText(c.dpadKeys[i])
                    gravity = Gravity.CENTER
                    isSingleLine = true
                    typeface = Typeface.MONOSPACE
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    minWidth = dp(72)
                }
                dpadEdits += e
                grid.addView(e)
            }
            root.addView(grid)
            root.addView(Button(context).apply {
                text = "Reset to y k u / h . l / b j n"; isAllCaps = false
                setOnClickListener { dpadEdits.forEachIndexed { i, e -> e.setText(ControlSpec.DEFAULT_DPAD_KEYS[i]) } }
            })
            val eight = CheckBox(context).apply {
                text = "8-way (diagonals)"; isChecked = c.eightWay
                setOnCheckedChangeListener { _, v -> c.eightWay = v }
            }
            root.addView(eight)
            note("Tip: the Run button makes the next move a run (H J K L...). Long-press Run to keep it on.")
        }

        // ---- behaviour
        header("Behaviour")
        root.addView(CheckBox(context).apply {
            text = if (c.type == ControlType.DPAD) "Keep moving while held" else "Repeat while held"
            isChecked = c.repeat
            setOnCheckedChangeListener { _, v -> c.repeat = v }
        })
        if (c.type == ControlType.BUTTON) {
            root.addView(CheckBox(context).apply {
                text = "Round button"
                isChecked = c.shape == ButtonShape.CIRCLE
                setOnCheckedChangeListener { _, v -> c.shape = if (v) ButtonShape.CIRCLE else ButtonShape.ROUNDED }
            })
        }

        // ---- size
        header("Size")
        fun sizeRow(name: String, get: () -> Float, set: (Float) -> Unit): SeekBar {
            val lbl = TextView(context)
            val bar = SeekBar(context).apply {
                max = 300 - 24
                progress = (get() - 24).toInt()
            }
            fun refresh() { lbl.text = "$name: ${get().toInt()} dp" }
            refresh()
            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { set((p + 24).toFloat()); refresh() }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            root.addView(lbl); root.addView(bar)
            return bar
        }
        if (c.type == ControlType.DPAD) {
            sizeRow("Diameter", { c.widthDp }) { c.widthDp = it; c.heightDp = it }
        } else {
            sizeRow("Width", { c.widthDp }) { c.widthDp = it }
            sizeRow("Height", { c.heightDp }) { c.heightDp = it }
        }

        val scroll = ScrollView(context).apply { addView(root) }

        val dialog = AlertDialog.Builder(context)
            .setTitle(if (c.type == ControlType.DPAD) "Edit D-pad" else "Edit button")
            .setView(scroll)
            .setPositiveButton("Save") { _, _ ->
                c.label = labelEdit.text.toString()
                c.keys = keysEdit.text.toString()
                if (c.type == ControlType.DPAD) c.dpadKeys = dpadEdits.map { it.text.toString() }.toMutableList()
                if (c.shape == ButtonShape.CIRCLE) { val s = maxOf(c.widthDp, c.heightDp); c.widthDp = s; c.heightDp = s }
                onSave(c)
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("More…", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                AlertDialog.Builder(context)
                    .setItems(arrayOf("Duplicate", "Delete")) { _, which ->
                        dialog.dismiss()
                        if (which == 0) onDuplicate() else onDelete()
                    }.show()
            }
        }
        dialog.show()
    }
}
