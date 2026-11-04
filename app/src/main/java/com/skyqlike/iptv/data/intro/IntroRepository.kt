package com.skyqlike.iptv.data.intro

import com.skyqlike.iptv.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A stretch of an episode that can be skipped, with the words for the button. Times are milliseconds. */
data class SkipSegment(val label: String, val startMs: Long, val endMs: Long)

/**
 * Looks up where the intro (and recap) of an episode are, from the community database at introdb.app.
 * It only knows episodes people have submitted times for, so many shows will have nothing, and then
 * no button appears. Reading needs no account. If anything goes wrong the answer is simply "nothing to skip".
 */
class IntroRepository {

    /** [videoId] is an episode id like tt0944947:1:1 (IMDb id, season, episode). */
    suspend fun segmentsFor(videoId: String?): List<SkipSegment> = withContext(Dispatchers.IO) {
        val parts = videoId?.split(':') ?: return@withContext emptyList()
        if (parts.size != 3 || !parts[0].startsWith("tt")) return@withContext emptyList()
        val season = parts[1].toIntOrNull() ?: return@withContext emptyList()
        val episode = parts[2].toIntOrNull() ?: return@withContext emptyList()
        try {
            val text = Http.get("https://api.introdb.app/segments?imdb_id=${parts[0]}&season=$season&episode=$episode", 10_000)
            val o = JSONObject(text)
            val out = ArrayList<SkipSegment>()
            segment(o.optJSONObject("recap"), "Skip Recap")?.let { out += it }
            segment(o.optJSONObject("intro"), "Skip Intro")?.let { out += it }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun segment(o: JSONObject?, label: String): SkipSegment? {
        if (o == null) return null
        val start = if (o.has("start_ms")) o.optLong("start_ms", -1L) else (o.optDouble("start_sec", -1.0) * 1000).toLong()
        val end = if (o.has("end_ms")) o.optLong("end_ms", -1L) else (o.optDouble("end_sec", -1.0) * 1000).toLong()
        // Ignore anything that looks wrong, like an end before the start or a "skip" longer than ten minutes.
        if (start < 0 || end <= start || end - start > 10 * 60_000L) return null
        return SkipSegment(label, start, end)
    }
}
