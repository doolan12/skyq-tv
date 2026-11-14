package com.skyqlike.iptv.data.intro

import com.skyqlike.iptv.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class SegmentKind { INTRO, RECAP, CREDITS }

/** A part of an episode that can be skipped. Times are milliseconds from the start of the episode. */
data class Segment(val kind: SegmentKind, val startMs: Long, val endMs: Long)

/**
 * Intro, recap and credits times from TheIntroDB, a free community database (no key needed).
 * It doesn't know every show, and its times can be wrong, so odd-looking ones are thrown away.
 */
object IntroRepository {

    suspend fun segments(tmdbId: Int, season: Int, episode: Int, durationMs: Long): List<Segment> = withContext(Dispatchers.IO) {
        if (tmdbId <= 0 || season <= 0 || episode <= 0) return@withContext emptyList()
        val body = try {
            Http.get("https://api.theintrodb.org/v3/media?tmdb_id=$tmdbId&season=$season&episode=$episode&duration_ms=$durationMs", 15_000)
        } catch (e: Exception) {
            return@withContext emptyList()
        }
        val out = ArrayList<Segment>()
        try {
            val o = JSONObject(body)
            fun read(key: String, kind: SegmentKind) {
                val arr = o.optJSONArray(key) ?: return
                for (i in 0 until arr.length()) {
                    val s = arr.optJSONObject(i) ?: continue
                    val start = if (s.has("start_ms") && !s.isNull("start_ms")) s.optLong("start_ms") else null
                    val end = if (s.has("end_ms") && !s.isNull("end_ms")) s.optLong("end_ms") else null
                    clean(kind, start, end, durationMs)?.let { out += Segment(kind, it.first, it.second) }
                }
            }
            read("intro", SegmentKind.INTRO)
            read("recap", SegmentKind.RECAP)
            read("credits", SegmentKind.CREDITS)
        } catch (e: Exception) {
            // Not the answer we expected: no skip buttons.
        }
        out
    }

    /** Keeps a section only if it makes sense for an episode of this length. */
    internal fun clean(kind: SegmentKind, start: Long?, end: Long?, duration: Long): Pair<Long, Long>? {
        val limit = if (duration > 0) duration else Long.MAX_VALUE / 4
        return when (kind) {
            SegmentKind.INTRO, SegmentKind.RECAP -> {
                val e = end ?: return null
                val s = start ?: 0L
                val len = e - s
                val longest = if (kind == SegmentKind.INTRO) 180_000L else 240_000L
                if (len < 5_000 || len > longest || e > limit + 5_000) return null
                // An intro belongs in the first half of an episode, a recap in the first third.
                if (duration > 0 && s > duration * (if (kind == SegmentKind.INTRO) 0.5 else 0.35)) return null
                s to minOf(e, limit)
            }
            SegmentKind.CREDITS -> {
                val s = start ?: return null
                val e = end ?: limit
                if (e - s < 5_000) return null
                // Credits belong in the last quarter.
                if (duration > 0 && (s < duration * 0.75 || s >= duration)) return null
                s to minOf(e, limit)
            }
        }
    }
}
