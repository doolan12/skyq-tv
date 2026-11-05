package com.skyqlike.iptv.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.history.HistoryEntry
import com.skyqlike.iptv.data.profile.Profile
import com.skyqlike.iptv.data.stremio.StremioItem
import com.skyqlike.iptv.data.stremio.StremioVideo
import com.skyqlike.iptv.data.stremio.StremioRow
import com.skyqlike.iptv.data.stremio.StremioState
import com.skyqlike.iptv.data.tmdb.Trending
import com.skyqlike.iptv.data.topposters.TopPosters
import com.skyqlike.iptv.ui.common.ActionButton
import com.skyqlike.iptv.ui.common.Artwork
import com.skyqlike.iptv.ui.common.FocusTile
import com.skyqlike.iptv.ui.common.HomeTile
import com.skyqlike.iptv.ui.common.LabeledField
import com.skyqlike.iptv.ui.common.MetaLine
import com.skyqlike.iptv.ui.common.Rail
import com.skyqlike.iptv.ui.common.RailRow
import com.skyqlike.iptv.ui.common.Top10Row
import com.skyqlike.iptv.ui.profiles.ProfileAvatar
import com.skyqlike.iptv.ui.stremio.StremioActions
import com.skyqlike.iptv.ui.theme.Palette
import kotlinx.coroutines.delay

/**
 * What the browse page remembers while you open a title and come back:
 * which tab you were on, the genre, your search, and how far down you had scrolled.
 */
class BrowseUi {
    var tab by mutableIntStateOf(0)
    var genre by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    val list = LazyListState()
}

private const val TAB_HOME = 0
private const val TAB_SERIES = 1
private const val TAB_MOVIES = 2
private const val TAB_MY_LIST = 3
private const val TAB_SEARCH = 4

private val TabNames = listOf("Home", "Series", "Movies", "My List", "Search")

/** Titles that share a second genre with the one picked, for a row like "Action & Comedy". */
private class PairBucket(val label: String) {
    val items = ArrayList<StremioItem>()
}

/** The episode after one you just finished. */
private class UpNext(val item: StremioItem, val video: StremioVideo, val updatedAt: Long)

/** A row on the page, with the size its tiles are drawn at. */
private class RailSpec(val rail: Rail, val width: Dp, val height: Dp, val top10: Boolean = false)

@Composable
fun BrowseScreen(
    ui: BrowseUi,
    profile: Profile,
    state: StremioState,
    continueEntries: List<HistoryEntry>,
    /** Everything this profile has watched, including finished titles. Used for suggestions. */
    watched: List<HistoryEntry>,
    favourites: List<StremioItem>,
    trending: Trending?,
    /** The Top Posters key from Settings. Empty means normal posters. */
    topPostersKey: String,
    actions: StremioActions,
    onOpen: (StremioItem) -> Unit,
    onPlay: (StremioItem) -> Unit,
    /** Opens a series and starts this episode (used by the "Up next" tiles in Continue Watching). */
    onOpenEpisode: (StremioItem, String) -> Unit,
    onSettings: () -> Unit,
    onSwitchProfile: () -> Unit,
) {
    val tab = ui.tab
    val browsing = tab == TAB_HOME || tab == TAB_SERIES || tab == TAB_MOVIES
    val type: String? = when (tab) {
        TAB_SERIES -> "series"
        TAB_MOVIES -> "movie"
        else -> null
    }
    val searching = tab == TAB_SEARCH && ui.query.trim().length >= 2

    var results by remember { mutableStateOf<List<StremioItem>?>(null) }
    var genreRows by remember { mutableStateOf<List<StremioRow>?>(null) }
    var heroIndex by remember { mutableIntStateOf(0) }
    val playFocus = remember { FocusRequester() }

    // Back goes to Home first, and only then leaves the app.
    BackHandler(enabled = tab != TAB_HOME) {
        ui.tab = TAB_HOME
        ui.genre = null
    }

    val selectTab: (Int) -> Unit = {
        ui.tab = it
        ui.genre = null
        heroIndex = 0
    }

    // Searches a moment after you stop typing.
    LaunchedEffect(ui.query) {
        val q = ui.query.trim()
        if (q.length < 2) {
            results = null
        } else {
            results = null
            delay(500)
            results = runCatching { actions.search(q) }.getOrDefault(emptyList())
        }
    }
    // Fetches the chosen genre from the addons whenever the genre or the tab changes.
    LaunchedEffect(ui.genre, tab) {
        val g = ui.genre
        genreRows = null
        if (g != null && type != null) genreRows = runCatching { actions.byGenre(type, g) }.getOrDefault(emptyList())
    }

    val ready = state as? StremioState.Ready

    // The big banner at the top shows a few popular titles, one after the other.
    val heroItems: List<StremioItem> = if (!browsing || ready == null) emptyList() else {
        val pool = ready.home.rows
            .filter { type == null || it.type == type }
            .flatMap { it.items }
            .filter { !it.background.isNullOrBlank() || !it.poster.isNullOrBlank() }
            .distinctBy { it.type + ":" + it.id }
        if (type == null) {
            interleave(pool.filter { it.type == "series" }.take(4), pool.filter { it.type == "movie" }.take(4))
        } else {
            pool.take(6)
        }
    }
    LaunchedEffect(heroItems.size, tab) {
        while (heroItems.size > 1) {
            delay(9000)
            heroIndex = (heroIndex + 1) % heroItems.size
        }
    }

    // The remote starts on the Play button, like a streaming app.
    LaunchedEffect(ready != null, tab) {
        if (ready != null && ui.list.firstVisibleItemIndex == 0) {
            delay(250)
            runCatching { playFocus.requestFocus() }
        }
    }

    // Works out the genres of what you've watched recently, so the suggestion rows know your taste.
    // Each title is looked up once; the answers are kept while the app is open.
    val genreCache = remember { HashMap<String, List<String>>() }
    var watchedGenres by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    LaunchedEffect(ready != null, watched.firstOrNull()?.key, favourites.size) {
        val r = ready ?: return@LaunchedEffect
        val pool = r.home.rows.flatMap { it.items }
        val recent = watched.filter { it.key.startsWith("stremio:") }.mapNotNull { entryToItem(it) }
            .distinctBy { it.type + ":" + it.id }.take(6)
        val out = LinkedHashMap<String, List<String>>()
        for (w in recent) {
            val k = w.type + ":" + w.id
            out[k] = genreCache.getOrPut(k) {
                pool.firstOrNull { it.type == w.type && it.id == w.id && it.genres.isNotEmpty() }?.genres
                    ?: favourites.firstOrNull { it.type == w.type && it.id == w.id && it.genres.isNotEmpty() }?.genres
                    ?: runCatching { actions.loadDetails(w).genres }.getOrDefault(emptyList())
            }
        }
        watchedGenres = out
    }

    // Finished an episode? Continue Watching should offer the next one, like Netflix does.
    // Only the newest entry for each show counts, and each show is looked up once.
    var upNext by remember { mutableStateOf<List<UpNext>>(emptyList()) }
    val videosCache = remember { HashMap<String, List<StremioVideo>>() }
    LaunchedEffect(ready != null, watched.take(12).map { it.key + it.finished }) {
        if (ready == null) return@LaunchedEffect
        val seen = HashSet<String>()
        val latest = watched.filter { it.key.startsWith("stremio:series:") }
            .filter { seen.add(it.key.substringAfter("stremio:series:").substringBefore('|')) }
            .take(6)
        val out = ArrayList<UpNext>()
        for (e in latest) {
            if (!e.finished) continue
            val seriesId = e.key.substringAfter("stremio:series:").substringBefore('|')
            val epId = e.key.substringAfter('|', "")
            if (epId.isEmpty()) continue
            val item = entryToItem(e) ?: continue
            val videos = videosCache.getOrPut(seriesId) {
                runCatching { actions.loadDetails(item).videos }.getOrDefault(emptyList())
            }
            val ordered = videos.filter { it.season > 0 }.sortedWith(compareBy({ it.season }, { it.episode }))
            val at = ordered.indexOfFirst { it.id == epId }
            val next = if (at >= 0) ordered.getOrNull(at + 1) else null
            if (next != null) out += UpNext(item, next, e.updatedAt)
        }
        upNext = out
    }

    when (state) {
        is StremioState.Idle, is StremioState.Loading -> Column(Modifier.fillMaxSize()) {
            TopBar(tab, selectTab, profile, onSettings, onSwitchProfile)
            Note("Loading your addons...")
        }
        is StremioState.Error -> Column(Modifier.fillMaxSize()) {
            TopBar(tab, selectTab, profile, onSettings, onSwitchProfile)
            Column(Modifier.padding(horizontal = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.message, fontSize = 17.sp, color = Palette.TextSecondary)
                ActionButton("Try again", Modifier.width(150.dp), accent = true) { actions.onNeedData() }
            }
        }
        is StremioState.Ready -> {
            val toggle: (StremioItem) -> Unit = { actions.onToggleFavourite(it) }
            val rails = ArrayList<RailSpec>()

            fun add(title: String, items: List<StremioItem>, w: Dp = 130.dp, h: Dp = 195.dp) {
                if (items.isNotEmpty()) rails += RailSpec(Rail(title, items.map { tileFor(it, onOpen, toggle, topPostersKey) }), w, h)
            }

            // A numbered row. With a TMDB key it shows what is trending today. Without one it shows the
            // first list your addons give (normally "Popular"), named honestly, and that list is left out
            // of the normal rows so it doesn't show twice.
            val usedForTop10 = ArrayList<StremioRow>()
            fun notTop10(r: StremioRow) = usedForTop10.none { it === r }

            fun addTop10(rowType: String) {
                val word = if (rowType == "movie") "Movies" else "Series"
                val live = (if (rowType == "movie") trending?.movies else trending?.series).orEmpty().take(10)
                if (live.size >= 5) {
                    rails += RailSpec(Rail("Trending $word Today", live.map { tileFor(it, onOpen, toggle, topPostersKey) }), 130.dp, 195.dp, top10 = true)
                    return
                }
                val first = state.home.rows.firstOrNull { it.type == rowType } ?: return
                val items = first.items.filter { !it.poster.isNullOrBlank() }.take(10)
                if (items.size < 5) return
                usedForTop10 += first
                rails += RailSpec(Rail("Popular $word", items.map { tileFor(it, onOpen, toggle, topPostersKey) }), 130.dp, 195.dp, top10 = true)
            }

            fun addContinue(filterType: String?) {
                val cont = continueEntries.mapNotNull { e -> entryToItem(e)?.let { e to it } }
                    .filter { filterType == null || it.second.type == filterType }
                    .distinctBy { it.second.type + ":" + it.second.id }
                val tiles = ArrayList<Pair<Long, HomeTile>>()
                cont.forEach { (e, found) ->
                    tiles += e.updatedAt to HomeTile(
                        id = "cw:" + e.key,
                        title = found.name,
                        subtitle = e.subtitle,
                        imageUrl = found.poster,
                        progress = if (e.durationMs > 0) e.positionMs.toFloat() / e.durationMs else null,
                    ) { onOpen(found) }
                }
                if (filterType == null || filterType == "series") {
                    upNext.forEach { u ->
                        tiles += u.updatedAt to HomeTile(
                            id = "next:" + u.item.id + ":" + u.video.id,
                            title = u.item.name,
                            subtitle = "Up next: S${u.video.season} E${u.video.episode}",
                            imageUrl = u.item.poster,
                        ) { onOpenEpisode(u.item, u.video.id) }
                    }
                }
                if (tiles.isEmpty()) return
                rails += RailSpec(Rail("Continue Watching", tiles.sortedByDescending { it.first }.map { it.second }), 200.dp, 112.dp)
            }

            // Suggestions for Home: "Because you watched ..." and "Picked for you", from the titles
            // already loaded. Things you've watched or put in My List are left out.
            fun addSuggestions() {
                val seen = HashSet<String>()
                val recentItems = watched.filter { it.key.startsWith("stremio:") }.mapNotNull { entryToItem(it) }
                recentItems.forEach { seen += it.type + ":" + it.id }
                favourites.forEach { seen += it.type + ":" + it.id }
                val fresh = state.home.rows.flatMap { it.items }
                    .filter { !it.poster.isNullOrBlank() && (it.type + ":" + it.id) !in seen }
                    .distinctBy { it.type + ":" + it.id }
                if (fresh.isEmpty()) return

                fun overlap(a: List<String>, b: List<String>): Int {
                    val set = a.map { it.lowercase() }.toSet()
                    return b.count { it.lowercase() in set }
                }

                val shown = HashSet<String>()
                // Titles like the one you watched most recently.
                val last = watchedGenres.entries.firstOrNull { it.value.isNotEmpty() }
                if (last != null) {
                    val need = minOf(2, last.value.size)
                    val like = fresh.map { it to overlap(last.value, it.genres) }
                        .filter { it.second >= need }
                        .sortedByDescending { it.second }
                        .map { it.first }
                        .take(20)
                    val name = recentItems.firstOrNull { (it.type + ":" + it.id) == last.key }?.name
                    if (like.size >= 5 && name != null) {
                        like.forEach { shown += it.type + ":" + it.id }
                        add("Because you watched $name", like)
                    }
                }
                // Your overall taste: genres of what you've watched count double, My List counts once.
                val weights = HashMap<String, Int>()
                watchedGenres.values.forEach { gs -> gs.forEach { g -> weights[g.lowercase()] = (weights[g.lowercase()] ?: 0) + 2 } }
                favourites.take(10).forEach { f -> f.genres.forEach { g -> weights[g.lowercase()] = (weights[g.lowercase()] ?: 0) + 1 } }
                if (weights.isNotEmpty()) {
                    val picks = fresh.filter { (it.type + ":" + it.id) !in shown }
                        .map { it to it.genres.sumOf { g -> weights[g.lowercase()] ?: 0 } }
                        .filter { it.second > 0 }
                        .sortedByDescending { it.second }
                        .map { it.first }
                        .take(20)
                    if (picks.size >= 5) add("Picked for you", picks)
                }
            }

            val hits = results
            when {
                tab == TAB_SEARCH -> if (searching && hits != null) {
                    add("Movies", hits.filter { it.type == "movie" })
                    add("Series", hits.filter { it.type == "series" })
                }
                tab == TAB_MY_LIST -> {
                    addContinue(null)
                    add("My Movies", favourites.filter { it.type == "movie" })
                    add("My Series", favourites.filter { it.type == "series" })
                }
                ui.genre != null -> {
                    val g = ui.genre!!
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
                        // Mixed rows such as "Action & Comedy": titles that carry both genres.
                        // They only appear here, once a genre is picked, and never on Home.
                        val localAll = state.home.rows.filter { it.type == type }.flatMap { it.items }
                        val both = (fromAddons.flatMap { it.items } + localAll)
                            .filter { it.genres.any { x -> x.equals(g, true) } }
                            .distinctBy { it.type + ":" + it.id }
                        val buckets = LinkedHashMap<String, PairBucket>()
                        both.forEach { item ->
                            item.genres.forEach { other ->
                                if (!other.equals(g, true)) {
                                    buckets.getOrPut(other.lowercase()) { PairBucket(other) }.items += item
                                }
                            }
                        }
                        buckets.values
                            .filter { it.items.size >= 6 }
                            .sortedByDescending { it.items.size }
                            .take(6)
                            .forEach { add("$g & ${it.label}", it.items.take(30)) }
                    }
                }
                tab == TAB_HOME -> {
                    addContinue(null)
                    addTop10("series")
                    addTop10("movie")
                    addSuggestions()
                    val movieRows = state.home.rows.filter { it.type == "movie" && notTop10(it) }
                    val seriesRows = state.home.rows.filter { it.type == "series" && notTop10(it) }
                    // Series and movie rows take turns, so Home has a bit of everything.
                    for (i in 0 until maxOf(movieRows.size, seriesRows.size)) {
                        seriesRows.getOrNull(i)?.let { add(it.title, it.items) }
                        movieRows.getOrNull(i)?.let { add(it.title, it.items) }
                    }
                }
                else -> {
                    addContinue(type)
                    if (type == "series") addTop10("series")
                    if (type == "movie") addTop10("movie")
                    state.home.rows.filter { it.type == type && notTop10(it) }.forEach { row: StremioRow -> add(row.title, row.items) }
                }
            }

            val genreList: List<String> = if (type == null) emptyList() else run {
                val fromLists = state.home.addons.flatMap { a ->
                    a.catalogs.filter { it.type == type && it.genreOnly }.flatMap { it.genreOptions }
                }
                val all = fromLists.ifEmpty {
                    state.home.rows.filter { it.type == type }.flatMap { it.items }.flatMap { it.genres }
                }
                all.distinctBy { it.lowercase() }.take(40)
            }
            val showBanner = browsing && ui.genre == null && heroItems.isNotEmpty()
            val names = state.home.addons.joinToString(", ") { it.name }
            val addonNote = "${state.home.addons.size} addon(s) loaded: $names" +
                if (state.home.failedAddons > 0) "  |  ${state.home.failedAddons} couldn't be reached" else ""

            LazyColumn(
                Modifier.fillMaxSize(),
                state = ui.list,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 40.dp),
            ) {
                item {
                    Box(Modifier.fillMaxWidth()) {
                        if (showBanner) {
                            val hero = heroItems[heroIndex.coerceIn(0, heroItems.size - 1)]
                            Billboard(
                                item = hero,
                                isFavourite = favourites.any { it.type == hero.type && it.id == hero.id },
                                playFocus = playFocus,
                                onPlay = { onPlay(hero) },
                                onInfo = { onOpen(hero) },
                                onFavourite = { toggle(hero) },
                            )
                        }
                        TopBar(tab, selectTab, profile, onSettings, onSwitchProfile, Modifier.align(Alignment.TopStart))
                    }
                }
                if (genreList.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ActionButton("All", Modifier.width(80.dp), accent = ui.genre == null) { ui.genre = null }
                            genreList.forEach { g ->
                                ActionButton(g, Modifier.width(150.dp), accent = ui.genre.equals(g, true)) { ui.genre = g }
                            }
                        }
                    }
                }
                if (tab == TAB_SEARCH) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 48.dp)) {
                            LabeledField("Search movies and series", ui.query) { ui.query = it }
                        }
                    }
                }
                if (tab == TAB_SEARCH && !searching) {
                    item { Note("Type at least 2 letters to search all your addons.") }
                } else if (tab == TAB_SEARCH && hits == null) {
                    item { Note("Searching...") }
                } else if (browsing && ui.genre != null && genreRows == null) {
                    item { Note("Loading ${ui.genre}...") }
                } else if (rails.isEmpty()) {
                    item {
                        Note(
                            when {
                                tab == TAB_SEARCH -> "Nothing found."
                                tab == TAB_MY_LIST -> "Nothing here yet. Open a title and choose My List, or hold OK on a poster."
                                ui.genre != null -> "Nothing found for ${ui.genre}. Try another genre."
                                else -> "None of your addons offer a plain list of titles here. Try Search, or add Cinemeta in Stremio."
                            }
                        )
                    }
                }
                itemsIndexed(rails, key = { i, r -> "$i|${r.rail.title}" }) { _, spec ->
                    if (spec.top10) Top10Row(spec.rail.title, spec.rail.tiles, spec.width, spec.height)
                    else RailRow(spec.rail, spec.width, spec.height)
                }
                item {
                    Text(
                        addonNote,
                        fontSize = 12.sp,
                        color = Palette.TextSecondary,
                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------- top bar

/** Wordmark, the tabs, Settings, and who's watching. */
@Composable
private fun TopBar(
    tab: Int,
    onTab: (Int) -> Unit,
    profile: Profile,
    onSettings: () -> Unit,
    onSwitchProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
            .padding(horizontal = 40.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "REELHOUSE",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Palette.Accent,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(end = 20.dp),
        )
        // The tabs scroll sideways if the screen is too narrow, so Settings and the profile never get pushed off.
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TabNames.forEachIndexed { i, label -> NavItem(label, tab == i) { onTab(i) } }
        }
        NavItem("Settings", false) { onSettings() }
        FocusTile(
            onClick = onSwitchProfile,
            modifier = Modifier.width(38.dp).height(38.dp),
            corner = 6.dp,
            focusedScale = 1.1f,
            backgroundColor = Color.Transparent,
        ) {
            ProfileAvatar(profile, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    FocusTile(
        onClick = onClick,
        modifier = Modifier.width(100.dp).height(38.dp),
        corner = 19.dp,
        focusedScale = 1.05f,
        backgroundColor = if (selected) Color(0x40FFFFFF) else Color.Transparent,
    ) {
        Text(
            label,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else Color(0xFFD0D0D0),
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

// ---------------------------------------------------------------------------- billboard

/** The big featured picture across the top of the page. */
@Composable
private fun Billboard(
    item: StremioItem,
    isFavourite: Boolean,
    playFocus: FocusRequester,
    onPlay: () -> Unit,
    onInfo: () -> Unit,
    onFavourite: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(340.dp)) {
        FadeIn(item.type + ":" + item.id) {
            Artwork(item.background ?: item.poster, item.name, Modifier.fillMaxSize())
        }
        // Dark on the left so the writing is easy to read, and fading into the page at the bottom.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.92f), 0.55f to Color.Black.copy(alpha = 0.5f), 1f to Color.Transparent)
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
                color = Palette.Accent,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Text(
                item.name,
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            MetaLine(item.year, "", item.rating, fontSize = 14.sp)
            if (item.genres.isNotEmpty()) {
                Text(item.genres.take(3).joinToString("  •  "), fontSize = 14.sp, color = Color(0xFFD0D0D8))
            }
            if (item.description.isNotBlank()) {
                Text(item.description, fontSize = 14.sp, color = Color(0xFFC0C0C8), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                ActionButton(
                    "Play",
                    Modifier.width(120.dp).focusRequester(playFocus),
                    accent = true,
                    accentColor = Color.White,
                    textColor = Color.Black,
                ) { onPlay() }
                ActionButton("More Info", Modifier.width(130.dp)) { onInfo() }
                ActionButton(if (isFavourite) "✓ My List" else "+ My List", Modifier.width(130.dp)) { onFavourite() }
            }
        }
    }
}

/** Fades its content in whenever [key] changes, so the banner glides from one title to the next. */
@Composable
private fun FadeIn(key: Any, content: @Composable () -> Unit) {
    val fade = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { fade.animateTo(1f, tween(700)) }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }) { content() }
}

// ---------------------------------------------------------------------------- helpers

@Composable
private fun Note(text: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 8.dp)) {
        Text(text, fontSize = 17.sp, color = Palette.TextSecondary)
    }
}

private fun interleave(a: List<StremioItem>, b: List<StremioItem>): List<StremioItem> {
    val out = ArrayList<StremioItem>()
    for (i in 0 until maxOf(a.size, b.size)) {
        if (i < a.size) out += a[i]
        if (i < b.size) out += b[i]
    }
    return out
}

private fun tileFor(item: StremioItem, onOpen: (StremioItem) -> Unit, onFavourite: (StremioItem) -> Unit, topPostersKey: String) =
    HomeTile(
        id = item.type + ":" + item.id,
        title = item.name,
        subtitle = item.year,
        imageUrl = TopPosters.poster(topPostersKey, item.id, item.poster),
        onLongClick = { onFavourite(item) },
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
