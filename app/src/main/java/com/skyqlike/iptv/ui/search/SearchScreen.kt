package com.skyqlike.iptv.ui.search

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.data.model.Programme
import com.skyqlike.iptv.data.vod.Episode
import com.skyqlike.iptv.data.vod.Movie
import com.skyqlike.iptv.data.vod.Series
import com.skyqlike.iptv.data.vod.VodState
import com.skyqlike.iptv.ui.common.Artwork
import com.skyqlike.iptv.ui.home.FocusTile
import com.skyqlike.iptv.ui.providers.LabeledField
import com.skyqlike.iptv.ui.vod.MovieDetails
import com.skyqlike.iptv.ui.vod.SeriesDetails
import com.skyqlike.iptv.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_PER_SECTION = 25

private data class Hit(val key: String, val title: String, val subtitle: String, val image: String?, val fit: Boolean, val onClick: () -> Unit)

/**
 * One search box for everything: channel names, shows in the TV guide (on now or coming up),
 * and movies. Movies load the first time you search, if they haven't been loaded already.
 */
@Composable
fun SearchScreen(
    channels: List<Channel>,
    epg: Map<String, List<Programme>>,
    vod: VodState,
    onNeedCatalog: () -> Unit,
    onPlayChannel: (Channel) -> Unit,
    onPlayMovie: (Movie) -> Unit,
    loadMovieInfo: suspend (Movie) -> Movie,
    loadEpisodes: suspend (Series) -> Result<List<Episode>>,
    onPlayEpisodes: (Series, List<Episode>, Int) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var openMovie by remember { mutableStateOf<Movie?>(null) }
    var openSeries by remember { mutableStateOf<Series?>(null) }
    val q = query.trim()
    LaunchedEffect(q.length >= 2) { if (q.length >= 2) onNeedCatalog() }

    val clock = remember { SimpleDateFormat("EEE h:mm a", Locale.getDefault()) }
    val channelHits = remember(q, channels) {
        if (q.length < 2) emptyList() else channels.filter { it.name.contains(q, ignoreCase = true) }.take(MAX_PER_SECTION).map { ch ->
            Hit("c:${ch.id}", ch.name, ch.group, ch.logoUrl, true) { onPlayChannel(ch) }
        }
    }
    val showHits = remember(q, channels, epg) {
        if (q.length < 2) emptyList() else {
            val now = System.currentTimeMillis()
            val byEpg = HashMap<String, Channel>()
            channels.forEach { c -> c.epgId?.let { byEpg.putIfAbsent(it, c) } }
            val out = ArrayList<Hit>()
            for ((id, list) in epg) {
                val ch = byEpg[id] ?: continue
                val p = list.firstOrNull { it.stop > now && it.title.contains(q, ignoreCase = true) } ?: continue
                val whenText = if (p.start <= now) "On now" else clock.format(Date(p.start))
                out += Hit("p:$id:${p.start}", p.title, "$whenText  |  ${ch.name}", p.iconUrl ?: ch.logoUrl, p.iconUrl == null) { onPlayChannel(ch) }
                if (out.size >= MAX_PER_SECTION) break
            }
            out.sortedBy { if (it.subtitle.startsWith("On now")) 0 else 1 }
        }
    }
    val movieHits = remember(q, vod) {
        val ready = vod as? VodState.Ready
        if (q.length < 2 || ready == null) emptyList() else ready.catalog.movies
            .filter { it.name.contains(q, ignoreCase = true) }.take(MAX_PER_SECTION).map { m ->
                Hit("m:${m.id}", m.name, listOf(m.year, m.category).filter { it.isNotBlank() }.joinToString("  |  "), m.posterUrl, false) { openMovie = m }
            }
    }

    val seriesHits = remember(q, vod) {
        val ready = vod as? VodState.Ready
        if (q.length < 2 || ready == null) emptyList() else ready.catalog.series
            .filter { it.name.contains(q, ignoreCase = true) }.take(MAX_PER_SECTION).map { s ->
                Hit("s:${s.id}", s.name, listOf(s.year, s.category).filter { it.isNotBlank() }.joinToString("  |  "), s.coverUrl, false) { openSeries = s }
            }
    }

    val pickedMovie = openMovie
    val pickedSeries = openSeries
    if (pickedMovie != null) {
        MovieDetails(pickedMovie, loadMovieInfo, onPlayMovie) { openMovie = null }
        return
    }
    if (pickedSeries != null) {
        SeriesDetails(pickedSeries, loadEpisodes, onPlayEpisodes) { openSeries = null }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Search", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary, modifier = Modifier.padding(bottom = 8.dp))
            LabeledField("Channels, shows, movies and series", query) { query = it }
        }
        if (q.length < 2) {
            item { Note("Type at least two letters.") }
        } else {
            section("Channels", channelHits)
            section("TV shows", showHits)
            section("Movies", movieHits)
            section("TV series", seriesHits)
            if (vod !is VodState.Ready) {
                item { Note(if (vod is VodState.Error) "Movies couldn't be loaded." else "Loading movies...") }
            }
            if (channelHits.isEmpty() && showHits.isEmpty() && movieHits.isEmpty() && seriesHits.isEmpty() && vod is VodState.Ready) {
                item { Note("Nothing found for \"$q\".") }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, fontSize = 16.sp, color = Palette.TextSecondary, modifier = Modifier.padding(top = 8.dp))
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(title: String, hits: List<Hit>) {
    if (hits.isEmpty()) return
    item(key = "h:$title") {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary, modifier = Modifier.padding(top = 10.dp))
    }
    items(hits, key = { it.key }) { hit ->
        FocusTile(onClick = hit.onClick, modifier = Modifier.fillMaxWidth().height(64.dp), corner = 12.dp, focusedScale = 1.02f) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Artwork(
                    url = hit.image,
                    seed = hit.title,
                    modifier = Modifier.width(56.dp).height(44.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = if (hit.fit) ContentScale.Fit else ContentScale.Crop,
                )
                Column(Modifier.weight(1f)) {
                    Text(hit.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Palette.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(hit.subtitle, fontSize = 13.sp, color = Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
