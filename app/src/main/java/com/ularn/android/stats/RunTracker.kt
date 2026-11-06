package com.ularn.android.stats

import android.content.Context
import android.os.SystemClock
import com.ularn.android.game.GameState
import java.io.File

/**
 * Feeds [RunBook] from the game's display state and keeps it on disk.
 *
 * Call [observe] for every frame drawn while a game runs, [onGameExit] when
 * the game process ends, and [flush] when the app goes to the background.
 * Everything runs on the main thread.
 */
class RunTracker(context: Context) {

    private val store = StatsStore(File(context.filesDir, "stats.json"))
    val book: RunBook = store.load()

    /** Called with achievements as they unlock. */
    var onUnlocked: ((List<Achievement>) -> Unit)? = null

    private var lastFrame = 0L
    private var lastSave = 0L

    fun observe(s: GameState) {
        val now = SystemClock.elapsedRealtime()
        // Time played: the screen redraws at least every second or so while
        // the game is shown, so a longer gap means the app was away.
        val gap = if (lastFrame > 0) (now - lastFrame).coerceAtMost(MAX_FRAME_GAP_MS) else 0L
        lastFrame = now

        val unlocked = book.observe(observationOf(s), System.currentTimeMillis())
        // Only while this run's character is on screen (not at character
        // creation or the scoreboard, where no run id is set yet).
        if (s.runId != 0 && s.runId == book.current?.id) book.addPlayTime(gap)
        if (unlocked.isNotEmpty()) onUnlocked?.invoke(unlocked)
        if (book.important || (book.dirty && now - lastSave > SAVE_INTERVAL_MS)) save(now)
    }

    /** The game process ended; [resumable] = a save or checkpoint is left. */
    fun onGameExit(s: GameState?, resumable: Boolean) {
        // Catch a final score shown while the app wasn't drawing.
        if (s != null && s.refresh()) {
            val unlocked = book.observe(observationOf(s), System.currentTimeMillis())
            if (unlocked.isNotEmpty()) onUnlocked?.invoke(unlocked)
        }
        book.onGameExit(resumable, System.currentTimeMillis())
        lastFrame = 0
        save(SystemClock.elapsedRealtime())
    }

    /** Save now and stop the play clock (the app is going to the background). */
    fun flush() {
        lastFrame = 0
        if (book.dirty || book.important) save(SystemClock.elapsedRealtime())
    }

    private fun save(now: Long) {
        store.save(book)
        lastSave = now
    }

    private fun observationOf(s: GameState) = Observation(
        runId = s.runId,
        hp = s.hp,
        hpMax = s.hpMax,
        dlevel = s.dlevel,
        levelKnown = s.levelName != "?",
        plevel = s.playerLevel,
        gold = s.gold,
        turns = s.gameTime,
        kills = s.kills,
        classNum = s.classNum,
        difficulty = s.hardness,
        endState = s.endState,
        endScore = s.endScore,
        endReason = s.endReason,
    )

    companion object {
        private const val MAX_FRAME_GAP_MS = 30_000L
        private const val SAVE_INTERVAL_MS = 15_000L
    }
}
