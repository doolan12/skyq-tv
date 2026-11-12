package com.skyqlike.iptv.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.epg.progressAt
import com.skyqlike.iptv.data.history.HistoryEntry
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.data.model.Programme
import com.skyqlike.iptv.data.model.favKey
import com.skyqlike.iptv.data.scores.GameState
import com.skyqlike.iptv.data.scores.Score
import com.skyqlike.iptv.data.tmdb.ArtKind
import com.skyqlike.iptv.data.vod.Movie
import com.skyqlike.iptv.data.vod.Series
import com.skyqlike.iptv.ui.common.Artwork
import com.skyqlike.iptv.ui.common.HomeTile
import com.skyqlike.iptv.ui.common.Rail
import kotlinx.coroutines.delay

/** Extra things the Home page needs to be smart: scores, what you watch most, and what is new. */
class HomeExtras(
    val scores: List<Score> = emptyList(),
    val tmdb: com.skyqlike.iptv.data.tmdb.TmdbRepository? = null,
    val tmdbKey: String = "",
    val watchCounts: Map<String, Int> = emptyMap(),
    val movies: List<Movie> = emptyList(),
    val series: List<Series> = emptyList(),
    val onOpenMovie: (Movie) -> Unit = {},
    val onOpenSeries: (Series) -> Unit = {},
    /** The person's team, and its games found in the guide. */
    val teamName: String = "",
    val teamMatches: List<TeamMatch> = emptyList(),
    val onRemind: (TeamMatch) -> Unit = {},
)

/** A game that is on right now, with the channel and show carrying it. */
class LiveGame(val score: Score, val channel: Channel, val programme: Programme)

private fun showText(p: Programme) = "${p.title} ${p.description}".lowercase()

/** Finds, for each game that is under way, a channel whose current show names both teams. */
fun liveGames(scores: List<Score>, channels: List<Channel>, nowFor: (Channel) -> Programme?): List<LiveGame> {
    val live = scores.filter { it.state == GameState.LIVE }
    if (live.isEmpty()) return emptyList()
    val onAir = ArrayList<Triple<Channel, Programme, String>>()
    for (ch in channels) {
        val p = nowFor(ch) ?: continue
        onAir += Triple(ch, p, showText(p))
    }
    val out = ArrayList<LiveGame>()
    for (sc in live) {
        val hit = onAir.firstOrNull { (_, _, t) -> sc.homeNames.any { t.contains(it) } && sc.awayNames.any { t.contains(it) } } ?: continue
        out += LiveGame(sc, hit.first, hit.second)
    }
    return out
}

/** One slide of the big banner at the top of Home. */
data class Spotlight(
    val label: String,
    val title: String,
    val meta: String,
    val description: String,
    /** A wide picture that fills the banner (a show's own artwork). */
    val bannerUrl: String?,
    /** A smaller picture shown on the right (a poster or channel logo). */
    val posterUrl: String?,
    val posterFit: Boolean,
    val progress: Float?,
    val channel: Channel?,
    val watchLabel: String,
    val colors: List<Color>,
    val onWatch: () -> Unit,
    val onInfo: (() -> Unit)?,
    /** When set, and the slide has no banner picture, TMDB is asked for one using [artTitle]. */
    val artKind: ArtKind? = null,
    val artTitle: String = title,
    val artYear: String? = null,
)

private val SportColors = listOf(Color(0xFF0B5E3A), Color(0xFF14306B))
private val LiveColors = listOf(Color(0xFF2236C8), Color(0xFF6A2BB8), Color(0xFFC2306A))
private val FilmColors = listOf(Color(0xFF5A1A6B), Color(0xFF1B1F5E))
private val SeriesColors = listOf(Color(0xFF0E4F66), Color(0xFF2B2F7A))
private val ResumeColors = listOf(Color(0xFF7A3B12), Color(0xFF3A1F5E))

/**
 * Chooses what the banner shows, in an order that suits the time of day:
 * live sport first in the evening, where you left off first in the morning.
 */
fun buildSpotlights(
    channels: List<Channel>,
    nowFor: (Channel) -> Programme?,
    favouriteKeys: Set<String>,
    continueWatching: List<HistoryEntry>,
    live: List<LiveGame>,
    extras: HomeExtras,
    hour: Int,
    onPlay: (Channel) -> Unit,
    onPlayHistory: (HistoryEntry) -> Unit,
): List<Spotlight> {
    val now = System.currentTimeMillis()
    fun liveSlide(label: String, ch: Channel, p: Programme?, title: String, meta: String, colors: List<Color>, lookup: Boolean = false) = Spotlight(
        label = label,
        title = title,
        meta = meta,
        description = p?.description.orEmpty(),
        bannerUrl = p?.iconUrl,
        posterUrl = ch.logoUrl,
        posterFit = true,
        progress = p?.progressAt(now),
        channel = ch,
        watchLabel = "Watch Live",
        colors = colors,
        onWatch = { onPlay(ch) },
        onInfo = null,
        artKind = if (lookup && p != null) ArtKind.ANY else null,
        artTitle = p?.title ?: ch.name,
    )

    val sport = ArrayList<Spotlight>()
    live.take(2).forEach { g ->
        val s = g.score
        sport += liveSlide(
            "LIVE SPORT", g.channel, g.programme,
            "${s.home}  ${s.homeScore} - ${s.awayScore}  ${s.away}",
            listOf(s.league, s.detail, g.channel.name).filter { it.isNotBlank() }.joinToString("  |  "),
            SportColors,
        )
    }
    if (sport.isEmpty()) {
        channels.filter { it.group.contains("sport", ignoreCase = true) && nowFor(it) != null }
            .sortedByDescending { it.favKey in favouriteKeys }
            .firstOrNull()?.let { ch ->
                val p = nowFor(ch)
                sport += liveSlide("LIVE SPORT", ch, p, p?.title ?: ch.name, ch.name, SportColors)
            }
    }
    val favourite = channels.firstOrNull { it.favKey in favouriteKeys && nowFor(it) != null }?.let { ch ->
        val p = nowFor(ch)
        listOf(liveSlide("ON NOW  |  YOUR FAVOURITE", ch, p, p?.title ?: ch.name, ch.name, LiveColors, lookup = true))
    }.orEmpty()
    val resume = continueWatching.firstOrNull()?.let { e ->
        listOf(
            Spotlight(
                label = "CONTINUE WATCHING", title = e.title, meta = e.subtitle, description = "",
                bannerUrl = null, posterUrl = e.imageUrl, posterFit = false,
                progress = if (e.durationMs > 0) e.positionMs.toFloat() / e.durationMs else null,
                channel = null, watchLabel = "Resume", colors = ResumeColors,
                onWatch = { onPlayHistory(e) }, onInfo = null,
            )
        )
    }.orEmpty()
    val movie = extras.movies.filter { !it.posterUrl.isNullOrBlank() }.maxByOrNull { it.addedEpoch }?.let { m ->
        listOf(
            Spotlight(
                label = "NEW MOVIE", title = m.name,
                meta = listOf(m.year, m.genre.ifBlank { m.category }, if (m.durationMin > 0) "${m.durationMin} min" else "").filter { it.isNotBlank() }.joinToString("  |  "),
                description = m.description, bannerUrl = null, posterUrl = m.posterUrl, posterFit = false, progress = null,
                channel = null, watchLabel = "View", colors = FilmColors,
                onWatch = { extras.onOpenMovie(m) }, onInfo = null,
                artKind = ArtKind.MOVIE, artTitle = m.name, artYear = m.year,
            )
        )
    }.orEmpty()
    val series = extras.series.filter { !it.coverUrl.isNullOrBlank() }.maxByOrNull { it.addedEpoch }?.let { s ->
        listOf(
            Spotlight(
                label = "NEW TV SERIES", title = s.name,
                meta = listOf(s.year, s.category).filter { it.isNotBlank() }.joinToString("  |  "),
                description = s.plot, bannerUrl = null, posterUrl = s.coverUrl, posterFit = false, progress = null,
                channel = null, watchLabel = "View", colors = SeriesColors,
                onWatch = { extras.onOpenSeries(s) }, onInfo = null,
                artKind = ArtKind.TV, artTitle = s.name, artYear = s.year,
            )
        )
    }.orEmpty()

    // The person's own team always comes first: live now, or the next game if it is close.
    val teamSlides = ArrayList<Spotlight>()
    val names = teamNames(extras.teamName)
    if (names.isNotEmpty()) {
        val teamLive = live.firstOrNull { g -> names.any { n -> g.score.home.lowercase().contains(n) || g.score.away.lowercase().contains(n) } }
        val matches = extras.teamMatches
        val onNow = matches.firstOrNull { it.programme.start <= now }
        val next = matches.firstOrNull { it.programme.start > now && it.programme.start - now < 36L * 60 * 60 * 1000 }
        val pretty = extras.teamName.split(',').first().trim().replaceFirstChar { it.uppercase() }
        if (teamLive != null) {
            val sc = teamLive.score
            teamSlides += liveSlide(
                "$pretty  |  LIVE", teamLive.channel, teamLive.programme,
                "${sc.home}  ${sc.homeScore} - ${sc.awayScore}  ${sc.away}",
                listOf(sc.league, sc.detail, teamLive.channel.name).filter { it.isNotBlank() }.joinToString("  |  "),
                SportColors,
            )
        } else if (onNow != null) {
            teamSlides += liveSlide("$pretty  |  ON NOW", onNow.channel, onNow.programme, onNow.programme.title, onNow.channel.name, SportColors)
        } else if (next != null) {
            val p = next.programme
            teamSlides += Spotlight(
                label = "$pretty  |  NEXT GAME",
                title = p.title,
                meta = listOf(teamWhen(p, now), next.channel.name).joinToString("  |  "),
                description = p.description,
                bannerUrl = p.iconUrl, posterUrl = next.channel.logoUrl, posterFit = true, progress = null,
                channel = next.channel, watchLabel = "Remind me", colors = SportColors,
                onWatch = { extras.onRemind(next) }, onInfo = null,
            )
        }
    }

    val ordered = teamSlides + when (hour) {
        in 17..22 -> sport + favourite + resume + movie + series
        in 5..10 -> resume + favourite + series + movie + sport
        else -> resume + favourite + movie + series + sport
    }
    val result = ordered.distinctBy { it.title }.take(5)
    if (result.isNotEmpty()) return result
    // Nothing special to show: fall back to whatever is on now.
    val ch = channels.firstOrNull { nowFor(it) != null } ?: channels.firstOrNull() ?: return emptyList()
    val p = nowFor(ch)
    return listOf(liveSlide("ON NOW  |  ${ch.name}", ch, p, p?.title ?: ch.name, ch.group, LiveColors, lookup = true))
}

/** Builds the smart rows: games you can watch now, what you watch most, news. Empty ones are dropped later. */
fun smartRails(
    channels: List<Channel>,
    extras: HomeExtras,
    live: List<LiveGame>,
    onPlay: (Channel) -> Unit,
    tile: (Channel) -> HomeTile,
): List<Rail> {
    val liveRail = Rail(
        "Live Sport Now",
        live.take(15).map { g ->
            val s = g.score
            HomeTile(
                id = "live:${g.channel.id}:${s.home}",
                title = "${s.home}  ${s.homeScore} - ${s.awayScore}  ${s.away}",
                subtitle = listOf("LIVE", s.detail, g.channel.name).filter { it.isNotBlank() }.joinToString("  |  "),
                imageUrl = g.programme.iconUrl ?: g.channel.logoUrl,
                fit = g.programme.iconUrl == null,
            ) { onPlay(g.channel) }
        },
    )
    val byKey = channels.associateBy { it.favKey }
    val most = extras.watchCounts.entries.filter { it.value >= 2 }.sortedByDescending { it.value }.mapNotNull { byKey[it.key] }.take(12)
    val nowMs = System.currentTimeMillis()
    val teamRail = Rail(
        "My Team: " + extras.teamName.split(',').first().trim().replaceFirstChar { it.uppercase() },
        extras.teamMatches.take(15).map { m ->
            val p = m.programme
            val liveNow = p.start <= nowMs
            HomeTile(
                id = "team:${m.channel.id}:${p.start}",
                title = p.title,
                subtitle = listOf(teamWhen(p, nowMs), m.channel.name, if (liveNow) "" else "Tap for reminder").filter { it.isNotBlank() }.joinToString("  |  "),
                imageUrl = p.iconUrl ?: m.channel.logoUrl,
                fit = p.iconUrl == null,
            ) { if (liveNow) onPlay(m.channel) else extras.onRemind(m) }
        },
    )
    return listOf(
        teamRail,
        liveRail,
        Rail("Most Watched", most.map(tile)),
        Rail("News", channels.filter { it.group.contains("news", ignoreCase = true) }.take(25).map(tile)),
    )
}

private val EveningOrder = listOf("Live Sport Now", "Continue Watching", "On Now", "Favourite Channels", "Live Sports", "Most Watched", "New Movies", "New TV Series", "Movie Channels", "Recently Watched")
private val MorningOrder = listOf("Continue Watching", "On Now", "News", "Favourite Channels", "Most Watched", "Recently Watched", "New TV Series", "New Movies")
private val DayOrder = listOf("Continue Watching", "On Now", "Favourite Channels", "Live Sport Now", "New Movies", "New TV Series", "Most Watched", "Movie Channels")
private val NightOrder = listOf("Continue Watching", "Movie Channels", "New Movies", "New TV Series", "Favourite Channels", "On Now", "Most Watched")

/** Puts the rows in the order that suits the time of day. Rows not named keep their usual order, after the named ones. */
fun orderRails(rails: List<Rail>, hour: Int): List<Rail> {
    val order = when (hour) {
        in 5..10 -> MorningOrder
        in 11..16 -> DayOrder
        in 17..22 -> EveningOrder
        else -> NightOrder
    }
    return rails.withIndex().sortedBy { (i, r) ->
        val rank = if (r.title.startsWith("My Team")) 1 else order.indexOf(r.title)
        if (rank >= 0) rank else order.size + i
    }.map { it.value }
}

/**
 * Fills in wide banner pictures from TMDB where a slide has none. The slides show straight away,
 * and each one gets its picture as soon as it arrives.
 */
@Composable
fun rememberArtworkSpotlights(spotlights: List<Spotlight>, extras: HomeExtras): List<Spotlight> {
    val tmdb = extras.tmdb
    val key = extras.tmdbKey
    val shown by produceState(spotlights, spotlights, key, tmdb) {
        value = spotlights
        if (tmdb == null || key.isBlank()) return@produceState
        var current = spotlights
        spotlights.forEachIndexed { i, s ->
            val kind = s.artKind ?: return@forEachIndexed
            if (s.bannerUrl != null) return@forEachIndexed
            val art = tmdb.lookup(key, s.artTitle, s.artYear, kind) ?: return@forEachIndexed
            if (art.backdrop == null) return@forEachIndexed
            current = current.toMutableList().also {
                it[i] = s.copy(bannerUrl = art.backdrop, description = s.description.ifBlank { art.overview.orEmpty() })
            }
            value = current
        }
    }
    return shown
}

// ---------------------------------------------------------------- the banner

/** The big banner on the TV Home page. It changes slide every few seconds, until you move onto its buttons. */
@Composable
fun SpotlightHero(
    spotlights: List<Spotlight>,
    onMoreInfo: (Spotlight) -> Unit,
) {
    var index by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    val last = spotlights.lastIndex
    val current = spotlights[index.coerceIn(0, last)]
    LaunchedEffect(spotlights.size, focused, index) {
        if (!focused && spotlights.size > 1) {
            delay(8_000)
            index = (index + 1) % spotlights.size
        }
    }

    Box(
        Modifier
            .padding(horizontal = 36.dp)
            .fillMaxWidth()
            .height(330.dp)
            .clip(RoundedCornerShape(22.dp))
            .onFocusChanged { focused = it.hasFocus }
    ) {
        Crossfade(targetState = current, animationSpec = tween(700), label = "spotlight") { s ->
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(s.colors))) {
                if (s.bannerUrl != null) {
                    // The show's own artwork fills the banner, with a dark fade on the left so the writing reads.
                    Artwork(s.bannerUrl, s.title, Modifier.fillMaxSize(), ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xE6000000), Color(0x99000000), Color.Transparent))))
                } else if (s.posterUrl != null) {
                    Artwork(
                        s.posterUrl, s.title,
                        Modifier.align(Alignment.CenterEnd).padding(end = 40.dp).height(if (s.posterFit) 150.dp else 270.dp).width(if (s.posterFit) 280.dp else 180.dp).clip(RoundedCornerShape(14.dp)),
                        if (s.posterFit) ContentScale.Fit else ContentScale.Crop,
                    )
                }
                Column(
                    Modifier.align(Alignment.CenterStart).padding(start = 36.dp).width(560.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(s.label, color = Color(0xFFB8C7FF), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(s.title, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (s.meta.isNotBlank()) Text(s.meta, fontSize = 14.sp, color = Color(0xCCFFFFFF), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (s.description.isNotBlank()) Text(s.description, fontSize = 14.sp, color = Color(0xB3FFFFFF), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    s.progress?.let { p ->
                        Box(Modifier.fillMaxWidth(0.7f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x40FFFFFF))) {
                            Box(Modifier.fillMaxWidth(p.coerceIn(0f, 1f).coerceAtLeast(0.02f)).height(5.dp).background(Color.White))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                        FocusTile(onClick = s.onWatch, modifier = Modifier.width(160.dp).height(46.dp), corner = 23.dp, backgroundColor = Color.White) {
                            Text(s.watchLabel, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF101030), modifier = Modifier.align(Alignment.Center))
                        }
                        if (s.channel != null) {
                            FocusTile(onClick = { onMoreInfo(s) }, modifier = Modifier.width(150.dp).height(46.dp), corner = 23.dp, backgroundColor = Color(0x40FFFFFF)) {
                                Text("More Info", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
                            }
                        }
                    }
                }
            }
        }
        if (spotlights.size > 1) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FocusTile(onClick = { index = (index - 1 + spotlights.size) % spotlights.size }, modifier = Modifier.size(36.dp), corner = 18.dp, backgroundColor = Color(0x40000000)) {
                    Text("<", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
                }
                spotlights.indices.forEach { i ->
                    Box(Modifier.size(if (i == index) 10.dp else 7.dp).clip(CircleShape).background(if (i == index) Color.White else Color(0x80FFFFFF)))
                }
                FocusTile(onClick = { index = (index + 1) % spotlights.size }, modifier = Modifier.size(36.dp), corner = 18.dp, backgroundColor = Color(0x40000000)) {
                    Text(">", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

/** The phone's version of the banner: tap to open, swipe sideways to change slide. */
@Composable
fun PhoneSpotlight(spotlights: List<Spotlight>) {
    var index by remember { mutableIntStateOf(0) }
    var dragged by remember { mutableIntStateOf(0) }
    val last = spotlights.lastIndex
    val current = spotlights[index.coerceIn(0, last)]
    LaunchedEffect(spotlights.size, index, dragged) {
        if (spotlights.size > 1) {
            delay(7_000)
            index = (index + 1) % spotlights.size
        }
    }
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(18.dp))
            .pointerInput(spotlights.size) {
                detectTapGestures(onTap = { spotlights[index.coerceIn(0, spotlights.lastIndex)].onWatch() })
            }
            .pointerInput(spotlights.size) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (kotlin.math.abs(total) > 80f && spotlights.size > 1) {
                            index = if (total < 0) (index + 1) % spotlights.size else (index - 1 + spotlights.size) % spotlights.size
                            dragged++
                        }
                    },
                ) { _, amount -> total += amount }
            }
    ) {
        Crossfade(targetState = current, animationSpec = tween(600), label = "phoneSpotlight") { s ->
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(s.colors))) {
                if (s.bannerUrl != null) {
                    Artwork(s.bannerUrl, s.title, Modifier.fillMaxSize(), ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))))
                } else if (s.posterUrl != null) {
                    Artwork(
                        s.posterUrl, s.title,
                        Modifier.align(Alignment.TopEnd).padding(12.dp).height(if (s.posterFit) 60.dp else 120.dp).width(if (s.posterFit) 110.dp else 80.dp).clip(RoundedCornerShape(10.dp)),
                        if (s.posterFit) ContentScale.Fit else ContentScale.Crop,
                    )
                }
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(s.label, color = Color(0xFFB8C7FF), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(s.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (s.meta.isNotBlank()) Text(s.meta, fontSize = 12.sp, color = Color(0xCCFFFFFF), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    s.progress?.let { p ->
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x40FFFFFF))) {
                            Box(Modifier.fillMaxWidth(p.coerceIn(0f, 1f).coerceAtLeast(0.02f)).fillMaxHeight().background(Color.White))
                        }
                    }
                }
            }
        }
        if (spotlights.size > 1) {
            Row(Modifier.align(Alignment.TopStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                spotlights.indices.forEach { i ->
                    Box(Modifier.size(if (i == index) 9.dp else 6.dp).clip(CircleShape).background(if (i == index) Color.White else Color(0x80FFFFFF)))
                }
            }
        }
    }
}
