package com.ularn.android.stats

/**
 * Run statistics, lifetime totals and achievements: the bookkeeping only, no
 * Android classes (unit-tested on the JVM). [RunTracker] feeds it from the
 * game's shared state and [StatsStore] persists it.
 *
 * A run is one character, from a new game to its death, win or quit. The
 * game front end gives every character a random id that is stored inside its
 * save / checkpoint, so continuing or resuming a game continues the same run
 * and nothing is counted twice.
 */

enum class Outcome(val label: String) {
    IN_PROGRESS("In progress"), DIED("Died"), WON("Won"), QUIT("Quit");

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: IN_PROGRESS
    }
}

/** One frame's worth of what the run tracker needs from the game. */
data class Observation(
    val runId: Int,
    val hp: Int,
    val hpMax: Int,
    /** Level index: 0 home, 1..15 dungeon, 16..20 volcano V1..V5. */
    val dlevel: Int,
    /** False while the game shows the level as "?" (after a teleport). */
    val levelKnown: Boolean = true,
    val plevel: Int,
    val gold: Int,
    val turns: Int,
    val kills: Int,
    val classNum: Int = -1,
    val difficulty: Int = 0,
    /** GameState.END_*: 0 none, 1 died, 2 won, 3 quit. */
    val endState: Int = 0,
    val endScore: Int = 0,
    val endReason: String = "",
)

class RunRecord(val id: Int, var startedAt: Long) {
    var endedAt = 0L
    var classNum = -1
    var difficulty = 0
    /** Deepest dungeon level (1..15), 0 = never left town. */
    var maxDungeon = 0
    /** Deepest volcano level (1..5), 0 = never entered. */
    var maxVolcano = 0
    var maxPlevel = 0
    var maxGold = 0
    var turns = 0
    var kills = 0
    var playMs = 0L
    var outcome = Outcome.IN_PROGRESS
    var cause = ""
    var score = 0
    /** HP fell below 10% of max this run ... */
    var wasNearDeath = false
    /** ... and later got back to full. */
    var recoveredFromBrink = false

    val finished get() = outcome != Outcome.IN_PROGRESS
    val className get() = CLASS_NAMES.getOrNull(classNum) ?: "?"

    companion object {
        val CLASS_NAMES = listOf("Ogre", "Wizard", "Klingon", "Elf", "Rogue", "Adventurer", "Dwarf", "Rambo")
    }
}

class Lifetime {
    var gamesPlayed = 0
    var wins = 0
    var deaths = 0
    var quits = 0
    var bestDungeon = 0
    var bestVolcano = 0
    var bestPlevel = 0
    var bestGold = 0
    var bestScore = 0
    /** Totals over finished runs. */
    var totalKills = 0L
    var totalTurns = 0L
    var totalPlayMs = 0L
    var fastestWinTurns = 0
    val deathCauses = LinkedHashMap<String, Int>()
}

class Achievement(
    val id: String,
    val title: String,
    val criteria: String,
    val test: (RunRecord, Lifetime) -> Boolean,
)

object Achievements {
    const val FAST_WIN_TURNS = 20_000

    val ALL = listOf(
        Achievement("first_blood", "First Blood", "Kill a monster.") { r, _ -> r.kills >= 1 },
        Achievement("slayer", "Slayer", "Kill 100 monsters in one game.") { r, _ -> r.kills >= 100 },
        Achievement("delver", "Delver", "Reach dungeon level 5.") { r, _ -> r.maxDungeon >= 5 },
        Achievement("deep_delver", "Deep Delver", "Reach dungeon level 10.") { r, _ -> r.maxDungeon >= 10 },
        Achievement("rock_bottom", "Rock Bottom", "Reach the bottom of the dungeon (level 15).") { r, _ -> r.maxDungeon >= 15 },
        Achievement("volcano", "Into the Fire", "Enter the volcano.") { r, _ -> r.maxVolcano >= 1 },
        Achievement("volcano_deep", "Heart of the Volcano", "Reach volcano level V5.") { r, _ -> r.maxVolcano >= 5 },
        Achievement("seasoned", "Seasoned", "Reach character level 10.") { r, _ -> r.maxPlevel >= 10 },
        Achievement("veteran", "Veteran", "Reach character level 20.") { r, _ -> r.maxPlevel >= 20 },
        Achievement("hoarder", "Hoarder", "Carry 10,000 gold.") { r, _ -> r.maxGold >= 10_000 },
        Achievement("dragon_hoard", "Dragon's Hoard", "Carry 100,000 gold.") { r, _ -> r.maxGold >= 100_000 },
        Achievement("brink", "Back from the Brink", "Fall below 10% of your HP, then recover to full health.") { r, _ -> r.recoveredFromBrink },
        Achievement("survivor", "Survivor", "Last 10,000 turns in one game.") { r, _ -> r.turns >= 10_000 },
        Achievement("persistent", "Persistent", "Start 10 games.") { _, l -> l.gamesPlayed >= 10 },
        Achievement("saviour", "Saviour", "Win the game.") { r, _ -> r.outcome == Outcome.WON },
        Achievement("against_clock", "Against the Clock", "Win in under ${"%,d".format(FAST_WIN_TURNS)} turns.") { r, _ ->
            r.outcome == Outcome.WON && r.turns < FAST_WIN_TURNS
        },
        Achievement("glutton", "Glutton for Punishment", "Win at difficulty 1 or higher.") { r, _ ->
            r.outcome == Outcome.WON && r.difficulty >= 1
        },
    )

    fun byId(id: String) = ALL.firstOrNull { it.id == id }
}

class RunBook {
    var current: RunRecord? = null
    val lifetime = Lifetime()
    /** Finished runs, newest first. */
    val recent = ArrayList<RunRecord>()
    /** Achievement id to unlock time. */
    val unlocked = LinkedHashMap<String, Long>()

    /** Set whenever something worth saving changed. */
    var dirty = false
    /** Set when a change should be saved right away (new run, end, unlock). */
    var important = false

    /**
     * Updates the run from one frame of game state. Returns achievements
     * unlocked by it (usually none).
     */
    fun observe(o: Observation, now: Long): List<Achievement> {
        if (o.runId == 0) return emptyList()
        var run = current
        if (run == null || run.id != o.runId) {
            // A run that already ended never comes back (its save files are gone).
            if (recent.any { it.id == o.runId }) return emptyList()
            // A new character: whatever was in progress can no longer be
            // continued (starting a new game discards its save/checkpoint).
            run?.let { if (!it.finished) finish(it, Outcome.QUIT, "abandoned", 0, now) }
            run = RunRecord(o.runId, now)
            current = run
            lifetime.gamesPlayed++
            important = true
        }
        if (run.finished) return emptyList()

        if (o.hpMax > 0) {
            if (o.classNum >= 0) run.classNum = o.classNum
            run.difficulty = o.difficulty
            if (o.levelKnown) {
                if (o.dlevel in 1..DUNGEON_BOTTOM) run.maxDungeon = maxOf(run.maxDungeon, o.dlevel)
                if (o.dlevel > DUNGEON_BOTTOM) run.maxVolcano = maxOf(run.maxVolcano, o.dlevel - DUNGEON_BOTTOM)
            }
            run.maxPlevel = maxOf(run.maxPlevel, o.plevel)
            run.maxGold = maxOf(run.maxGold, o.gold)
            // Turns and kills are the game's own counters: after resuming an
            // older checkpoint they go back, and the replayed part is not
            // counted twice.
            run.turns = o.turns
            run.kills = o.kills
            if (o.hp * 10L < o.hpMax) run.wasNearDeath = true
            else if (run.wasNearDeath && o.hp >= o.hpMax) run.recoveredFromBrink = true
            with(lifetime) {
                bestDungeon = maxOf(bestDungeon, run.maxDungeon)
                bestVolcano = maxOf(bestVolcano, run.maxVolcano)
                bestPlevel = maxOf(bestPlevel, run.maxPlevel)
                bestGold = maxOf(bestGold, run.maxGold)
            }
            dirty = true
        }

        when (o.endState) {
            END_DIED -> finish(run, Outcome.DIED, o.endReason, o.endScore, now)
            END_WON -> finish(run, Outcome.WON, o.endReason, o.endScore, now)
            END_QUIT -> finish(run, Outcome.QUIT, o.endReason, o.endScore, now)
        }
        return checkAchievements(run, now)
    }

    /** Wall-clock time spent playing the current run. */
    fun addPlayTime(ms: Long) {
        val run = current ?: return
        if (run.finished || ms <= 0) return
        run.playMs += ms
        dirty = true
    }

    /**
     * The game process ended without showing a final score. If it left no
     * save or checkpoint, the run can't be continued: the player quit.
     */
    fun onGameExit(resumable: Boolean, now: Long) {
        val run = current ?: return
        if (run.finished || resumable) return
        finish(run, Outcome.QUIT, "quit", 0, now)
    }

    private fun finish(run: RunRecord, outcome: Outcome, cause: String, score: Int, now: Long) {
        run.outcome = outcome
        run.cause = cause
        run.score = score
        run.endedAt = now
        with(lifetime) {
            when (outcome) {
                Outcome.DIED -> {
                    deaths++
                    val c = normalizeCause(cause)
                    deathCauses[c] = (deathCauses[c] ?: 0) + 1
                }
                Outcome.WON -> {
                    wins++
                    if (fastestWinTurns == 0 || run.turns < fastestWinTurns) fastestWinTurns = run.turns
                }
                else -> quits++
            }
            bestScore = maxOf(bestScore, score)
            totalKills += run.kills
            totalTurns += run.turns
            totalPlayMs += run.playMs
        }
        recent.add(0, run)
        while (recent.size > MAX_RECENT) recent.removeAt(recent.size - 1)
        dirty = true
        important = true
    }

    private fun checkAchievements(run: RunRecord, now: Long): List<Achievement> {
        var out: MutableList<Achievement>? = null
        for (a in Achievements.ALL) {
            if (a.id in unlocked) continue
            if (a.test(run, lifetime)) {
                unlocked[a.id] = now
                (out ?: ArrayList<Achievement>().also { out = it }).add(a)
            }
        }
        if (out != null) { dirty = true; important = true }
        return out ?: emptyList()
    }

    companion object {
        const val END_DIED = 1
        const val END_WON = 2
        const val END_QUIT = 3
        const val DUNGEON_BOTTOM = 15
        const val MAX_RECENT = 30

        private val ON_LEVEL = Regex("""\s+on\s+(H|\d+|V\d)$""")
        private val TAGS = Regex("""\s*\((wizard|cheater)\)""")

        /** "killed by a giant ant on 3" -> "killed by a giant ant". */
        fun normalizeCause(cause: String): String =
            cause.replace(TAGS, "").trim().replace(ON_LEVEL, "").trim().ifEmpty { "unknown" }

        /** Level index to the game's name for it: "Home", "3", "V2". */
        fun levelName(index: Int): String = when {
            index <= 0 -> "Home"
            index <= DUNGEON_BOTTOM -> "$index"
            else -> "V${index - DUNGEON_BOTTOM}"
        }
    }
}
