package com.ularn.android

import java.nio.ByteBuffer

object NativeBridge {
    init {
        System.loadLibrary("ularn")
    }

    /** Forks a game. Returns [status (0 or -errno), pid, inputFd, notifyFd]. */
    @JvmStatic external fun spawn(workDir: String, args: Array<String>, env: Array<String>): IntArray

    /** The display state shared with the game process (a direct buffer). */
    @JvmStatic external fun sharedState(): ByteBuffer?

    /** Field offsets of the shared struct; see GameState.F. */
    @JvmStatic external fun layout(): IntArray

    /** Blocks until the game exits; returns its exit code. */
    @JvmStatic external fun waitFor(pid: Int): Int

    @JvmStatic external fun sendSignal(pid: Int, signal: Int)

    const val SIGKILL = 9
}
