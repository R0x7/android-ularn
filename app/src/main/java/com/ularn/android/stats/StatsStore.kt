package com.ularn.android.stats

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Saves a [RunBook] as JSON (files/stats.json), written atomically. */
class StatsStore(private val file: File) {

    fun load(): RunBook {
        val book = RunBook()
        if (!file.exists()) return book
        try {
            val o = JSONObject(file.readText())
            o.optJSONObject("current")?.let { book.current = runFrom(it) }
            o.optJSONArray("parked")?.let { a ->
                for (i in 0 until a.length()) runFrom(a.getJSONObject(i)).let { book.parked[it.id] = it }
            }
            o.optJSONArray("recent")?.let { a -> for (i in 0 until a.length()) book.recent += runFrom(a.getJSONObject(i)) }
            o.optJSONObject("lifetime")?.let { l ->
                with(book.lifetime) {
                    gamesPlayed = l.optInt("gamesPlayed"); wins = l.optInt("wins")
                    deaths = l.optInt("deaths"); quits = l.optInt("quits")
                    bestDungeon = l.optInt("bestDungeon"); bestVolcano = l.optInt("bestVolcano")
                    bestPlevel = l.optInt("bestPlevel"); bestGold = l.optInt("bestGold")
                    bestScore = l.optInt("bestScore")
                    totalKills = l.optLong("totalKills"); totalTurns = l.optLong("totalTurns")
                    totalPlayMs = l.optLong("totalPlayMs"); fastestWinTurns = l.optInt("fastestWinTurns")
                    l.optJSONObject("deathCauses")?.let { d -> for (k in d.keys()) deathCauses[k] = d.optInt(k) }
                }
            }
            o.optJSONObject("achievements")?.let { a -> for (k in a.keys()) book.unlocked[k] = a.optLong(k) }
        } catch (e: Exception) {
            Log.w(TAG, "couldn't read ${file.name}", e)
        }
        return book
    }

    fun save(book: RunBook) {
        try {
            val o = JSONObject()
            book.current?.let { o.put("current", runTo(it)) }
            o.put("parked", JSONArray().apply { book.parked.values.forEach { put(runTo(it)) } })
            o.put("recent", JSONArray().apply { book.recent.forEach { put(runTo(it)) } })
            o.put("lifetime", JSONObject().apply {
                with(book.lifetime) {
                    put("gamesPlayed", gamesPlayed); put("wins", wins); put("deaths", deaths); put("quits", quits)
                    put("bestDungeon", bestDungeon); put("bestVolcano", bestVolcano)
                    put("bestPlevel", bestPlevel); put("bestGold", bestGold); put("bestScore", bestScore)
                    put("totalKills", totalKills); put("totalTurns", totalTurns)
                    put("totalPlayMs", totalPlayMs); put("fastestWinTurns", fastestWinTurns)
                    put("deathCauses", JSONObject().apply { deathCauses.forEach { (k, v) -> put(k, v) } })
                }
            })
            o.put("achievements", JSONObject().apply { book.unlocked.forEach { (k, v) -> put(k, v) } })
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
            book.dirty = false
            book.important = false
        } catch (e: Exception) {
            Log.w(TAG, "couldn't write ${file.name}", e)
        }
    }

    private fun runTo(r: RunRecord) = JSONObject().apply {
        put("id", r.id); put("startedAt", r.startedAt); put("endedAt", r.endedAt)
        put("class", r.classNum); put("difficulty", r.difficulty)
        put("maxDungeon", r.maxDungeon); put("maxVolcano", r.maxVolcano)
        put("maxPlevel", r.maxPlevel); put("maxGold", r.maxGold)
        put("turns", r.turns); put("kills", r.kills); put("playMs", r.playMs)
        put("outcome", r.outcome.name); put("cause", r.cause); put("score", r.score)
        put("nearDeath", r.wasNearDeath); put("brink", r.recoveredFromBrink)
    }

    private fun runFrom(o: JSONObject) = RunRecord(o.getInt("id"), o.optLong("startedAt")).apply {
        endedAt = o.optLong("endedAt"); classNum = o.optInt("class", -1); difficulty = o.optInt("difficulty")
        maxDungeon = o.optInt("maxDungeon"); maxVolcano = o.optInt("maxVolcano")
        maxPlevel = o.optInt("maxPlevel"); maxGold = o.optInt("maxGold")
        turns = o.optInt("turns"); kills = o.optInt("kills"); playMs = o.optLong("playMs")
        outcome = Outcome.of(o.optString("outcome")); cause = o.optString("cause"); score = o.optInt("score")
        wasNearDeath = o.optBoolean("nearDeath"); recoveredFromBrink = o.optBoolean("brink")
    }

    companion object {
        private const val TAG = "StatsStore"
    }
}
