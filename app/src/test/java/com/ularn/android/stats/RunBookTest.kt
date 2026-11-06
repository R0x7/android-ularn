package com.ularn.android.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunBookTest {

    private fun obs(
        runId: Int = 7, hp: Int = 20, hpMax: Int = 20, dlevel: Int = 0, plevel: Int = 1,
        gold: Int = 0, turns: Int = 0, kills: Int = 0, endState: Int = 0, endScore: Int = 0,
        endReason: String = "", levelKnown: Boolean = true, difficulty: Int = 0,
    ) = Observation(runId, hp, hpMax, dlevel, levelKnown, plevel, gold, turns, kills, 3, difficulty, endState, endScore, endReason)

    @Test fun ignoresNoCharacter() {
        val b = RunBook()
        b.observe(obs(runId = 0), 1)
        assertNull(b.current)
        assertEquals(0, b.lifetime.gamesPlayed)
    }

    @Test fun tracksMaximaAndCountsRunOnce() {
        val b = RunBook()
        b.observe(obs(dlevel = 3, plevel = 4, gold = 500, turns = 100, kills = 5), 1)
        b.observe(obs(dlevel = 1, plevel = 4, gold = 50, turns = 120, kills = 6), 2)
        b.observe(obs(dlevel = 17, plevel = 5, gold = 60, turns = 130, kills = 6), 3)
        val r = b.current!!
        assertEquals(1, b.lifetime.gamesPlayed)
        assertEquals(3, r.maxDungeon)
        assertEquals(2, r.maxVolcano)
        assertEquals(5, r.maxPlevel)
        assertEquals(500, r.maxGold)
        assertEquals(130, r.turns)
        assertEquals(6, r.kills)
        assertEquals("Elf", r.className)
    }

    @Test fun resumedCheckpointDoesNotDoubleCount() {
        val b = RunBook()
        b.observe(obs(turns = 500, kills = 10), 1)
        // process killed, checkpoint from earlier restored: same run id, counters go back
        b.observe(obs(turns = 450, kills = 8), 2)
        b.observe(obs(turns = 520, kills = 11), 3)
        assertEquals(1, b.lifetime.gamesPlayed)
        assertEquals(11, b.current!!.kills)
        assertEquals(520, b.current!!.turns)
    }

    @Test fun unknownLevelIgnored() {
        val b = RunBook()
        b.observe(obs(dlevel = 9, levelKnown = false), 1)
        assertEquals(0, b.current!!.maxDungeon)
    }

    @Test fun deathFinishesRunOnce() {
        val b = RunBook()
        b.observe(obs(dlevel = 2), 1)
        b.observe(obs(hp = 0, endState = 1, endScore = 1234, endReason = "killed by a giant ant on 2"), 2)
        b.observe(obs(hp = 0, endState = 1, endScore = 1234, endReason = "killed by a giant ant on 2"), 3)
        b.onGameExit(resumable = false, now = 4)
        assertEquals(1, b.lifetime.deaths)
        assertEquals(0, b.lifetime.quits)
        assertEquals(1234, b.lifetime.bestScore)
        assertEquals(1, b.recent.size)
        assertEquals(Outcome.DIED, b.recent[0].outcome)
        assertEquals(mapOf("killed by a giant ant" to 1), b.lifetime.deathCauses.toMap())
    }

    @Test fun exitWithoutSaveIsQuitButSaveKeepsRunGoing() {
        val b = RunBook()
        b.observe(obs(), 1)
        b.onGameExit(resumable = true, now = 2)
        assertFalse(b.current!!.finished)
        b.observe(obs(turns = 10), 3)
        b.onGameExit(resumable = false, now = 4)
        assertEquals(Outcome.QUIT, b.current!!.outcome)
        assertEquals(1, b.lifetime.quits)
        assertEquals(1, b.lifetime.gamesPlayed)
    }

    @Test fun switchingGamesParksTheOtherRun() {
        val b = RunBook()
        b.observe(obs(runId = 1, kills = 3), 1)
        b.observe(obs(runId = 2), 2)
        assertEquals(2, b.lifetime.gamesPlayed)
        assertEquals(0, b.lifetime.quits)
        assertEquals(2, b.current!!.id)
        // back to the first slot: same run, not a new game
        b.observe(obs(runId = 1, kills = 4), 3)
        assertEquals(1, b.current!!.id)
        assertEquals(4, b.current!!.kills)
        assertEquals(2, b.lifetime.gamesPlayed)
        assertTrue(2 in b.parked)
    }

    @Test fun tooManyUnfinishedRunsAbandonsTheOldest() {
        val b = RunBook()
        for (id in 1..RunBook.MAX_PARKED + 2) b.observe(obs(runId = id), id.toLong())
        assertEquals(RunBook.MAX_PARKED, b.parked.size)
        assertEquals(1, b.lifetime.quits)
        assertEquals("abandoned", b.recent[0].cause)
        assertEquals(1, b.recent[0].id)
        // a finished run never restarts
        b.observe(obs(runId = 1), 99)
        assertEquals(RunBook.MAX_PARKED + 2, b.current!!.id)
    }

    @Test fun achievementsUnlockOnce() {
        val b = RunBook()
        val first = b.observe(obs(kills = 1, dlevel = 5), 1).map { it.id }
        assertTrue("first_blood" in first)
        assertTrue("delver" in first)
        assertFalse("deep_delver" in first)
        val again = b.observe(obs(kills = 2, dlevel = 5), 2)
        assertTrue(again.isEmpty())
        assertEquals(1L, b.unlocked["first_blood"])
    }

    @Test fun winAchievements() {
        val b = RunBook()
        b.observe(obs(turns = 15000, difficulty = 1), 1)
        val got = b.observe(obs(turns = 15000, difficulty = 1, endState = 2, endScore = 99999, endReason = "a winner"), 2).map { it.id }
        assertTrue("saviour" in got)
        assertTrue("against_clock" in got)
        assertTrue("glutton" in got)
        assertEquals(1, b.lifetime.wins)
        assertEquals(15000, b.lifetime.fastestWinTurns)
    }

    @Test fun slowWinIsNotFast() {
        val b = RunBook()
        val got = b.observe(obs(turns = 30000, endState = 2, endReason = "a winner"), 1).map { it.id }
        assertTrue("saviour" in got)
        assertFalse("against_clock" in got)
    }

    @Test fun brinkNeedsFullRecovery() {
        val b = RunBook()
        b.observe(obs(hp = 100, hpMax = 100), 1)
        b.observe(obs(hp = 9, hpMax = 100), 2)
        assertTrue(b.observe(obs(hp = 99, hpMax = 100), 3).none { it.id == "brink" })
        assertTrue(b.observe(obs(hp = 100, hpMax = 100), 4).any { it.id == "brink" })
    }

    @Test fun persistentCountsGames() {
        val b = RunBook()
        for (i in 1..9) b.observe(obs(runId = i), i.toLong())
        assertFalse("persistent" in b.unlocked)
        b.observe(obs(runId = 10), 10)
        assertTrue("persistent" in b.unlocked)
    }

    @Test fun playTimeOnlyForRunningRun() {
        val b = RunBook()
        b.addPlayTime(1000)
        b.observe(obs(), 1)
        b.addPlayTime(1000)
        b.observe(obs(endState = 1, endReason = "x"), 2)
        b.addPlayTime(1000)
        assertEquals(1000, b.current!!.playMs)
        assertEquals(1000, b.lifetime.totalPlayMs)
    }

    @Test fun normalizeCause() {
        assertEquals("killed by a giant ant", RunBook.normalizeCause("killed by a giant ant on 3"))
        assertEquals("fell into a pit", RunBook.normalizeCause("fell into a pit on V2"))
        assertEquals("killed by a bat", RunBook.normalizeCause("killed by a bat on H (wizard)"))
        assertEquals("unknown", RunBook.normalizeCause(""))
    }

    @Test fun recentIsCapped() {
        val b = RunBook()
        for (i in 1..(RunBook.MAX_RECENT + 5)) {
            b.observe(obs(runId = i), i.toLong())
            b.observe(obs(runId = i, endState = 1, endReason = "x"), i.toLong())
        }
        assertEquals(RunBook.MAX_RECENT, b.recent.size)
        assertEquals(RunBook.MAX_RECENT + 5, b.lifetime.deaths)
    }
}
