package com.ularn.android.audio

import kotlin.math.pow

/*
 * Dungeon Radio (Wild Edition): songs and their tiny text notation.
 * Pure Kotlin, no Android classes, so the note maths and the parser are
 * unit-tested on the JVM (SongTest).
 *
 * Pattern notation, whitespace separated, one step = a sixteenth note:
 *   c4  f#3  bb2      a note (letter, any number of # or b, octave 0..8)
 *   c4:6              ... lasting 6 steps (default: the current length)
 *   -  or  r          a rest
 *   k  s  h           kick, snare, hi-hat (meant for the noise voice)
 *   l4                set the current length to 4 steps (no sound)
 *   [ ... ]3          play the group 3 times ("]" alone = twice); nests
 *   |                 bar line, ignored (for the reader)
 */

/** Waveforms of the NES-style voices. */
enum class Wave { PULSE12, PULSE25, PULSE50, TRIANGLE, NOISE }

object Notes {
    const val REST = -1
    const val KICK = -2
    const val SNARE = -3
    const val HAT = -4

    /** Equal temperament, A4 (MIDI 69) = 440 Hz. */
    fun freq(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    /** "C4", "f#3", "Bb2" to a MIDI number (C4 = 60); null if it isn't a note. */
    fun midi(name: String): Int? {
        if (name.length < 2) return null
        val base = when (name[0].lowercaseChar()) {
            'c' -> 0; 'd' -> 2; 'e' -> 4; 'f' -> 5; 'g' -> 7; 'a' -> 9; 'b' -> 11
            else -> return null
        }
        var i = 1
        var acc = 0
        while (i < name.length && (name[i] == '#' || name[i] == 'b')) {
            acc += if (name[i] == '#') 1 else -1
            i++
        }
        val oct = name.substring(i).toIntOrNull() ?: return null
        if (oct !in 0..8) return null
        return 12 * (oct + 1) + base + acc
    }
}

/** One note (or rest / drum hit, see [Notes]) lasting [len] steps. */
data class Step(val note: Int, val len: Int)

class Pattern(val steps: List<Step>) {
    /** Length in steps (sixteenth notes). */
    val length: Int = steps.sumOf { it.len }

    companion object {
        fun parse(text: String): Pattern {
            val stack = ArrayDeque<MutableList<Step>>()
            stack.addLast(mutableListOf())
            var defLen = 1
            for (tok in tokens(text)) {
                when {
                    tok == "|" -> {}
                    tok == "[" -> stack.addLast(mutableListOf())
                    tok.startsWith("]") -> {
                        require(stack.size > 1) { "unmatched ']' in \"$text\"" }
                        val times = if (tok.length == 1) 2 else tok.substring(1).toIntOrNull()
                            ?: throw IllegalArgumentException("bad repeat \"$tok\"")
                        require(times >= 1) { "bad repeat \"$tok\"" }
                        val group = stack.removeLast()
                        repeat(times) { stack.last().addAll(group) }
                    }
                    tok.length > 1 && tok[0] == 'l' && tok.substring(1).toIntOrNull() != null -> {
                        defLen = tok.substring(1).toInt()
                        require(defLen >= 1) { "bad length \"$tok\"" }
                    }
                    else -> stack.last() += step(tok, defLen)
                }
            }
            require(stack.size == 1) { "unclosed '[' in \"$text\"" }
            return Pattern(stack.single())
        }

        private fun step(tok: String, defLen: Int): Step {
            val colon = tok.indexOf(':')
            val name = if (colon < 0) tok else tok.substring(0, colon)
            val len = if (colon < 0) defLen else tok.substring(colon + 1).toIntOrNull()
                ?: throw IllegalArgumentException("bad duration in \"$tok\"")
            require(len >= 1) { "bad duration in \"$tok\"" }
            val note = when (name) {
                "-", "r" -> Notes.REST
                "k" -> Notes.KICK
                "s" -> Notes.SNARE
                "h" -> Notes.HAT
                else -> Notes.midi(name) ?: throw IllegalArgumentException("not a note: \"$tok\"")
            }
            return Step(note, len)
        }

        /** Splits on whitespace; '[' and ']N' are tokens even when glued to a note. */
        private fun tokens(text: String): List<String> {
            val out = mutableListOf<String>()
            var i = 0
            while (i < text.length) {
                val ch = text[i]
                when {
                    ch.isWhitespace() -> i++
                    ch == '[' -> { out += "["; i++ }
                    ch == ']' -> {
                        var j = i + 1
                        while (j < text.length && text[j].isDigit()) j++
                        out += text.substring(i, j); i = j
                    }
                    else -> {
                        var j = i
                        while (j < text.length && !text[j].isWhitespace() && text[j] != '[' && text[j] != ']') j++
                        out += text.substring(i, j); i = j
                    }
                }
            }
            return out
        }
    }
}

/** Attack / decay / release in seconds, sustain as a level 0..1. */
data class Adsr(val attack: Float, val decay: Float, val sustain: Float, val release: Float) {
    companion object {
        val PLUCK = Adsr(0.003f, 0.12f, 0.45f, 0.06f)
        val LEAD = Adsr(0.005f, 0.08f, 0.7f, 0.08f)
        val PAD = Adsr(0.03f, 0.2f, 0.6f, 0.15f)
        val BASS = Adsr(0.002f, 0.05f, 0.9f, 0.03f)
    }
}

class Voice(val wave: Wave, val pattern: Pattern, val volume: Float = 1f, val adsr: Adsr = Adsr.LEAD)

/**
 * A tune: [bpm] in quarter notes, each voice looping its own pattern (so a
 * one-bar drum loop can run under an eight-bar melody). A non-looping song
 * ([loop] = false, the stingers) plays once and stops.
 */
class Song(val name: String, val bpm: Int, val voices: List<Voice>, val loop: Boolean = true) {
    /** Length in steps: the longest voice. */
    val lengthSteps: Int = voices.maxOf { it.pattern.length }
    val stepSeconds: Double get() = 60.0 / bpm / 4
    val seconds: Double get() = lengthSteps * stepSeconds
}

/** Shorthand for the song table. */
internal fun v(wave: Wave, pattern: String, volume: Float = 1f, adsr: Adsr = Adsr.LEAD) =
    Voice(wave, Pattern.parse(pattern), volume, adsr)
