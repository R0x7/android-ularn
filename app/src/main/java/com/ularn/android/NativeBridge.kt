package com.ularn.android

object NativeBridge {
    init {
        System.loadLibrary("ularn")
    }

    /** Forks the game on a new pty. Returns [masterFd, pid]; masterFd < 0 is -errno. */
    @JvmStatic external fun spawn(workDir: String, args: Array<String>, env: Array<String>, rows: Int, cols: Int): IntArray

    /** Blocks until the game exits; returns its exit code. */
    @JvmStatic external fun waitFor(pid: Int): Int

    @JvmStatic external fun setSize(fd: Int, rows: Int, cols: Int)

    @JvmStatic external fun sendSignal(pid: Int, signal: Int)

    const val SIGHUP = 1
    const val SIGKILL = 9
}
