package com.skyqlike.iptv.data.profile

import com.skyqlike.iptv.data.stremio.StremioHome
import com.skyqlike.iptv.data.stremio.StremioItem

/**
 * Keeps a Kids profile to child-friendly titles. Addons don't give age ratings, so this goes by genre:
 * a title must be Animation, Family or Kids, and must not be Horror, Crime, Thriller and the like.
 * A title with no genre information is hidden, to be safe.
 */
object KidsFilter {
    private val good = setOf("animation", "family", "kids", "children", "cartoon")
    private val bad = setOf(
        "horror", "crime", "thriller", "war", "adult", "erotic", "mystery", "documentary",
        "reality-tv", "reality", "talk-show", "news", "film-noir", "history", "biography",
    )

    fun ok(item: StremioItem): Boolean {
        val g = item.genres.map { it.trim().lowercase() }
        if (g.isEmpty()) return false
        return g.any { it in good } && g.none { it in bad }
    }

    fun filter(items: List<StremioItem>): List<StremioItem> = items.filter { ok(it) }

    fun filter(home: StremioHome): StremioHome = home.copy(
        rows = home.rows.map { it.copy(items = filter(it.items)) }.filter { it.items.isNotEmpty() },
    )
}
