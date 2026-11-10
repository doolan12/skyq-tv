package com.skyqlike.iptv.ui.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.data.scores.GameState
import com.skyqlike.iptv.data.scores.Score
import com.skyqlike.iptv.data.scores.ScoresRepository
import com.skyqlike.iptv.data.model.Programme
import com.skyqlike.iptv.data.model.favKey
import com.skyqlike.iptv.ui.common.Artwork
import com.skyqlike.iptv.ui.guide.CategoryBar
import com.skyqlike.iptv.ui.home.FocusTile
import com.skyqlike.iptv.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The sports on the filter buttons, with words that give a show away as that sport. */
private val Sports = listOf(
    "Football" to listOf("football", "soccer", "premier league", "champions league", "europa league", "la liga", "bundesliga", "serie a", "ligue 1", "a-league", "fifa", "uefa", "world cup", "socceroos", "matildas"),
    "Rugby" to listOf("rugby", "nrl", "super rugby", "six nations", "wallabies", "state of origin"),
    "AFL" to listOf("afl", "aussie rules", "australian football"),
    "Cricket" to listOf("cricket", "big bash", "test match", "t20", "ashes"),
    "Tennis" to listOf("tennis", "atp", "wta", "wimbledon", "us open", "australian open", "roland garros"),
    "Basketball" to listOf("basketball", "nba", "nbl", "wnba"),
    "Motorsport" to listOf("formula 1", "formula one", "f1", "motogp", "supercars", "nascar", "grand prix", "indycar", "rally"),
    "Fighting" to listOf("ufc", "boxing", "wwe", "mma", "wrestling", "fight night"),
)
private val Labels = listOf("All sport") + Sports.map { it.first }
private val AllWords = Sports.flatMap { it.second }

private const val LOOK_AHEAD_MS = 12L * 60 * 60 * 1000
private const val SOON_MS = 3L * 60 * 60 * 1000
private const val MAX_RESULTS = 150

private data class Match(val channel: Channel, val programme: Programme)

private fun Programme.text() = "$title $genre $description".lowercase(Locale.ROOT)

private fun isSport(channel: Channel, p: Programme, words: List<String>?): Boolean {
    val text = p.text()
    // A chosen sport must match by name. "All sport" also accepts anything on a sport channel or marked as sport.
    if (words != null) return words.any { text.contains(it) }
    return channel.group.contains("sport", ignoreCase = true) ||
        p.genre.contains("sport", ignoreCase = true) ||
        AllWords.any { text.contains(it) }
}

/**
 * Everything sporty that is on now or starting in the next 12 hours, from the TV guide:
 * pick a sport, see what is live and what is coming, tap to watch.
 */
@Composable
fun MatchCentreScreen(
    channels: List<Channel>,
    epg: Map<String, List<Programme>>,
    favouriteKeys: Set<String>,
    scoresRepo: ScoresRepository,
    onPlay: (Channel) -> Unit,
    /** True on a phone: tighter margins, and score lines wrap onto two lines. */
    compact: Boolean = false,
) {
    val edge = if (compact) 16.dp else 36.dp
    var sportIndex by remember { mutableIntStateOf(0) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowMs = System.currentTimeMillis()
        }
    }
    val clock = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    // Scores come from free public websites and refresh every minute while this page is open.
    var scores by remember { mutableStateOf<List<Score>>(emptyList()) }
    LaunchedEffect(Unit) {
        while (true) {
            scores = runCatching { scoresRepo.load() }.getOrDefault(scores)
            delay(60_000)
        }
    }

    val matches by produceState(emptyList<Match>(), channels, epg, sportIndex, nowMs) {
        value = withContext(Dispatchers.Default) {
            val words = if (sportIndex == 0) null else Sports[sportIndex - 1].second
            val end = nowMs + LOOK_AHEAD_MS
            val out = ArrayList<Match>()
            for (ch in channels) {
                val list = ch.epgId?.let { epg[it] } ?: continue
                var taken = 0
                for (p in list) {
                    if (p.stop <= nowMs) continue
                    if (p.start >= end) break
                    if (isSport(ch, p, words)) {
                        out += Match(ch, p)
                        if (++taken >= 2) break
                    }
                }
            }
            // Live ones first, then by start time. Favourite channels win ties.
            out.sortedWith(
                compareBy<Match>({ it.programme.start > nowMs }, { it.programme.start }, { it.channel.favKey !in favouriteKeys })
            ).take(MAX_RESULTS)
        }
    }

    val sportName = Labels[sportIndex]
    // Games under way, or finished in the last few hours, for the chosen sport.
    val shownScores = scores.filter { sc ->
        (sportIndex == 0 || sc.sport == sportName) &&
            (sc.state == GameState.LIVE || (sc.state == GameState.FINISHED && sc.startMs > nowMs - 8 * 3600_000L))
    }.sortedBy { if (it.state == GameState.LIVE) 0 else 1 }.take(40)
    // Which TV show is carrying each game, found by team names in the guide listing.
    fun scoreFor(m: Match): Score? {
        val t = m.programme.text()
        return scores.firstOrNull { sc -> sc.homeNames.any { t.contains(it) } && sc.awayNames.any { t.contains(it) } }
    }
    val live = matches.filter { it.programme.start <= nowMs }
    val soon = matches.filter { it.programme.start > nowMs && it.programme.start < nowMs + SOON_MS }
    val later = matches.filter { it.programme.start >= nowMs + SOON_MS }

    Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
        Text("Match Centre", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary, modifier = Modifier.padding(horizontal = edge))
        Text(
            "Sport on now and coming up in the next 12 hours, from your TV guide.",
            fontSize = 14.sp,
            color = Palette.TextSecondary,
            modifier = Modifier.padding(horizontal = edge, vertical = 4.dp),
        )
        CategoryBar(Labels, sportIndex) { sportIndex = it }
        if (matches.isEmpty() && shownScores.isEmpty()) {
            Text(
                if (epg.isEmpty()) "There is no TV guide loaded yet." else "No sport found in the guide for this choice.",
                fontSize = 16.sp,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(edge),
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = edge, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (shownScores.isNotEmpty()) {
                    item(key = "h:scores") {
                        Text("Scores", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary, modifier = Modifier.padding(top = 8.dp))
                    }
                    items(shownScores, key = { "s|${it.sport}|${it.home}|${it.away}|${it.startMs}" }) { sc ->
                        // Tapping a score plays the channel showing that game, if the guide has one.
                        val carrier = matches.firstOrNull { m ->
                            val t = m.programme.text()
                            sc.homeNames.any { t.contains(it) } && sc.awayNames.any { t.contains(it) }
                        }?.channel
                        FocusTile(
                            onClick = { carrier?.let(onPlay) },
                            modifier = Modifier.fillMaxWidth().height(64.dp),
                            corner = 12.dp,
                            focusedScale = 1.02f,
                        ) {
                            Row(
                                Modifier.fillMaxSize().padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Text(
                                    if (sc.state == GameState.LIVE) "LIVE" else "FT",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (sc.state == GameState.LIVE) Color(0xFFFF5A5A) else Palette.TextSecondary,
                                    modifier = Modifier.width(56.dp),
                                )
                                val detailText = listOf(sc.league, sc.detail).filter { it.isNotBlank() }.joinToString("  |  ") + if (carrier != null) "  |  ${carrier.name}" else ""
                                if (compact) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            "${sc.home}  ${sc.homeScore} - ${sc.awayScore}  ${sc.away}",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Palette.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(detailText, fontSize = 12.sp, color = Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                } else {
                                    Text(
                                        "${sc.home}  ${sc.homeScore} - ${sc.awayScore}  ${sc.away}",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Palette.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        listOf(sc.league, sc.detail).filter { it.isNotBlank() }.joinToString("  |  ") + if (carrier != null) "  |  ${carrier.name}" else "",
                                        fontSize = 13.sp,
                                        color = Palette.TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                section("On now", live, nowMs, clock, ::scoreFor, compact, onPlay)
                section("Starting soon", soon, nowMs, clock, ::scoreFor, compact, onPlay)
                section("Later today", later, nowMs, clock, ::scoreFor, compact, onPlay)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    list: List<Match>,
    nowMs: Long,
    clock: SimpleDateFormat,
    scoreFor: (Match) -> Score?,
    compact: Boolean,
    onPlay: (Channel) -> Unit,
) {
    if (list.isEmpty()) return
    item(key = "h:$title") {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary, modifier = Modifier.padding(top = 8.dp))
    }
    items(list, key = { "${it.channel.id}|${it.programme.start}" }) { m ->
        val isLive = m.programme.start <= nowMs
        val recordContext = androidx.compose.ui.platform.LocalContext.current
        Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FocusTile(
            onClick = { onPlay(m.channel) },
            modifier = Modifier.weight(1f).fillMaxSize(),
            corner = 12.dp,
            focusedScale = 1.02f,
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    if (isLive) "LIVE" else clock.format(Date(m.programme.start)),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isLive) Color(0xFFFF5A5A) else Palette.Accent,
                    modifier = Modifier.width(if (compact) 62.dp else 84.dp),
                )
                if (!compact) Artwork(m.channel.logoUrl, m.channel.name, Modifier.width(64.dp).height(44.dp).clip(RoundedCornerShape(8.dp)), ContentScale.Fit)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val sc = if (isLive) scoreFor(m) else null
                    Text(m.programme.title + if (sc != null) "   ${sc.homeScore} - ${sc.awayScore}" + (if (sc.detail.isNotBlank()) "  (${sc.detail})" else "") else "", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(m.channel.name, fontSize = 13.sp, color = Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (isLive) {
                        val progress = ((nowMs - m.programme.start).toFloat() / (m.programme.stop - m.programme.start).coerceAtLeast(1L)).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF))) {
                            Box(Modifier.fillMaxWidth(progress.coerceAtLeast(0.02f)).height(3.dp).background(Palette.Accent))
                        }
                    }
                }
            }
        }
        FocusTile(
            onClick = { com.skyqlike.iptv.ui.record.recordProgramme(recordContext, m.channel, m.programme) },
            modifier = Modifier.width(if (compact) 64.dp else 96.dp).fillMaxSize(),
            corner = 12.dp,
            focusedScale = 1.05f,
        ) {
            Text("REC", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5A5A), modifier = Modifier.align(Alignment.Center))
        }
        }
    }
}
