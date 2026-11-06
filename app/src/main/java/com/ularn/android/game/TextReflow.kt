package com.ularn.android.game

/**
 * Re-wraps one of the game's 80-column text pages (help, shops, bank, ...) to
 * a narrower width, so it can be read on a phone held upright without
 * shrinking the text or scrolling sideways.
 *
 * The game lays its pages out for an 80x24 terminal: prose wrapped at ~78
 * columns, tables lined up with runs of spaces, headings centred. Rows are
 * handled like this:
 *  - a row that already fits is kept as it is;
 *  - runs of rows that line up into columns (a table) keep their columns if
 *    they fit, otherwise the columns are split into bands shown one under
 *    the other (a two-column shop list becomes one column);
 *  - other rows are prose: consecutive lines of a paragraph are joined and
 *    word-wrapped to the width;
 *  - a long row that is mostly indentation (right-aligned totals) just moves
 *    left;
 *  - runs of blank rows (the game positions prompts at fixed rows) shrink to
 *    one.
 *
 * Every output cell remembers where it came from, so the text cursor can be
 * placed after reflowing.
 */
object TextReflow {

    class Page(val chars: List<CharArray>, val fmts: List<IntArray>, val cursorRow: Int, val cursorCol: Int) {
        val rows get() = chars.size
        val cols get() = chars.maxOfOrNull { it.size } ?: 0
    }

    /** One source cell: character, format and position in the source page. */
    private class Cell(val ch: Char, val fmt: Int, val row: Int, val col: Int)

    private class Line(val cells: ArrayList<Cell> = ArrayList()) {
        fun add(c: Cell) = cells.add(c)
        val len get() = cells.size
    }

    /** Minimum gap (in spaces) that makes a row look like part of a table. */
    private const val GUTTER = 3
    /** Minimum gap, blank in every row of a table, between two columns. */
    private const val COLUMN_GAP = 2
    private const val INVERSE = GameState.FMT_INVERSE

    /**
     * @param ch     character at (row, col) of the source page
     * @param fmt    format at (row, col)
     * @param cursorRow, cursorCol  0-based cursor position in the source
     * @param width  target width in columns
     */
    fun reflow(rows: Int, cols: Int, ch: (Int, Int) -> Char, fmt: (Int, Int) -> Int,
               cursorRow: Int, cursorCol: Int, width: Int): Page {
        // Source rows as cells, trailing blanks trimmed.
        val src = (0 until rows).map { r ->
            val cells = (0 until cols).map { c ->
                val x = ch(r, c)
                Cell(if (x.code == 0) ' ' else x, fmt(r, c), r, c)
            }
            var n = cells.size
            while (n > 0 && isBlank(cells[n - 1])) n--
            cells.subList(0, n)
        }

        val out = ArrayList<Line>()
        val w = maxOf(width, 10)
        var r = 0
        while (r < src.size) {
            val row = src[r]
            when {
                isRule(row) -> {   // a line of dashes: cut to the width
                    out.add(Line(ArrayList(row.subList(0, minOf(row.size, w)))))
                    r++
                }
                row.isEmpty() -> {
                    // Keep one blank row; drop the rest of a run (but never before the first output row).
                    if (out.isNotEmpty() && out.last().len != 0) out.add(Line())
                    else if (out.isEmpty()) out.add(Line())
                    r++
                }
                isTabular(row) && r + 1 < src.size && isTabular(src[r + 1]) -> {
                    var end = r + 1
                    while (end + 1 < src.size && isTabular(src[end + 1])) end++
                    // A table may continue past non-tabular rows that line up with it?
                    // Keep it simple: only consecutive tabular rows.
                    layoutTable(src.subList(r, end + 1), w, out)
                    r = end + 1
                }
                else -> {
                    // Prose: gather the rows of this paragraph.
                    var end = r
                    while (end + 1 < src.size && joins(src[end], src[end + 1], end == r, w)) end++
                    if (end == r && row.size <= w) out.add(Line(ArrayList(row)))
                    else layoutParagraph(src.subList(r, end + 1), w, out)
                    r = end + 1
                }
            }
        }
        // Trailing blank lines carry nothing.
        while (out.size > 1 && out.last().len == 0) out.removeAt(out.size - 1)

        // Cursor: right after the last output cell from the cursor's source row
        // that lies before it.
        var cr = -1
        var cc = 0
        for ((i, line) in out.withIndex()) for ((j, cell) in line.cells.withIndex()) {
            if (cell.row == cursorRow && cell.col < cursorCol) {
                cr = i; cc = j + (cursorCol - cell.col)
            }
        }
        if (cursorRow !in 0 until rows) {
            // No cursor on the page: end of the last line.
            cr = out.size - 1
            cc = out[cr].len
        } else if (cr < 0) {
            // Nothing before the cursor on its row: start of the first line that came from that row,
            // or the end of the page.
            cr = out.indexOfFirst { l -> l.cells.any { it.row == cursorRow } }
            if (cr < 0) {
                cr = out.size
                out.add(Line())
            }
            cc = 0
        }
        if (cc >= w) {   // would fall off the right edge: next line
            cc = 0; cr++
            if (cr >= out.size) out.add(Line())
        }

        return Page(out.map { l -> CharArray(l.len) { l.cells[it].ch } },
                    out.map { l -> IntArray(l.len) { l.cells[it].fmt } }, cr, cc)
    }

    /** A row of one repeated symbol, like "-------". */
    private fun isRule(row: List<Cell>): Boolean {
        val i = indent(row)
        if (row.size - i < 10) return false
        val ch = row[i].ch
        return !ch.isLetterOrDigit() && (i until row.size).all { row[it].ch == ch }
    }

    private fun isBlank(c: Cell) = c.ch == ' ' && c.fmt != INVERSE

    private fun indent(row: List<Cell>): Int {
        var i = 0
        while (i < row.size && isBlank(row[i])) i++
        return i
    }

    /** Has a gap of GUTTER+ blanks between two pieces of text. */
    private fun isTabular(row: List<Cell>) = hasGap(row, GUTTER)

    /**
     * Should row `b` continue the paragraph that ends with row `a`?
     * `aStarts`: `a` is the paragraph's first row.
     */
    private fun joins(a: List<Cell>, b: List<Cell>, aStarts: Boolean, width: Int): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        if (hasGap(b, 6)) return false                 // a table row, not prose
        if (isRule(a) || isRule(b)) return false
        val ia = indent(a)
        val ib = indent(b)
        // A heading (highlighted text) starts its own paragraph.
        if (b[ib].fmt != GameState.FMT_NORMAL && b[ib].fmt != a.last().fmt) return false
        // A capitalised line after one that didn't finish a sentence is a new
        // item ("...magic arrow" / "Improves with experience"), not a continuation.
        if (b[ib].ch.isUpperCase() && a.last().ch.isLetterOrDigit()) return false
        // `a` must have run on to the right margin of the original layout
        // (the author wrapped it there) ...
        if (a.size < 50 || a.size <= width - 20) return false
        // ... and `b` must sit where a continuation line does: at the same
        // indent, or at the margin under an indented first line.
        return ib == ia || (aStarts && ib == 0 && ia in 3..8)
    }

    private fun hasGap(row: List<Cell>, min: Int): Boolean {
        var i = indent(row)
        var gap = 0
        while (i < row.size) {
            if (isBlank(row[i])) gap++ else { if (gap >= min) return true; gap = 0 }
            i++
        }
        return false
    }

    /** Word-wraps the rows of one paragraph into `out`. */
    private fun layoutParagraph(rows: List<List<Cell>>, width: Int, out: ArrayList<Line>) {
        val first = rows[0]
        val ind = indent(first)
        val contentLen = first.size - ind
        if (rows.size == 1 && contentLen <= width && ind > 4) {
            // Just too far right (right-aligned or centred): move it left.
            val shift = ind - (width - contentLen)
            out.add(Line(ArrayList(first.subList(shift, first.size))))
            return
        }
        // Words, with the original spacing between them (runs of 3+ cut to 2).
        val words = ArrayList<ArrayList<Cell>>()
        val gaps = ArrayList<Int>()          // spaces before each word
        for ((k, row) in rows.withIndex()) {
            var i = indent(row)
            var pending = if (k == 0) 0 else 1
            while (i < row.size) {
                if (isBlank(row[i])) { pending++; i++; continue }
                val word = ArrayList<Cell>()
                while (i < row.size && !isBlank(row[i])) word.add(row[i++])
                gaps.add(if (words.isEmpty()) 0 else pending.coerceIn(1, 2))
                words.add(word)
                pending = 0
            }
        }
        val firstIndent = minOf(ind, 4, width / 4)
        val contIndent = if (rows.size > 1) minOf(indent(rows[1]), 4) else if (ind <= 4) ind else 0
        var line = Line()
        repeat(firstIndent) { line.add(Cell(' ', 0, first[0].row, -1)) }
        var atStart = true
        for ((k, word) in words.withIndex()) {
            val gap = if (atStart) 0 else gaps[k]
            if (!atStart && line.len + gap + word.size > width) {
                out.add(line)
                line = Line()
                repeat(contIndent) { line.add(Cell(' ', 0, word[0].row, -1)) }
                atStart = true
            }
            if (!atStart) repeat(gaps[k]) { line.add(Cell(' ', 0, word[0].row, -1)) }
            // A single word longer than the line: hard-break it.
            for (c in word) {
                if (line.len >= width) { out.add(line); line = Line() }
                line.add(c)
            }
            atStart = false
        }
        if (line.len > 0) out.add(line)
    }

    /** Lays out consecutive tabular rows, keeping columns where possible. */
    private fun layoutTable(rows: List<List<Cell>>, width: Int, out: ArrayList<Line>) {
        val maxLen = rows.maxOf { it.size }
        // Gutter columns: blank in every row (rows that ended count as blank).
        val blank = BooleanArray(maxLen) { c -> rows.all { c >= it.size || isBlank(it[c]) } }
        // Column spans [start, end) separated by gutters of COLUMN_GAP or more.
        val spans = ArrayList<IntArray>()
        var c = 0
        while (c < maxLen) {
            while (c < maxLen && blank[c]) c++
            if (c >= maxLen) break
            val start = c
            var end = c
            var run = 0
            while (c < maxLen) {
                if (blank[c]) { run++; if (run >= COLUMN_GAP) break } else { run = 0; end = c + 1 }
                c++
            }
            spans.add(intArrayOf(start, end))
            c = end
            while (c < maxLen && blank[c]) c++
        }
        if (spans.size < 2) {   // no real columns: treat each row as prose
            for (row in rows) {
                if (row.size <= width) out.add(Line(ArrayList(row)))
                else layoutParagraph(listOf(row), width, out)
            }
            return
        }

        fun cell(row: List<Cell>, s: IntArray): List<Cell> {
            if (s[0] >= row.size) return emptyList()
            val part = row.subList(s[0], minOf(s[1], row.size))
            var n = part.size
            while (n > 0 && isBlank(part[n - 1])) n--
            return part.subList(0, n)
        }
        val colW = spans.map { s -> s[1] - s[0] }
        val lead = spans[0][0]

        // 1) Original spacing, just less indentation. 2) Gutters of 2.
        val origW = spans.last()[1] - lead
        val tightW = colW.sum() + 2 * (colW.size - 1)
        if (origW <= width || tightW <= width) {
            val keep = origW <= width
            val total = if (keep) origW else tightW
            val ind = minOf(lead, width - total)
            for (row in rows) {
                val line = Line()
                repeat(ind) { line.add(Cell(' ', 0, row.firstOrNull()?.row ?: 0, -1)) }
                var x = 0
                for ((k, s) in spans.withIndex()) {
                    val target = if (keep) s[0] - lead else colW.subList(0, k).sum() + 2 * k
                    val part = cell(row, s)
                    if (part.isEmpty()) continue
                    while (x < target) { line.add(Cell(' ', 0, part[0].row, -1)); x++ }
                    for (p in part) { line.add(p); x++ }
                }
                out.add(line)
            }
            return
        }

        // 3) Columns as they are, except the last one, which wraps in the
        //    space left (a list of names and descriptions).
        val n0 = colW.size
        val hangAt = colW.subList(0, n0 - 1).sum() + 2 * (n0 - 1)
        if (width - hangAt >= maxOf(16, width / 3)) {
            for (row in rows) {
                val line = Line()
                var x = 0
                for (k in 0 until n0 - 1) {
                    val part = cell(row, spans[k])
                    if (part.isEmpty()) continue
                    val target = colW.subList(0, k).sum() + 2 * k
                    while (x < target) { line.add(Cell(' ', 0, part[0].row, -1)); x++ }
                    for (p in part) { line.add(p); x++ }
                }
                val last = cell(row, spans[n0 - 1]).dropWhile { isBlank(it) }
                if (last.isEmpty()) { if (line.len > 0) out.add(line); continue }
                val wrapped = ArrayList<Line>()
                layoutParagraph(listOf(last), width - hangAt, wrapped)
                for ((i, part) in wrapped.withIndex()) {
                    val l = if (i == 0) line else Line()
                    var lx = if (i == 0) x else 0
                    while (lx < hangAt) { l.add(Cell(' ', 0, last[0].row, -1)); lx++ }
                    for (p in part.cells) l.add(p)
                    out.add(l)
                }
            }
            return
        }

        // 4) Split the columns into bands shown one under the other. A table
        //    that repeats its columns side by side (a shop's two lists of
        //    "item  price") splits into equal bands that line up with each
        //    other; anything else packs as many columns per band as fit.
        val n = colW.size
        var bands: List<IntRange>? = null
        var offsets: IntArray? = null   // column start within a band, by position in band
        var slotW: IntArray? = null     // width of each position in a band
        // Columns whose cells all end at the same place (prices) stay right-aligned.
        val rightAligned = BooleanArray(spans.size) { k ->
            rows.all { row -> val p = cell(row, spans[k]); p.isEmpty() || p.last().col == spans[k][1] - 1 }
        }
        for (k in n - 1 downTo 2) {
            if (n % k != 0) continue
            val off = IntArray(k)
            val sw = IntArray(k)
            var x = 0
            for (j in 0 until k) {
                off[j] = x
                sw[j] = (0 until n / k).maxOf { colW[it * k + j] }
                x += sw[j] + 2
            }
            if (x - 2 <= width) {
                bands = (0 until n / k).map { it * k until (it + 1) * k }
                offsets = off
                slotW = sw
                break
            }
        }
        if (bands == null) {
            // Columns kept with their original spacing, as many per band as
            // fit; a right-aligned last column (prices) lines up across bands.
            val greedy = ArrayList<IntRange>()
            var b0 = 0
            for (k in 1..n) {
                if (k == n || spans[k][1] - spans[b0][0] > width) { greedy.add(b0 until k); b0 = k }
            }
            if (greedy.all { spans[it.last][1] - spans[it.first][0] <= width }) {
                val bandW = greedy.maxOf { spans[it.last][1] - spans[it.first][0] }
                for (row in rows) for (band in greedy) {
                    val line = Line()
                    var x = 0
                    for (k in band) {
                        val part = cell(row, spans[k])
                        if (part.isEmpty()) continue
                        var target = spans[k][0] - spans[band.first][0]
                        if (k == band.last && k != band.first && rightAligned[k]) target = bandW - colW[k]
                        while (x < target) { line.add(Cell(' ', 0, part[0].row, -1)); x++ }
                        for (p in part) { line.add(p); x++ }
                    }
                    if (line.len > 0) out.add(line)
                }
                return
            }
        }
        if (bands == null) {
            val greedy = ArrayList<IntRange>()
            var b0 = 0
            var bw = colW[0]
            for (k in 1 until n) {
                if (bw + 2 + colW[k] <= width) bw += 2 + colW[k]
                else { greedy.add(b0 until k); b0 = k; bw = colW[k] }
            }
            greedy.add(b0 until n)
            bands = greedy
        }
        for (row in rows) {
            for ((bi, band) in bands.withIndex()) {
                val bandIndent = if (offsets != null || bi == 0) 0 else minOf(2, width / 8)
                val line = Line()
                var x = 0
                var any = false
                for (k in band) {
                    val j = k - band.first
                    var target = offsets?.get(j) ?: (band.first until k).sumOf { colW[it] + 2 }
                    if (slotW != null && rightAligned[k]) target += slotW[j] - colW[k]
                    val part = cell(row, spans[k])
                    if (part.isEmpty()) continue
                    if (!any) { repeat(bandIndent) { line.add(Cell(' ', 0, part[0].row, -1)) } }
                    any = true
                    while (x < target) { line.add(Cell(' ', 0, part[0].row, -1)); x++ }
                    for (p in part) { line.add(p); x++ }
                }
                if (!any) continue
                if (line.len <= width) out.add(line)
                else layoutParagraph(listOf(line.cells), width, out)  // one column wider than the screen
            }
        }
    }
}
