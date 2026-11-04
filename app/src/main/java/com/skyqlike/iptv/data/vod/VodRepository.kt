package com.skyqlike.iptv.data.vod

import com.skyqlike.iptv.data.Http
import com.skyqlike.iptv.data.provider.Provider
import com.skyqlike.iptv.data.provider.ProviderType
import com.skyqlike.iptv.data.provider.XtreamAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Gets movies and series from an Xtream Codes provider.
 * M3U links only carry live channels, so they have no movies or series.
 */
class VodRepository {

    suspend fun catalog(provider: Provider?): Result<VodCatalog> = withContext(Dispatchers.IO) {
        if (provider == null) return@withContext Result.success(SampleVod.catalog())
        if (provider.type != ProviderType.XTREAM) {
            return@withContext Result.failure(
                IOException("Movies and series need an Xtream login. An M3U link only has live channels.")
            )
        }
        val x = XtreamAccess(provider)
        var firstError: Exception? = null
        val movies = try {
            loadMovies(x)
        } catch (e: Exception) {
            firstError = e
            emptyList()
        }
        val series = try {
            loadSeries(x)
        } catch (e: Exception) {
            if (firstError == null) firstError = e
            emptyList()
        }
        val err = firstError
        if (movies.isEmpty() && series.isEmpty() && err != null) {
            Result.failure(IOException(Http.friendly(err)))
        } else {
            Result.success(VodCatalog(movies, series))
        }
    }

    /** Fills in the description, genre and length of one movie (providers only send these on request). */
    suspend fun movieInfo(provider: Provider?, movie: Movie): Movie = withContext(Dispatchers.IO) {
        if (provider == null || provider.type != ProviderType.XTREAM) return@withContext movie
        val x = XtreamAccess(provider)
        val info = JSONObject(Http.get(x.api("get_vod_info", "&vod_id=${movie.id}")))
            .optJSONObject("info") ?: return@withContext movie
        val seconds = info.optString("duration_secs").toIntOrNull() ?: 0
        movie.copy(
            description = info.optString("plot").ifBlank { info.optString("description") }.ifBlank { movie.description },
            genre = info.optString("genre").ifBlank { movie.genre },
            year = info.optString("releasedate").take(4).ifBlank { movie.year },
            durationMin = if (seconds > 0) seconds / 60 else movie.durationMin,
            posterUrl = movie.posterUrl ?: info.optString("movie_image").takeIf { it.isNotBlank() },
        )
    }

    suspend fun episodes(provider: Provider?, series: Series): Result<List<Episode>> = withContext(Dispatchers.IO) {
        if (provider == null) return@withContext Result.success(SampleVod.episodes(series))
        if (provider.type != ProviderType.XTREAM) {
            return@withContext Result.failure(IOException("Series need an Xtream login."))
        }
        try {
            val x = XtreamAccess(provider)
            val root = JSONObject(Http.get(x.api("get_series_info", "&series_id=${series.id}")))
            val out = ArrayList<Episode>()
            val episodes = root.opt("episodes")
            if (episodes is JSONObject) {
                val keys = episodes.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    readSeason(x, episodes.optJSONArray(key), key.toIntOrNull() ?: 1, out)
                }
            } else if (episodes is JSONArray) {
                for (i in 0 until episodes.length()) {
                    readSeason(x, episodes.optJSONArray(i), i + 1, out)
                }
            }
            out.sortWith(compareBy({ it.season }, { it.number }))
            if (out.isEmpty()) Result.failure(IOException("No episodes were found for this series."))
            else Result.success(out)
        } catch (e: Exception) {
            Result.failure(IOException(Http.friendly(e)))
        }
    }

    private fun readSeason(x: XtreamAccess, arr: JSONArray?, season: Int, out: MutableList<Episode>) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isEmpty()) continue
            val info = o.optJSONObject("info")
            val seconds = info?.optString("duration_secs")?.toIntOrNull() ?: 0
            out += Episode(
                id = id,
                season = season,
                number = o.optString("episode_num").toIntOrNull() ?: (i + 1),
                title = o.optString("title").ifBlank { "Episode ${i + 1}" },
                description = info?.optString("plot").orEmpty(),
                durationMin = seconds / 60,
                streamUrl = x.seriesUrl(id, o.optString("container_extension").ifBlank { "mp4" }),
                imageUrl = info?.optString("movie_image")?.takeIf { it.isNotBlank() },
            )
        }
    }

    private fun loadMovies(x: XtreamAccess): List<Movie> {
        val names = categoryNames(x.api("get_vod_categories"))
        val arr = JSONArray(Http.get(x.api("get_vod_streams"), readTimeoutMs = 120_000))
        val out = ArrayList<Movie>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("stream_id")
            if (id.isEmpty()) continue
            out += Movie(
                id = id,
                name = o.optString("name").ifBlank { "Movie $id" },
                posterUrl = o.optString("stream_icon").takeIf { it.isNotBlank() },
                category = names[o.optString("category_id")] ?: "Other",
                year = o.optString("year").ifBlank { o.optString("releasedate").take(4) },
                rating = o.optString("rating"),
                addedEpoch = o.optString("added").toLongOrNull() ?: 0L,
                streamUrl = x.movieUrl(id, o.optString("container_extension").ifBlank { "mp4" }),
            )
        }
        return out
    }

    private fun loadSeries(x: XtreamAccess): List<Series> {
        val names = categoryNames(x.api("get_series_categories"))
        val arr = JSONArray(Http.get(x.api("get_series"), readTimeoutMs = 120_000))
        val out = ArrayList<Series>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("series_id")
            if (id.isEmpty()) continue
            out += Series(
                id = id,
                name = o.optString("name").ifBlank { "Series $id" },
                coverUrl = o.optString("cover").takeIf { it.isNotBlank() },
                category = names[o.optString("category_id")] ?: "Other",
                plot = o.optString("plot"),
                genre = o.optString("genre"),
                year = o.optString("releaseDate").ifBlank { o.optString("release_date") }.take(4),
                rating = o.optString("rating"),
                addedEpoch = o.optString("last_modified").toLongOrNull() ?: 0L,
            )
        }
        return out
    }

    private fun categoryNames(address: String): Map<String, String> {
        val map = HashMap<String, String>()
        try {
            val arr = JSONArray(Http.get(address))
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                map[o.optString("category_id")] = o.optString("category_name")
            }
        } catch (e: Exception) {
            // Categories are a nice-to-have. Without them, everything goes under "Other".
        }
        return map
    }
}
