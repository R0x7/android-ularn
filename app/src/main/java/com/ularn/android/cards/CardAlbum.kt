package com.ularn.android.cards

/**
 * The card collection, kept across all runs: the bookkeeping only, no
 * Android classes (unit-tested on the JVM). [CardStore] persists it and
 * [CardDrops] feeds it from the game's US_EV_WILD_CARD events.
 */
class CardEntry(
    val monst: Int,
    /** Copies found, holo ones included. */
    var count: Int = 0,
    /** How many of [count] were holo. */
    var holoCount: Int = 0,
    /** Run id (GameState.runId) of the run that found the first copy; 0 if unknown. */
    val firstSeenRun: Int = 0,
    /** Wall-clock time of the first copy (ms), 0 if unknown. */
    val firstSeenAt: Long = 0L,
) {
    val holo get() = holoCount > 0
}

class CardAlbum {
    /** What one drop did to the album (for the "New card" popup). */
    data class Drop(val monst: Int, val holo: Boolean, val newCard: Boolean, val newHolo: Boolean, val count: Int)

    private val cards = sortedMapOf<Int, CardEntry>()

    val entries: Collection<CardEntry> get() = cards.values
    /** Different cards owned. */
    val collected get() = cards.size
    val holoCollected get() = cards.values.count { it.holo }
    val complete get() = collected >= MonsterFacts.COUNT

    operator fun get(monst: Int): CardEntry? = cards[monst]
    fun owns(monst: Int) = cards.containsKey(monst)

    /** Records a dropped card; null (and no change) for an id that isn't a card. */
    fun add(monst: Int, holo: Boolean, runId: Int, now: Long): Drop? {
        if (!MonsterFacts.isCard(monst)) return null
        val old = cards[monst]
        val e = old ?: CardEntry(monst, firstSeenRun = runId, firstSeenAt = now).also { cards[monst] = it }
        val newHolo = holo && e.holoCount == 0
        e.count++
        if (holo) e.holoCount++
        return Drop(monst, holo, newCard = old == null, newHolo = newHolo, count = e.count)
    }

    /** Card ids, owned or not, of [rarity] (null = all), in monster order. */
    fun ids(rarity: Rarity?): List<Int> =
        (1..MonsterFacts.COUNT).filter { rarity == null || MonsterFacts.rarityOf(it) == rarity }

    // ---- serialisation: a small JSON array, written and read by hand so the
    // logic (and its test) needs no Android org.json.

    fun toJson(): String = cards.values.joinToString(",", "[", "]") {
        "{\"m\":${it.monst},\"n\":${it.count},\"h\":${it.holoCount},\"r\":${it.firstSeenRun},\"t\":${it.firstSeenAt}}"
    }

    companion object {
        private val OBJECT = Regex("\\{([^{}]*)\\}")
        private val FIELD = Regex("\"(\\w+)\"\\s*:\\s*(-?\\d+)")

        /** Parses [toJson]'s output; anything unreadable is skipped, never thrown. */
        fun fromJson(json: String?): CardAlbum {
            val album = CardAlbum()
            if (json.isNullOrBlank()) return album
            for (obj in OBJECT.findAll(json)) {
                val f = HashMap<String, Long>()
                for (m in FIELD.findAll(obj.groupValues[1])) m.groupValues[2].toLongOrNull()?.let { f[m.groupValues[1]] = it }
                val monst = f["m"]?.toInt() ?: continue
                if (!MonsterFacts.isCard(monst)) continue
                val count = (f["n"] ?: 1L).toInt().coerceAtLeast(1)
                val e = CardEntry(
                    monst,
                    count = count,
                    holoCount = (f["h"] ?: 0L).toInt().coerceIn(0, count),
                    firstSeenRun = (f["r"] ?: 0L).toInt(),
                    firstSeenAt = f["t"] ?: 0L,
                )
                album.cards[monst] = e
            }
            return album
        }
    }
}
