package com.ularn.android.controls

/**
 * Converts between the human-editable key notation used in the control editor
 * and the bytes sent to the game.
 *
 *   plain text   sent as-is            e.g.  "i", "Hl", "q"
 *   \e  or <esc> Escape
 *   \n  or <ret> Enter
 *   \s  or <spc> Space (a literal space also works)
 *   \t           Tab
 *   \b  or <del> Backspace
 *   ^X           Ctrl+X (any letter / @[\]^_ )
 *   \\  \^       a literal backslash / caret
 */
object KeySequence {

    private val named = mapOf(
        "esc" to 0x1b, "ret" to 0x0d, "enter" to 0x0d, "spc" to 0x20,
        "space" to 0x20, "tab" to 0x09, "del" to 0x7f, "bs" to 0x7f,
    )

    fun parse(notation: String): ByteArray {
        val out = ArrayList<Byte>()
        var i = 0
        val s = notation
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' && i + 1 < s.length -> {
                    val n = s[i + 1]
                    val v = when (n) {
                        'e', 'E' -> 0x1b
                        'n', 'r' -> 0x0d
                        's' -> 0x20
                        't' -> 0x09
                        'b' -> 0x7f
                        '\\' -> '\\'.code
                        '^' -> '^'.code
                        '<' -> '<'.code
                        else -> null
                    }
                    if (v != null) { out.add(v.toByte()); i += 2 } else { out.add(c.code.toByte()); i++ }
                }
                c == '^' && i + 1 < s.length && ctrlCode(s[i + 1]) != null -> {
                    out.add(ctrlCode(s[i + 1])!!.toByte()); i += 2
                }
                c == '<' -> {
                    val end = s.indexOf('>', i)
                    val name = if (end > i) s.substring(i + 1, end).lowercase() else null
                    val v = name?.let { named[it] }
                    if (v != null) { out.add(v.toByte()); i = end + 1 } else { out.add(c.code.toByte()); i++ }
                }
                else -> {
                    // Ularn is 7-bit ASCII; drop anything else.
                    if (c.code in 0x20..0x7e) out.add(c.code.toByte())
                    i++
                }
            }
        }
        return out.toByteArray()
    }

    private fun ctrlCode(c: Char): Int? = when (c) {
        in 'a'..'z' -> c.code - 'a'.code + 1
        in 'A'..'Z' -> c.code - 'A'.code + 1
        '@' -> 0; '[' -> 27; '\\' -> 28; ']' -> 29; '^' -> 30; '_' -> 31
        '?' -> 127
        else -> null
    }

    /** Pretty description, e.g. for showing what a button sends. */
    fun describe(bytes: ByteArray): String = bytes.joinToString(" ") { b ->
        when (val v = b.toInt() and 0xff) {
            0x1b -> "Esc"
            0x0d, 0x0a -> "Enter"
            0x20 -> "Space"
            0x09 -> "Tab"
            0x7f -> "Bksp"
            in 1..26 -> "Ctrl+" + ('A' + v - 1)
            else -> v.toChar().toString()
        }
    }
}
