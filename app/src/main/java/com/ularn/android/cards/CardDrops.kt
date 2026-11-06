package com.ularn.android.cards

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.ularn.android.Settings
import com.ularn.android.game.GameEvents

/**
 * Wild Edition trading cards, app side: turns the game's US_EV_WILD_CARD
 * events into album entries and shows a little "new card" popup.
 *
 * Register with [GameEvents.addListener]. Events arrive on the game's notify
 * thread, so everything is handed to the main thread first.
 */
class CardDrops(
    private val activity: Activity,
    private val settings: Settings,
    /** GameState.runId of the game running now (0 if none). */
    private val runId: () -> Int,
) : GameEvents.Listener {

    private val store = CardStore(activity)
    val album: CardAlbum = store.load()
    val renderer = CardRenderer(activity)
    private val main = Handler(Looper.getMainLooper())

    val enabled get() = settings.wild(Settings.WILDF_CARDS)

    override fun onEvent(e: GameEvents.Event) {
        if (e.type != GameEvents.WILD_CARD) return
        main.post { onCard(e.a, e.b != 0) }
    }

    private fun onCard(monst: Int, holo: Boolean) {
        if (!enabled) return
        val drop = album.add(monst, holo, runId(), System.currentTimeMillis()) ?: return
        store.save(album)
        if (settings.cardToasts) enqueue(drop)
    }

    fun showAlbum() = CardAlbumDialog.show(activity, album, renderer)

    // ---- popup: one at a time, a short queue for a fireball's worth of drops

    private val queue = ArrayDeque<CardAlbum.Drop>()
    private var showing = false

    private fun enqueue(d: CardAlbum.Drop) {
        if (queue.size >= 4) queue.removeFirst()
        queue.addLast(d)
        if (!showing) next()
    }

    private fun next() {
        val d = queue.removeFirstOrNull() ?: run { showing = false; return }
        val root = activity.findViewById<ViewGroup>(android.R.id.content) as? FrameLayout
        if (root == null || activity.isFinishing) { queue.clear(); showing = false; return }
        showing = true
        val popup = popupView(d)
        root.addView(popup, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(56)
        })
        popup.announceForAccessibility(popupText(d))
        popup.alpha = 0f
        popup.translationY = -dp(16).toFloat()
        popup.animate().alpha(1f).translationY(0f).setDuration(180).start()
        main.postDelayed({
            popup.animate().alpha(0f).setDuration(220).withEndAction {
                root.removeView(popup)
                next()
            }.start()
        }, if (d.newCard || d.newHolo) 2600L else 1700L)
    }

    private fun popupText(d: CardAlbum.Drop): String {
        val name = MonsterFacts.get(d.monst)?.title ?: "?"
        val star = if (d.holo) " ★ HOLO" else ""
        return if (d.newCard || d.newHolo) "New card: $name$star" else "Card: $name$star (x${d.count})"
    }

    private fun popupView(d: CardAlbum.Drop): LinearLayout {
        val rarity = MonsterFacts.rarityOf(d.monst)
        val thumb = renderer.render(d.monst, album[d.monst], renderer.scaleFor(dp(40)))
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(14), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(Color.argb(235, 22, 22, 26))
                setStroke(dp(2), rarity.frame)
            }
            elevation = dp(8).toFloat()
            isClickable = true
            setOnClickListener { CardAlbumDialog.showCard(activity, album, renderer, d.monst) }
            addView(ImageView(activity).apply {
                setImageDrawable(BitmapDrawable(activity.resources, thumb))  // shrunk, so filtered
            }, LinearLayout.LayoutParams(dp(40), dp(40) * CardRenderer.H / CardRenderer.W))
            addView(TextView(activity).apply {
                text = popupText(d)
                setTextColor(if (d.holo) Color.rgb(255, 214, 90) else Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                textSize = 15f
                setPadding(dp(10), 0, 0, 0)
            })
        }
    }

    private fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()
}
