package com.ularn.android.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.util.Log

/**
 * Dungeon Radio's output: a streaming AudioTrack (44.1 kHz, mono, 16-bit)
 * fed by a thread that renders [mixer]. Nothing is pre-recorded; the songs
 * and effects are synthesized as they play.
 */
class Chiptune {
    val mixer = Mixer(SAMPLE_RATE)

    @Volatile private var running = false
    @Volatile private var thread: Thread? = null

    val isRunning get() = running

    /** Starts the audio thread (no-op if it runs). */
    @Synchronized fun start() {
        if (running) return
        running = true
        val t = Thread({ loop() }, "ularn-radio")
        thread = t   // before start(): the loop checks it is the current thread
        t.start()
    }

    /** Stops the audio thread and releases the AudioTrack. */
    @Synchronized fun stop() {
        if (!running) return
        running = false
        thread?.let { t -> t.interrupt(); try { t.join(500) } catch (_: InterruptedException) {} }
        thread = null
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val track = try { open() } catch (e: Exception) {
            Log.w(TAG, "no audio output", e); quit(); return
        }
        val f = FloatArray(BUFFER)
        val s = ShortArray(BUFFER)
        try {
            track.play()
            // A thread left over from a quick stop()/start() sees it isn't current and ends.
            while (running && thread === Thread.currentThread()) {
                mixer.render(f, BUFFER)
                for (i in 0 until BUFFER) s[i] = (f[i] * 32000f).toInt().toShort()
                var off = 0
                while (running && off < BUFFER) {
                    val w = track.write(s, off, BUFFER - off)
                    if (w < 0) { Log.w(TAG, "AudioTrack.write: $w"); quit(); break }
                    off += w
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "radio stopped", e)
            quit()
        } finally {
            try { track.pause(); track.flush(); track.stop() } catch (_: Exception) {}
            track.release()
        }
    }

    /** Gives up after an audio error, unless a newer thread has taken over. */
    private fun quit() { synchronized(this) { if (thread === Thread.currentThread()) { running = false; thread = null } } }

    private fun open(): AudioTrack {
        val min = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        return AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            // Small enough that a sound effect follows its event closely.
            .setBufferSizeInBytes(maxOf(min, BUFFER * 2 * 2))
            .build()
    }

    companion object {
        const val SAMPLE_RATE = 44100
        /** Samples per render (~23 ms). */
        const val BUFFER = 1024
        private const val TAG = "UlarnRadio"
    }
}
