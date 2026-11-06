package com.ularn.android.stats

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

/** "Statistics & achievements": lifetime totals, recent runs, achievements. */
object StatsDialog {

    fun show(context: Context, book: RunBook) {
        val d = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), dp(8))
        }
        fun header(t: String) = box.addView(TextView(context).apply {
            text = t; textSize = 16f; setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(255, 190, 60))
            setPadding(0, dp(14), 0, dp(4))
        })
        fun row(label: String, value: String) = box.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(context).apply { text = label; alpha = 0.8f }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(context).apply { text = value; gravity = Gravity.END; setTypeface(typeface, Typeface.BOLD) })
        })
        fun note(t: String, dim: Boolean = true) = box.addView(TextView(context).apply {
            text = t; textSize = 13f; if (dim) alpha = 0.75f
            setPadding(0, dp(2), 0, dp(2))
        })

        val l = book.lifetime
        val cur = book.current?.takeIf { !it.finished }

        header("Lifetime")
        row("Games played", "${l.gamesPlayed}")
        row("Wins", "${l.wins}")
        row("Deaths", "${l.deaths}")
        row("Quit / abandoned", "${l.quits}")
        row("Deepest dungeon level", if (l.bestDungeon > 0) "${l.bestDungeon}" else "-")
        row("Deepest volcano level", if (l.bestVolcano > 0) "V${l.bestVolcano}" else "-")
        row("Highest character level", if (l.bestPlevel > 0) "${l.bestPlevel}" else "-")
        row("Most gold carried", num(l.bestGold))
        row("Best score", num(l.bestScore))
        if (l.fastestWinTurns > 0) row("Fastest win", "${num(l.fastestWinTurns)} turns")
        row("Monsters killed", num(l.totalKills + (cur?.kills ?: 0)))
        row("Turns played", num(l.totalTurns + (cur?.turns ?: 0)))
        row("Time played", duration(l.totalPlayMs + (cur?.playMs ?: 0)))

        if (cur != null) {
            header("Current game")
            note(describe(cur), dim = false)
        }

        val unlockedCount = Achievements.ALL.count { it.id in book.unlocked }
        header("Achievements ($unlockedCount/${Achievements.ALL.size})")
        val dateFmt = DateFormat.getDateInstance(DateFormat.MEDIUM)
        for (a in Achievements.ALL) {
            val at = book.unlocked[a.id]
            box.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(4), 0, dp(4))
                alpha = if (at != null) 1f else 0.4f
                addView(TextView(context).apply {
                    text = if (at != null) "✓ ${a.title}" else "${a.title}  (locked)"
                    setTypeface(typeface, Typeface.BOLD)
                    if (at != null) setTextColor(Color.rgb(110, 220, 120))
                })
                addView(TextView(context).apply {
                    text = if (at != null) "${a.criteria}  Unlocked ${dateFmt.format(Date(at))}." else a.criteria
                    textSize = 13f
                })
            })
        }

        header("Recent games")
        if (book.recent.isEmpty()) note("No finished games yet.")
        for (r in book.recent.take(15)) note(describe(r), dim = false)

        if (l.deathCauses.isNotEmpty()) {
            header("Causes of death")
            for ((cause, n) in l.deathCauses.entries.sortedByDescending { it.value }.take(10))
                row(cause.replaceFirstChar { it.uppercase() }, "$n")
        }

        AlertDialog.Builder(context)
            .setTitle("Statistics & achievements")
            .setView(ScrollView(context).apply { addView(box) })
            .setPositiveButton("OK", null)
            .show()
    }

    private fun describe(r: RunRecord): String {
        val sb = StringBuilder()
        sb.append(r.className)
        if (r.difficulty > 0) sb.append(" (difficulty ${r.difficulty})")
        sb.append(" - ")
        sb.append(when (r.outcome) {
            Outcome.IN_PROGRESS -> "in progress"
            Outcome.WON -> "won!"
            Outcome.DIED -> r.cause.ifEmpty { "died" }
            Outcome.QUIT -> if (r.cause == "abandoned") "abandoned" else "quit"
        })
        sb.append("\n   Deepest ")
        sb.append(if (r.maxDungeon > 0) "level ${r.maxDungeon}" else "Home")
        if (r.maxVolcano > 0) sb.append(", V${r.maxVolcano}")
        sb.append(" · char lv ${r.maxPlevel} · ${num(r.turns)} turns · ${r.kills} kills · ${num(r.maxGold)} gold max")
        sb.append(" · ${duration(r.playMs)}")
        if (r.score > 0) sb.append(" · score ${num(r.score)}")
        return sb.toString()
    }

    private fun num(v: Number) = "%,d".format(v.toLong())

    private fun duration(ms: Long): String {
        val m = ms / 60_000
        return if (m < 60) "${m}m" else "${m / 60}h ${m % 60}m"
    }
}
