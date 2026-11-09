package com.skyqlike.iptv.ui.shuffle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.history.HistoryEntry
import com.skyqlike.iptv.data.stremio.StremioItem
import com.skyqlike.iptv.data.stremio.StremioVideo
import com.skyqlike.iptv.ui.common.ActionButton
import com.skyqlike.iptv.ui.common.Artwork
import com.skyqlike.iptv.ui.common.FocusTile
import com.skyqlike.iptv.ui.common.MetaLine
import com.skyqlike.iptv.ui.stremio.StremioActions
import com.skyqlike.iptv.ui.theme.Palette
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The choices on the Play Something screen. */
data class ShuffleFilters(
    /** 0 = anything, 1 = movies, 2 = series. */
    val type: Int = 0,
    val genre: String? = null,
    /** 0 = any, 1 = under 90 minutes, 2 = under 2 hours, 3 = 2 hours or more. Movies only. */
    val length: Int = 0,
    val unwatchedOnly: Boolean = true,
    val myListOnly: Boolean = false,
    /** 0 = any, otherwise the lowest IMDb score. */
    val minRating: Int = 0,
)

/** A title picked for you, and for a series the episode to start on. */
class ShufflePick(val item: StremioItem, val episode: StremioVideo?)

/** Kept by the app so your choices and the last pick are still there when you come back from a title's page. */
class ShuffleState {
    var filters by mutableStateOf(ShuffleFilters())
    /** 0 = nothing yet, 1 = looking, 2 = nothing matched, 3 = a pick is showing. */
    var phase by mutableIntStateOf(0)
    var result by mutableStateOf<ShufflePick?>(null)
    val seen = HashSet<String>()
}

private val TypeNames = listOf("Movies and series", "Movies only", "Series only")
private val LengthNames = listOf("Any length", "Under 90 minutes", "Under 2 hours", "2 hours or more")
private val RatingChoices = listOf(0, 6, 7, 8)

/** Turns "95 min", "1h 35min" or "2h" into minutes. */
internal fun runtimeMinutes(text: String): Int? {
    val t = text.lowercase().trim()
    if (t.isEmpty()) return null
    val h = Regex("(\\d+)\\s*h").find(t)?.groupValues?.get(1)?.toIntOrNull()
    val m = Regex("(\\d+)\\s*m").find(t)?.groupValues?.get(1)?.toIntOrNull()
    if (h != null || m != null) return (h ?: 0) * 60 + (m ?: 0)
    return Regex("\\d+").find(t)?.value?.toIntOrNull()
}

private fun lengthOk(minutes: Int?, choice: Int): Boolean = when (choice) {
    0 -> true
    1 -> minutes != null && minutes < 90
    2 -> minutes != null && minutes < 120
    else -> minutes != null && minutes >= 120
}

private fun ratingOf(item: StremioItem): Float =
    Regex("\\d+(\\.\\d+)?").find(item.rating)?.value?.toFloatOrNull() ?: 0f

/**
 * "Play Something": picks a random title that fits your choices (type, genre, length, unwatched,
 * My List, rating), shows it, and starts it when you say so.
 */
@Composable
fun PlaySomethingScreen(
    state: ShuffleState,
    /** Every title the Home page has loaded. */
    items: List<StremioItem>,
    favourites: List<StremioItem>,
    watched: List<HistoryEntry>,
    actions: StremioActions,
    onPlay: (StremioItem) -> Unit,
    onPlayEpisode: (StremioItem, String) -> Unit,
    onInfo: (StremioItem) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(250)
        runCatching { firstFocus.requestFocus() }
    }

    val f = state.filters
    fun change(new: ShuffleFilters) {
        state.filters = new
        state.seen.clear()
    }

    // The genres to choose from: the ones that show up most in what is loaded.
    val genres = remember(items, favourites) {
        (items + favourites).flatMap { it.genres }
            .map { it.trim() }.filter { it.isNotEmpty() }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(14).map { it.key }
    }

    suspend fun buildPool(): List<StremioItem> {
        val byKey = LinkedHashMap<String, StremioItem>()
        val verified = HashSet<String>()
        fun add(i: StremioItem) {
            byKey[i.type + ":" + i.id] = byKey[i.type + ":" + i.id] ?: i
        }
        if (f.myListOnly) {
            favourites.forEach { add(it) }
        } else {
            items.forEach { add(it) }
            val g = f.genre
            if (g != null) {
                // Ask the addons for more of that genre, so there is plenty to choose from.
                val types = when (f.type) {
                    1 -> listOf("movie")
                    2 -> listOf("series")
                    else -> listOf("movie", "series")
                }
                types.forEach { t ->
                    runCatching { actions.byGenre(t, g) }.getOrDefault(emptyList()).forEach { row ->
                        row.items.forEach {
                            add(it)
                            verified += it.type + ":" + it.id
                        }
                    }
                }
            }
        }
        val watchedKeys = watched.filter { it.key.startsWith("stremio:") }
            .map { it.key.removePrefix("stremio:").substringBefore('|') }.toSet()
        return byKey.values.filter { i ->
            val k = i.type + ":" + i.id
            (f.type == 0 || (f.type == 1 && i.type == "movie") || (f.type == 2 && i.type == "series")) &&
                (f.length == 0 || i.type == "movie") &&
                (f.genre == null || k in verified || i.genres.any { it.equals(f.genre, true) }) &&
                (!f.unwatchedOnly || k !in watchedKeys) &&
                (f.minRating == 0 || ratingOf(i) >= f.minRating) &&
                k !in state.seen
        }
    }

    suspend fun findOne(): ShufflePick? {
        val pool = buildPool().shuffled()
        if (pool.isEmpty()) return null
        val chosen: StremioItem = if (f.length == 0) {
            runCatching { actions.loadDetails(pool.first()) }.getOrDefault(pool.first())
        } else {
            // Most lists don't say how long a film is, so a few are looked up at a time until one fits.
            var found: StremioItem? = null
            for (batch in pool.take(20).chunked(4)) {
                val full = coroutineScope {
                    batch.map { i ->
                        async { if (runtimeMinutes(i.runtime) != null) i else runCatching { actions.loadDetails(i) }.getOrDefault(i) }
                    }.awaitAll()
                }
                found = full.firstOrNull { lengthOk(runtimeMinutes(it.runtime), f.length) }
                if (found != null) break
            }
            found ?: return null
        }
        var episode: StremioVideo? = null
        if (chosen.type == "series") {
            val done = watched.filter { it.finished }.map { it.key }.toSet()
            episode = chosen.videos.filter { it.season > 0 }
                .sortedWith(compareBy({ it.season }, { it.episode }))
                .firstOrNull { "stremio:series:${chosen.id}|${it.id}" !in done }
        }
        return ShufflePick(chosen, episode)
    }

    fun shuffle() {
        if (state.phase == 1) return
        state.phase = 1
        scope.launch {
            val r = runCatching { findOne() }.getOrNull()
            if (r == null) {
                state.seen.clear()
                state.phase = 2
            } else {
                state.seen += r.item.type + ":" + r.item.id
                state.result = r
                state.phase = 3
            }
        }
    }

    val narrow = com.skyqlike.iptv.ui.common.isNarrowScreen()
    val resultPanel: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {
            val pick = state.result
            when {
                state.phase == 1 -> Text("Looking for something...", fontSize = 18.sp, color = Palette.TextSecondary, modifier = Modifier.align(Alignment.Center))
                state.phase == 2 -> Text(
                    "Nothing matches those choices. Loosen one and try again.",
                    fontSize = 17.sp,
                    color = Palette.TextSecondary,
                    modifier = Modifier.align(Alignment.Center),
                )
                state.phase == 3 && pick != null -> PickCard(pick, onPlay, onPlayEpisode, onInfo)
                else -> Text(
                    "Your pick shows up here.",
                    fontSize = 17.sp,
                    color = Palette.TextSecondary,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
    }
    if (narrow) {
        // Phone: one scrolling page. The button and your pick come first, the choices are underneath.
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text("Play Something", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary) }
            item {
                ActionButton(
                    if (state.phase == 3) "Shuffle again" else "Pick something for me",
                    Modifier.fillMaxWidth().focusRequester(firstFocus),
                    accent = true,
                    accentColor = Color.White,
                    textColor = Color.Black,
                ) { shuffle() }
            }
            item {
                Box(
                    Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x14FFFFFF)).padding(14.dp),
                ) { resultPanel() }
            }
            item { Text("Your choices", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextSecondary, modifier = Modifier.padding(top = 6.dp)) }
            item { ChoiceRow("Show me", TypeNames[f.type]) { change(f.copy(type = (f.type + 1) % TypeNames.size)) } }
            item {
                ChoiceRow("Genre", f.genre ?: "Any") {
                    val list = listOf<String?>(null) + genres
                    val at = list.indexOf(f.genre).coerceAtLeast(0)
                    change(f.copy(genre = list[(at + 1) % list.size]))
                }
            }
            item {
                ChoiceRow("Length (movies)", LengthNames[f.length]) { change(f.copy(length = (f.length + 1) % LengthNames.size)) }
            }
            item {
                ChoiceRow("Already watched", if (f.unwatchedOnly) "Hide them" else "Include them") {
                    change(f.copy(unwatchedOnly = !f.unwatchedOnly))
                }
            }
            item {
                ChoiceRow("Choose from", if (f.myListOnly) "My List only" else "Everything") {
                    change(f.copy(myListOnly = !f.myListOnly))
                }
            }
            item {
                ChoiceRow("IMDb score", if (f.minRating == 0) "Any" else "${f.minRating}+") {
                    val at = RatingChoices.indexOf(f.minRating).coerceAtLeast(0)
                    change(f.copy(minRating = RatingChoices[(at + 1) % RatingChoices.size]))
                }
            }
            if (f.length != 0 && f.type == 2) {
                item { Text("Length only applies to movies, so series are left out while a length is chosen.", fontSize = 13.sp, color = Palette.TextSecondary) }
            }
            item { ActionButton("Back", Modifier.fillMaxWidth()) { onClose() } }
        }
        return
    }

    Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        // Left: the choices.
        LazyColumn(
            Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text("Play Something", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary) }
            item { Text("Pick what you're in the mood for, then let the app choose.", fontSize = 14.sp, color = Palette.TextSecondary) }
            item {
                ActionButton(
                    if (state.phase == 3) "Shuffle again" else "Pick something for me",
                    Modifier.fillMaxWidth().focusRequester(firstFocus),
                    accent = true,
                    accentColor = Color.White,
                    textColor = Color.Black,
                ) { shuffle() }
            }
            item { ChoiceRow("Show me", TypeNames[f.type]) { change(f.copy(type = (f.type + 1) % TypeNames.size)) } }
            item {
                ChoiceRow("Genre", f.genre ?: "Any") {
                    val list = listOf<String?>(null) + genres
                    val at = list.indexOf(f.genre).coerceAtLeast(0)
                    change(f.copy(genre = list[(at + 1) % list.size]))
                }
            }
            item {
                ChoiceRow("Length (movies)", LengthNames[f.length]) { change(f.copy(length = (f.length + 1) % LengthNames.size)) }
            }
            item {
                ChoiceRow("Already watched", if (f.unwatchedOnly) "Hide them" else "Include them") {
                    change(f.copy(unwatchedOnly = !f.unwatchedOnly))
                }
            }
            item {
                ChoiceRow("Choose from", if (f.myListOnly) "My List only" else "Everything") {
                    change(f.copy(myListOnly = !f.myListOnly))
                }
            }
            item {
                ChoiceRow("IMDb score", if (f.minRating == 0) "Any" else "${f.minRating}+") {
                    val at = RatingChoices.indexOf(f.minRating).coerceAtLeast(0)
                    change(f.copy(minRating = RatingChoices[(at + 1) % RatingChoices.size]))
                }
            }
            if (f.length != 0 && f.type == 2) {
                item { Text("Length only applies to movies, so series are left out while a length is chosen.", fontSize = 13.sp, color = Palette.TextSecondary) }
            }
            item { ActionButton("Back", Modifier.width(130.dp)) { onClose() } }
        }

        // Right: the pick.
        Box(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Color(0x14FFFFFF)).padding(18.dp),
        ) {
            val pick = state.result
            when {
                state.phase == 1 -> Text("Looking for something...", fontSize = 18.sp, color = Palette.TextSecondary, modifier = Modifier.align(Alignment.Center))
                state.phase == 2 -> Text(
                    "Nothing matches those choices. Loosen one and try again.",
                    fontSize = 17.sp,
                    color = Palette.TextSecondary,
                    modifier = Modifier.align(Alignment.Center),
                )
                state.phase == 3 && pick != null -> PickCard(pick, onPlay, onPlayEpisode, onInfo)
                else -> Text(
                    "Your pick shows up here.",
                    fontSize = 17.sp,
                    color = Palette.TextSecondary,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun PickCard(
    pick: ShufflePick,
    onPlay: (StremioItem) -> Unit,
    onPlayEpisode: (StremioItem, String) -> Unit,
    onInfo: (StremioItem) -> Unit,
) {
    val item = pick.item
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Artwork(item.poster, item.name, Modifier.width(110.dp).height(165.dp).clip(RoundedCornerShape(8.dp)))
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(if (item.type == "series") "SERIES" else "FILM", fontSize = 12.sp, color = Palette.Accent, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text(item.name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
            // The buttons come straight after the title, so they're always on screen even on a short phone display.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                val ep = pick.episode
                ActionButton(
                    if (ep != null) "Play S${ep.season} E${ep.episode}" else "Play",
                    Modifier.weight(1f),
                    accent = true,
                    accentColor = Color.White,
                    textColor = Color.Black,
                ) { if (ep != null) onPlayEpisode(item, ep.id) else onPlay(item) }
                ActionButton("More Info", Modifier.weight(1f)) { onInfo(item) }
            }
            MetaLine(item.year, item.runtime, item.rating, fontSize = 13.sp)
            if (item.genres.isNotEmpty()) Text(item.genres.take(3).joinToString("  •  "), fontSize = 13.sp, color = Color(0xFFD0D0D8))
            if (item.description.isNotBlank()) {
                Text(item.description, fontSize = 13.sp, color = Color(0xFFC0C0C8), maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, value: String, onClick: () -> Unit) {
    FocusTile(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        corner = 8.dp,
        focusedScale = 1.02f,
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 16.sp, color = Palette.TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
