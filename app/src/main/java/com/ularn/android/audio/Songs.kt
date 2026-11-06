package com.ularn.android.audio

import com.ularn.android.audio.Wave.NOISE
import com.ularn.android.audio.Wave.PULSE12
import com.ularn.android.audio.Wave.PULSE25
import com.ularn.android.audio.Wave.PULSE50
import com.ularn.android.audio.Wave.TRIANGLE

/**
 * Dungeon Radio's track list (notation: see Song.kt). One bar = 16 steps.
 * Every voice of a looping song is a whole number of its loops long, so the
 * parts stay in step (SongTest checks it).
 */
object Songs {
    val TOWN = Song("Town", 132, listOf(
        v(PULSE25, "l2 e5 g5 c6 g5 e5 g5 c6:4 | d5 f5 a5 f5 d5 f5 a5:4 | e5 g5 c6 g5 f5 a5 c6 a5 | g5:4 b5:2 d6:2 c6:8", 0.8f, Adsr.PLUCK),
        v(PULSE12, "l4 c5 e5 c5 e5 | c5 f5 c5 f5 | c5 e5 c5 f5 | b4 d5 c5:8", 0.35f, Adsr.PAD),
        v(TRIANGLE, "l2 [c3 g3]4 | [f2 c3]4 | [c3 g3]2 [f2 c3]2 | [g2 d3]2 c3:4 g2:4", 1f, Adsr.BASS),
        v(NOISE, "k:2 h:2 s:2 h:2 k:2 k:2 s:2 h:2", 0.5f),
    ))

    val SHALLOWS = Song("Shallows", 110, listOf(
        v(PULSE50, "l4 a4 c5 e5 d5 | c5:6 b4:2 a4:8 | f4 a4 c5 b4 | g#4:12 -:4", 0.55f),
        v(PULSE12, "l1 [a4 c5 e5 c5]8 | [f4 a4 c5 a4]4 | [e4 g#4 b4 g#4]4", 0.25f, Adsr.PLUCK),
        v(TRIANGLE, "l2 [a2 e3]8 | [f2 c3]4 | [e2 b2]4", 1f, Adsr.BASS),
        v(NOISE, "h:4 h:4 s:4 h:4", 0.35f),
    ))

    val DEPTHS = Song("Depths", 80, listOf(
        v(PULSE50, "l8 d5 f5 | e5:4 c5:4 a4:8 | bb4 a4 | g4:12 a4:4 | d5 f5 | a5:4 g5:4 f5:8 | e5:4 f5:4 d5:8 | c#5:16", 0.5f, Adsr.PAD),
        v(PULSE12, "l4 [d4 f4 a4 f4]2 | [bb3 d4 f4 d4]2 | [d4 f4 a4 f4]2 | g3 bb3 d4 bb3 a3 c#4 e4 c#4", 0.25f, Adsr.PAD),
        v(TRIANGLE, "l16 d3 a2 bb2 g2 d3 f2 g2 a2", 1f, Adsr.BASS),
        v(NOISE, "k:8 -:4 h:4", 0.45f),
    ))

    val VOLCANO = Song("Volcano", 168, listOf(
        v(PULSE25, "l1 [ [e5 f5 e5 bb4]2 [e5 f5 g5 f5]2 | [e5 f5 e5 bb4]2 b4:2 c5:2 bb4:4 ]2", 0.6f, Adsr.PLUCK),
        v(PULSE12, "l4 [e4 bb4 f4 b4]4", 0.3f),
        v(TRIANGLE, "l2 [e2 e2 e3 e2 f2 e2 bb2 e2]4", 1f, Adsr.BASS),
        v(NOISE, "k:2 h:1 h:1 s:2 h:1 k:1 k:2 h:2 s:2 h:2", 0.5f),
    ))

    val SHOP = Song("Shop", 144, listOf(
        v(PULSE25, "l2 f5 - a5 f5 c6:4 a5:2 f5:2 | g5 - bb5 g5 d6:4 bb5:2 g5:2 | a5 c6 a5 f5 g5 bb5 g5 e5 | f5:4 c5:4 f5:8", 0.7f, Adsr.PLUCK),
        v(TRIANGLE, "l4 f2 c3 f2 c3 | g2 d3 c3 e3 | f2 a2 c3 c2 | f2 c3 f2:8", 1f, Adsr.BASS),
        v(NOISE, "k:4 s:4 k:2 k:2 s:4", 0.45f),
    ))

    val GAME_OVER = Song("Game Over", 100, listOf(
        v(PULSE50, "l4 g4 f#4 f4 e4:12", 0.6f),
        v(TRIANGLE, "l4 c3 b2 bb2 a2:12", 1f, Adsr.BASS),
    ), loop = false)

    val VICTORY = Song("Victory", 150, listOf(
        v(PULSE25, "l2 c5 e5 g5 c6 - g5 c6:12", 0.7f),
        v(PULSE12, "l12 e5 e5", 0.35f, Adsr.PAD),
        v(TRIANGLE, "l4 c3 g3 c3 c4:12", 1f, Adsr.BASS),
        v(NOISE, "l2 k h s h k h s:12", 0.4f),
    ), loop = false)

    // ---- bonus tracks, unlocked by cassette tapes ----

    val ELEVATOR = Song("Elevator Music", 100, listOf(
        v(PULSE12, "l2 d5 - f5 d5 - c5 bb4:4 | c5 - eb5 c5 - bb4 a4:4 | bb4 d5 f5 a5 g5:4 f5:4 | eb5:4 d5:4 c5:8", 0.6f),
        v(PULSE50, "l4 - d4 - f4 | - eb4 - a4 | - g4 - g4 | - a4 - a4", 0.2f, Adsr.PLUCK),
        v(TRIANGLE, "l2 bb2 - f2 bb2 - f2 bb2 f2 | f2 - c3 f2 - c3 f2 c3 | eb2 - bb2 eb2 - bb2 eb2 bb2 | f2 - c3 f2 f2:8", 1f, Adsr.BASS),
        v(NOISE, "h:2 h:1 h:1 s:2 h:2 h:2 h:1 h:1 s:2 h:2", 0.3f),
    ))

    val SURF = Song("Surf's Up", 176, listOf(
        v(PULSE25, "l1 [e5 e5 e5 e5 d#5 d#5 d#5 d#5]2 [g5 g5 g5 g5 f#5 f#5 f#5 f#5]2 [a5 a5 a5 a5 g5 g5 g5 g5]2 b5:8 -:8", 0.6f, Adsr.PLUCK),
        v(TRIANGLE, "l2 [e2 e2 b2 e3]4 [a2 a2 e3 a3]2 [b2 b2 f#3 b3]2", 1f, Adsr.BASS),
        v(NOISE, "l2 k h s h k k s h", 0.5f),
    ))

    val BOSS = Song("Boss Fight", 150, listOf(
        v(PULSE50, "l2 c5 c5 eb5 c5 f#5:4 g5:4 | c5 c5 eb5 c5 b4:4 ab4:4 | c6 b5 bb5 a5 ab5 g5 f#5 f5 | g5:8 g4:8", 0.6f),
        v(TRIANGLE, "l1 [c3 c3 c4 c3]8 | [ab2 ab2 ab3 ab2]4 | [g2 g2 g3 g2]4", 1f, Adsr.BASS),
        v(NOISE, "l1 k h s h k k s h k h s h k s s s", 0.5f),
    ))

    /** Cassette tracks, in the order of the C side's track index (compat/wild_radio.c). */
    val BONUS = listOf(ELEVATOR, SURF, BOSS)
    const val BONUS_ELEVATOR = 0
    const val BONUS_SURF = 1
    const val BONUS_BOSS = 2

    /** Tracks the location picks (and the radio's "next track" cycles through). */
    val REGULAR = listOf(TOWN, SHALLOWS, DEPTHS, VOLCANO, SHOP)

    val ALL = REGULAR + listOf(GAME_OVER, VICTORY) + BONUS
}
