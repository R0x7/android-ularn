package com.ularn.android.cards

import android.content.Context

/** Keeps the [CardAlbum] in SharedPreferences ("cards"), shared by every run and save slot. */
class CardStore(context: Context) {
    private val p = context.getSharedPreferences("cards", Context.MODE_PRIVATE)

    fun load(): CardAlbum = CardAlbum.fromJson(p.getString(KEY, null))

    fun save(album: CardAlbum) = p.edit().putString(KEY, album.toJson()).apply()

    private companion object { const val KEY = "album" }
}
