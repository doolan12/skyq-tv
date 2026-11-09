package com.skyqlike.iptv.data.scores

import com.skyqlike.iptv.data.Http
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class GameState { UPCOMING, LIVE, FINISHED }

/** One game and its score. [sport] matches the sport buttons in Match Centre. */
data class Score(
    val sport: String,
    val league: String,
    val home: String,
    val away: String,
    /** Other ways the TV guide might write each team's name, lower case ("man city"). */
    val homeNames: List<String>,
    val awayNames: List<String>,
    val homeScore: String,
    val awayScore: String,
    val state: GameState,
    /** "67'", "Half time", "Q3 4:12", "Full time", "Sat 7:30 PM"... */
    val detail: String,
    val startMs: Long,
)

/**
 * Live scores from free public sources: ESPN's public scoreboards (football, NRL, basketball)
 * and Squiggle (AFL). These are unofficial, so any one of them can change or go away without warning.
 * Each source is fetched on its own, so if one breaks the others still show.
 */
class ScoresRepository {

    private var cached: List<Score> = emptyList()
    private var cachedAt = 0L

    suspend fun load(force: Boolean = false): List<Score> {
        val now = System.currentTimeMillis()
        // Scores don't need to be fresher than a minute, and this keeps us polite to the free servers.
        if (!force && cached.isNotEmpty() && now - cachedAt < 60_000L) return cached
        val result = withContext(Dispatchers.IO) {
            coroutineScope {
                val jobs = EspnSources.map { src -> async { runCatching { espn(src) }.getOrDefault(emptyList()) } } +
                    async { runCatching { squiggle() }.getOrDefault(emptyList()) }
                jobs.awaitAll().flatten()
            }
        }
        // If everything failed, keep showing the last good set rather than nothing.
        if (result.isNotEmpty()) {
            cached = result
            cachedAt = now
        }
        return cached
    }

    private class Source(val sport: String, val path: String, val league: String)

    private companion object {
        const val UA = "SkyQLikeIPTV/1.0 (personal app)"
        const val ESPN = "https://site.api.espn.com/apis/site/v2/sports/"

        val EspnSources = listOf(
            Source("Football", "soccer/all", "Football"),
            Source("Rugby", "rugby-league/3", "NRL"),
            Source("Basketball", "basketball/nba", "NBA"),
        )
    }

    private fun espn(src: Source): List<Score> {
        val json = JSONObject(Http.get(ESPN + src.path + "/scoreboard", 20_000, UA))
        val events = json.optJSONArray("events") ?: return emptyList()
        val out = ArrayList<Score>()
        for (i in 0 until events.length()) {
            val e = events.getJSONObject(i)
            val comp = e.optJSONArray("competitions")?.optJSONObject(0) ?: continue
            val teams = comp.optJSONArray("competitors") ?: continue
            var home: JSONObject? = null
            var away: JSONObject? = null
            for (t in 0 until teams.length()) {
                val c = teams.getJSONObject(t)
                if (c.optString("homeAway") == "home") home = c else if (c.optString("homeAway") == "away") away = c
            }
            if (home == null || away == null) continue
            val status = e.optJSONObject("status")?.optJSONObject("type")
            val state = when (status?.optString("state")) {
                "in" -> GameState.LIVE
                "post" -> GameState.FINISHED
                else -> GameState.UPCOMING
            }
            val clock = e.optJSONObject("status")?.optString("displayClock").orEmpty()
            val detail = when (state) {
                GameState.LIVE -> status?.optString("shortDetail").orEmpty().ifBlank { clock }
                GameState.FINISHED -> "Full time"
                GameState.UPCOMING -> ""
            }
            out += Score(
                sport = src.sport,
                league = src.league,
                home = home.team().optString("displayName"),
                away = away.team().optString("displayName"),
                homeNames = home.teamNames(),
                awayNames = away.teamNames(),
                homeScore = home.optString("score", "0").ifBlank { "0" },
                awayScore = away.optString("score", "0").ifBlank { "0" },
                state = state,
                detail = detail,
                startMs = parseIso(e.optString("date")),
            )
        }
        return out
    }

    private fun JSONObject.team(): JSONObject = optJSONObject("team") ?: JSONObject()

    private fun JSONObject.teamNames(): List<String> {
        val t = team()
        return listOf("displayName", "shortDisplayName", "name", "location")
            .map { t.optString(it).lowercase(Locale.ROOT).trim() }
            .filter { it.length >= 3 }
            .distinct()
    }

    /** AFL, from Squiggle (a free AFL fixtures and scores service that asks apps to say who they are). */
    private fun squiggle(): List<Score> {
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val json = JSONObject(Http.get("https://api.squiggle.com.au/?q=games;year=$year", 20_000, UA))
        val games = json.optJSONArray("games") ?: return emptyList()
        val now = System.currentTimeMillis()
        val out = ArrayList<Score>()
        for (i in 0 until games.length()) {
            val g = games.getJSONObject(i)
            val start = g.optLong("unixtime", 0L) * 1000L
            // Only games from yesterday to tomorrow matter here.
            if (start < now - 24 * 3600_000L || start > now + 24 * 3600_000L) continue
            val complete = g.optInt("complete", 0)
            val state = when {
                complete >= 100 -> GameState.FINISHED
                complete > 0 || start <= now -> GameState.LIVE
                else -> GameState.UPCOMING
            }
            val home = g.optString("hteam")
            val away = g.optString("ateam")
            if (home.isBlank() || away.isBlank()) continue
            out += Score(
                sport = "AFL",
                league = "AFL",
                home = home,
                away = away,
                homeNames = listOf(home.lowercase(Locale.ROOT)),
                awayNames = listOf(away.lowercase(Locale.ROOT)),
                homeScore = if (g.isNull("hscore")) "0" else g.optString("hscore", "0"),
                awayScore = if (g.isNull("ascore")) "0" else g.optString("ascore", "0"),
                state = state,
                detail = when (state) {
                    GameState.LIVE -> g.optString("timestr").ifBlank { "Live" }
                    GameState.FINISHED -> "Full time"
                    GameState.UPCOMING -> ""
                },
                startMs = start,
            )
        }
        return out
    }

    private fun parseIso(text: String): Long {
        if (text.isBlank()) return 0L
        // ESPN gives times like 2026-11-09T09:30Z or 2026-11-09T09:30:00Z.
        for (pattern in listOf("yyyy-MM-dd'T'HH:mm'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")) {
            try {
                val f = java.text.SimpleDateFormat(pattern, Locale.US)
                f.timeZone = java.util.TimeZone.getTimeZone("UTC")
                return f.parse(text)?.time ?: 0L
            } catch (_: Exception) {
            }
        }
        return 0L
    }
}
