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

    /**
     * Your own TMDB list, shown as rows of movies and rows of series. [listRef] can be the list's link
     * (like themoviedb.org/list/8512345-kids) or just its number.
     */
    suspend fun customList(apiKey: String, listRef: String, cacheOnly: Boolean = false): Result<List<StremioRow>> = withContext(Dispatchers.IO) {
        try {
            val id = Regex("list/([A-Za-z0-9]+)").find(listRef)?.groupValues?.get(1)
                ?: listRef.trim().takeIf { it.matches(Regex("[A-Za-z0-9]+")) }
                ?: return@withContext Result.failure(Exception("That doesn't look like a TMDB list link."))
            val numeric = id.all { it.isDigit() }
            val url = if (numeric) "$BASE/list/$id?api_key=${enc(apiKey)}&language=en-US"
            else "https://api.themoviedb.org/4/list/$id?api_key=${enc(apiKey)}&language=en-US"
            val root = JSONObject(Http.getCached(url, 15_000, 6_000, cacheOnly))
            val arr = root.optJSONArray("items") ?: root.optJSONArray("results")
                ?: return@withContext Result.failure(Exception("Couldn't read that list. Is it public?"))
            val movies = ArrayList<JSONObject>()
            val shows = ArrayList<JSONObject>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (str(o, "poster_path") == null) continue
                when (str(o, "media_type")) {
                    "tv" -> shows += o
                    "movie", null -> movies += o
                }
            }
            suspend fun resolve(list: List<JSONObject>, kind: String): List<StremioItem> = coroutineScope {
                list.take(60).map { o ->
                    async {
                        runCatching<StremioItem?> {
                            val tid = o.getInt("id")
                            val ext = JSONObject(Http.getCached("$BASE/$kind/$tid/external_ids?api_key=${enc(apiKey)}", 12_000, 6_000, cacheOnly))
                            val imdb = str(ext, "imdb_id") ?: return@runCatching null
                            toItem(o, imdb, kind)
                        }.getOrNull()
                    }
                }.awaitAll().filterNotNull()
            }
            val (m, t) = coroutineScope {
                val a = async { resolve(movies, "movie") }
                val b = async { resolve(shows, "tv") }
                a.await() to b.await()
            }
            val rows = ArrayList<StremioRow>()
            m.chunked(20).forEachIndexed { i, c -> rows += StremioRow(if (i == 0) "Kids picks - Movies" else "More kids picks - Movies", "movie", c) }
            t.chunked(20).forEachIndexed { i, c -> rows += StremioRow(if (i == 0) "Kids picks - Series" else "More kids picks - Series", "series", c) }
            Result.success(rows)
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

    /**
     * Actors and directors matching a search, with the films and shows they are known for.
     * Only people whose name contains every word typed are used, so a title search doesn't pull in strangers.
     */
    suspend fun people(apiKey: String, query: String): List<StremioRow> = withContext(Dispatchers.IO) {
        val k = apiKey.trim()
        if (k.isEmpty()) return@withContext emptyList<StremioRow>()
        runCatching {
            val words = query.lowercase().split(' ').filter { it.isNotBlank() }
            val found = JSONObject(Http.getCached("$BASE/search/person?api_key=${enc(k)}&language=en-US&include_adult=false&query=${enc(query)}", 12_000, 6_000, false))
                .optJSONArray("results") ?: return@runCatching emptyList<StremioRow>()
            val people = ArrayList<JSONObject>()
            for (i in 0 until found.length()) {
                val o = found.optJSONObject(i) ?: continue
                val name = (str(o, "name") ?: continue).lowercase()
                val dept = str(o, "known_for_department")
                if (words.all { it in name } && (dept == "Acting" || dept == "Directing" || dept == "Writing")) people += o
                if (people.size >= 2) break
            }
            val rows = ArrayList<StremioRow>()
            coroutineScope {
                people.map { person ->
                    async {
                        runCatching {
                            val id = person.getInt("id")
                            val name = str(person, "name") ?: return@runCatching null
                            val directing = str(person, "known_for_department") == "Directing"
                            val credits = JSONObject(Http.getCached("$BASE/person/$id/combined_credits?api_key=${enc(k)}&language=en-US", 12_000, 6_000, false))
                            val src = credits.optJSONArray(if (directing) "crew" else "cast") ?: return@runCatching null
                            val picks = ArrayList<JSONObject>()
                            for (i in 0 until src.length()) {
                                val o = src.optJSONObject(i) ?: continue
                                if (str(o, "poster_path") == null) continue
                                if (directing && str(o, "job") != "Director") continue
                                if (!directing && (str(o, "character") ?: "").contains("Self", ignoreCase = true)) continue
                                val genres = o.optJSONArray("genre_ids")
                                var talk = false
                                if (genres != null) for (g in 0 until genres.length()) if (genres.optInt(g) in intArrayOf(10767, 10763, 10764)) talk = true
                                if (talk || o.optInt("vote_count") < 40) continue
                                picks += o
                            }
                            val top = picks.distinctBy { it.optInt("id").toString() + str(it, "media_type") }
                                .sortedByDescending { it.optDouble("popularity") }.take(24)
                            val items = top.map { o ->
                                async {
                                    runCatching<StremioItem?> {
                                        val kind = if (str(o, "media_type") == "tv") "tv" else "movie"
                                        val ext = JSONObject(Http.getCached("$BASE/$kind/${o.getInt("id")}/external_ids?api_key=${enc(k)}", 12_000, 6_000, false))
                                        val imdb = str(ext, "imdb_id") ?: return@runCatching null
                                        toItem(o, imdb, kind)
                                    }.getOrNull()
                                }
                            }.awaitAll().filterNotNull().take(20)
                            if (items.isEmpty()) null else StremioRow(if (directing) "Directed by $name" else "Starring $name", "mixed", items)
                        }.getOrNull()
                    }
                }.awaitAll().filterNotNull().forEach { rows += it }
            }
            rows
        }.getOrDefault(emptyList())
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
