package com.ularn.android.saves

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Save slots. Each slot is a directory, `<workDir>/slots/<n>/`, that the game
 * uses as its $HOME, so it holds that slot's `ularn_player.sav`,
 * `ularn_player.ckp` and `ularn.opt` (Ularn puts all three in $HOME; see
 * ULarnSetup in ularn.c). The game still runs with `<workDir>` as its
 * working directory, so the data files and the scoreboard (LIBDIR ".") stay
 * shared between slots.
 *
 * Alongside the game's files each slot has `slot.json` ([SlotMeta]): the
 * character name, level, depth and when it was last played, written by the
 * app (the game knows nothing about it).
 */
class SaveSlots(private val workDir: File) {

    val root: File get() = File(workDir, "slots")

    fun dir(id: Int) = File(root, id.toString())
    fun saveFile(id: Int) = File(dir(id), SAVE_NAME)
    fun checkpointFile(id: Int) = File(dir(id), CKP_NAME)
    private fun metaFile(id: Int) = File(dir(id), META_NAME)

    data class SlotInfo(val id: Int, val hasSave: Boolean, val hasCheckpoint: Boolean, val meta: SlotMeta?) {
        val isEmpty get() = !hasSave && !hasCheckpoint
        /** What to call the slot: its custom label, else the character's name. */
        val title: String get() = meta?.label?.takeIf { it.isNotBlank() } ?: meta?.character ?: "Unknown character"
    }

    /** Slots that hold a game, or have at least a folder / metadata. */
    private fun usedIds(): List<Int> =
        (root.list() ?: emptyArray()).mapNotNull { it.toIntOrNull() }.filter { it in 1..MAX_SLOTS }.sorted()

    fun info(id: Int) = SlotInfo(id, saveFile(id).isFile, checkpointFile(id).isFile, readMeta(id))

    /**
     * Slots to show: every slot up to the highest one holding a game, plus
     * one empty slot after it (at least [MIN_SHOWN] in all).
     */
    fun list(): List<SlotInfo> {
        val highest = usedIds().filter { !info(it).isEmpty }.maxOrNull() ?: 0
        val count = maxOf(MIN_SHOWN, highest + 1).coerceAtMost(MAX_SLOTS)
        return (1..count).map { info(it) }
    }

    fun firstEmpty(): Int? = (1..MAX_SLOTS).firstOrNull { info(it).isEmpty }

    // ------------------------------------------------------------ metadata

    fun readMeta(id: Int): SlotMeta? = try {
        val f = metaFile(id)
        if (f.isFile) SlotMeta.fromJson(JSONObject(f.readText())) else null
    } catch (e: Exception) {
        Log.w(TAG, "bad slot.json in slot $id", e); null
    }

    fun writeMeta(id: Int, meta: SlotMeta) {
        dir(id).mkdirs()
        writeAtomically(metaFile(id), meta.toJson().toString(2).toByteArray())
    }

    fun rename(id: Int, label: String) {
        val m = readMeta(id) ?: SlotMeta()
        writeMeta(id, m.copy(label = label.trim().take(40).ifBlank { null }))
    }

    /** Deletes a slot's game, checkpoint, options and metadata. */
    fun delete(id: Int) {
        val d = dir(id)
        for (name in listOf(SAVE_NAME, CKP_NAME, META_NAME, OPT_NAME)) File(d, name).delete()
        d.delete()   // only if nothing else is left in it
    }

    /** Clears a slot's game files (not its folder) before a new game in it. */
    fun clearGame(id: Int) {
        saveFile(id).delete()
        checkpointFile(id).delete()
    }

    // ----------------------------------------------------------- migration

    /**
     * Moves a save and/or checkpoint from before slots existed
     * (`<workDir>/ularn_player.sav|ckp`) into the first empty slot (slot 1 on
     * a normal upgrade). Files are moved, never deleted: by rename within the
     * same filesystem, or by copy-verify-then-delete if rename fails.
     *
     * To survive being interrupted, the files go into `slots/.legacy/` first,
     * which is then renamed to the slot folder in one step; a leftover
     * `.legacy/` is finished on the next launch.
     *
     * @return the slot the old game went into, or null if there was none.
     */
    fun migrateLegacy(defaultName: String, defaultFemale: Boolean): Int? {
        val staging = File(root, ".legacy")
        val legacy = listOf(File(workDir, SAVE_NAME), File(workDir, CKP_NAME)).filter { it.isFile }
        if (legacy.isEmpty() && !staging.isDirectory) return null
        root.mkdirs()
        staging.mkdirs()
        for (f in legacy) {
            val dest = File(staging, f.name)
            if (dest.exists()) {
                // Shouldn't happen; keep both rather than overwrite anything.
                Log.w(TAG, "migration: ${dest.name} already staged; leaving ${f.path} in place")
                continue
            }
            if (!moveFile(f, dest)) Log.w(TAG, "migration: couldn't move ${f.path}; left in place")
        }
        val staged = listOf(SAVE_NAME, CKP_NAME).map { File(staging, it) }.filter { it.isFile }
        if (staged.isEmpty()) { staging.delete(); return null }

        val target = firstEmpty() ?: run {
            Log.w(TAG, "migration: no empty slot; old game kept in ${staging.path}")
            return null
        }
        val newest = staged.maxOf { it.lastModified() }
        if (!File(staging, META_NAME).exists()) {
            writeAtomically(File(staging, META_NAME), SlotMeta(
                character = defaultName, female = defaultFemale, lastPlayed = newest,
            ).toJson().toString(2).toByteArray())
        }
        val dest = dir(target)
        if (!dest.exists() && staging.renameTo(dest)) return target
        // The slot folder exists (an empty slot with leftovers): move file by file.
        dest.mkdirs()
        for (name in listOf(SAVE_NAME, CKP_NAME, META_NAME)) {
            val src = File(staging, name)
            if (!src.isFile) continue
            if (name != META_NAME && File(dest, name).exists()) continue   // never overwrite a game
            File(dest, name).let { if (name == META_NAME) it.delete() }
            if (!moveFile(src, File(dest, name))) {
                Log.w(TAG, "migration: couldn't move ${src.path} into slot $target")
                return null
            }
        }
        staging.delete()
        return target
    }

    companion object {
        private const val TAG = "SaveSlots"
        /** Ularn names these after $USER, which the app fixes as "player". */
        const val SAVE_NAME = "ularn_player.sav"
        const val CKP_NAME = "ularn_player.ckp"
        const val META_NAME = "slot.json"
        const val OPT_NAME = "ularn.opt"
        const val MAX_SLOTS = 10
        private const val MIN_SHOWN = 3

        /** Writes via a temp file + rename, so a crash never leaves half a file. */
        fun writeAtomically(f: File, bytes: ByteArray) {
            val tmp = File(f.parentFile, f.name + ".tmp")
            FileOutputStream(tmp).use { it.write(bytes); it.fd.sync() }
            if (!tmp.renameTo(f)) {
                f.delete()
                if (!tmp.renameTo(f)) { tmp.delete(); throw java.io.IOException("can't write ${f.path}") }
            }
        }

        /**
         * Moves [src] to [dest] (which must not exist). Rename first; else copy,
         * check the copy is byte-identical, and only then delete [src].
         */
        fun moveFile(src: File, dest: File): Boolean {
            if (dest.exists()) return false
            if (src.renameTo(dest)) return true
            val tmp = File(dest.parentFile, dest.name + ".tmp")
            return try {
                FileInputStream(src).use { i -> FileOutputStream(tmp).use { o -> i.copyTo(o); o.fd.sync() } }
                if (!sameContent(src, tmp) || !tmp.renameTo(dest)) { tmp.delete(); false }
                else { src.delete(); true }
            } catch (e: Exception) {
                Log.w(TAG, "copy failed", e); tmp.delete(); false
            }
        }

        private fun sameContent(a: File, b: File): Boolean {
            if (a.length() != b.length()) return false
            FileInputStream(a).use { ia ->
                FileInputStream(b).use { ib ->
                    val ba = ByteArray(8192); val bb = ByteArray(8192)
                    while (true) {
                        val n = ia.read(ba)
                        if (n <= 0) return true
                        var m = 0
                        while (m < n) { val r = ib.read(bb, m, n - m); if (r <= 0) return false; m += r }
                        if (!ba.copyOf(n).contentEquals(bb.copyOf(n))) return false
                    }
                }
            }
        }
    }
}

/** App-side information about a slot (the game's own files hold the game). */
data class SlotMeta(
    /** Custom slot name (Rename); null = show the character's name. */
    val label: String? = null,
    val character: String? = null,
    val female: Boolean = false,
    /** Character level, or -1 if not known yet. */
    val level: Int = -1,
    val levelTitle: String? = null,
    /** The game's level name: "H" (home), "1".."10" (dungeon), "V1".."V5" (volcano). */
    val depth: String? = null,
    val lastPlayed: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("label", label ?: JSONObject.NULL)
        put("character", character ?: JSONObject.NULL)
        put("female", female)
        put("level", level)
        put("levelTitle", levelTitle ?: JSONObject.NULL)
        put("depth", depth ?: JSONObject.NULL)
        put("lastPlayed", lastPlayed)
    }

    /** "Home", "Dungeon level 3", "Volcano level 2", or null. */
    val depthDescription: String? get() = when {
        depth.isNullOrBlank() -> null
        depth == "H" -> "Home"
        depth.startsWith("V") -> "Volcano level ${depth.drop(1)}"
        else -> "Dungeon level $depth"
    }

    companion object {
        private fun JSONObject.str(k: String): String? =
            if (isNull(k)) null else optString(k).takeIf { it.isNotEmpty() }?.take(80)

        fun fromJson(o: JSONObject) = SlotMeta(
            label = o.str("label"),
            character = o.str("character"),
            female = o.optBoolean("female", false),
            level = o.optInt("level", -1),
            levelTitle = o.str("levelTitle"),
            depth = o.str("depth")?.take(8),
            lastPlayed = o.optLong("lastPlayed", 0L),
        )
    }
}
