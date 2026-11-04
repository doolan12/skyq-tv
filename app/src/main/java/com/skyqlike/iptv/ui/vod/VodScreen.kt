package com.skyqlike.iptv.ui.vod

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.vod.Episode
import com.skyqlike.iptv.data.vod.Movie
import com.skyqlike.iptv.data.vod.Series
import com.skyqlike.iptv.data.vod.VodState
import com.skyqlike.iptv.ui.common.Artwork
import com.skyqlike.iptv.ui.common.HomeTile
import com.skyqlike.iptv.ui.common.Rail
import com.skyqlike.iptv.ui.common.RailRow
import com.skyqlike.iptv.ui.home.FocusTile
import com.skyqlike.iptv.ui.providers.ActionButton
import com.skyqlike.iptv.ui.providers.LabeledField
import com.skyqlike.iptv.ui.theme.Palette

/** Movies and TV Series: browse, search, open details, play. */
@Composable
fun VodScreen(
    state: VodState,
    onNeedCatalog: () -> Unit,
    loadMovieInfo: suspend (Movie) -> Movie,
    loadEpisodes: suspend (Series) -> Result<List<Episode>>,
    onPlayMovie: (Movie) -> Unit,
    onPlayEpisodes: (Series, List<Episode>, Int) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var openMovie by remember { mutableStateOf<Movie?>(null) }
    var openSeries by remember { mutableStateOf<Series?>(null) }

    LaunchedEffect(state) {
        if (state is VodState.Idle) onNeedCatalog()
    }

    val movie = openMovie
    val series = openSeries
    when {
        movie != null -> MovieDetails(movie, loadMovieInfo, onPlayMovie) { openMovie = null }
        series != null -> SeriesDetails(series, loadEpisodes, onPlayEpisodes) { openSeries = null }
        else -> Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
            Row(
                Modifier.padding(horizontal = 36.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ActionButton("Movies", Modifier.width(130.dp), accent = tab == 0) { tab = 0 }
                ActionButton("TV Series", Modifier.width(140.dp), accent = tab == 1) { tab = 1 }
                Box(Modifier.weight(1f)) { LabeledField("Search", query) { query = it } }
            }
            Spacer(Modifier.height(14.dp))

            when (state) {
                is VodState.Idle, is VodState.Loading -> Message("Loading movies and series...")
                is VodState.Error -> Column(Modifier.padding(36.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(state.message, fontSize = 16.sp, color = Palette.TextSecondary)
                    ActionButton("Try again", Modifier.width(150.dp), accent = true) { onNeedCatalog() }
                }
                is VodState.Ready -> {
                    val movies = state.catalog.movies
                    val seriesList = state.catalog.series
                    if (tab == 0) {
                        if (movies.isEmpty()) {
                            Message("Your provider doesn't supply any movies.")
                        } else {
                            val base = remember(movies) { movieRails(movies) { openMovie = it } }
                            val rails = if (query.isBlank()) base else movieSearch(movies, query) { openMovie = it }
                            RailList(rails)
                        }
                    } else {
                        if (seriesList.isEmpty()) {
                            Message("Your provider doesn't supply any TV series.")
                        } else {
                            val base = remember(seriesList) { seriesRails(seriesList) { openSeries = it } }
                            val rails = if (query.isBlank()) base else seriesSearch(seriesList, query) { openSeries = it }
                            RailList(rails)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RailList(rails: List<Rail>) {
    if (rails.isEmpty()) {
        Message("Nothing found.")
    } else {
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp),
        ) {
            itemsIndexed(rails, key = { i, r -> "$i|${r.title}" }) { _, rail -> RailRow(rail, 150.dp, 225.dp) }
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(36.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 18.sp, color = Palette.TextSecondary)
    }
}

private fun movieRails(movies: List<Movie>, onOpen: (Movie) -> Unit): List<Rail> {
    fun tile(m: Movie) = HomeTile(m.id, m.name, m.year.ifBlank { m.category }, m.posterUrl) { onOpen(m) }
    val recent = movies.sortedByDescending { it.addedEpoch }
    val featured = recent.take(200).sortedByDescending { it.rating.toDoubleOrNull() ?: 0.0 }.take(10)
    val popular = movies.sortedByDescending { it.rating.toDoubleOrNull() ?: 0.0 }.take(30)
    val byCategory = movies
        .groupBy { it.category.ifBlank { "Other" } }
        .entries
        .sortedByDescending { it.value.size }
        .take(12)
        .map { Rail(it.key, it.value.take(30).map(::tile)) }
    return (listOf(
        Rail("Featured", featured.map(::tile)),
        Rail("Recently Added", recent.take(30).map(::tile)),
        Rail("Most Popular", popular.map(::tile)),
    ) + byCategory).filter { it.tiles.isNotEmpty() }
}

private fun movieSearch(movies: List<Movie>, query: String, onOpen: (Movie) -> Unit): List<Rail> {
    val q = query.trim()
    val hits = movies.filter { it.name.contains(q, ignoreCase = true) }.take(60)
    return listOf(
        Rail("Results for \"$q\"", hits.map { m ->
            HomeTile(m.id, m.name, m.year.ifBlank { m.category }, m.posterUrl) { onOpen(m) }
        })
    ).filter { it.tiles.isNotEmpty() }
}

private fun seriesRails(list: List<Series>, onOpen: (Series) -> Unit): List<Rail> {
    fun tile(s: Series) = HomeTile(s.id, s.name, s.year.ifBlank { s.category }, s.coverUrl) { onOpen(s) }
    val recent = list.sortedByDescending { it.addedEpoch }
    val featured = recent.take(200).sortedByDescending { it.rating.toDoubleOrNull() ?: 0.0 }.take(10)
    val popular = list.sortedByDescending { it.rating.toDoubleOrNull() ?: 0.0 }.take(30)
    val byCategory = list
        .groupBy { it.category.ifBlank { "Other" } }
        .entries
        .sortedByDescending { it.value.size }
        .take(12)
        .map { Rail(it.key, it.value.take(30).map(::tile)) }
    return (listOf(
        Rail("Featured", featured.map(::tile)),
        Rail("Recently Added", recent.take(30).map(::tile)),
        Rail("Most Popular", popular.map(::tile)),
    ) + byCategory).filter { it.tiles.isNotEmpty() }
}

private fun seriesSearch(list: List<Series>, query: String, onOpen: (Series) -> Unit): List<Rail> {
    val q = query.trim()
    val hits = list.filter { it.name.contains(q, ignoreCase = true) }.take(60)
    return listOf(
        Rail("Results for \"$q\"", hits.map { s ->
            HomeTile(s.id, s.name, s.year.ifBlank { s.category }, s.coverUrl) { onOpen(s) }
        })
    ).filter { it.tiles.isNotEmpty() }
}

private fun formatMinutes(min: Int): String = when {
    min <= 0 -> ""
    min >= 60 -> "${min / 60}h ${min % 60}m"
    else -> "${min}m"
}

@Composable
private fun MovieDetails(
    movie: Movie,
    loadInfo: suspend (Movie) -> Movie,
    onPlay: (Movie) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var info by remember(movie.id) { mutableStateOf(movie) }
    LaunchedEffect(movie.id) {
        info = runCatching { loadInfo(movie) }.getOrDefault(movie)
    }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }

    Row(
        Modifier.fillMaxSize().padding(36.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Artwork(
            info.posterUrl, info.name,
            Modifier.width(200.dp).height(300.dp).clip(RoundedCornerShape(12.dp)),
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(info.name, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
            val meta = listOf(
                info.year,
                info.genre.ifBlank { info.category },
                formatMinutes(info.durationMin),
                if (info.rating.isNotBlank()) "Rating ${info.rating}" else "",
            ).filter { it.isNotBlank() }.joinToString("  |  ")
            if (meta.isNotBlank()) Text(meta, fontSize = 16.sp, color = Palette.Accent)
            Text(
                info.description.ifBlank { "Your provider didn't send a description for this movie." },
                fontSize = 16.sp,
                color = Palette.TextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                ActionButton("Play", Modifier.width(140.dp).focusRequester(playFocus), accent = true) { onPlay(info) }
                ActionButton("Back", Modifier.width(120.dp)) { onBack() }
            }
        }
    }
}

@Composable
private fun SeriesDetails(
    series: Series,
    loadEpisodes: suspend (Series) -> Result<List<Episode>>,
    onPlay: (Series, List<Episode>, Int) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var episodes by remember(series.id) { mutableStateOf<List<Episode>?>(null) }
    var error by remember(series.id) { mutableStateOf<String?>(null) }
    var season by remember(series.id) { mutableIntStateOf(-1) }

    LaunchedEffect(series.id) {
        loadEpisodes(series)
            .onSuccess { episodes = it }
            .onFailure { error = it.message ?: "Couldn't load the episodes." }
    }

    val all = episodes
    val seasons = all?.map { it.season }?.distinct()?.sorted().orEmpty()
    val currentSeason = if (season in seasons) season else seasons.firstOrNull() ?: 1
    val inSeason = all?.filter { it.season == currentSeason }.orEmpty()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(36.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Artwork(
                    series.coverUrl, series.name,
                    Modifier.width(110.dp).height(165.dp).clip(RoundedCornerShape(12.dp)),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(series.name, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
                    val meta = listOf(series.year, series.genre.ifBlank { series.category })
                        .filter { it.isNotBlank() }.joinToString("  |  ")
                    if (meta.isNotBlank()) Text(meta, fontSize = 15.sp, color = Palette.Accent)
                    Text(
                        series.plot.ifBlank { "Your provider didn't send a description for this series." },
                        fontSize = 15.sp,
                        color = Palette.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val first = all.orEmpty().sortedWith(compareBy({ it.season }, { it.number }))
                        ActionButton(
                            if (all == null) "Loading..." else "Play",
                            Modifier.width(140.dp),
                            accent = true,
                        ) {
                            // Plays from the first episode of the season you're looking at.
                            val start = first.indexOfFirst { it.season == currentSeason }.coerceAtLeast(0)
                            if (first.isNotEmpty()) onPlay(series, first, start)
                        }
                        ActionButton("Back", Modifier.width(120.dp)) { onBack() }
                    }
                }
            }
        }
        if (all == null && error == null) {
            item { Text("Loading episodes...", fontSize = 16.sp, color = Palette.TextSecondary) }
        }
        error?.let { msg ->
            item { Text(msg, fontSize = 16.sp, color = Palette.TextSecondary) }
        }
        if (seasons.isNotEmpty()) {
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    seasons.forEach { s ->
                        ActionButton("Season $s", Modifier.width(130.dp), accent = s == currentSeason) { season = s }
                    }
                }
            }
        }
        items(inSeason, key = { it.id }) { ep ->
            val ordered = remember(all) { all.orEmpty().sortedWith(compareBy({ it.season }, { it.number })) }
            FocusTile(
                onClick = { onPlay(series, ordered, ordered.indexOfFirst { it.id == ep.id }.coerceAtLeast(0)) },
                modifier = Modifier.fillMaxWidth().height(80.dp),
                corner = 10.dp,
                focusedScale = 1.02f,
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("E${ep.number}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Accent)
                    Column(Modifier.weight(1f)) {
                        Text(
                            ep.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Palette.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (ep.description.isNotBlank()) {
                            Text(
                                ep.description,
                                fontSize = 13.sp,
                                color = Palette.TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(formatMinutes(ep.durationMin), fontSize = 14.sp, color = Palette.TextSecondary)
                }
            }
        }
    }
}
