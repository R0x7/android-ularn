package com.ularn.android.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextReflowTest {

    /** Reflows a page given as text rows (and optional format rows of digits). */
    private fun reflow(rows: List<String>, width: Int, cursorRow: Int = -1, cursorCol: Int = 0,
                       fmts: List<String> = emptyList()): TextReflow.Page =
        TextReflow.reflow(rows.size, 80,
            { r, c -> rows[r].getOrElse(c) { ' ' } },
            { r, c -> fmts.getOrNull(r)?.getOrNull(c)?.minus('0') ?: 0 },
            cursorRow, cursorCol, width)

    private fun TextReflow.Page.lines() = chars.map { String(it).trimEnd() }

    @Test fun rowsThatFitAreKept() {
        val rows = listOf("    a short line", "", "another")
        assertEquals(rows, reflow(rows, 40).lines())
    }

    @Test fun nothingIsWiderThanTheTarget() {
        val rows = listOf(
            "Welcome to the Ularn Thrift Shoppe.  We stock many items explorers find useful",
            "in their adventures.  Feel free to browse to your heart's content.",
            "",
            "a) an energy ring                1800   b) a ring of increase damage     1250",
            "c) a ring of regeneration        2200   d) a ring of extra regeneration 10000",
            "                                                 You have 500000 gold pieces",
            "Enter your transaction [space for more, escape to leave]?")
        for (w in listOf(30, 40, 46, 60)) {
            val p = reflow(rows, w)
            p.chars.forEach { assertTrue("width $w: '${String(it)}'", it.size <= w) }
        }
    }

    @Test fun paragraphIsJoinedAndWrapped() {
        val rows = listOf(
            "Welcome to the Ularn Trading Post.  We buy items that explorers no longer find",
            "useful.  Since the condition of the items you bring in is not certain,")
        val p = reflow(rows, 40)
        assertEquals("Welcome to the Ularn Trading Post.  We", p.lines()[0])
        assertEquals("buy items that explorers no longer find", p.lines()[1])
        assertEquals(4, p.rows)
    }

    @Test fun sideBySideListsBecomeOneAlignedList() {
        val rows = listOf(
            "a) an energy ring                1800   b) a ring of increase damage     1250",
            "c) a ring of regeneration        2200   d) a ring of extra regeneration 10000")
        assertEquals(listOf(
            "a) an energy ring                1800",
            "b) a ring of increase damage     1250",
            "c) a ring of regeneration        2200",
            "d) a ring of extra regeneration 10000"), reflow(rows, 46).lines())
    }

    @Test fun highlightedHeadingStartsANewParagraph() {
        val rows = listOf(
            "  Causes some monsters to go to sleep so you can get some quick easy hits in",
            "Charm Monster (chm) [Self] {Short}")
        val fmts = listOf("", "1111111111111")
        val lines = reflow(rows, 46, fmts = fmts).lines()
        assertEquals("Charm Monster (chm) [Self] {Short}", lines.last())
    }

    @Test fun ruleIsCutToWidth() {
        val p = reflow(listOf("-".repeat(63), "Score: 1"), 20)
        assertEquals(listOf("-".repeat(20), "Score: 1"), p.lines())
    }

    @Test fun cursorFollowsThePrompt() {
        val prompt = "Your wish? [(d) deposit, (w) withdraw, (s) sell a stone, or escape] "
        val rows = listOf("", "", prompt)
        // The game leaves the cursor after the trailing space.
        val p = reflow(rows, 40, cursorRow = 2, cursorCol = prompt.length)
        val last = p.lines()[p.cursorRow]
        assertTrue(last.endsWith("escape]"))
        assertEquals(last.length + 1, p.cursorCol)
    }

    @Test fun blankRunsShrink() {
        val rows = listOf("x".repeat(60), "", "", "", "", "prompt?")
        assertEquals(listOf("x".repeat(40), "x".repeat(20), "", "prompt?"), reflow(rows, 40).lines())
    }
}
