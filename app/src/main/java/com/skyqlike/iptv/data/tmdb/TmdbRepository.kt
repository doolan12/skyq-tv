package com.skyqlike.iptv.data.tmdb

import com.skyqlike.iptv.data.Http
import com.skyqlike.iptv.data.stremio.StremioItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/** What's trending today, as titles the rest of the app can open. */
data class Trending(val movies: List<StremioItem>, val series: List<StremioItem>)

/**
 * Asks TMDB (themoviedb.org) what is trending today. It needs a free TMDB key, which the
 * user types into Settings and which stays on the device.
 *
 * TMDB lists titles by its own number, but the addons need the IMDb number (tt1234567),
 * so each title is looked up once to get it.
 */
class TmdbRepository {

    suspend fun trending(apiKey: String): Result<Trending> = withContext(Dispatchers.IO) {
        try {
            val (movies, series) = coroutineScope {
                val m = async { load(apiKey, "movie") }
                val s = async { load(apiKey, "tv") }
                m.await() to s.await()
            }
            Result.success(Trending(movies, series))
        } catch (e: Exception) {
            Result.failure(Exception(friendly(e)))
        }
    }

    private suspend fun load(apiKey: String, kind: String): List<StremioItem> = coroutineScope {
        val url = "$BASE/trending/$kind/day?api_key=${enc(apiKey)}&language=en-US"
        val results = JSONObject(Http.get(url, 20_000)).optJSONArray("results")
            ?: return@coroutineScope emptyList<StremioItem>()

        val picks = ArrayList<JSONObject>()
        for (i in 0 until results.length()) {
            val o = results.optJSONObject(i) ?: continue
            if (str(o, "poster_path") != null) picks += o
        }

        // A few spares, because some titles have no IMDb number and get dropped.
        picks.take(14).map { o ->
            async {
                runCatching<StremioItem?> {
                    val id = o.getInt("id")
                    val ext = JSONObject(Http.get("$BASE/$kind/$id/external_ids?api_key=${enc(apiKey)}", 15_000))
                    val imdb = str(ext, "imdb_id") ?: return@runCatching null
                    toItem(o, imdb, kind)
                }.getOrNull()
            }
        }.awaitAll().filterNotNull().take(10)
    }

    private fun toItem(o: JSONObject, imdbId: String, kind: String): StremioItem? {
        val name = str(o, "title") ?: str(o, "name") ?: return null
        return StremioItem(
            id = imdbId,
            type = if (kind == "tv") "series" else "movie",
            name = name,
            poster = str(o, "poster_path")?.let { "https://image.tmdb.org/t/p/w342$it" },
            background = str(o, "backdrop_path")?.let { "https://image.tmdb.org/t/p/w1280$it" },
            description = str(o, "overview") ?: "",
            year = (str(o, "release_date") ?: str(o, "first_air_date"))?.take(4) ?: "",
            // The rating is left blank on purpose. TMDB's score isn't IMDb's, and the real IMDb score
            // is filled in from your addons when the title is opened.
        )
    }

    private fun friendly(e: Exception): String =
        if (e.message?.contains("401") == true) "TMDB didn't accept that key." else Http.friendly(e)

    private companion object {
        const val BASE = "https://api.themoviedb.org/3"

        fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

        /** Reads a text field, treating missing, null and blank as "not there". */
        fun str(o: JSONObject, key: String): String? {
            if (!o.has(key) || o.isNull(key)) return null
            return o.optString(key).trim().takeIf { it.isNotEmpty() }
        }
    }
}
