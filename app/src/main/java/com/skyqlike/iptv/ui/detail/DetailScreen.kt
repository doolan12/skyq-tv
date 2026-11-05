package com.skyqlike.iptv.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.skyqlike.iptv.data.stremio.StremioItem
import com.skyqlike.iptv.data.stremio.StremioStreams
import com.skyqlike.iptv.data.stremio.StremioVideo
import com.skyqlike.iptv.ui.common.ActionButton
import com.skyqlike.iptv.ui.common.Artwork
import com.skyqlike.iptv.ui.common.FocusTile
import com.skyqlike.iptv.ui.common.HomeTile
import com.skyqlike.iptv.ui.common.PosterTile
import com.skyqlike.iptv.ui.common.MetaLine
import com.skyqlike.iptv.ui.stremio.StremioActions
import com.skyqlike.iptv.ui.trailer.TrailerPlayer
import com.skyqlike.iptv.ui.theme.Palette

/** How far through an episode you are: [fraction] 0 to 1, or [finished]. */
data class EpisodeState(val finished: Boolean, val fraction: Float)

/** Which episode is being played (null means it's a movie). */
private data class Pick(val video: StremioVideo?)

/**
 * A movie or series page: big picture, description, Play and My List,
 * and for a series the seasons and episodes. Play leads to the choice of streams.
 */
@Composable
fun DetailScreen(
    start: StremioItem,
    actions: StremioActions,
    isFavourite: Boolean,
    autoPlay: Boolean,
    quickPlay: Boolean,
    /** For "Up next" tiles: the episode to start as soon as the series has loaded. */
    autoEpisodeId: String? = null,
    /** Opens another title (from the "More like this" row). */
    onOpen: (StremioItem) -> Unit = {},
    /** What you have watched of an episode, for the ticks and progress bars. */
    episodeState: (StremioVideo) -> EpisodeState? = { null },
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var info by remember(start.id) { mutableStateOf(start) }
    var picking by remember(start.id) {
        mutableStateOf<Pick?>(if (autoPlay && start.type != "series") Pick(null) else null)
    }
    var season by remember(start.id) { mutableIntStateOf(-1) }
    var showTrailer by remember(start.id) { mutableStateOf(false) }

    LaunchedEffect(start.id) {
        info = runCatching { actions.loadDetails(start) }.getOrDefault(start)
        if (autoEpisodeId != null) {
            info.videos.firstOrNull { it.id == autoEpisodeId }?.let { picking = Pick(it) }
        }
    }

    // "More like this": titles from your addons that share this one's genres.
    var similar by remember(start.id) { mutableStateOf<List<StremioItem>>(emptyList()) }
    val firstGenre = info.genres.firstOrNull()
    LaunchedEffect(start.id, firstGenre) {
        val g = firstGenre ?: return@LaunchedEffect
        val rows = runCatching { actions.byGenre(info.type, g) }.getOrDefault(emptyList())
        val mine = info.genres.map { it.lowercase() }.toSet()
        similar = rows.flatMap { it.items }
            .filter { it.id != info.id && !it.poster.isNullOrBlank() }
            .distinctBy { it.type + ":" + it.id }
            .sortedByDescending { it.genres.count { x -> x.lowercase() in mine } }
            .take(20)
    }

    val pick = picking
    if (pick != null) {
        StreamPicker(info, pick, actions, quickPlay, onBack = { picking = null })
        return
    }

    val playFocus = remember { FocusRequester() }
    LaunchedEffect(start.id, showTrailer) { if (!showTrailer) runCatching { playFocus.requestFocus() } }

    val genreLine = info.genres.take(3).joinToString("  •  ")
    val listLabel = if (isFavourite) "✓ In My List" else "+ My List"

    Box(Modifier.fillMaxSize()) {
        Artwork(info.background ?: info.poster, info.name, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.96f), 0.55f to Color.Black.copy(alpha = 0.8f), 1f to Color.Black.copy(alpha = 0.3f))
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.7f to Color.Transparent, 1f to Palette.Background)
            )
        )

        if (info.type == "series") {
            val seasons = info.videos.map { it.season }.distinct().sorted()
            // The episode to offer on the Play button: the first one you haven't finished.
            val ordered = info.videos.filter { it.season > 0 }.sortedWith(compareBy({ it.season }, { it.episode })).ifEmpty { info.videos }
            val target = ordered.firstOrNull { episodeState(it)?.finished != true } ?: ordered.firstOrNull()
            val current = if (season in seasons) season else target?.season ?: seasons.firstOrNull { it > 0 } ?: seasons.firstOrNull() ?: 1
            val episodes = info.videos.filter { it.season == current }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 48.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Column(Modifier.fillMaxWidth(0.58f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(info.name, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        MetaLine(info.year, info.runtime, info.rating, fontSize = 15.sp)
                        if (genreLine.isNotBlank()) Text(genreLine, fontSize = 15.sp, color = Color(0xFFD0D0D8))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
                            ActionButton(
                                target?.let { t ->
                                    val st = episodeState(t)
                                    (if (st != null && !st.finished && st.fraction > 0f) "Resume" else "Play") + " S${t.season} E${t.episode}"
                                } ?: "Loading...",
                                Modifier.width(210.dp).focusRequester(playFocus),
                                accent = true,
                                accentColor = Color.White,
                                textColor = Color.Black,
                            ) { target?.let { picking = Pick(it) } }
                            ActionButton(listLabel, Modifier.width(170.dp)) { actions.onToggleFavourite(info) }
                            if (info.trailerId != null) ActionButton("Trailer", Modifier.width(130.dp)) { showTrailer = true }
                            ActionButton("Back", Modifier.width(110.dp)) { onBack() }
                        }
                        Text(
                            info.description.ifBlank { "No description." },
                            fontSize = 15.sp,
                            color = Color(0xFFC0C0C8),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (seasons.size > 1) {
                    item {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(top = 10.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            seasons.forEach { sn ->
                                ActionButton(if (sn == 0) "Specials" else "Season $sn", Modifier.width(130.dp), accent = sn == current) { season = sn }
                            }
                        }
                    }
                }
                items(episodes, key = { it.id }) { ep ->
                    FocusTile(
                        onClick = { picking = Pick(ep) },
                        modifier = Modifier.fillMaxWidth(0.75f).height(88.dp),
                        corner = 8.dp,
                        focusedScale = 1.02f,
                        backgroundColor = Color(0xB3000000),
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            val st = episodeState(ep)
                            Box(Modifier.width(130.dp).fillMaxHeight().clip(RoundedCornerShape(6.dp))) {
                                Artwork(ep.thumbnail ?: info.poster, ep.title.ifBlank { info.name }, Modifier.fillMaxSize())
                                if (st != null && !st.finished && st.fraction > 0f) {
                                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x66FFFFFF)))
                                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(st.fraction.coerceIn(0.03f, 1f)).height(4.dp).background(Palette.Accent))
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Episode ${ep.episode}" + if (st?.finished == true) "   ✓ Watched" else "", fontSize = 13.sp, color = Palette.TextSecondary)
                                Text(
                                    ep.title.ifBlank { "Episode ${ep.episode}" },
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                if (similar.size >= 4) {
                    item { MoreLikeThis(similar, onOpen, Modifier.padding(top = 14.dp)) }
                }
                if (info.videos.isEmpty()) {
                    item { Text("Loading episodes... If this stays empty, none of your addons list episodes for this series.", fontSize = 15.sp, color = Palette.TextSecondary) }
                }
            }
        } else {
            Column(
                // Scrolls if the screen is short (like a phone on its side), so nothing is ever out of reach.
                Modifier.align(Alignment.CenterStart).fillMaxWidth(0.55f).verticalScroll(rememberScrollState()).padding(start = 56.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(info.name, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                MetaLine(info.year, info.runtime, info.rating, fontSize = 16.sp)
                if (genreLine.isNotBlank()) Text(genreLine, fontSize = 16.sp, color = Color(0xFFD0D0D8))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    ActionButton(
                        "Play",
                        Modifier.width(140.dp).focusRequester(playFocus),
                        accent = true,
                        accentColor = Color.White,
                        textColor = Color.Black,
                    ) { picking = Pick(null) }
                    ActionButton(listLabel, Modifier.width(170.dp)) { actions.onToggleFavourite(info) }
                    if (info.trailerId != null) ActionButton("Trailer", Modifier.width(130.dp)) { showTrailer = true }
                    ActionButton("Back", Modifier.width(110.dp)) { onBack() }
                }
                Text(
                    info.description.ifBlank { "No description." },
                    fontSize = 17.sp,
                    color = Color(0xFFC0C0C8),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                if (similar.size >= 4) MoreLikeThis(similar, onOpen, Modifier.padding(top = 6.dp))
            }
        }

        val trailerId = info.trailerId
        if (showTrailer && trailerId != null) {
            TrailerPlayer(trailerId) { showTrailer = false }
        }
    }
}

/** A sideways row of similar titles. */
@Composable
private fun MoreLikeThis(items: List<StremioItem>, onOpen: (StremioItem) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("More like this", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(items, key = { it.type + ":" + it.id }) { it2 ->
                PosterTile(
                    HomeTile(
                        id = it2.type + ":" + it2.id,
                        title = it2.name,
                        subtitle = it2.year,
                        imageUrl = it2.poster,
                    ) { onOpen(it2) },
                    100.dp,
                    150.dp,
                )
            }
        }
    }
}

/** The list of streams the addons found. With quick play on, the first one starts by itself. */
@Composable
private fun StreamPicker(item: StremioItem, pick: Pick, actions: StremioActions, quickPlay: Boolean, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val videoId = pick.video?.id ?: item.id
    var result by remember(videoId) { mutableStateOf<StremioStreams?>(null) }
    // Starts the first stream only once, so coming back from the player shows the list instead of replaying.
    var autoDone by remember(videoId) { mutableStateOf(false) }
    var done by remember(videoId) { mutableStateOf(false) }
    var grace by remember(videoId) { mutableStateOf(false) }
    var focusedOnce by remember(videoId) { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val uiScope = rememberCoroutineScope()

    // Streams show up as each addon answers; the full list keeps filling in behind the scenes.
    LaunchedEffect(videoId) {
        result = runCatching { actions.loadStreams(item.type, videoId) { partial -> uiScope.launch { result = partial } } }
            .getOrDefault(StremioStreams(emptyList(), 0, 1))
        done = true
    }
    val r = result
    val haveStream = r != null && r.playable.isNotEmpty()
    // Once there is something to play, wait a moment for the other addons, so a better stream isn't missed.
    LaunchedEffect(haveStream) {
        if (haveStream) {
            delay(2500)
            grace = true
        }
    }
    LaunchedEffect(r, done, grace) {
        if (r != null && r.playable.isNotEmpty()) {
            if (quickPlay && !autoDone) {
                if (done || grace) {
                    autoDone = true
                    actions.onPlay(item, pick.video, r.playable.first())
                }
            } else if (!focusedOnce) {
                focusedOnce = true
                runCatching { firstFocus.requestFocus() }
            }
        } else if (r != null && done && r.playable.isEmpty()) {
            runCatching { firstFocus.requestFocus() }
        }
    }

    val label = pick.video?.let { "S${it.season} E${it.episode}  ${it.title}" } ?: item.year

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
        Text("Choose a stream", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
        Text(
            item.name + if (label.isNotBlank()) "  -  $label" else "",
            fontSize = 15.sp,
            color = Palette.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        when {
            r == null -> Text("Asking your addons for streams...", fontSize = 17.sp, color = Palette.TextSecondary)
            r.playable.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("No playable streams found.", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary)
                if (r.torrentOnly > 0) {
                    Text(
                        "${r.torrentOnly} torrent-only stream(s) were skipped. They only play through a debrid service, so check your debrid addon is added under Settings > Account and addons.",
                        fontSize = 15.sp,
                        color = Palette.TextSecondary,
                    )
                }
                if (r.failedAddons > 0) {
                    Text("${r.failedAddons} addon(s) didn't answer. Try again in a moment.", fontSize = 15.sp, color = Palette.TextSecondary)
                }
                ActionButton("Back", Modifier.width(120.dp).focusRequester(firstFocus)) { onBack() }
            }
            else -> {
                if (r.torrentOnly > 0 || r.failedAddons > 0) {
                    Text(
                        listOfNotNull(
                            if (r.torrentOnly > 0) "${r.torrentOnly} torrent-only skipped" else null,
                            if (r.failedAddons > 0) "${r.failedAddons} addon(s) didn't answer" else null,
                        ).joinToString("  |  "),
                        fontSize = 13.sp,
                        color = Palette.TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(r.playable) { i, s ->
                        FocusTile(
                            onClick = { actions.onPlay(item, pick.video, s) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(76.dp)
                                .then(if (i == 0) Modifier.focusRequester(firstFocus) else Modifier),
                            corner = 8.dp,
                            focusedScale = 1.02f,
                        ) {
                            Row(
                                Modifier.fillMaxSize().padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        (s.name.replace('\n', ' ').ifBlank { "Stream" }) + "  -  " + s.addonName,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Palette.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (s.description.isNotBlank()) {
                                        Text(
                                            s.description.replace('\n', ' '),
                                            fontSize = 13.sp,
                                            color = Palette.TextSecondary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                s.sizeBytes?.let {
                                    Text(formatSize(it), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Accent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    val gb = bytes / 1_000_000_000.0
    return if (gb >= 1.0) "%.1f GB".format(gb) else "${bytes / 1_000_000} MB"
}
