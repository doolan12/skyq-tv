package com.skyqlike.iptv.data.stremio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Remembers the movies and series one profile has added to My List. */
class StremioFavouritesStore(context: Context, profileId: String) {

    private val prefs = context.getSharedPreferences("mylist_$profileId", Context.MODE_PRIVATE)

    /** Newest first. */
    fun all(): List<StremioItem> {
        val text = prefs.getString("items", null) ?: return emptyList()
        return try {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val genres = o.optJSONArray("genres")
                StremioItem(
                    id = id,
                    type = o.optString("type", "movie"),
                    name = o.optString("name"),
                    poster = o.optString("poster").takeIf { it.isNotBlank() },
                    background = o.optString("background").takeIf { it.isNotBlank() },
                    description = o.optString("description"),
                    year = o.optString("year"),
                    rating = o.optString("rating"),
                    genres = if (genres == null) emptyList() else (0 until genres.length()).map { genres.optString(it) },
                    sourceBase = o.optString("sourceBase"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(items: List<StremioItem>) {
        val arr = JSONArray()
        items.forEach { m ->
            arr.put(
                JSONObject()
                    .put("id", m.id).put("type", m.type).put("name", m.name)
                    .put("poster", m.poster ?: "").put("background", m.background ?: "")
                    .put("description", m.description.take(400)).put("year", m.year).put("rating", m.rating)
                    .put("genres", JSONArray(m.genres)).put("sourceBase", m.sourceBase)
            )
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }
}
