package com.skyqlike.iptv.data.profile

import com.skyqlike.iptv.data.stremio.StremioHome
import com.skyqlike.iptv.data.stremio.StremioItem
import com.skyqlike.iptv.data.stremio.StremioRow

/**
 * Keeps a Kids profile to child-friendly titles. Addons don't give age ratings, so this goes by genre:
 * a title must be Family or Kids (Animation alone isn't enough, because adult cartoons are Animation too),
 * and must not be Horror, Crime, Thriller and the like. A title with no genre information is hidden, to be safe.
 */
object KidsFilter {
    private val good = setOf("family", "kids", "children")
    private val bad = setOf(
        "horror", "crime", "thriller", "war", "adult", "erotic", "mystery", "documentary",
        "reality-tv", "reality", "talk-show", "news", "film-noir", "history", "biography",
    )

    /** Cartoons and shows made for adults that are never shown, whatever their genres say. */
    private val blockedNames = listOf(
        "south park", "family guy", "rick and morty", "american dad", "big mouth", "bojack", "beavis",
        "archer", "harley quinn", "invincible", "castlevania", "love, death", "blue eye samurai",
        "the boys", "deadpool", "bob's burgers", "king of the hill", "solar opposites", "paradise pd",
    )

    private fun nameBlocked(item: StremioItem): Boolean {
        val n = item.name.lowercase()
        return blockedNames.any { it in n }
    }

    /** For addon titles, which carry genres. */
    fun ok(item: StremioItem): Boolean {
        if (nameBlocked(item)) return false
        val g = item.genres.map { it.trim().lowercase() }
        if (g.isEmpty()) return false
        return g.any { it in good } && g.none { it in bad }
    }

    fun filter(items: List<StremioItem>): List<StremioItem> = items.filter { ok(it) }

    /** For titles TMDB already picked as kids' content. Only the name block list applies. */
    fun filterPicked(rows: List<StremioRow>): List<StremioRow> =
        rows.map { r -> r.copy(items = r.items.filterNot { nameBlocked(it) }) }.filter { it.items.isNotEmpty() }

    /** Kids Home: TMDB's kids rows first (if you have a TMDB key), then whatever of your addons' lists passes. */
    fun filter(home: StremioHome, picked: List<StremioRow> = emptyList()): StremioHome = home.copy(
        rows = filterPicked(picked) + home.rows.map { it.copy(items = filter(it.items)) }.filter { it.items.isNotEmpty() },
    )
}
