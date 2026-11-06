package com.ularn.android.game

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Sends a command, then answers the prompt it leads to, but only once the game
 * really asks that question.
 *
 * Sending "." + "u" blindly would be dangerous: if the stairs prompt doesn't
 * appear (time stopped, a monster next to a fountain, ...), the "u" would be
 * read as a command (move north-east). So each follow-up [Step] waits until
 * the shared state shows the expected input mode and answers; anything else
 * (back to command mode, a different prompt, a timeout) drops the rest.
 *
 * The game only publishes while it runs: blocked waiting for a key, its state
 * doesn't change. So a snapshot whose `seq` differs from the one we sent on
 * is a reply to our key. All calls must be on the main thread (the same one
 * that draws from [GameState]).
 */
class KeyFollowUp(
    private val state: () -> GameState?,
    private val send: (ByteArray) -> Unit,
) {
    /** Keys to send once the game waits in [mode] with answers that [accepts]. */
    class Step(val keys: String, val mode: Int, val accepts: (String) -> Boolean = { true })

    private val handler = Handler(Looper.getMainLooper())
    private val steps = ArrayDeque<Step>()
    private var sentSeq = 0
    private var deadline = 0L

    private val poll = object : Runnable {
        override fun run() {
            check()
            if (steps.isNotEmpty()) handler.postDelayed(this, POLL_MS)
        }
    }

    /** Sends [first] now and queues [then]. Returns false if the game isn't up. */
    fun start(first: String, vararg then: Step): Boolean {
        cancel()
        val s = state() ?: return false
        if (!s.refresh()) return false
        sentSeq = s.seq
        send(first.toByteArray(Charsets.ISO_8859_1))
        if (then.isEmpty()) return true
        steps.addAll(then)
        deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        handler.postDelayed(poll, POLL_MS)
        return true
    }

    fun cancel() {
        steps.clear()
        handler.removeCallbacks(poll)
    }

    /** Call when the game state changed (also polled while a step is pending). */
    fun check() {
        val step = steps.firstOrNull() ?: return
        val s = state()
        if (s == null || SystemClock.uptimeMillis() > deadline) { cancel(); return }
        if (!s.refresh() || s.seq == sentSeq || s.inputMode == GameState.INPUT_BUSY) return
        if (s.inputMode == step.mode && step.accepts(s.answers)) {
            steps.removeFirst()
            sentSeq = s.seq
            deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
            send(step.keys.toByteArray(Charsets.ISO_8859_1))
            if (steps.isEmpty()) handler.removeCallbacks(poll)
        } else {
            cancel()
        }
    }

    private companion object {
        const val POLL_MS = 60L
        const val TIMEOUT_MS = 3000L
    }
}
