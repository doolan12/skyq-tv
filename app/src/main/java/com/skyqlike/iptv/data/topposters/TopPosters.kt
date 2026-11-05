package com.skyqlike.iptv.data.topposters

import java.net.URLEncoder

/**
 * Posters with rating badges from top-posters.com. The user's free key (typed into Settings, kept on
 * the device) goes in the address, and the site draws the badges on the picture.
 *
 * If the site can't supply a picture (for example the monthly free allowance is used up), it sends
 * the app on to the normal poster instead, so a tile never goes blank.
 */
object TopPosters {

    /** The badge poster for a title, or the normal poster if there is no key or no IMDb number. */
    fun poster(key: String, id: String, normalPoster: String?): String? {
        val k = key.trim()
        if (k.isEmpty() || !id.startsWith("tt")) return normalPoster
        val base = "https://api.top-posters.com/${enc(k)}/imdb/poster/${enc(id)}.jpg"
        return if (normalPoster.isNullOrBlank()) base else base + "?fallback_url=" + enc(normalPoster)
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
