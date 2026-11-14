package com.skyqlike.iptv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.scores.GameState
import com.skyqlike.iptv.data.scores.Score

/** A short message about the person's team: kick-off, a goal, full time. */
class TeamAlert(val title: String, val text: String)

/**
 * Watches the scores for changes in your team's game. The first look only takes note of where things stand,
 * so nothing pops up for a game that was already under way when the app opened.
 */
class TeamAlertTracker {
    private val seen = HashMap<String, Score>()
    private var seeded = false

    fun check(scores: List<Score>, teamText: String): List<TeamAlert> {
        val names = teamNames(teamText)
        if (names.isEmpty()) {
            seen.clear()
            seeded = false
            return emptyList()
        }
        val mine = scores.filter { sc -> names.any { sc.home.lowercase().contains(it) || sc.away.lowercase().contains(it) } }
        val out = ArrayList<TeamAlert>()
        if (seeded) {
            for (sc in mine) {
                val prev = seen[keyOf(sc)] ?: continue
                val line = "${sc.home}  ${sc.homeScore} - ${sc.awayScore}  ${sc.away}"
                if (prev.state == GameState.UPCOMING && sc.state == GameState.LIVE) {
                    out += TeamAlert("Kick-off", "${sc.home} v ${sc.away}" + if (sc.league.isNotBlank()) "  |  ${sc.league}" else "")
                }
                if (sc.state != GameState.UPCOMING && (grew(prev.homeScore, sc.homeScore) || grew(prev.awayScore, sc.awayScore))) {
                    out += TeamAlert("GOAL!", line)
                }
                if (prev.state == GameState.LIVE && sc.state == GameState.FINISHED) {
                    out += TeamAlert("Full time", line)
                }
            }
        }
        mine.forEach { seen[keyOf(it)] = it }
        seeded = true
        return out
    }

    private fun keyOf(sc: Score) = "${sc.home}|${sc.away}|${sc.startMs / 86_400_000L}"

    private fun grew(before: String, after: String): Boolean {
        val a = before.trim().toIntOrNull() ?: return false
        val b = after.trim().toIntOrNull() ?: return false
        return b > a
    }
}

/** The little message at the top of the screen. It sits above the player too. */
@Composable
fun TeamAlertBanner(alert: TeamAlert, modifier: Modifier = Modifier) {
    Column(
        modifier
            .padding(top = 20.dp, start = 16.dp, end = 16.dp)
            .widthIn(max = 520.dp)
            .background(Color(0xF2102060), RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(alert.title.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8FB4FF), maxLines = 1)
        Text(alert.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
