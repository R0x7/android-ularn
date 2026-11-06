package com.ularn.android.cards

/** Card rarity, from the monster's level and kind. Frame colours are ARGB. */
enum class Rarity(val label: String, val frame: Int, val frameLight: Int, val frameDark: Int) {
    COMMON("Common", 0xFF8C8C8C.toInt(), 0xFFC8C8C8.toInt(), 0xFF4A4A4A.toInt()),
    UNCOMMON("Uncommon", 0xFF4E9A3C.toInt(), 0xFF8FD46E.toInt(), 0xFF24501A.toInt()),
    RARE("Rare", 0xFF3D6FC4.toInt(), 0xFF86AEF0.toInt(), 0xFF1C3566.toInt()),
    EPIC("Epic", 0xFF8E44C0.toInt(), 0xFFC98AF0.toInt(), 0xFF46205E.toInt()),
    LEGENDARY("Legendary", 0xFFD98A1E.toInt(), 0xFFFFD36A.toInt(), 0xFF6E3F08.toInt()),
}

/** One monster as a card shows it. */
class MonsterFact(
    val id: Int,
    val name: String,
    val level: Int,
    val ac: Int,
    val dam: Int,
    val hp: Int,
    val exp: Int,
    /** Tile id in ularn_tiles.png. */
    val tile: Int,
    val lore: String,
) {
    val rarity: Rarity get() = MonsterFacts.rarityOf(id)
    /** "gnome king" -> "Gnome King" (and "God of Hellfire" stays as it is). */
    val title: String get() = name.split(' ').joinToString(" ") { w ->
        if (w == "of" || w.all { it.isUpperCase() }) w else w.replaceFirstChar { it.uppercaseChar() }
    }
}

/**
 * The monster table, card edition.
 *
 * Mirrors `monster[]` (name, LV, AC, DAM, HP, EXP) and `monsttilelist[]` in
 * app/src/main/cpp/ularn/monster.c, in MonsterIdType order (monster.h). Keep
 * the two in step when monsters change: the cards only show these numbers,
 * the game never reads them.
 *
 * Index 0 ("unseen attacker") is not a card; ids 1 until [COUNT] are.
 */
object MonsterFacts {
    // Ids we need by name (MonsterIdType in monster.h).
    const val WHITEDRAGON = 25
    const val GNOMEKING = 45
    const val WATERLORD = 47
    const val BRONZEDRAGON = 48
    const val GREENDRAGON = 49
    const val SILVERDRAGON = 53
    const val PLATINUMDRAGON = 54
    const val REDDRAGON = 56
    const val NULLPOINTER = 57
    const val FOUNTAINKRAKEN = 58
    const val TAXAUDITOR = 59
    const val ROOMBA = 60
    const val DEMONLORD = 61
    const val LUCIFER = 69

    private fun m(id: Int, name: String, lv: Int, ac: Int, dam: Int, hp: Int, exp: Int, lore: String) =
        MonsterFact(id, name, lv, ac, dam, hp, exp, tileOf(id), lore)

    /** monsttilelist[]: ordinary monsters use tiles 0..55, Wild Edition ones 384..387, demons 56..63. */
    fun tileOf(id: Int): Int = when {
        id in 1..REDDRAGON -> id - 1
        id in NULLPOINTER..ROOMBA -> 384 + (id - NULLPOINTER)
        id == DEMONLORD -> 56                       // upstream: types I and II share a tile
        id in DEMONLORD + 1..LUCIFER -> id - DEMONLORD + 55
        else -> 0
    }

    //            id  name                  LV    AC  DAM    HP      EXP
    val ALL: List<MonsterFact> = listOf(
        m(0, "unseen attacker", 0, 0, 0, 0, 0, ""),
        m(1, "lemming", 1, 0, 0, 0, 1, "Followed the crowd. The crowd was going to the volcano."),
        m(2, "gnome", 1, 10, 1, 2, 2, "Short, furious, and 30 gold richer than you."),
        m(3, "hobgoblin", 1, 13, 2, 3, 2, "A goblin with a hobby. The hobby is stabbing."),
        m(4, "jackal", 1, 7, 1, 1, 1, "Laughs at your armour class. Out loud."),
        m(5, "kobold", 1, 15, 1, 1, 1, "AC 15, HP 1. All shell, no snail."),
        m(6, "orc", 2, 15, 3, 5, 2, "Brutal, cunning, and a famously bad speller."),
        m(7, "snake", 2, 10, 1, 3, 1, "No arms, no legs, no regrets."),
        m(8, "giant centipede", 2, 13, 1, 2, 2, "A hundred legs and every one of them kicks."),
        m(9, "jaculi", 2, 9, 1, 2, 1, "A snake that throws itself like a javelin. Evolution had a weird day."),
        m(10, "troglodyte", 2, 10, 2, 5, 3, "Smells like a cave because it mostly is one."),
        m(11, "giant ant", 2, 8, 1, 5, 4, "Lifts fifty times its weight. You weigh less."),
        m(12, "floating eye", 3, 8, 2, 7, 2, "Don't hit it. Don't look at it. Don't even think about it."),
        m(13, "leprechaun", 3, 3, 0, 15, 40, "Your gold called. It's somewhere else now."),
        m(14, "nymph", 3, 3, 0, 20, 40, "Stole your heart. Also your sword. Mostly the sword."),
        m(15, "quasit", 3, 5, 3, 14, 10, "A demon's intern. Unpaid, therefore bitter."),
        m(16, "rust monster", 3, 5, 0, 18, 20, "Thinks your plate mail is a crunchy snack."),
        m(17, "zombie", 3, 12, 3, 9, 7, "Just wants a hug. A very bitey hug."),
        m(18, "assassin bug", 4, 4, 3, 23, 13, "Licensed, bonded, and very, very small."),
        m(19, "bugbear", 4, 5, 4, 24, 33, "Neither a bug nor a bear. Fully a lawsuit."),
        m(20, "hell hound", 4, 5, 2, 20, 33, "Good boy! Fetch! No - drop the fireball!"),
        m(21, "ice lizard", 4, 11, 3, 19, 23, "Cold-blooded, cold-hearted, and frankly just cold."),
        m(22, "centaur", 4, 6, 4, 25, 43, "Half man, half horse, all opinions about archery."),
        m(23, "troll", 5, 9, 5, 55, 250, "Regenerates. Bring fire, or a very long afternoon."),
        m(24, "yeti", 5, 8, 4, 45, 90, "Abominable, but has an excellent skincare routine."),
        m(WHITEDRAGON, "white dragon", 5, 4, 5, 65, 1000, "The weakest dragon. Which is still a dragon."),
        m(26, "elf", 5, 3, 3, 25, 33, "Lives 800 years, mostly to be smug for longer."),
        m(27, "gelatinous cube", 5, 9, 3, 24, 43, "Contents: one boot, two coins, the last adventurer."),
        m(28, "metamorph", 6, 9, 3, 32, 40, "Could be anything it wants. Chose 'a problem'."),
        m(29, "vortex", 6, 5, 4, 33, 53, "Spin class from the very worst gym."),
        m(30, "ziller", 6, 15, 3, 34, 33, "Nobody knows what a ziller is. Including the ziller."),
        m(31, "violet fungus", 6, 12, 3, 39, 90, "A mushroom that hugs back. Far too hard."),
        m(32, "wraith", 6, 3, 1, 36, 300, "Drains levels. Like a tax, only spookier."),
        m(33, "forvalaka", 6, 3, 5, 55, 270, "A were-leopard. Spot it before it spots you."),
        m(34, "lama nobe", 7, 14, 7, 36, 70, "Not a llama. Never call it a llama. It knows."),
        m(35, "osequip", 7, 4, 7, 36, 90, "Six legs, one appetite, zero respect for ankles."),
        m(36, "rothe", 7, 15, 5, 53, 230, "A musk ox with a grudge and a hundred gold in its pocket."),
        m(37, "xorn", 7, 6, 7, 63, 290, "Walks through walls, eats gems, ignores your feelings."),
        m(38, "vampire", 7, 5, 4, 55, 950, "Has never seen a mirror. Assumes it looks fabulous."),
        m(39, "invisible stalker", 7, 5, 6, 55, 330, "This card is blank. Don't worry about it."),
        m(40, "poltergeist", 8, 1, 8, 55, 430, "Slams doors, rattles chains, never pays rent."),
        m(41, "disenchantress", 8, 3, 1, 57, 500, "Your +3 sword is now a +0 stick. Magic!"),
        m(42, "shambling mound", 8, 13, 5, 47, 390, "A compost heap with ambitions."),
        m(43, "yellow mold", 8, 12, 4, 37, 240, "Do not lick. We mean it this time."),
        m(44, "umber hulk", 8, 6, 7, 67, 600, "Its gaze confuses you. So does its name."),
        m(GNOMEKING, "gnome king", 9, -1, 10, 120, 3000, "The crown is real. The 2000 gold is realer."),
        m(46, "mimic", 9, 9, 7, 57, 100, "Are you sure this is a card? Count your fingers."),
        m(WATERLORD, "water lord", 9, -10, 15, 155, 15000, "Owns every fountain in the dungeon. Charges by the sip."),
        m(BRONZEDRAGON, "bronze dragon", 9, 5, 9, 90, 4000, "Third place in the dragon pageant, still furious."),
        m(GREENDRAGON, "green dragon", 9, 4, 4, 80, 2500, "Breath like a swamp. Manners like one too."),
        m(50, "purple worm", 9, -1, 13, 130, 15000, "Swallows adventurers whole. Chews later, if ever."),
        m(51, "xvart", 9, -2, 14, 100, 1000, "Blue, three feet tall, and why your armour exists."),
        m(52, "spirit naga", 10, -20, 15, 100, 20000, "Half snake, half spirit, entirely out of your league."),
        m(SILVERDRAGON, "silver dragon", 10, -4, 10, 110, 10000, "Polished, polite, and will absolutely eat you."),
        m(PLATINUMDRAGON, "platinum dragon", 10, -7, 15, 150, 25000, "Premium dragon. Premium pain. No refunds."),
        m(55, "green urchin", 10, -5, 12, 95, 5000, "A sea urchin a long way from the sea, and bitter about it."),
        m(REDDRAGON, "red dragon", 10, -4, 13, 120, 14000, "Fire, gold, and a strict no-trespassing policy."),
        m(NULLPOINTER, "null pointer", 8, 2, 7, 60, 400, "Points at nothing. Hurts like everything. (core dumped)"),
        m(FOUNTAINKRAKEN, "fountain kraken", 7, 4, 6, 70, 300, "You wished for a fish. The fountain heard 'kraken'."),
        m(TAXAUDITOR, "tax auditor", 6, 6, 0, 40, 50, "Has a few questions about your LarnCoin. Has all day."),
        m(ROOMBA, "roomba", 4, 8, 1, 25, 30, "It has mapped the dungeon. It has mapped you."),
        m(DEMONLORD, "type I demon lord", 12, -40, 20, 150, 50000, "Entry-level demon. Still has a corner office in hell."),
        m(62, "type II demon lord", 13, -45, 25, 200, 75000, "Twice the demon, half the patience."),
        m(63, "type III demon lord", 14, -50, 30, 250, 100000, "Third of its name, first in line to roast you."),
        m(64, "type IV demon lord", 15, -55, 35, 300, 125000, "Middle management of the underworld. Loves meetings."),
        m(65, "type V demon lord", 16, -60, 40, 350, 150000, "Five ranks of evil and a really nice cape."),
        m(66, "type VI demon lord", 17, -65, 45, 400, 175000, "Has filed a complaint about Type VII's parking."),
        m(67, "type VII demon lord", 18, -70, 50, 450, 200000, "Seventh circle, seventh lord, zero chill."),
        m(68, "demon prince", 19, -90, 80, 1000, 500000, "Heir to the throne of hell, and getting impatient."),
        m(LUCIFER, "God of Hellfire", 20, -120, 100, 32767, 1000000, "The last boss. The last card. Frame it, then run."),
    )

    /** Number of collectible cards (ids 1 until COUNT): 69. */
    val COUNT = ALL.size - 1

    fun isCard(id: Int) = id in 1..COUNT
    fun get(id: Int): MonsterFact? = if (isCard(id)) ALL[id] else null

    private val DRAGONS = setOf(WHITEDRAGON, BRONZEDRAGON, GREENDRAGON, SILVERDRAGON, PLATINUMDRAGON, REDDRAGON)
    /** One-of-a-kind characters: the bosses of the dungeon and the Wild Edition oddities (never randomly generated). */
    private val UNIQUES = setOf(GNOMEKING, WATERLORD, NULLPOINTER, FOUNTAINKRAKEN, TAXAUDITOR, ROOMBA)

    /**
     * Common = level 1-3, uncommon 4-6, rare 7 and up; dragons and uniques
     * are epic and the demons legendary, whatever their level.
     */
    fun rarityOf(id: Int): Rarity {
        if (id in DEMONLORD..LUCIFER) return Rarity.LEGENDARY
        if (id in DRAGONS || id in UNIQUES) return Rarity.EPIC
        val lv = ALL.getOrNull(id)?.level ?: return Rarity.COMMON
        return when {
            lv <= 3 -> Rarity.COMMON
            lv <= 6 -> Rarity.UNCOMMON
            else -> Rarity.RARE
        }
    }
}
