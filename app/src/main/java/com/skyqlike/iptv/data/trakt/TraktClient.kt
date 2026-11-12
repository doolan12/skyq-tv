package com.skyqlike.iptv.data.trakt

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** A title on the user's Trakt watchlist. */
data class TraktListItem(val type: String, val imdb: String, val name: String)

/**
 * Talks to Trakt: sign in with a short code, record what you've watched, and keep the watchlist in step with My List.
 * Every function here makes web requests, so call them from a background thread.
 */
class TraktClient(context: Context) {
    private val prefs = context.getSharedPreferences("trakt", Context.MODE_PRIVATE)

    val connected: Boolean get() = !prefs.getString("access", null).isNullOrBlank()
    val username: String get() = prefs.getString("user", "") ?: ""

    class Code(val userCode: String, val verifyUrl: String, val deviceCode: String, val intervalSec: Int, val expiresSec: Int)

    private class Reply(val code: Int, val body: String)

    private fun request(method: String, path: String, body: String? = null, auth: Boolean = true): Reply {
        val conn = URL(API + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.requestMethod = method
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("trakt-api-version", "2")
        conn.setRequestProperty("trakt-api-key", CLIENT_ID)
        conn.setRequestProperty("User-Agent", "Reelhouse/1.0")
        if (auth) prefs.getString("access", null)?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
        try {
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return Reply(code, stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            conn.disconnect()
        }
    }

    /** Like [request], but if the sign-in has run out it renews it once and tries again. */
    private fun authed(method: String, path: String, body: String? = null): Reply {
        val first = request(method, path, body)
        if (first.code != 401) return first
        return if (refresh()) request(method, path, body) else first
    }

    private fun saveTokens(json: JSONObject) {
        prefs.edit()
            .putString("access", json.optString("access_token"))
            .putString("refresh", json.optString("refresh_token"))
            .apply()
    }

    private fun refresh(): Boolean = runCatching {
        val refresh = prefs.getString("refresh", null) ?: return false
        val r = request(
            "POST", "/oauth/token",
            JSONObject().put("refresh_token", refresh).put("client_id", CLIENT_ID)
                .put("redirect_uri", "urn:ietf:wg:oauth:2.0:oob").put("grant_type", "refresh_token").toString(),
            auth = false,
        )
        if (r.code in 200..299) { saveTokens(JSONObject(r.body)); true } else false
    }.getOrDefault(false)

    // ---- Sign in with a code shown on the screen ----

    fun requestCode(): Result<Code> = runCatching {
        val r = request("POST", "/oauth/device/code", JSONObject().put("client_id", CLIENT_ID).toString(), auth = false)
        if (r.code !in 200..299) error("Trakt said no (code ${r.code}). ${r.body.take(120)}")
        val j = JSONObject(r.body)
        Code(j.getString("user_code"), j.getString("verification_url"), j.getString("device_code"), j.optInt("interval", 5), j.optInt("expires_in", 600))
    }

    /** Returns: 1 = signed in, 0 = still waiting, -1 = failed or expired (message in [lastError]). */
    var lastError: String = ""
    fun pollToken(deviceCode: String): Int = runCatching {
        val r = request("POST", "/oauth/device/token", JSONObject().put("code", deviceCode).put("client_id", CLIENT_ID).toString(), auth = false)
        when (r.code) {
            200 -> { saveTokens(JSONObject(r.body)); 1 }
            400, 429 -> 0
            404 -> { lastError = "Trakt didn't recognise that code."; -1 }
            409 -> { lastError = "That code was already used."; -1 }
            410 -> { lastError = "The code expired. Try again."; -1 }
            418 -> { lastError = "The sign-in was refused."; -1 }
            else -> { lastError = "Trakt said no (code ${r.code}). ${r.body.take(120)}"; -1 }
        }
    }.getOrElse { 0 }

    fun loadUsername() {
        runCatching {
            val r = authed("GET", "/users/settings")
            if (r.code in 200..299) {
                val user = JSONObject(r.body).optJSONObject("user")
                prefs.edit().putString("user", user?.optString("username") ?: "").apply()
            }
        }
    }

    fun disconnect() {
        prefs.edit().clear().apply()
    }

    // ---- Watched ----

    /** Titles Trakt says you've watched, saved on the device so the screens can show ticks. */
    fun isWatched(key: String): Boolean = prefs.getStringSet("watched", emptySet())?.contains(key) == true

    /** Downloads the watched list from Trakt and keeps it. Returns how many items it holds. */
    fun syncWatched(): Int {
        if (!connected) return 0
        val keys = watchedKeys()
        if (keys.isNotEmpty()) prefs.edit().putStringSet("watched", keys).apply()
        return keys.size
    }

    private fun rememberWatched(key: String) {
        val set = HashSet(prefs.getStringSet("watched", emptySet()) ?: emptySet())
        if (set.add(key)) prefs.edit().putStringSet("watched", set).apply()
    }

    private fun now(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.000'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

    /** Records a finished movie or episode. [key] is the app's own key, like stremio:movie:tt123 or stremio:series:tt1|tt1:2:3. */
    fun markWatched(key: String) {
        runCatching {
            val rest = key.removePrefix("stremio:")
            val type = rest.substringBefore(':')
            val body = if (type == "movie") {
                val imdb = rest.substringAfter(':').substringBefore('|')
                if (!imdb.startsWith("tt")) return
                JSONObject().put("movies", JSONArray().put(JSONObject().put("ids", JSONObject().put("imdb", imdb)).put("watched_at", now())))
            } else {
                val show = rest.substringAfter(':').substringBefore('|')
                val parts = rest.substringAfter('|', "").split(':')
                if (!show.startsWith("tt") || parts.size < 3) return
                val s = parts[parts.size - 2].toIntOrNull() ?: return
                val e = parts[parts.size - 1].toIntOrNull() ?: return
                JSONObject().put(
                    "shows",
                    JSONArray().put(
                        JSONObject().put("ids", JSONObject().put("imdb", show)).put(
                            "seasons",
                            JSONArray().put(JSONObject().put("number", s).put("episodes", JSONArray().put(JSONObject().put("number", e).put("watched_at", now())))),
                        )
                    ),
                )
            }
            val r = authed("POST", "/sync/history", body.toString())
            if (r.code in 200..299) rememberWatched(key)
        }
    }

    /** Everything already watched on Trakt, as the app's own keys. */
    fun watchedKeys(): Set<String> {
        val out = HashSet<String>()
        runCatching {
            val m = authed("GET", "/sync/watched/movies")
            if (m.code in 200..299) {
                val a = JSONArray(m.body)
                for (i in 0 until a.length()) {
                    val imdb = a.getJSONObject(i).optJSONObject("movie")?.optJSONObject("ids")?.optString("imdb") ?: continue
                    if (imdb.startsWith("tt")) out += "stremio:movie:$imdb"
                }
            }
        }
        runCatching {
            val s = authed("GET", "/sync/watched/shows")
            if (s.code in 200..299) {
                val a = JSONArray(s.body)
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    val imdb = o.optJSONObject("show")?.optJSONObject("ids")?.optString("imdb") ?: continue
                    if (!imdb.startsWith("tt")) continue
                    val seasons = o.optJSONArray("seasons") ?: continue
                    for (j in 0 until seasons.length()) {
                        val se = seasons.getJSONObject(j)
                        val sn = se.optInt("number")
                        val eps = se.optJSONArray("episodes") ?: continue
                        for (k in 0 until eps.length()) {
                            out += "stremio:series:$imdb|$imdb:$sn:${eps.getJSONObject(k).optInt("number")}"
                        }
                    }
                }
            }
        }
        return out
    }

    // ---- Suggestions and new episodes ----

    /** Trakt's picks for you, movies and series taking turns. */
    fun recommendations(): List<TraktListItem> {
        fun load(kind: String, type: String): List<TraktListItem> {
            val out = ArrayList<TraktListItem>()
            runCatching {
                val r = authed("GET", "/recommendations/$kind?limit=15")
                if (r.code in 200..299) {
                    val a = JSONArray(r.body)
                    for (i in 0 until a.length()) {
                        val o = a.getJSONObject(i)
                        val imdb = o.optJSONObject("ids")?.optString("imdb") ?: continue
                        if (imdb.startsWith("tt")) out += TraktListItem(type, imdb, o.optString("title"))
                    }
                }
            }
            return out
        }
        val movies = load("movies", "movie")
        val shows = load("shows", "series")
        val mixed = ArrayList<TraktListItem>()
        for (i in 0 until maxOf(movies.size, shows.size)) {
            shows.getOrNull(i)?.let { mixed += it }
            movies.getOrNull(i)?.let { mixed += it }
        }
        return mixed
    }

    /** Shows you follow that had an episode come out in the last week (newest first, one per show). */
    fun newEpisodes(): List<TraktListItem> {
        val out = LinkedHashMap<String, TraktListItem>()
        runCatching {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val start = fmt.format(Date(System.currentTimeMillis() - 7L * 24 * 3600 * 1000))
            val r = authed("GET", "/calendars/my/shows/$start/8")
            if (r.code in 200..299) {
                val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                val a = JSONArray(r.body)
                val list = (0 until a.length()).map { a.getJSONObject(it) }.sortedByDescending { it.optString("first_aired") }
                for (o in list) {
                    val aired = runCatching { iso.parse(o.optString("first_aired").take(19))?.time }.getOrNull() ?: continue
                    if (aired > System.currentTimeMillis()) continue
                    val show = o.optJSONObject("show") ?: continue
                    val imdb = show.optJSONObject("ids")?.optString("imdb") ?: continue
                    if (imdb.startsWith("tt") && imdb !in out) out[imdb] = TraktListItem("series", imdb, show.optString("title"))
                }
            }
        }
        return out.values.toList()
    }

    // ---- Watchlist (My List) ----

    fun watchlist(): List<TraktListItem> {
        val out = ArrayList<TraktListItem>()
        runCatching {
            val r = authed("GET", "/sync/watchlist")
            if (r.code in 200..299) {
                val a = JSONArray(r.body)
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    val isShow = o.optString("type") == "show"
                    val node = o.optJSONObject(if (isShow) "show" else "movie") ?: continue
                    val imdb = node.optJSONObject("ids")?.optString("imdb") ?: continue
                    if (imdb.startsWith("tt")) out += TraktListItem(if (isShow) "series" else "movie", imdb, node.optString("title"))
                }
            }
        }
        return out
    }

    fun setOnWatchlist(type: String, imdb: String, on: Boolean) {
        runCatching {
            if (!imdb.startsWith("tt")) return
            val item = JSONObject().put("ids", JSONObject().put("imdb", imdb))
            val body = JSONObject().put(if (type == "series") "shows" else "movies", JSONArray().put(item))
            authed("POST", if (on) "/sync/watchlist" else "/sync/watchlist/remove", body.toString())
        }
    }

    companion object {
        private const val API = "https://api.trakt.tv"
        /** This app's public Trakt Client ID. It isn't a secret. */
        const val CLIENT_ID = "2ogAKrwBOQtsOw7MAcdYTLHdRtAJNuMEymh-hDLGR5s"
    }
}
