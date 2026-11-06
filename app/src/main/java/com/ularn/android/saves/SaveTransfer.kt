package com.ularn.android.saves

import android.os.Build
import android.os.Process
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Exports a slot as one zip file and imports such a file into an empty slot.
 *
 * The zip holds `ularn-slot.json` (a manifest with the slot's [SlotMeta]),
 * and whichever of `ularn_player.sav` / `ularn_player.ckp` the slot has.
 * Imports accept only those names, cap their sizes, and are unpacked into a
 * staging folder that becomes the slot only once everything checks out.
 */
object SaveTransfer {

    const val MIME = "application/zip"
    const val MANIFEST = "ularn-slot.json"
    private const val FORMAT = "ularn-android-slot"
    private const val VERSION = 1
    /** Ularn saves are a few hundred KB; anything far bigger isn't one. */
    private const val MAX_FILE = 8L * 1024 * 1024
    private const val MAX_MANIFEST = 64L * 1024

    class ImportException(message: String) : IOException(message)

    /** Suggested file name for an export. */
    fun fileName(info: SaveSlots.SlotInfo): String {
        val who = info.title.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').take(24).ifEmpty { "game" }
        return "ularn-slot${info.id}-$who.zip"
    }

    fun export(slots: SaveSlots, id: Int, out: OutputStream) {
        val info = slots.info(id)
        if (info.isEmpty) throw IOException("Slot $id has no game to export")
        ZipOutputStream(out).use { zip ->
            val manifest = JSONObject().apply {
                put("format", FORMAT)
                put("version", VERSION)
                put("exported", System.currentTimeMillis())
                put("abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "")
                // Ularn writes C longs straight to the save, so 32- and 64-bit
                // builds can't read each other's saves.
                put("is64", Process.is64Bit())
                put("meta", (info.meta ?: SlotMeta()).toJson())
            }
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest.toString(2).toByteArray())
            zip.closeEntry()
            for (f in listOf(slots.saveFile(id), slots.checkpointFile(id))) {
                if (!f.isFile) continue
                zip.putNextEntry(ZipEntry(f.name).apply { time = f.lastModified() })
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * Unpacks an exported slot into [target], which must be empty (no save or
     * checkpoint). Throws [ImportException] with a user-readable reason if
     * the file isn't a valid export; the target slot is untouched then.
     */
    fun import(slots: SaveSlots, input: InputStream, target: Int) {
        if (!slots.info(target).isEmpty) throw ImportException("Slot $target isn't empty")
        slots.root.mkdirs()
        val staging = File(slots.root, ".import-${System.nanoTime()}")
        try {
            staging.mkdirs()
            var manifest: JSONObject? = null
            val seen = HashSet<String>()
            try {
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        val name = e.name
                        if (e.isDirectory) continue
                        if (name !in setOf(MANIFEST, SaveSlots.SAVE_NAME, SaveSlots.CKP_NAME))
                            throw ImportException("Unexpected file in the archive: ${name.take(40)}")
                        if (!seen.add(name)) throw ImportException("Duplicate entry: $name")
                        if (name == MANIFEST) {
                            val bytes = readCapped(zip, MAX_MANIFEST)
                            manifest = try { JSONObject(String(bytes)) } catch (_: Exception) {
                                throw ImportException("The manifest is damaged")
                            }
                        } else {
                            val f = File(staging, name)
                            f.outputStream().use { o -> copyCapped(zip, o, MAX_FILE) }
                            if (e.time > 0) f.setLastModified(e.time)
                        }
                    }
                }
            } catch (e: ImportException) { throw e
            } catch (e: Exception) { throw ImportException("Not a readable zip file") }

            val m = manifest ?: throw ImportException("This isn't an Ularn save export (no $MANIFEST)")
            if (m.optString("format") != FORMAT) throw ImportException("This isn't an Ularn save export")
            if (m.optInt("version", 0) > VERSION) throw ImportException("This export is from a newer version of the app")
            if (m.has("is64") && m.optBoolean("is64") != Process.is64Bit())
                throw ImportException("This save comes from a " + (if (m.optBoolean("is64")) "64" else "32") +
                    "-bit device and can't be loaded on this one")
            val sav = File(staging, SaveSlots.SAVE_NAME)
            val ckp = File(staging, SaveSlots.CKP_NAME)
            if (!sav.isFile && !ckp.isFile) throw ImportException("The export contains no saved game")
            if ((sav.isFile && sav.length() == 0L) || (ckp.isFile && ckp.length() == 0L))
                throw ImportException("The saved game in the export is empty")
            val meta = try { m.optJSONObject("meta")?.let { SlotMeta.fromJson(it) } } catch (_: Exception) { null } ?: SlotMeta()
            SaveSlots.writeAtomically(File(staging, SaveSlots.META_NAME), meta.toJson().toString(2).toByteArray())

            // Commit. Check again: the target must still be empty.
            if (!slots.info(target).isEmpty) throw ImportException("Slot $target isn't empty")
            val dest = slots.dir(target)
            if (dest.exists()) {
                // An empty slot folder (e.g. leftover metadata): replace its contents.
                for (name in listOf(SaveSlots.META_NAME, SaveSlots.OPT_NAME)) File(dest, name).delete()
                if (!dest.delete() && dest.exists()) {
                    // Something unexpected is in there; move files one by one.
                    for (name in listOf(SaveSlots.SAVE_NAME, SaveSlots.CKP_NAME, SaveSlots.META_NAME)) {
                        val src = File(staging, name)
                        if (src.isFile && !SaveSlots.moveFile(src, File(dest, name)))
                            throw IOException("Couldn't write slot $target")
                    }
                    return
                }
            }
            if (!staging.renameTo(dest)) throw IOException("Couldn't write slot $target")
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    private fun readCapped(input: InputStream, max: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        copyCapped(input, out, max)
        return out.toByteArray()
    }

    private fun copyCapped(input: InputStream, out: OutputStream, max: Long) {
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > max) throw ImportException("A file in the archive is too large to be a saved game")
            out.write(buf, 0, n)
        }
    }
}
