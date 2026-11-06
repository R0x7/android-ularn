package com.ularn.android.a11y

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityManager
import com.ularn.android.game.GameState

/**
 * Sends new lines of the game's message area to TalkBack.
 *
 * The game's display changes many times per turn, so changes are coalesced
 * (a short delay after the last one), and only lines that weren't there
 * before are spoken: the message area scrolls up, so the previous lines are
 * matched against the start of the current ones, and a line that merely grew
 * ("You hit the bat." -> "You hit the bat.  The bat dies.") speaks just the
 * new part.
 */
class MessageAnnouncer(private val view: View, private val state: () -> GameState?) {

    var enabled = false
        set(v) { field = v; previous = emptyList() }

    private val main = Handler(Looper.getMainLooper())
    private var previous: List<String> = emptyList()
    private var lastSpoken = ""
    private var lastSpokenAt = 0L
    private var pending = false

    private val manager get() = view.context.getSystemService(AccessibilityManager::class.java)

    private val check = Runnable { pending = false; announceNew() }

    /** Call whenever the game reports a display change (any thread). */
    fun onStateChanged() {
        if (!enabled) return
        main.post {
            if (pending) main.removeCallbacks(check)
            pending = true
            main.postDelayed(check, DELAY_MS)
        }
    }

    /** Forget the message history (new game). */
    fun reset() { previous = emptyList(); lastSpoken = "" }

    private fun announceNew() {
        if (!enabled || manager?.isEnabled != true) return
        val s = state() ?: return
        if (!s.refresh() || s.displayMode != GameState.DISPLAY_MAP) return
        val current = (0 until s.msgLines).map { r ->
            val chars = CharArray(s.lineLen) { s.msgChar(r, it) }
            String(chars).replace('\u0000', ' ').trim()
        }.filter { it.isNotEmpty() }
        val fresh = newLines(previous, current)
        previous = current
        val text = fresh.joinToString(" ").replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        if (text == lastSpoken && now - lastSpokenAt < REPEAT_MS) return
        lastSpoken = text; lastSpokenAt = now
        @Suppress("DEPRECATION")
        view.announceForAccessibility(text)
    }

    companion object {
        private const val DELAY_MS = 350L
        /** The same text again within this time is a redraw, not a new message. */
        private const val REPEAT_MS = 1500L

        /** Lines of [cur] that are new relative to [prev] (see the class comment). */
        fun newLines(prev: List<String>, cur: List<String>): List<String> {
            if (prev.isEmpty()) return cur
            for (k in minOf(prev.size, cur.size) downTo 1) {
                val off = prev.size - k
                var ok = true
                for (i in 0 until k - 1) if (prev[off + i] != cur[i]) { ok = false; break }
                if (!ok) continue
                val last = prev[prev.size - 1]
                val grown = cur[k - 1]
                if (!grown.startsWith(last)) continue
                val extra = grown.substring(last.length).trim()
                return (if (extra.isNotEmpty()) listOf(extra) else emptyList()) + cur.drop(k)
            }
            return cur
        }
    }
}
