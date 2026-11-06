package com.ularn.android.photo

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.Toast
import com.ularn.android.Settings
import com.ularn.android.game.GameEvents
import com.ularn.android.game.GameState
import com.ularn.android.game.GameView
import java.io.File
import java.util.Date

/**
 * Wild Edition photo mode (Settings.WILDF_PHOTO): "Take a photo" in the
 * in-game menu (or a control button) snaps the map as it is on screen into a
 * [Polaroid], flashes the screen, clicks, saves it to Pictures/Ularn Wild and
 * offers to share it. With "Auto-photo on death/victory" it also snaps the
 * final scene by itself (listen to [GameEvents]).
 *
 * The newest photo is also kept privately as the run's "last photo" for the
 * statistics dialog ([lastPhotoFile]).
 *
 * API 29+ saves through MediaStore with no permission; API 24-28 asks for
 * WRITE_EXTERNAL_STORAGE the first time (the manifest limits it to <= 28).
 * Forward [onRequestPermissionsResult] from the activity.
 */
class PhotoMode(
    private val activity: Activity,
    private val settings: Settings,
    private val game: GameView,
    private val state: () -> GameState?,
    /** The character's name for the caption (null: the settings' default). */
    private val characterName: () -> String?,
    /** Plays the shutter through the Radio when it can; false = do it yourself. */
    private val radioShutter: () -> Boolean,
) : GameEvents.Listener {

    private val main = Handler(Looper.getMainLooper())
    /** A photo waiting for the storage permission (API 24-28). */
    private var pending: Bitmap? = null

    val enabled get() = settings.wild(Settings.WILDF_PHOTO)

    /** The menu entry / button. */
    fun takePhoto() {
        if (!enabled) return
        val photo = snap()
        if (photo == null) { toast("Nothing to photograph right now"); return }
        shutter()
        saveAndOffer(photo)
    }

    /** From the game's events: auto-snap the final scene. */
    override fun onEvent(e: GameEvents.Event) {
        if (e.type != GameEvents.DIED && e.type != GameEvents.WON) return
        if (!enabled || !settings.autoPhoto) return
        main.post {
            val photo = snap() ?: return@post
            keepLast(photo)
            if (canWriteWithoutAsking()) save(photo)?.let { toast("Photo of your final moment saved to Pictures/Ularn Wild") }
        }
    }

    // ------------------------------------------------------------ snapping

    private fun snap(): Bitmap? {
        val s = state() ?: return null
        val map = game.renderMapBitmap(2) ?: return null
        val name = characterName() ?: settings.playerName
        val caption = PolaroidMath.caption(name, s.levelTitle, s.levelName, s.gameTime)
        val now = System.currentTimeMillis()
        val out = try {
            Polaroid.compose(map, caption, Date(now), now, game.playerTile()) { game.tileBitmap(it) }
        } catch (_: OutOfMemoryError) { null }
        map.recycle()
        return out
    }

    /** The white flash over the window and the click. */
    private fun shutter() {
        val decor = activity.window.decorView
        val flash = ColorDrawable(Color.WHITE).apply { setBounds(0, 0, decor.width, decor.height); alpha = 0 }
        decor.overlay.add(flash)
        ValueAnimator.ofInt(230, 0).apply {
            duration = 350
            addUpdateListener { flash.alpha = it.animatedValue as Int; decor.invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { decor.overlay.remove(flash) }
            })
            start()
        }
        if (!radioShutter()) {
            try {
                val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
                tg.startTone(ToneGenerator.TONE_PROP_ACK, 120)
                main.postDelayed({ tg.release() }, 400)
            } catch (_: RuntimeException) { /* no audio: a silent camera is fine */ }
        }
    }

    // -------------------------------------------------------------- saving

    private fun canWriteWithoutAsking() = Build.VERSION.SDK_INT >= 29 ||
        activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun saveAndOffer(photo: Bitmap) {
        keepLast(photo)
        if (!canWriteWithoutAsking()) {
            pending = photo
            activity.requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_STORAGE)
            return
        }
        offer(photo, save(photo))
    }

    /** Forwarded from Activity.onRequestPermissionsResult; true if it was ours. */
    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray): Boolean {
        if (requestCode != REQ_STORAGE) return false
        val photo = pending ?: return true
        pending = null
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        offer(photo, if (granted) save(photo) else null)
        return true
    }

    /** Writes the PNG to Pictures/Ularn Wild; the content Uri, or null on failure. */
    private fun save(photo: Bitmap): Uri? {
        val name = PolaroidMath.fileName(System.currentTimeMillis())
        val resolver = activity.contentResolver
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + ALBUM)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
                val ok = resolver.openOutputStream(uri)?.use { photo.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
                if (!ok) { resolver.delete(uri, null, null); return null }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
                if (!dir.isDirectory && !dir.mkdirs()) return null
                val file = File(dir, name)
                file.outputStream().use { photo.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    @Suppress("DEPRECATION")
                    put(MediaStore.Images.Media.DATA, file.absolutePath)
                }
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            }
        } catch (_: Exception) { null }
    }

    /** Shows the photo with Share / OK. */
    private fun offer(photo: Bitmap, uri: Uri?) {
        if (activity.isFinishing) return
        val image = ImageView(activity).apply {
            setImageBitmap(photo)
            adjustViewBounds = true
            val pad = (12 * activity.resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
            contentDescription = "Your photo"
        }
        val b = AlertDialog.Builder(activity)
            .setTitle(if (uri != null) "Saved to Pictures/$ALBUM" else "Photo (not saved to the gallery)")
            .setView(image)
            .setPositiveButton("OK", null)
        if (uri != null) b.setNeutralButton("Share") { _, _ -> share(uri) }
        b.show()
    }

    private fun share(uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "A snapshot from Ularn Wild")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { activity.startActivity(Intent.createChooser(send, "Share photo")) }
        catch (_: Exception) { toast("No app to share with") }
    }

    private fun keepLast(photo: Bitmap) {
        try {
            val f = lastPhotoFile(activity)
            f.parentFile?.mkdirs()
            f.outputStream().use { photo.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (_: Exception) { }
    }

    private fun toast(s: String) = Toast.makeText(activity, s, Toast.LENGTH_SHORT).show()

    companion object {
        const val ALBUM = "Ularn Wild"
        const val REQ_STORAGE = 4710

        /** The newest photo, kept privately for the statistics dialog. */
        fun lastPhotoFile(context: Context) = File(File(context.filesDir, "photos"), "last.png")

        /** The newest photo scaled to at most [maxW] px wide, or null if none. */
        fun loadLastPhoto(context: Context, maxW: Int): Bitmap? {
            val f = lastPhotoFile(context)
            if (!f.isFile) return null
            return try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.absolutePath, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= maxW) sample *= 2
                BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            } catch (_: Exception) { null }
        }
    }
}
