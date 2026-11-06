package com.ularn.android.audio

import kotlin.math.floor
import kotlin.math.pow

/*
 * Dungeon Radio (Wild Edition): the synthesizer. Pure Kotlin, renders float
 * samples into arrays; Chiptune.kt pushes them to an AudioTrack. Kept free of
 * Android classes so it runs in JVM unit tests.
 */

/** One oscillator: pulse with a duty cycle, 16-step triangle, or LFSR noise. */
class Osc(private val sampleRate: Int) {
    var wave = Wave.PULSE50
    var freq = 440.0
    private var phase = 0.0
    private var lfsr = 1
    private var noiseOut = 1f

    fun reset() { phase = 0.0 }

    fun next(): Float {
        val inc = freq / sampleRate
        return when (wave) {
            Wave.NOISE -> {
                // freq = how often the shift register clocks, like the NES noise channel.
                phase += inc
                while (phase >= 1.0) {
                    phase -= 1.0
                    val bit = (lfsr xor (lfsr shr 1)) and 1
                    lfsr = (lfsr shr 1) or (bit shl 14)
                    noiseOut = if (lfsr and 1 != 0) 1f else -1f
                }
                noiseOut
            }
            Wave.TRIANGLE -> {
                phase = (phase + inc) % 1.0
                val t = if (phase < 0.5) 4 * phase - 1 else 3 - 4 * phase
                // 16 levels, the NES triangle's staircase.
                (floor(t * 8) / 8).toFloat().coerceIn(-1f, 1f)
            }
            else -> {
                phase = (phase + inc) % 1.0
                val duty = when (wave) { Wave.PULSE12 -> 0.125; Wave.PULSE25 -> 0.25; else -> 0.5 }
                if (phase < duty) 1f else -1f
            }
        }
    }
}

/** Linear ADSR envelope. */
class Envelope(private val sampleRate: Int) {
    private var stage = OFF
    private var level = 0f
    private var adsr = Adsr.LEAD

    val idle get() = stage == OFF

    fun on(a: Adsr) { adsr = a; stage = ATTACK }
    fun off() { if (stage != OFF) stage = RELEASE }
    fun kill() { stage = OFF; level = 0f }

    fun next(): Float {
        when (stage) {
            ATTACK -> {
                level += 1f / (adsr.attack * sampleRate).coerceAtLeast(1f)
                if (level >= 1f) { level = 1f; stage = DECAY }
            }
            DECAY -> {
                level -= (1f - adsr.sustain) / (adsr.decay * sampleRate).coerceAtLeast(1f)
                if (level <= adsr.sustain) {
                    level = adsr.sustain
                    stage = if (level <= 0f) OFF else SUSTAIN
                }
            }
            RELEASE -> {
                level -= 1f / (adsr.release * sampleRate).coerceAtLeast(1f)
                if (level <= 0f) { level = 0f; stage = OFF }
            }
        }
        return level
    }

    private companion object {
        const val OFF = 0; const val ATTACK = 1; const val DECAY = 2; const val SUSTAIN = 3; const val RELEASE = 4
    }
}

/** Plays a [Song] voice by voice; [render] adds into a buffer. */
class Sequencer(val song: Song, private val sampleRate: Int) {
    private val stepSamples = sampleRate * song.stepSeconds
    private val channels = song.voices.map { Channel(it) }
    private var pos = 0L

    /** A non-looping song has played to the end (and its notes have died away). */
    val finished get() = !song.loop && channels.all { it.done && it.env.idle }

    fun render(out: FloatArray, n: Int, gain: Float) {
        for (i in 0 until n) {
            var s = 0f
            for (ch in channels) s += ch.next(pos)
            out[i] += s * gain
            pos++
        }
    }

    private inner class Channel(val voice: Voice) {
        val osc = Osc(sampleRate).apply { wave = voice.wave }
        val env = Envelope(sampleRate)
        var idx = 0
        var nextAt = 0.0
        var gateOffAt = -1.0
        var done = voice.pattern.steps.isEmpty()
        /** Per-sample pitch multiplier (the kick's falling pitch). */
        var sweep = 1.0

        fun next(pos: Long): Float {
            if (!done && pos >= nextAt) trigger()
            if (gateOffAt >= 0 && pos >= gateOffAt) { env.off(); gateOffAt = -1.0 }
            if (env.idle) return 0f
            if (sweep != 1.0) osc.freq = (osc.freq * sweep).coerceAtLeast(30.0)
            return osc.next() * env.next() * voice.volume
        }

        private fun trigger() {
            val steps = voice.pattern.steps
            if (idx >= steps.size) {
                if (!song.loop) { done = true; env.off(); return }
                idx = 0
            }
            val st = steps[idx++]
            val start = nextAt
            nextAt += st.len * stepSamples
            sweep = 1.0
            when (st.note) {
                Notes.REST -> { env.off(); gateOffAt = -1.0; return }
                Notes.KICK -> {
                    osc.wave = Wave.TRIANGLE; osc.freq = 180.0
                    sweep = 0.25.pow(1.0 / (0.08 * sampleRate))   // two octaves down in 80 ms
                    env.on(DRUM_KICK)
                }
                Notes.SNARE -> { osc.wave = Wave.NOISE; osc.freq = 7000.0; env.on(DRUM_SNARE) }
                Notes.HAT -> { osc.wave = Wave.NOISE; osc.freq = 16000.0; env.on(DRUM_HAT) }
                else -> {
                    osc.wave = voice.wave
                    // A pitched note on the noise voice: higher note, faster clock.
                    osc.freq = Notes.freq(st.note) * if (voice.wave == Wave.NOISE) 8 else 1
                    env.on(voice.adsr)
                }
            }
            // Leave a little air between notes so repeated notes are heard.
            gateOffAt = start + st.len * stepSamples * 0.85
        }
    }

    companion object {
        val DRUM_KICK = Adsr(0.001f, 0.12f, 0f, 0.02f)
        val DRUM_SNARE = Adsr(0.001f, 0.10f, 0f, 0.02f)
        val DRUM_HAT = Adsr(0.001f, 0.03f, 0f, 0.01f)
    }
}

/** A sound effect: a few segments, each a pitch slide on one waveform. */
class SfxDef(val name: String, val segs: List<Seg>) {
    class Seg(val wave: Wave, val f0: Double, val f1: Double, val ms: Int, val vol: Float = 1f)

    val ms: Int = segs.sumOf { it.ms }
}

/** Plays one [SfxDef]. */
class SfxVoice(private val sampleRate: Int) {
    private val osc = Osc(sampleRate)
    private var def: SfxDef? = null
    private var seg = 0
    private var t = 0
    private var segLen = 0
    /** Monotonic start counter, to steal the oldest voice. */
    var started = 0L

    val busy get() = def != null

    fun start(d: SfxDef, order: Long) {
        def = d; seg = 0; t = 0; started = order
        begin()
    }

    private fun begin() {
        val d = def ?: return
        if (seg >= d.segs.size) { def = null; return }
        val s = d.segs[seg]
        osc.wave = s.wave
        osc.reset()
        segLen = (s.ms * sampleRate / 1000).coerceAtLeast(1)
        t = 0
    }

    fun next(): Float {
        val d = def ?: return 0f
        val s = d.segs[seg]
        val x = t.toDouble() / segLen
        osc.freq = s.f0 * (s.f1 / s.f0).pow(x)
        // Fall to 40% over the segment, with 3 ms fades at both ends against clicks.
        val edge = (3 * sampleRate / 1000).coerceAtLeast(1)
        val fade = minOf(1f, t.toFloat() / edge, (segLen - t).toFloat() / edge)
        val out = osc.next() * s.vol * (1f - 0.6f * x.toFloat()) * fade
        if (++t >= segLen) { seg++; begin() }
        return out
    }
}

/**
 * Music (with a cross-fade between songs) plus up to [POLYPHONY] sound
 * effects. Every public method is synchronized: the game's event thread and
 * the UI thread call in while the audio thread renders.
 */
class Mixer(val sampleRate: Int) {
    var musicVolume = 0.6f
    var sfxVolume = 0.8f

    private var current: Sequencer? = null
    private var fading: Sequencer? = null
    private var fadePos = 0
    private val fadeLen = (sampleRate * FADE_SECONDS).toInt()
    private val sfx = Array(POLYPHONY) { SfxVoice(sampleRate) }
    private var sfxOrder = 0L
    private var scratch = FloatArray(0)

    /** The song playing (or fading in), null for silence. */
    @get:Synchronized val song: Song? get() = current?.song

    /** Nothing is playing or about to: the audio thread may stop. */
    @get:Synchronized val silent: Boolean
        get() = (current == null || current!!.finished) && fading == null && sfx.none { it.busy }

    /** Cross-fades to [s] (null = fade out). Asking for the song already playing does nothing. */
    @Synchronized fun play(s: Song?) {
        if (s === current?.song) return
        fading = current
        current = s?.let { Sequencer(it, sampleRate) }
        fadePos = 0
    }

    @Synchronized fun sfx(d: SfxDef) {
        val v = sfx.firstOrNull { !it.busy } ?: sfx.minByOrNull { it.started }!!
        v.start(d, ++sfxOrder)
    }

    /** Fills out[0 until n] with the mix, roughly -1..1. */
    @Synchronized fun render(out: FloatArray, n: Int) {
        if (scratch.size < n) scratch = FloatArray(n)
        java.util.Arrays.fill(out, 0, n, 0f)
        val music = scratch
        java.util.Arrays.fill(music, 0, n, 0f)
        val inGain: Float
        val outGain: Float
        if (fadePos < fadeLen) {
            // One gain per buffer is smooth enough at ~20 ms per buffer.
            val f = fadePos.toFloat() / fadeLen
            inGain = f; outGain = 1f - f
            fadePos += n
        } else {
            inGain = 1f; outGain = 0f
            fading = null
        }
        current?.render(music, n, inGain)
        fading?.render(music, n, outGain)
        for (i in 0 until n) {
            var s = music[i] * MUSIC_LEVEL * musicVolume
            var e = 0f
            for (v in sfx) if (v.busy) e += v.next()
            s += e * SFX_LEVEL * sfxVolume
            out[i] = s.coerceIn(-1f, 1f)
        }
    }

    companion object {
        const val POLYPHONY = 4
        const val FADE_SECONDS = 0.3
        /** Head-room: four music voices or four effects at full tilt stay below 1. */
        const val MUSIC_LEVEL = 0.22f
        const val SFX_LEVEL = 0.3f
    }
}
