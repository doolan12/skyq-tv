package com.skyqlike.iptv.ui.stremio

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.history.HistoryEntry
import com.skyqlike.iptv.data.stremio.StremioItem
import com.skyqlike.iptv.data.stremio.StremioRow
import com.skyqlike.iptv.data.stremio.StremioSession
import com.skyqlike.iptv.data.stremio.StremioState
import com.skyqlike.iptv.data.stremio.StremioStream
import com.skyqlike.iptv.data.stremio.StremioStreams
import com.skyqlike.iptv.data.stremio.StremioVideo
import com.skyqlike.iptv.ui.common.Artwork
import com.skyqlike.iptv.ui.common.HomeTile
import com.skyqlike.iptv.ui.common.Rail
import com.skyqlike.iptv.ui.common.RailRow
import com.skyqlike.iptv.ui.home.FocusTile
import com.skyqlike.iptv.ui.home.ImmersiveEffect
import com.skyqlike.iptv.ui.providers.ActionButton
import com.skyqlike.iptv.ui.providers.LabeledField
import com.skyqlike.iptv.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Everything the Stremio section needs from the rest of the app. */
class StremioActions(
    val onNeedData: () -> Unit,
    /** Returns an error message, or null if it worked. */
    val onSignIn: suspend (email: String, password: String) -> String?,
    val onSignOut: () -> Unit,
    val onAddAddon: suspend (link: String) -> String?,
    val onRemoveAddon: (link: String) -> Unit,
    val search: suspend (query: String) -> List<StremioItem>,
    val onToggleFavourite: (StremioItem) -> Unit,
    val byGenre: suspend (type: String, genre: String) -> List<StremioRow>,
    val loadDetails: suspend (StremioItem) -> StremioItem,
    val loadStreams: suspend (type: String, videoId: String) -> StremioStreams,
    val onPlay: (item: StremioItem, video: StremioVideo?, stream: StremioStream) -> Unit,
)

/** The Stremio section: movies and series from your Stremio addons only (nothing from your IPTV provider). */
@Composable
fun StremioScreen(
    session: StremioSession,
    state: StremioState,
    continueEntries: List<HistoryEntry>,
    favourites: List<StremioItem>,
    actions: StremioActions,
) {
    var showManage by remember { mutableStateOf(false) }
    var openItem by remember { mutableStateOf<StremioItem?>(null) }
    var autoPlay by remember { mutableStateOf(false) }

    LaunchedEffect(state, session) {
        if (state is StremioState.Idle && session.hasAnything) actions.onNeedData()
    }

    val item = openItem
    when {
        // Nothing connected yet, or the user asked to manage: the connect page.
        !session.hasAnything || showManage -> ManagePage(
            session = session,
            actions = actions,
            canClose = session.hasAnything,
            onClose = { showManage = false },
        )
        item != null -> Details(
            item,
            actions,
            isFavourite = favourites.any { it.type == item.type && it.id == item.id },
            autoPlay = autoPlay,
        ) {
            openItem = null
            autoPlay = false
        }
        else -> Browse(
            state = state,
            continueEntries = continueEntries,
            favourites = favourites,
            actions = actions,
            onOpen = { openItem = it; autoPlay = false },
            onPlay = { openItem = it; autoPlay = true },
            onManage = { showManage = true },
        )
    }
}

// ---------------------------------------------------------------------------- browse

/** The accent colour of the Stremio section. */
private val NetRed = Color(0xFFE23744)

/** A row on the browse page, with the size its tiles are drawn at. */
private class RailSpec(val rail: Rail, val width: Dp, val height: Dp)

private val TabNames = listOf("Movies", "Series", "My List", "Search")

@Composable
private fun Browse(
    state: StremioState,
    continueEntries: List<HistoryEntry>,
    favourites: List<StremioItem>,
    actions: StremioActions,
    onOpen: (StremioItem) -> Unit,
    onPlay: (StremioItem) -> Unit,
    onManage: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<StremioItem>?>(null) }
    var genre by remember { mutableStateOf<String?>(null) }
    var genreRows by remember { mutableStateOf<List<StremioRow>?>(null) }
    var heroIndex by remember { mutableIntStateOf(0) }

    val browsing = tab == 0 || tab == 1
    val type = if (tab == 1) "series" else "movie"
    val searching = tab == 3 && query.trim().length >= 2

    // Searches a moment after you stop typing.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            results = null
        } else {
            results = null
            delay(500)
            results = runCatching { actions.search(q) }.getOrDefault(emptyList())
        }
    }
    // Fetches the chosen genre from the addons whenever the genre or the tab changes.
    LaunchedEffect(genre, tab) {
        val g = genre
        genreRows = null
        if (g != null && browsing) genreRows = runCatching { actions.byGenre(type, g) }.getOrDefault(emptyList())
    }
    // A different tab has different genres, so start again from "All".
    LaunchedEffect(tab) { genre = null; heroIndex = 0 }

    // The big banner at the top shows a few popular titles, one after the other.
    val heroItems: List<StremioItem> = if (!browsing) emptyList() else {
        (state as? StremioState.Ready)?.home?.rows.orEmpty()
            .filter { it.type == type }
            .flatMap { it.items }
            .filter { !it.background.isNullOrBlank() || !it.poster.isNullOrBlank() }
            .distinctBy { it.id }
            .take(6)
    }
    LaunchedEffect(heroItems.size, tab) {
        while (heroItems.size > 1) {
            delay(9000)
            heroIndex = (heroIndex + 1) % heroItems.size
        }
    }

    when (state) {
        is StremioState.Idle, is StremioState.Loading -> Column(Modifier.fillMaxSize()) {
            TabBar(tab, { tab = it }, onManage)
            Note("Loading your Stremio addons...")
        }
        is StremioState.Error -> Column(Modifier.fillMaxSize()) {
            TabBar(tab, { tab = it }, onManage)
            Column(Modifier.padding(horizontal = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.message, fontSize = 17.sp, color = Palette.TextSecondary)
                ActionButton("Try again", Modifier.width(150.dp), accent = true, accentColor = NetRed) { actions.onNeedData() }
            }
        }
        is StremioState.Ready -> {
            val toggle: (StremioItem) -> Unit = { actions.onToggleFavourite(it) }
            val rails = ArrayList<RailSpec>()

            fun add(title: String, items: List<StremioItem>, w: Dp = 150.dp, h: Dp = 225.dp) {
                if (items.isNotEmpty()) rails += RailSpec(Rail(title, items.map { tileFor(it, onOpen, toggle) }), w, h)
            }

            fun addContinue(filterType: String?) {
                val cont = continueEntries.mapNotNull { e -> entryToItem(e)?.let { e to it } }
                    .filter { filterType == null || it.second.type == filterType }
                    .distinctBy { it.second.type + ":" + it.second.id }
                if (cont.isEmpty()) return
                rails += RailSpec(
                    Rail(
                        "Continue Watching",
                        cont.map { (e, found) ->
                            HomeTile(
                                id = "cw:" + e.key,
                                title = found.name,
                                subtitle = e.subtitle,
                                imageUrl = found.poster,
                                progress = if (e.durationMs > 0) e.positionMs.toFloat() / e.durationMs else null,
                                progressColor = NetRed,
                            ) { onOpen(found) }
                        },
                    ),
                    220.dp, 124.dp,
                )
            }

            val hits = results
            when {
                tab == 3 -> if (searching && hits != null) {
                    add("Movies", hits.filter { it.type == "movie" })
                    add("Series", hits.filter { it.type == "series" })
                }
                tab == 2 -> {
                    addContinue(null)
                    add("My Movies", favourites.filter { it.type == "movie" }, 130.dp, 195.dp)
                    add("My Series", favourites.filter { it.type == "series" }, 130.dp, 195.dp)
                }
                genre != null -> {
                    val g = genre!!
                    val fromAddons = genreRows
                    if (fromAddons != null) {
                        fromAddons.forEach { row -> add(row.title, row.items) }
                        if (rails.isEmpty()) {
                            // The addons couldn't filter by genre, so filter what's already on screen.
                            val seen = HashSet<String>()
                            val local = state.home.rows.filter { it.type == type }.flatMap { it.items }
                                .filter { it.genres.any { x -> x.equals(g, true) } && seen.add(it.id) }
                            add(g, local)
                        }
                    }
                }
                else -> {
                    addContinue(type)
                    state.home.rows.filter { it.type == type }.forEach { row: StremioRow -> add(row.title, row.items) }
                }
            }

            val genreList: List<String> = if (!browsing) emptyList() else run {
                val fromLists = state.home.addons.flatMap { a ->
                    a.catalogs.filter { it.type == type && it.genreOnly }.flatMap { it.genreOptions }
                }
                val all = fromLists.ifEmpty {
                    state.home.rows.filter { it.type == type }.flatMap { it.items }.flatMap { it.genres }
                }
                all.distinctBy { it.lowercase() }.take(40)
            }
            val showBanner = browsing && genre == null && heroItems.isNotEmpty()
            val names = state.home.addons.joinToString(", ") { it.name }
            val addonNote = "${state.home.addons.size} addon(s) loaded: $names" +
                if (state.home.failedAddons > 0) "  |  ${state.home.failedAddons} couldn't be reached" else ""

            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 40.dp),
            ) {
                item {
                    Box(Modifier.fillMaxWidth()) {
                        if (showBanner) {
                            val hero = heroItems[heroIndex.coerceIn(0, heroItems.size - 1)]
                            Billboard(
                                item = hero,
                                isFavourite = favourites.any { it.type == hero.type && it.id == hero.id },
                                onPlay = { onPlay(hero) },
                                onInfo = { onOpen(hero) },
                                onFavourite = { toggle(hero) },
                            )
                        }
                        TabBar(tab, { tab = it }, onManage, Modifier.align(Alignment.TopStart))
                    }
                }
                if (genreList.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 40.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ActionButton("All", Modifier.width(80.dp), accent = genre == null, accentColor = NetRed) { genre = null }
                            genreList.forEach { g ->
                                ActionButton(g, Modifier.width(150.dp), accent = genre.equals(g, true), accentColor = NetRed) { genre = g }
                            }
                        }
                    }
                }
                if (tab == 3) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 40.dp)) {
                            LabeledField("Search movies and series", query) { query = it }
                        }
                    }
                }
                if (tab == 3 && !searching) {
                    item { Note("Type at least 2 letters to search all your addons.") }
                } else if (tab == 3 && hits == null) {
                    item { Note("Searching...") }
                } else if (browsing && genre != null && genreRows == null) {
                    item { Note("Loading ${genre}...") }
                } else if (rails.isEmpty()) {
                    item {
                        Note(
                            when {
                                tab == 3 -> "Nothing found."
                                tab == 2 -> "Nothing here yet. Open a title and choose Add to My List, or hold OK on a poster."
                                genre != null -> "Nothing found for $genre. Try another genre."
                                else -> "None of your addons offer a plain list of ${if (tab == 0) "movies" else "series"}. " +
                                    "Try Search, or add Cinemeta in Stremio."
                            }
                        )
                    }
                }
                itemsIndexed(rails, key = { i, r -> "$i|${r.rail.title}" }) { _, spec -> RailRow(spec.rail, spec.width, spec.height) }
                item { Text(addonNote, fontSize = 12.sp, color = Palette.TextSecondary, modifier = Modifier.padding(horizontal = 40.dp)) }
            }
        }
    }
}

/** The row of tabs along the top: Movies, Series, My List, Search, and the addon manager on the right. */
@Composable
private fun TabBar(tab: Int, onTab: (Int) -> Unit, onManage: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TabNames.forEachIndexed { i, label ->
            val on = tab == i
            FocusTile(
                onClick = { onTab(i) },
                modifier = Modifier.width(112.dp).height(40.dp),
                corner = 20.dp,
                focusedScale = 1.05f,
                backgroundColor = if (on) Color(0x4DFFFFFF) else Color(0x33000000),
            ) {
                Text(
                    label,
                    fontSize = 16.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) Color.White else Color(0xFFD6D6DE),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        FocusTile(
            onClick = onManage,
            modifier = Modifier.width(100.dp).height(40.dp),
            corner = 20.dp,
            focusedScale = 1.05f,
            backgroundColor = Color(0x33000000),
        ) {
            Text("Addons", fontSize = 15.sp, color = Color(0xFFD6D6DE), modifier = Modifier.align(Alignment.Center))
        }
    }
}

/** The big featured picture across the top of the page. */
@Composable
private fun Billboard(
    item: StremioItem,
    isFavourite: Boolean,
    onPlay: () -> Unit,
    onInfo: () -> Unit,
    onFavourite: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(340.dp)) {
        Artwork(item.background ?: item.poster, item.name, Modifier.fillMaxSize())
        // Dark on the left so the writing is easy to read, and fading into the page at the bottom.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.92f), 0.55f to Color.Black.copy(alpha = 0.55f), 1f to Color.Transparent)
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.7f to Color.Transparent, 1f to Palette.Background)
            )
        )
        Column(
            Modifier.align(Alignment.CenterStart).fillMaxWidth(0.5f).padding(start = 48.dp, top = 56.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (item.type == "series") "SERIES" else "FILM",
                fontSize = 13.sp,
                color = NetRed,
                fontWeight = FontWeight.Bold,
            )
            Text(
                item.name,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOf(item.year, item.genres.take(3).joinToString(", "), if (item.rating.isNotBlank()) "Rating ${item.rating}" else "")
                .filter { it.isNotBlank() }.joinToString("   |   ")
            if (meta.isNotBlank()) Text(meta, fontSize = 14.sp, color = Color(0xFFD0D0D8))
            if (item.description.isNotBlank()) {
                Text(item.description, fontSize = 14.sp, color = Color(0xFFC0C0C8), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                ActionButton("Play", Modifier.width(120.dp), accent = true, accentColor = Color.White, textColor = Color.Black) { onPlay() }
                ActionButton("More Info", Modifier.width(130.dp)) { onInfo() }
                ActionButton(if (isFavourite) "In My List" else "My List", Modifier.width(130.dp)) { onFavourite() }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 36.dp)) {
        Text(text, fontSize = 17.sp, color = Palette.TextSecondary)
    }
}

private fun tileFor(item: StremioItem, onOpen: (StremioItem) -> Unit, onFavourite: ((StremioItem) -> Unit)? = null) =
    HomeTile(
        id = item.type + ":" + item.id,
        title = item.name,
        subtitle = item.year,
        imageUrl = item.poster,
        onLongClick = onFavourite?.let { f -> { f(item) } },
    ) { onOpen(item) }

/**
 * Turns a saved watch entry back into a title to open.
 * Movie entries are "stremio:movie:ID". Episode entries are "stremio:series:ID|EPISODEID".
 */
private fun entryToItem(e: HistoryEntry): StremioItem? {
    val parts = e.key.removePrefix("stremio:").substringBefore('|').split(':', limit = 2)
    if (parts.size != 2) return null
    return StremioItem(id = parts[1], type = parts[0], name = e.title, poster = e.imageUrl)
}

// ---------------------------------------------------------------------------- details and streams

private data class Pick(val video: StremioVideo?)

@Composable
private fun Details(start: StremioItem, actions: StremioActions, isFavourite: Boolean, autoPlay: Boolean, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    // Full screen: the side menu steps out of the way.
    ImmersiveEffect(true)
    var info by remember(start.id) { mutableStateOf(start) }
    var picking by remember(start.id) {
        mutableStateOf<Pick?>(if (autoPlay && start.type != "series") Pick(null) else null)
    }
    var season by remember(start.id) { mutableIntStateOf(-1) }

    LaunchedEffect(start.id) {
        info = runCatching { actions.loadDetails(start) }.getOrDefault(start)
    }

    val pick = picking
    if (pick != null) {
        StreamPicker(info, pick, actions, onBack = { picking = null })
        return
    }

    val playFocus = remember { FocusRequester() }
    LaunchedEffect(start.id) { runCatching { playFocus.requestFocus() } }

    val meta = listOf(info.year, info.genres.take(3).joinToString(", "), info.runtime, if (info.rating.isNotBlank()) "Rating ${info.rating}" else "")
        .filter { it.isNotBlank() }.joinToString("   |   ")
    val listLabel = if (isFavourite) "Remove from My List" else "Add to My List"

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
            val current = if (season in seasons) season else seasons.firstOrNull { it > 0 } ?: seasons.firstOrNull() ?: 1
            val episodes = info.videos.filter { it.season == current }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 48.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Column(Modifier.fillMaxWidth(0.58f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(info.name, fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (meta.isNotBlank()) Text(meta, fontSize = 15.sp, color = Color(0xFFD0D0D8))
                        Text(
                            info.description.ifBlank { "No description." },
                            fontSize = 15.sp,
                            color = Color(0xFFC0C0C8),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
                            ActionButton(
                                if (episodes.isEmpty()) "Loading..." else "Play S$current E${episodes.first().episode}",
                                Modifier.width(190.dp).focusRequester(playFocus),
                                accent = true,
                                accentColor = Color.White,
                                textColor = Color.Black,
                            ) { episodes.firstOrNull()?.let { picking = Pick(it) } }
                            ActionButton(listLabel, Modifier.width(210.dp)) { actions.onToggleFavourite(info) }
                            ActionButton("Back", Modifier.width(110.dp)) { onBack() }
                        }
                    }
                }
                if (seasons.size > 1) {
                    item {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(top = 10.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            seasons.forEach { sn ->
                                ActionButton(if (sn == 0) "Specials" else "Season $sn", Modifier.width(130.dp), accent = sn == current, accentColor = NetRed) { season = sn }
                            }
                        }
                    }
                }
                items(episodes, key = { it.id }) { ep ->
                    FocusTile(
                        onClick = { picking = Pick(ep) },
                        modifier = Modifier.fillMaxWidth(0.75f).height(88.dp),
                        corner = 12.dp,
                        focusedScale = 1.02f,
                        backgroundColor = Color(0xB3000000),
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Artwork(ep.thumbnail ?: info.poster, ep.title.ifBlank { info.name }, Modifier.width(130.dp).fillMaxHeight().clip(RoundedCornerShape(8.dp)))
                            Column(Modifier.weight(1f)) {
                                Text("Episode ${ep.episode}", fontSize = 13.sp, color = Palette.TextSecondary)
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
                if (info.videos.isEmpty()) {
                    item { Text("Loading episodes... If this stays empty, none of your addons list episodes for this series.", fontSize = 15.sp, color = Palette.TextSecondary) }
                }
            }
        } else {
            Column(
                Modifier.align(Alignment.CenterStart).fillMaxWidth(0.55f).padding(start = 56.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(info.name, fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (meta.isNotBlank()) Text(meta, fontSize = 16.sp, color = Color(0xFFD0D0D8))
                Text(
                    info.description.ifBlank { "No description." },
                    fontSize = 17.sp,
                    color = Color(0xFFC0C0C8),
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    ActionButton(
                        "Play",
                        Modifier.width(140.dp).focusRequester(playFocus),
                        accent = true,
                        accentColor = Color.White,
                        textColor = Color.Black,
                    ) { picking = Pick(null) }
                    ActionButton(listLabel, Modifier.width(210.dp)) { actions.onToggleFavourite(info) }
                    ActionButton("Back", Modifier.width(110.dp)) { onBack() }
                }
            }
        }
    }
}

@Composable
private fun StreamPicker(item: StremioItem, pick: Pick, actions: StremioActions, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val videoId = pick.video?.id ?: item.id
    var result by remember(videoId) { mutableStateOf<StremioStreams?>(null) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(videoId) {
        result = runCatching { actions.loadStreams(item.type, videoId) }
            .getOrDefault(StremioStreams(emptyList(), 0, 1))
    }
    val r = result
    LaunchedEffect(r) {
        if (r != null && r.playable.isNotEmpty()) runCatching { firstFocus.requestFocus() }
    }

    val label = pick.video?.let { "S${it.season} E${it.episode}  ${it.title}" } ?: item.year

    Column(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 24.dp)) {
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
                        "${r.torrentOnly} torrent-only stream(s) were skipped. They can only play through a debrid service, so check your debrid addon is added in Manage.",
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
                            corner = 10.dp,
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

// ---------------------------------------------------------------------------- connect / manage

/** The sign-in and addon page, for use inside Settings. */
@Composable
fun StremioAccountPage(session: StremioSession, actions: StremioActions) {
    ManagePage(session = session, actions = actions, canClose = false, onClose = {})
}

@Composable
private fun ManagePage(session: StremioSession, actions: StremioActions, canClose: Boolean, onClose: () -> Unit) {
    BackHandler(enabled = canClose) { onClose() }
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var signMessage by remember { mutableStateOf<String?>(null) }
    var addMessage by remember { mutableStateOf<String?>(null) }
    var busySign by remember { mutableStateOf(false) }
    var busyAdd by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 36.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Stremio", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary) }
        item {
            Text(
                "Movies and series here come only from your Stremio addons. Your IPTV provider isn't mixed in.",
                fontSize = 15.sp,
                color = Palette.TextSecondary,
            )
        }

        if (session.signedIn) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "Signed in as ${session.email.ifBlank { "your Stremio account" }}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Palette.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    ActionButton("Sign out", Modifier.width(130.dp)) { actions.onSignOut() }
                }
            }
        } else {
            item { Text("Sign in with your Stremio account", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary) }
            item { LabeledField("Email", email) { email = it } }
            item { LabeledField("Password", password, password = true) { password = it } }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ActionButton(if (busySign) "Signing in..." else "Sign in", Modifier.width(160.dp), accent = true) {
                        if (!busySign) {
                            busySign = true
                            signMessage = null
                            scope.launch {
                                signMessage = actions.onSignIn(email, password)
                                password = ""
                                busySign = false
                            }
                        }
                    }
                    signMessage?.let { Text(it, fontSize = 15.sp, color = Palette.Accent, modifier = Modifier.weight(1f)) }
                }
            }
            item {
                Text(
                    "Your password is sent to Stremio once and is not saved here. If you sign in to Stremio with Facebook or Apple, add your addon links below instead.",
                    fontSize = 13.sp,
                    color = Palette.TextSecondary,
                )
            }
        }

        item { Text("Add an addon link", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary) }
        item { LabeledField("Addon link (ends in manifest.json, or starts with stremio://)", link) { link = it } }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ActionButton(if (busyAdd) "Checking..." else "Add addon", Modifier.width(160.dp)) {
                    if (!busyAdd) {
                        busyAdd = true
                        addMessage = null
                        scope.launch {
                            val error = actions.onAddAddon(link)
                            if (error == null) {
                                link = ""
                                addMessage = "Added."
                            } else {
                                addMessage = error
                            }
                            busyAdd = false
                        }
                    }
                }
                addMessage?.let { Text(it, fontSize = 15.sp, color = Palette.Accent, modifier = Modifier.weight(1f)) }
            }
        }

        if (session.manualAddons.isNotEmpty()) {
            item { Text("Addons you added", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary) }
            items(session.manualAddons, key = { it }) { url ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Links can contain a private key, so only the site name is shown.
                    Text(hostOf(url), fontSize = 16.sp, color = Palette.TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ActionButton("Remove", Modifier.width(120.dp)) { actions.onRemoveAddon(url) }
                }
            }
        }

        item {
            Text(
                "Addon links can contain your debrid key, so they are kept only on this device.",
                fontSize = 13.sp,
                color = Palette.TextSecondary,
            )
        }
        if (canClose) {
            item { ActionButton("Done", Modifier.width(140.dp), accent = true) { onClose() } }
        }
    }
}

private fun hostOf(url: String): String =
    try {
        java.net.URI(url).host ?: "addon"
    } catch (e: Exception) {
        "addon"
    }
