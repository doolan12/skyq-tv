package com.skyqlike.iptv.data

import android.content.Context

/** Remembers which channels the user has marked as favourites. */
class FavouritesStore(context: Context) {

    private val prefs = context.getSharedPreferences("favourites", Context.MODE_PRIVATE)

    fun all(): Set<String> = prefs.getStringSet("keys", emptySet())?.toSet() ?: emptySet()

    fun save(keys: Set<String>) {
        prefs.edit().putStringSet("keys", keys.toSet()).apply()
    }
}
