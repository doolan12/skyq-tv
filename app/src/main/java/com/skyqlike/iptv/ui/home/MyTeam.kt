package com.skyqlike.iptv.ui.home

import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.data.model.Programme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One game of the person's team, on a channel, from the TV guide. */
class TeamMatch(val channel: Channel, val programme: Programme)

/** The names the person typed, split on commas: "Liverpool, LFC" gives two names. */
fun teamNames(text: String): List<String> =
    text.split(',').map { it.trim().lowercase() }.filter { it.length >= 3 }

private val NotLive = Regex("replay|highlights|classic|re-live|relive|delayed|rewind|\\(r\\)", RegexOption.IGNORE_CASE)
private val FixtureLook = Regex(" v | vs | vs\\. | at | - ", RegexOption.IGNORE_CASE)

/**
 * Looks through the TV guide for games that name the person's team, from what is on now to [days] ahead.
 * The same game on several channels is listed once (sport channels are preferred). This is a lot of
 * searching, so call it off the main thread.
 */
fun findTeamMatches(
    teamText: String,
    channels: List<Channel>,
    epg: Map<String, List<Programme>>,
    nowMs: Long,
    days: Int = 7,
): List<TeamMatch> {
    val names = teamNames(teamText)
    if (names.isEmpty() || epg.isEmpty()) return emptyList()
    val end = nowMs + days * 24L * 60 * 60 * 1000
    val seen = HashSet<String>()
    val out = ArrayList<TeamMatch>()
    for (ch in channels.sortedByDescending { it.group.contains("sport", ignoreCase = true) }) {
        val list = epg[ch.epgId ?: continue] ?: continue
        val sporty = ch.group.contains("sport", ignoreCase = true)
        for (p in list) {
            if (p.stop <= nowMs || p.start > end) continue
            if (NotLive.containsMatchIn(p.title)) continue
            val title = p.title.lowercase()
            val inTitle = names.any { title.contains(it) }
            val inText = !inTitle && sporty && FixtureLook.containsMatchIn(p.title) && names.any { p.description.lowercase().contains(it) }
            if (!inTitle && !inText) continue
            if (!seen.add(title + "|" + p.start / 60_000L)) continue
            out += TeamMatch(ch, p)
        }
    }
    return out.sortedBy { it.programme.start }.take(24)
}

private val DayClock = SimpleDateFormat("EEE h:mm a", Locale.getDefault())

fun teamWhen(p: Programme, nowMs: Long): String =
    if (p.start <= nowMs) "LIVE NOW" else DayClock.format(Date(p.start))
