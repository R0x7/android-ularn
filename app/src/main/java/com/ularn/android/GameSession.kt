package com.ularn.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import com.ularn.android.term.TerminalEmulator
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.Executors

/**
 * Owns one running game process: prepares its working directory, spawns it
 * on a pty, pumps output into the [TerminalEmulator] and forwards input.
 */
class GameSession(
    private val context: Context,
    val emulator: TerminalEmulator,
    private val onOutput: () -> Unit,
    private val onExit: (code: Int) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val writer = Executors.newSingleThreadExecutor()
    private var pfd: ParcelFileDescriptor? = null
    private var out: FileOutputStream? = null
    @Volatile private var pid = -1

    val isRunning get() = pid > 0

    val workDir: File get() = File(context.filesDir, "ularn")

    fun hasSave() = File(workDir, "Ularn.sav").exists()
    fun hasCheckpoint() = File(workDir, "Ularn.ckp").exists()

    /** Copies data files and writes .Ularnopts from settings. */
    fun prepare(settings: Settings) {
        val dir = workDir
        dir.mkdirs()
        for (name in listOf("Uhelp", "Umaps", "Ufortune")) {
            context.assets.open("ularn/$name").use { input ->
                File(dir, name).outputStream().use { input.copyTo(it) }
            }
        }
        File(dir, ".Ularnopts").writeText(settings.buildOptsFile())
    }

    fun start(settings: Settings, args: List<String> = emptyList()) {
        if (isRunning) return
        prepare(settings)
        emulator.reset()
        val dir = workDir.absolutePath
        val env = arrayOf(
            "TERM=vt100",
            "HOME=$dir",
            "USER=player",
            "LOGNAME=player",
            "LINES=${emulator.rows}",
            "COLUMNS=${emulator.columns}",
            "SHELL=/system/bin/sh",
        )
        val result = NativeBridge.spawn(dir, (listOf("Ularn") + args).toTypedArray(), env, emulator.rows, emulator.columns)
        val fd = result[0]
        if (fd < 0) {
            emulator.feed("\r\nCould not start game (error ${-fd}).\r\n")
            onOutput()
            return
        }
        pid = result[1]
        val p = ParcelFileDescriptor.adoptFd(fd)
        pfd = p
        // Separate dup'd descriptors so reader and writer can close independently.
        val readPfd = p.dup()   // keep a reference: a GC'd PFD closes its fd
        val inStream = FileInputStream(readPfd.fileDescriptor)
        out = FileOutputStream(p.fileDescriptor)

        val myPid = pid
        Thread({
            val buf = ByteArray(8192)
            try {
                while (true) {
                    val n = inStream.read(buf)
                    if (n <= 0) break
                    emulator.feed(buf, 0, n)
                    onOutput()
                }
            } catch (e: Exception) {
                // EIO when the child closes the pty: normal end of game
            } finally {
                try { inStream.close() } catch (_: Exception) {}
                try { readPfd.close() } catch (_: Exception) {}
            }
        }, "ularn-reader").start()

        Thread({
            val code = NativeBridge.waitFor(myPid)
            main.post {
                if (pid == myPid) {
                    pid = -1
                    closeFds()
                    onExit(code)
                }
            }
        }, "ularn-waiter").start()
    }

    fun send(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val o = out ?: return
        writer.execute {
            try { o.write(bytes); o.flush() } catch (e: Exception) { Log.w(TAG, "write failed", e) }
        }
    }

    /**
     * Hangs up the game like a closed terminal. If checkpointing is enabled the
     * game writes Ularn.ckp first, so it can be restored later.
     */
    fun hangUp() {
        if (pid > 0) NativeBridge.sendSignal(pid, NativeBridge.SIGHUP)
    }

    fun kill() {
        if (pid > 0) NativeBridge.sendSignal(pid, NativeBridge.SIGKILL)
    }

    private fun closeFds() {
        try { out?.close() } catch (_: Exception) {}
        try { pfd?.close() } catch (_: Exception) {}
        out = null; pfd = null
    }

    companion object { private const val TAG = "GameSession" }
}
