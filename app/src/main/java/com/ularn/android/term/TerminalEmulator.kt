package com.ularn.android.term

/**
 * A small VT100 emulator: exactly what Ularn's built-in termcap emits
 * (cursor motion, clear screen/line, insert/delete line, standout), plus the
 * usual control characters and a few extras for robustness.
 *
 * Thread safety: [feed] is called from the reader thread, rendering happens on
 * the UI thread; both go through [lock].
 */
class TerminalEmulator(val columns: Int = 80, val rows: Int = 24) {

    val lock = Any()

    /** Character codes, row-major. */
    val chars = CharArray(columns * rows) { ' ' }

    /** Attribute bits per cell, see [ATTR_INVERSE] / [ATTR_BOLD]. */
    val attrs = ByteArray(columns * rows)

    var cursorRow = 0
        private set
    var cursorCol = 0
        private set

    /** Bumped on every change so the view can tell when to redraw. */
    @Volatile
    var revision = 0L
        private set

    var onBell: (() -> Unit)? = null

    private var curAttr: Int = 0
    private var scrollTop = 0
    private var scrollBottom = rows - 1
    private var savedRow = 0
    private var savedCol = 0
    private var savedAttr = 0

    private enum class State { NORMAL, ESC, CSI }

    private var state = State.NORMAL
    private val params = IntArray(16)
    private var paramCount = 0
    private var paramStarted = false
    private var privateMarker = false

    fun feed(data: ByteArray, offset: Int = 0, length: Int = data.size) {
        synchronized(lock) {
            for (i in offset until offset + length) process(data[i].toInt() and 0xff)
            revision++
        }
    }

    fun feed(text: String) = feed(text.toByteArray(Charsets.ISO_8859_1))

    fun reset() {
        synchronized(lock) {
            chars.fill(' ')
            attrs.fill(0)
            cursorRow = 0; cursorCol = 0
            curAttr = 0
            scrollTop = 0; scrollBottom = rows - 1
            state = State.NORMAL
            revision++
        }
    }

    /** Snapshot of a row's text (for tests / accessibility). */
    fun rowText(r: Int): String = synchronized(lock) {
        String(chars, r * columns, columns).trimEnd()
    }

    fun screenText(): String = (0 until rows).joinToString("\n") { rowText(it) }

    // ---------------------------------------------------------------------

    private fun process(b: Int) {
        when (state) {
            State.NORMAL -> normal(b)
            State.ESC -> escape(b)
            State.CSI -> csi(b)
        }
    }

    private fun normal(b: Int) {
        when (b) {
            0x1b -> state = State.ESC
            0x0d -> cursorCol = 0
            0x0a, 0x0b, 0x0c -> lineFeed()
            0x08 -> if (cursorCol > 0) cursorCol--
            0x09 -> cursorCol = minOf(columns - 1, (cursorCol / 8 + 1) * 8)
            0x07 -> onBell?.invoke()
            0x00 -> {}
            else -> if (b >= 0x20) putChar(b.toChar())
        }
    }

    private fun putChar(c: Char) {
        if (cursorCol >= columns) {        // deferred wrap, like a real VT100
            cursorCol = 0
            lineFeed()
        }
        val idx = cursorRow * columns + cursorCol
        chars[idx] = c
        attrs[idx] = curAttr.toByte()
        cursorCol++
    }

    private fun lineFeed() {
        if (cursorRow == scrollBottom) scrollUp(scrollTop, scrollBottom)
        else if (cursorRow < rows - 1) cursorRow++
    }

    private fun reverseLineFeed() {
        if (cursorRow == scrollTop) scrollDown(scrollTop, scrollBottom)
        else if (cursorRow > 0) cursorRow--
    }

    private fun escape(b: Int) {
        state = State.NORMAL
        when (b.toChar()) {
            '[' -> {
                state = State.CSI
                paramCount = 0; paramStarted = false; privateMarker = false
                params.fill(0)
            }
            '7' -> { savedRow = cursorRow; savedCol = cursorCol; savedAttr = curAttr }
            '8' -> { cursorRow = savedRow; cursorCol = savedCol; curAttr = savedAttr }
            'D' -> lineFeed()
            'E' -> { cursorCol = 0; lineFeed() }
            'M' -> reverseLineFeed()
            'c' -> {
                chars.fill(' '); attrs.fill(0)
                cursorRow = 0; cursorCol = 0; curAttr = 0
                scrollTop = 0; scrollBottom = rows - 1
            }
            else -> {} // ignore charset selection etc.
        }
    }

    private fun param(i: Int, def: Int): Int =
        if (i < paramCount && params[i] != 0) params[i] else def

    private fun csi(b: Int) {
        val c = b.toChar()
        when {
            c in '0'..'9' -> {
                if (!paramStarted) { paramStarted = true; if (paramCount == 0) paramCount = 1 }
                val i = paramCount - 1
                if (i < params.size) params[i] = minOf(9999, params[i] * 10 + (c - '0'))
                return
            }
            c == ';' -> {
                if (paramCount == 0) paramCount = 1
                if (paramCount < params.size) paramCount++
                paramStarted = true
                return
            }
            c == '?' -> { privateMarker = true; return }
            b < 0x20 -> { normal(b); return } // control chars inside CSI execute
        }
        state = State.NORMAL
        if (privateMarker) return // e.g. ?25l cursor visibility: ignore
        when (c) {
            'H', 'f' -> moveTo(param(0, 1) - 1, param(1, 1) - 1)
            'A' -> cursorRow = maxOf(if (cursorRow >= scrollTop) scrollTop else 0, cursorRow - param(0, 1))
            'B' -> cursorRow = minOf(if (cursorRow <= scrollBottom) scrollBottom else rows - 1, cursorRow + param(0, 1))
            'C' -> cursorCol = minOf(columns - 1, cursorCol + param(0, 1))
            'D' -> cursorCol = maxOf(0, minOf(columns - 1, cursorCol) - param(0, 1))
            'G' -> cursorCol = (param(0, 1) - 1).coerceIn(0, columns - 1)
            'd' -> cursorRow = (param(0, 1) - 1).coerceIn(0, rows - 1)
            'J' -> eraseDisplay(if (paramCount > 0) params[0] else 0)
            'K' -> eraseLine(if (paramCount > 0) params[0] else 0)
            'L' -> if (cursorRow in scrollTop..scrollBottom) repeat(param(0, 1)) { scrollDown(cursorRow, scrollBottom) }
            'M' -> if (cursorRow in scrollTop..scrollBottom) repeat(param(0, 1)) { scrollUp(cursorRow, scrollBottom) }
            'P' -> deleteChars(param(0, 1))
            '@' -> insertChars(param(0, 1))
            'X' -> eraseChars(param(0, 1))
            'm' -> sgr()
            'r' -> {
                val top = param(0, 1) - 1
                val bot = param(1, rows) - 1
                if (top < bot && bot < rows) { scrollTop = top; scrollBottom = bot }
                moveTo(0, 0)
            }
            's' -> { savedRow = cursorRow; savedCol = cursorCol }
            'u' -> { cursorRow = savedRow; cursorCol = savedCol }
            else -> {}
        }
    }

    private fun moveTo(r: Int, c: Int) {
        cursorRow = r.coerceIn(0, rows - 1)
        cursorCol = c.coerceIn(0, columns - 1)
    }

    private fun sgr() {
        if (paramCount == 0) { curAttr = 0; return }
        for (i in 0 until minOf(paramCount, params.size)) {
            when (params[i]) {
                0 -> curAttr = 0
                1 -> curAttr = curAttr or ATTR_BOLD
                7 -> curAttr = curAttr or ATTR_INVERSE
                22 -> curAttr = curAttr and ATTR_BOLD.inv()
                27 -> curAttr = curAttr and ATTR_INVERSE.inv()
            }
        }
    }

    private fun clearRange(from: Int, toExclusive: Int) {
        chars.fill(' ', from, toExclusive)
        attrs.fill(0, from, toExclusive)
    }

    private fun eraseDisplay(mode: Int) {
        val pos = cursorRow * columns + minOf(cursorCol, columns - 1)
        when (mode) {
            0 -> clearRange(cursorRow * columns + minOf(cursorCol, columns), chars.size)
            1 -> clearRange(0, pos + 1)
            2, 3 -> clearRange(0, chars.size)
        }
    }

    private fun eraseLine(mode: Int) {
        val start = cursorRow * columns
        val col = minOf(cursorCol, columns)
        when (mode) {
            0 -> clearRange(start + col, start + columns)
            1 -> clearRange(start, start + minOf(col + 1, columns))
            2 -> clearRange(start, start + columns)
        }
    }

    private fun eraseChars(n: Int) {
        val start = cursorRow * columns + minOf(cursorCol, columns)
        clearRange(start, minOf(start + n, (cursorRow + 1) * columns))
    }

    private fun deleteChars(n: Int) {
        val rowStart = cursorRow * columns
        val col = minOf(cursorCol, columns - 1)
        val count = minOf(n, columns - col)
        System.arraycopy(chars, rowStart + col + count, chars, rowStart + col, columns - col - count)
        System.arraycopy(attrs, rowStart + col + count, attrs, rowStart + col, columns - col - count)
        clearRange(rowStart + columns - count, rowStart + columns)
    }

    private fun insertChars(n: Int) {
        val rowStart = cursorRow * columns
        val col = minOf(cursorCol, columns - 1)
        val count = minOf(n, columns - col)
        System.arraycopy(chars, rowStart + col, chars, rowStart + col + count, columns - col - count)
        System.arraycopy(attrs, rowStart + col, attrs, rowStart + col + count, columns - col - count)
        clearRange(rowStart + col, rowStart + col + count)
    }

    /** Scroll rows [top..bottom] up by one; bottom row becomes blank. */
    private fun scrollUp(top: Int, bottom: Int) {
        if (bottom > top) {
            System.arraycopy(chars, (top + 1) * columns, chars, top * columns, (bottom - top) * columns)
            System.arraycopy(attrs, (top + 1) * columns, attrs, top * columns, (bottom - top) * columns)
        }
        clearRange(bottom * columns, (bottom + 1) * columns)
    }

    /** Scroll rows [top..bottom] down by one; top row becomes blank. */
    private fun scrollDown(top: Int, bottom: Int) {
        if (bottom > top) {
            System.arraycopy(chars, top * columns, chars, (top + 1) * columns, (bottom - top) * columns)
            System.arraycopy(attrs, top * columns, attrs, (top + 1) * columns, (bottom - top) * columns)
        }
        clearRange(top * columns, (top + 1) * columns)
    }

    companion object {
        const val ATTR_BOLD = 1
        const val ATTR_INVERSE = 2
    }
}
