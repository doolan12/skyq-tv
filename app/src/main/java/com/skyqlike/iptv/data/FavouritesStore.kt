package com.skyqlike.iptv.data

import android.content.Context

/** Remembers which channels the user has marked as favourites. */
class FavouritesStore(context: Context) {

    private val prefs = context.getSharedPreferences("favourites", Context.MODE_PRIVATE)

    /** The favourites in the order the user chose. Ones saved before ordering existed come last. */
    fun all(): Set<String> {
        val members = prefs.getStringSet("keys", emptySet())?.toSet() ?: emptySet()
        val ordered = try {
            val arr = org.json.JSONArray(prefs.getString("order", "[]"))
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
        val out = LinkedHashSet<String>()
        ordered.filterTo(out) { it in members }
        members.sorted().filterTo(out) { it !in out }
        return out
    }

    fun save(keys: Set<String>) {
        val arr = org.json.JSONArray()
        keys.forEach { arr.put(it) }
        prefs.edit().putStringSet("keys", keys.toSet()).putString("order", arr.toString()).apply()
    }
}
