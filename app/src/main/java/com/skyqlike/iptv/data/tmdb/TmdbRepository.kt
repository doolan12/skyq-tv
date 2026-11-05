package com.skyqlike.iptv.data.tmdb

import com.skyqlike.iptv.data.Http
import com.skyqlike.iptv.data.stremio.StremioItem
import com.skyqlike.iptv.data.stremio.StremioRow
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

    suspend fun trending(apiKey: String, cacheOnly: Boolean = false): Result<Trending> = withContext(Dispatchers.IO) {
        try {
            val (movies, series) = coroutineScope {
                val m = async { load(apiKey, "movie", cacheOnly) }
                val s = async { load(apiKey, "tv", cacheOnly) }
                m.await() to s.await()
            }
            Result.success(Trending(movies, series))
        } catch (e: Exception) {
            Result.failure(Exception(friendly(e)))
        }
    }

    /**
     * Ready-made rows for a Kids profile, chosen by TMDB itself: movies rated G or PG that are Family or
     * Animation, and shows in TMDB's Kids genre. This gives much more to watch than filtering your addons' lists.
     */
    suspend fun kidsRows(apiKey: String, cacheOnly: Boolean = false): Result<List<StremioRow>> = withContext(Dispatchers.IO) {
        try {
            val k = enc(apiKey)
            val common = "api_key=$k&language=en-US&include_adult=false&without_genres=27,80,53,10752,9648"
            val movieCert = "certification_country=US&certification.lte=PG"
            val specs = listOf(
                Triple("Family movies", "movie", "$BASE/discover/movie?$common&$movieCert&with_genres=10751&sort_by=popularity.desc&vote_count.gte=200"),
                Triple("Animated movies", "movie", "$BASE/discover/movie?$common&$movieCert&with_genres=16&sort_by=popularity.desc&vote_count.gte=300"),
                Triple("Top rated for kids", "movie", "$BASE/discover/movie?$common&$movieCert&with_genres=10751,16&sort_by=vote_average.desc&vote_count.gte=1500"),
                Triple("Kids shows", "tv", "$BASE/discover/tv?$common&with_genres=10762&sort_by=popularity.desc&vote_count.gte=20"),
                Triple("Family shows", "tv", "$BASE/discover/tv?$common&with_genres=10751&without_genres=27,80,53,10752,9648,10763,10764,10766&sort_by=popularity.desc&vote_count.gte=100"),
                Triple("Animated shows for kids", "tv", "$BASE/discover/tv?$common&with_genres=16,10762&sort_by=vote_average.desc&vote_count.gte=50"),
            )
            val rows = coroutineScope {
                specs.map { (title, kind, url) ->
                    async {
                        val items = runCatching { loadList(url, kind, 16, cacheOnly, apiKey) }.getOrDefault(emptyList())
                        StremioRow(title, if (kind == "tv") "series" else "movie", items)
                    }
                }.awaitAll()
            }
            Result.success(rows.filter { it.items.isNotEmpty() })
        } catch (e: Exception) {
            Result.failure(Exception(friendly(e)))
        }
    }

    private suspend fun loadList(url: String, kind: String, count: Int, cacheOnly: Boolean, apiKey: String): List<StremioItem> = coroutineScope {
        val results = JSONObject(Http.getCached(url, 12_000, 6_000, cacheOnly)).optJSONArray("results")
            ?: return@coroutineScope emptyList<StremioItem>()
        val picks = ArrayList<JSONObject>()
        for (i in 0 until results.length()) {
            val o = results.optJSONObject(i) ?: continue
            if (str(o, "poster_path") != null) picks += o
        }
        picks.take(count + 6).map { o ->
            async {
                runCatching<StremioItem?> {
                    val id = o.getInt("id")
                    val ext = JSONObject(Http.getCached("$BASE/$kind/$id/external_ids?api_key=${enc(apiKey)}", 12_000, 6_000, cacheOnly))
                    val imdb = str(ext, "imdb_id") ?: return@runCatching null
                    toItem(o, imdb, kind)
                }.getOrNull()
            }
        }.awaitAll().filterNotNull().take(count)
    }

    private suspend fun load(apiKey: String, kind: String, cacheOnly: Boolean): List<StremioItem> = coroutineScope {
        val url = "$BASE/trending/$kind/day?api_key=${enc(apiKey)}&language=en-US"
        val results = JSONObject(Http.getCached(url, 12_000, 6_000, cacheOnly)).optJSONArray("results")
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
                    val ext = JSONObject(Http.getCached("$BASE/$kind/$id/external_ids?api_key=${enc(apiKey)}", 12_000, 6_000, cacheOnly))
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
