package com.skyqlike.iptv.data.tmdb

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Pictures and a short description for one movie or show, from The Movie Database (TMDB). */
class TmdbArt(val poster: String?, val backdrop: String?, val overview: String?)

/** What we are looking for. ANY is for live TV shows, where we don't know if it's a film or a series. */
enum class ArtKind { MOVIE, TV, ANY }

/**
 * Looks up artwork on TMDB. Every answer is saved on the device, so each title is only asked about once
 * (titles with no result are tried again after a week).
 */
class TmdbRepository(context: Context) {
    private val prefs = context.getSharedPreferences("tmdb_cache", Context.MODE_PRIVATE)

    suspend fun lookup(key: String, title: String, year: String? = null, kind: ArtKind = ArtKind.ANY): TmdbArt? =
        withContext(Dispatchers.IO) {
            if (key.isBlank()) return@withContext null
            val clean = cleanTitle(title)
            if (clean.length < 2) return@withContext null
            val ck = "${kind.name}|${clean.lowercase()}|${year.orEmpty()}"
            val saved = prefs.getString(ck, null)
            if (saved != null) {
                if (saved.startsWith("-")) {
                    val at = saved.drop(1).toLongOrNull() ?: 0L
                    if (System.currentTimeMillis() - at < WEEK_MS) return@withContext null
                } else {
                    return@withContext try {
                        val o = JSONObject(saved)
                        TmdbArt(o.optString("p").ifBlank { null }, o.optString("b").ifBlank { null }, o.optString("o").ifBlank { null })
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            val art = try {
                search(key, clean, year, kind)
            } catch (e: Exception) {
                return@withContext null // a network problem is not saved, so it is tried again later
            }
            if (prefs.all.size > 900) prefs.edit().clear().apply()
            val value = if (art == null) "-${System.currentTimeMillis()}" else JSONObject()
                .put("p", art.poster.orEmpty()).put("b", art.backdrop.orEmpty()).put("o", art.overview.orEmpty()).toString()
            prefs.edit().putString(ck, value).apply()
            art
        }

    /** Checks a key by looking up a well-known film. Returns a plain-English result. */
    suspend fun test(key: String): String = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext "Paste a key first."
        try {
            val art = search(key, "Inception", null, ArtKind.MOVIE)
            if (art != null) "The key works." else "Connected, but nothing came back. Try again."
        } catch (e: IOException) {
            if (e.message?.contains("401") == true) "TMDB refused the key. Check it was copied in full."
            else "Couldn't reach TMDB. Check the internet connection."
        } catch (e: Exception) {
            "Something went wrong: ${e.message}"
        }
    }

    /** Returns null when TMDB answers but has nothing. Throws when it can't answer. */
    private fun search(key: String, title: String, year: String?, kind: ArtKind): TmdbArt? {
        val endpoint = when (kind) {
            ArtKind.MOVIE -> "search/movie"
            ArtKind.TV -> "search/tv"
            ArtKind.ANY -> "search/multi"
        }
        // A v4 "read access token" is long and starts with eyJ; a v3 key is 32 letters and numbers.
        val bearer = key.length > 50 || key.startsWith("eyJ")
        val sb = StringBuilder("https://api.themoviedb.org/3/$endpoint?include_adult=false&language=en-AU&query=")
        sb.append(URLEncoder.encode(title, "UTF-8"))
        val y = year?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
        if (y != null && kind == ArtKind.MOVIE) sb.append("&year=").append(y)
        if (y != null && kind == ArtKind.TV) sb.append("&first_air_date_year=").append(y)
        if (!bearer) sb.append("&api_key=").append(URLEncoder.encode(key, "UTF-8"))

        val conn = URL(sb.toString()).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", "application/json")
        if (bearer) conn.setRequestProperty("Authorization", "Bearer $key")
        val body = try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val results = JSONObject(body).optJSONArray("results") ?: return null
        val wanted = normal(title)
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            val type = r.optString("media_type")
            if (kind == ArtKind.ANY && type != "movie" && type != "tv") continue
            val name = r.optString("title").ifBlank { r.optString("name") }
            // For live shows we only accept the same title, so "News" doesn't turn into a random film.
            if (kind == ArtKind.ANY && normal(name) != wanted) continue
            val backdrop = r.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" }
            val poster = r.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }
            if (backdrop == null && poster == null) continue
            return TmdbArt(
                poster = poster?.let { "https://image.tmdb.org/t/p/w500$it" },
                backdrop = backdrop?.let { "https://image.tmdb.org/t/p/w1280$it" },
                overview = r.optString("overview").ifBlank { null },
            )
        }
        return null
    }

    private fun normal(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** Takes provider clutter out of a name: "EN - Dune (2021) 4K [MULTI]" becomes "Dune". */
    private fun cleanTitle(raw: String): String {
        var t = raw
        t = t.replace(Regex("\\[[^\\]]*\\]"), " ")
        t = t.replace(Regex("\\([^)]*\\)"), " ")
        t = t.replace(Regex("^\\s*[A-Za-z]{2,3}\\s*[-:|]\\s+"), "")
        t = t.replace(Regex("\\b(4K|UHD|FHD|HD|SD|HEVC|H265|1080p|720p|MULTI|NEW|LIVE)\\b", RegexOption.IGNORE_CASE), " ")
        return t.replace(Regex("\\s+"), " ").trim(' ', '-', ':', '|')
    }

    private companion object {
        const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    }
}
