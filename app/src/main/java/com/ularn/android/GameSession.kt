package com.ularn.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import com.ularn.android.game.GameState
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.Executors

/**
 * Owns one running game process: prepares its working directory, forks it,
 * forwards keys, and reports display changes and exit.
 *
 * The game writes its display into shared memory ([state]); it pokes a pipe
 * after each change so the UI redraws only when needed.
 */
class GameSession(
    private val context: Context,
    private val onChanged: () -> Unit,
    private val onExit: (code: Int) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val writer = Executors.newSingleThreadExecutor()
    private var out: FileOutputStream? = null
    private var inPfd: ParcelFileDescriptor? = null
    @Volatile private var pid = -1

    /** The display state; created once, shared by every game process. */
    val state: GameState? by lazy {
        NativeBridge.sharedState()?.let { GameState(it, NativeBridge.layout()) }
    }

    val isRunning get() = pid > 0

    val workDir: File get() = File(context.filesDir, "ularn163")

    /* saves / a11y */
    /**
     * The game's $HOME, where it keeps its save, checkpoint and ularn.opt: a
     * save slot's folder (see saves/SaveSlots), or [workDir] if null. Set it
     * before [start]. The working directory stays [workDir], so the data
     * files and the scoreboard are shared.
     */
    var homeDir: File? = null
    /** ularn.opt contents for the next [start]; null = from Settings. */
    var optsText: String? = null
    private val home: File get() = homeDir ?: workDir

    /** Ularn names save files after the login name, which we fix as "player". */
    val saveFile get() = File(home, "ularn_$LOGIN.sav")
    val checkpointFile get() = File(home, "ularn_$LOGIN.ckp")

    fun hasSave() = saveFile.exists()
    fun hasCheckpoint() = checkpointFile.exists()

    /** Copies data files and writes ularn.opt from settings. */
    private fun prepare(settings: Settings) {
        val dir = workDir
        dir.mkdirs()
        for (name in listOf("Uhelp", "Umaps", "Ufortune")) {
            context.assets.open("ularn/$name").use { input ->
                File(dir, name).outputStream().use { input.copyTo(it) }
            }
        }
        home.mkdirs()
        File(home, "ularn.opt").writeText(optsText ?: settings.buildOptsFile())
    }

    fun start(settings: Settings, args: List<String> = emptyList()): Boolean {
        if (isRunning) return true
        state ?: return false
        prepare(settings)
        val dir = workDir.absolutePath
        val env = arrayOf("HOME=${home.absolutePath}", "USER=$LOGIN", "LOGNAME=$LOGIN")
        val r = NativeBridge.spawn(dir, (listOf("ularn") + args).toTypedArray(), env)
        if (r[0] != 0) {
            Log.e(TAG, "spawn failed: errno ${-r[0]}")
            return false
        }
        val myPid = r[1]
        pid = myPid
        val inP = ParcelFileDescriptor.adoptFd(r[2])
        inPfd = inP
        out = FileOutputStream(inP.fileDescriptor)
        val notePfd = ParcelFileDescriptor.adoptFd(r[3])

        Thread({
            val buf = ByteArray(256)
            FileInputStream(notePfd.fileDescriptor).use { ins ->
                try {
                    while (true) {
                        if (ins.read(buf) <= 0) break
                        onChanged()
                    }
                } catch (_: Exception) {
                }
            }
            try { notePfd.close() } catch (_: Exception) {}
        }, "ularn-notify").start()

        Thread({
            val code = NativeBridge.waitFor(myPid)
            main.post {
                if (pid == myPid) {
                    pid = -1
                    closeInput()
                    onChanged()
                    onExit(code)
                }
            }
        }, "ularn-waiter").start()

        onChanged()
        return true
    }

    fun send(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val o = out ?: return
        writer.execute {
            try { o.write(bytes); o.flush() } catch (e: Exception) { Log.w(TAG, "write failed", e) }
        }
    }

    /**
     * Asks the game to write a checkpoint. It does so at its next command
     * prompt (where the game state is always consistent); in a sub-prompt the
     * key is ignored and the most recent automatic checkpoint stands.
     */
    fun requestCheckpoint() = send(byteArrayOf(KEY_CHECKPOINT))

    /** Leave the game: checkpoint if possible, then close its input so it exits. */
    fun leave() {
        if (!isRunning) return
        requestCheckpoint()
        writer.execute { closeInput() }
    }

    fun kill() {
        if (pid > 0) NativeBridge.sendSignal(pid, NativeBridge.SIGKILL)
    }

    private fun closeInput() {
        try { out?.close() } catch (_: Exception) {}
        try { inPfd?.close() } catch (_: Exception) {}
        out = null; inPfd = null
    }

    companion object {
        private const val TAG = "GameSession"
        const val LOGIN = "player"
        /** Ctrl+] - the front end treats it as "write a checkpoint now". */
        const val KEY_CHECKPOINT: Byte = 0x1d
    }
}
