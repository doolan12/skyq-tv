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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.navigationBarsPadding
import com.skyqlike.iptv.ui.common.LocalSidePad
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.skyqlike.iptv.ui.trailer.TrailerPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

/**
 * What the browse page remembers while you open a title and come back:
 * which tab you were on, the genre, your search, and how far down you had scrolled.
 */
class BrowseUi {
    var tab by mutableIntStateOf(0)
    var genre by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    /** My List filters: 0 = all, 1 = movies, 2 = series. */
    var listType by mutableIntStateOf(0)
    /** My List order: 0 = recently added, 1 = A to Z, 2 = top rated. */
    var listSort by mutableIntStateOf(0)
    val list = LazyListState()
    /** The poster the remote was last on, so Back from a title puts it there again. (Not state: reading it must not redraw anything.) */
    var lastTile: String? = null
}

private const val TAB_HOME = 0
private const val TAB_SERIES = 1
private const val TAB_MOVIES = 2
private const val TAB_MY_LIST = 3
private const val TAB_SEARCH = 4

private val MyListTypeNames = listOf("All titles", "Movies", "Series")
private val MyListSortNames = listOf("Recently added", "A to Z", "Top rated")

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
    /** Extra rows for Home, such as Trakt's "New episodes" and "Recommended on Trakt". */
    extraRows: List<Pair<String, List<StremioItem>>> = emptyList(),
    trending: Trending?,
    /** The Top Posters key from Settings. Empty means normal posters. */
    topPostersKey: String,
    actions: StremioActions,
    onOpen: (StremioItem) -> Unit,
    onPlay: (StremioItem) -> Unit,
    /** Opens a series and starts this episode (used by the "Up next" tiles in Continue Watching). */
    onOpenEpisode: (StremioItem, String) -> Unit,
    onRefresh: () -> Unit = {},
    onDownloads: () -> Unit = {},
    /** Press and hold on a Continue Watching tile removes that title from the row. */
    onRemoveContinue: (StremioItem) -> Unit,
    /** Whether a silent trailer plays behind the banner. */
    previewsOn: Boolean,
    onSettings: () -> Unit,
    onSwitchProfile: () -> Unit,
    /** Opens the Play Something screen. */
    onShuffle: () -> Unit,
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
    var peopleRows by remember { mutableStateOf<List<StremioRow>>(emptyList()) }
    var genreRows by remember { mutableStateOf<List<StremioRow>?>(null) }
    var heroIndex by remember { mutableIntStateOf(0) }
    val playFocus = remember { FocusRequester() }

    // Back goes to Home first, and only then leaves the app.
    BackHandler(enabled = tab != TAB_HOME) {
        ui.tab = TAB_HOME
        ui.genre = null
    }

    // The banner follows the poster the remote is on. Null means the banner is just rotating by itself.
    var focusedHero by remember { mutableStateOf<StremioItem?>(null) }
    val onHeroFocus: (StremioItem) -> Unit = { if (browsing && (!it.background.isNullOrBlank() || !it.poster.isNullOrBlank())) focusedHero = it }

    val selectTab: (Int) -> Unit = {
        ui.tab = it
        ui.lastTile = null
        ui.genre = null
        focusedHero = null
        heroIndex = 0
    }

    val searchContext = androidx.compose.ui.platform.LocalContext.current
    val recentStore = remember { com.skyqlike.iptv.data.settings.RecentSearchStore(searchContext) }
    var recent by remember { mutableStateOf(recentStore.load()) }

    // Searches a moment after you stop typing, and remembers the search once you've paused on it with results.
    LaunchedEffect(ui.query) {
        val q = ui.query.trim()
        if (q.length < 2) {
            results = null
            peopleRows = emptyList()
        } else {
            results = null
            peopleRows = emptyList()
            delay(500)
            val people = async { runCatching { actions.searchPeople(q) }.getOrDefault(emptyList()) }
            results = runCatching { actions.search(q) }.getOrDefault(emptyList())
            peopleRows = people.await()
            if (!results.isNullOrEmpty()) {
                delay(1_500)
                recent = recentStore.add(q)
            }
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
    // A silent trailer plays behind the banner. The banner stays on that title while it plays.
    val heroNow = focusedHero?.takeIf { browsing } ?: heroItems.getOrNull(heroIndex.coerceIn(0, (heroItems.size - 1).coerceAtLeast(0)))
    val heroKey = heroNow?.let { it.type + ":" + it.id }
    val trailerIds = remember { mutableStateMapOf<String, String>() }
    val previewFailed = remember { mutableStateListOf<String>() }
    var previewPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(heroKey, previewsOn) {
        previewPlaying = false
        val h = heroNow ?: return@LaunchedEffect
        val k = heroKey ?: return@LaunchedEffect
        if (!previewsOn || k in trailerIds || k in previewFailed) return@LaunchedEffect
        delay(1200)
        val id = h.trailerId ?: runCatching { actions.loadDetails(h).trailerId }.getOrNull()
        if (id != null) trailerIds[k] = id else previewFailed += k
    }
    // The banner shows the full details of the title you're on: ratings, cast and director come from the addon,
    // looked up once per title after you rest on it for a moment.
    val heroDetails = remember { mutableStateMapOf<String, StremioItem>() }
    LaunchedEffect(heroKey) {
        val h = heroNow ?: return@LaunchedEffect
        val k = heroKey ?: return@LaunchedEffect
        if (k in heroDetails || (h.cast.isNotEmpty() && h.rating.isNotBlank())) return@LaunchedEffect
        delay(450)
        runCatching { actions.loadDetails(h) }.getOrNull()?.let { heroDetails[k] = it.copy(videos = emptyList()) }
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
            TopBar(tab, selectTab, profile, onSettings, onSwitchProfile, onShuffle, onDownloads)
            com.skyqlike.iptv.ui.common.ShimmerHome()
        }
        is StremioState.Error -> Column(Modifier.fillMaxSize()) {
            TopBar(tab, selectTab, profile, onSettings, onSwitchProfile, onShuffle, onDownloads)
            Column(Modifier.padding(horizontal = LocalSidePad.current), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.message, fontSize = 17.sp, color = Palette.TextSecondary)
                ActionButton("Try again", Modifier.width(150.dp), accent = true) { actions.onNeedData() }
                // No internet (on a flight, say)? Downloaded titles still play.
                ActionButton("Open Downloads", Modifier.width(190.dp)) { onDownloads() }
            }
        }
        is StremioState.Ready -> {
            val isTv = androidx.compose.ui.platform.LocalContext.current.let { c ->
                c.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) ||
                    (c.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) ==
                    android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
            }
            val config = LocalConfiguration.current
            val phone = !isTv
            val portrait = config.screenHeightDp > config.screenWidthDp
            val posterW = if (phone && portrait) 112.dp else 130.dp
            val posterH = if (phone && portrait) 168.dp else 195.dp
            // On a phone the first tap on a poster picks it (the strip above changes to match); tapping the picked one opens it.
            val tapOpen: (StremioItem) -> Unit = { item ->
                val shown = heroNow
                if (phone && browsing && !(shown != null && shown.id == item.id && shown.type == item.type)) onHeroFocus(item)
                else onOpen(item)
            }
            val toggle: (StremioItem) -> Unit = { actions.onToggleFavourite(it) }
            val trailerFor: (StremioItem) -> (suspend () -> String?) = { item ->
                { item.trailerId ?: runCatching { actions.loadDetails(item).trailerId }.getOrNull() }
            }
            val rails = ArrayList<RailSpec>()

            // Every title already shown on this screen, so later rows don't repeat it.
            val shownKeys = HashSet<String>()
            fun add(title: String, items: List<StremioItem>, w: Dp = posterW, h: Dp = posterH) {
                items.forEach { shownKeys += it.type + ":" + it.id }
                if (items.isNotEmpty()) rails += RailSpec(Rail(title, items.map { tileFor(it, tapOpen, toggle, topPostersKey, onHeroFocus, trailerFor(it)) }), w, h)
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
                    live.forEach { shownKeys += it.type + ":" + it.id }
                    rails += RailSpec(Rail("Trending $word Today", live.map { tileFor(it, tapOpen, toggle, topPostersKey, onHeroFocus, trailerFor(it)) }), posterW, posterH, top10 = true)
                    return
                }
                val first = state.home.rows.firstOrNull { it.type == rowType } ?: return
                val items = first.items.filter { !it.poster.isNullOrBlank() }.take(10)
                if (items.size < 5) return
                usedForTop10 += first
                items.forEach { shownKeys += it.type + ":" + it.id }
                rails += RailSpec(Rail("Popular $word", items.map { tileFor(it, tapOpen, toggle, topPostersKey, onHeroFocus, trailerFor(it)) }), posterW, posterH, top10 = true)
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
                        onLongClick = { onRemoveContinue(found) },
                    ) {
                        // Straight back into it: the episode you were on, or the movie, and it resumes where you stopped.
                        val epId = e.key.substringAfter('|', "")
                        if (found.type == "series" && epId.isNotEmpty()) onOpenEpisode(found, epId) else onPlay(found)
                    }
                }
                if (filterType == null || filterType == "series") {
                    upNext.forEach { u ->
                        tiles += u.updatedAt to HomeTile(
                            id = "next:" + u.item.id + ":" + u.video.id,
                            title = u.item.name,
                            subtitle = "Up next: S${u.video.season} E${u.video.episode}",
                            imageUrl = u.item.poster,
                            onLongClick = { onRemoveContinue(u.item) },
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

            // Tidies a list of rows so Home isn't the same posters over and over: a title that already
            // appeared higher up is left out of later rows, and a row left with too little new is dropped
            // (that is how two addons' "Popular" lists, or a list that duplicates the Trending row, collapse into one).
            fun tidy(rows: List<StremioRow>, maxRows: Int): List<StremioRow> {
                // "Popular - Movies (TMDB)" and "Popular - Movies" are the same list; compare names without the addon part.
                fun nameKey(r: StremioRow) = r.type + "|" + r.title.substringBefore(" (").lowercase().trim()
                val out = ArrayList<StremioRow>()
                val keys = ArrayList<String>()
                for (r in rows) {
                    val ids = r.items.map { it.type + ":" + it.id }.toSet()
                    val fresh = r.items.filter { (it.type + ":" + it.id) !in shownKeys }.distinctBy { it.id }
                    val key = nameKey(r)
                    // Which earlier row is this one most like? Same name, or mostly the same titles.
                    var twin = keys.indexOf(key)
                    if (twin < 0) {
                        var best = 0
                        out.forEachIndexed { i, o ->
                            val same = o.items.count { (it.type + ":" + it.id) in ids }
                            if (same > best && same * 10 >= r.items.size * 4) { best = same; twin = i }
                        }
                    }
                    if (twin >= 0) {
                        // Fold the new titles into the earlier row, so similar categories become one.
                        if (fresh.isNotEmpty() && out[twin].items.size < 30) {
                            out[twin] = out[twin].copy(items = (out[twin].items + fresh).take(30))
                            fresh.forEach { shownKeys += it.type + ":" + it.id }
                        }
                        continue
                    }
                    if (out.size >= maxRows) continue
                    // Small lists are kept only if they're mostly new.
                    if (fresh.size < minOf(6, r.items.size) || fresh.size * 2 < r.items.size) continue
                    fresh.forEach { shownKeys += it.type + ":" + it.id }
                    out += r.copy(items = fresh)
                    keys += key
                }
                return out
            }

            val hits = results
            when {
                tab == TAB_SEARCH -> if (searching && hits != null) {
                    peopleRows.forEach { add(it.title, it.items) }
                    add("Movies", hits.filter { it.type == "movie" })
                    add("Series", hits.filter { it.type == "series" })
                }
                tab == TAB_MY_LIST -> {
                    addContinue(null)
                    // Laid out as a wall of posters, newest first unless you change the order.
                    val mine = favourites.filter {
                        ui.listType == 0 || (ui.listType == 1 && it.type == "movie") || (ui.listType == 2 && it.type == "series")
                    }
                    val ordered = when (ui.listSort) {
                        1 -> mine.sortedBy { it.name.lowercase() }
                        2 -> mine.sortedByDescending { Regex("\\d+(\\.\\d+)?").find(it.rating)?.value?.toFloatOrNull() ?: 0f }
                        else -> mine
                    }
                    ordered.chunked(7).forEachIndexed { i, chunk ->
                        add(if (i == 0) "My List (${ordered.size})" else "", chunk)
                    }
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
                    extraRows.forEach { (title, items) -> add(title, items) }
                    addTop10("series")
                    addTop10("movie")
                    addSuggestions()
                    val movieRows = state.home.rows.filter { it.type == "movie" && notTop10(it) }
                    val seriesRows = state.home.rows.filter { it.type == "series" && notTop10(it) }
                    // Series and movie rows take turns, so Home has a bit of everything.
                    val mixed = ArrayList<StremioRow>()
                    for (i in 0 until maxOf(movieRows.size, seriesRows.size)) {
                        seriesRows.getOrNull(i)?.let { mixed += it }
                        movieRows.getOrNull(i)?.let { mixed += it }
                    }
                    tidy(mixed, 20).forEach { add(it.title, it.items) }
                }
                else -> {
                    addContinue(type)
                    if (type == "series") addTop10("series")
                    if (type == "movie") addTop10("movie")
                    tidy(state.home.rows.filter { it.type == type && notTop10(it) }, 28).forEach { row: StremioRow -> add(row.title, row.items) }
                }
            }

            // Load the posters in the rows just below the screen ahead of time, so they're already there when you scroll.
            val prefetchContext = androidx.compose.ui.platform.LocalContext.current
            LaunchedEffect(rails.size, tab) {
                val loader = coil.Coil.imageLoader(prefetchContext)
                rails.take(8).forEach { spec ->
                    spec.rail.tiles.take(8).forEach { t ->
                        val u = t.imageUrl
                        if (!u.isNullOrBlank()) loader.enqueue(coil.request.ImageRequest.Builder(prefetchContext).data(u).build())
                    }
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

            // On a TV the banner and top bar stay put and only the rows underneath scroll, so the banner is always
            // visible (and follows the poster you're on). On a phone the banner scrolls away with the page.
            val headerBox: @Composable () -> Unit = {
                Column(Modifier.fillMaxWidth()) {
                    if (phone) PhoneTopBar(profile, onSettings, onSwitchProfile, onShuffle)
                    else TopBar(tab, selectTab, profile, onSettings, onSwitchProfile, onShuffle, onDownloads)
                    if (showBanner) {
                        val hero = focusedHero?.takeIf { browsing } ?: heroItems[heroIndex.coerceIn(0, heroItems.size - 1)]
                        val extra = heroDetails[hero.type + ":" + hero.id]
                        val shownHero = if (extra == null) hero else hero.copy(
                            description = extra.description.ifBlank { hero.description },
                            rating = extra.rating.ifBlank { hero.rating },
                            runtime = extra.runtime.ifBlank { hero.runtime },
                            year = extra.year.ifBlank { hero.year },
                            genres = extra.genres.ifEmpty { hero.genres },
                            cast = extra.cast.ifEmpty { hero.cast },
                            director = extra.director.ifBlank { hero.director },
                        )
                        InfoStrip(shownHero, compact = !isTv, onOpen = if (phone) ({ onOpen(hero) }) else null)
                    }
                }
            }
            val listBody: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
                if (genreList.isNotEmpty()) {
                    item {
                        // One button that opens a menu of categories, instead of a long row of them.
                        var menuOpen by remember { mutableStateOf(false) }
                        Row(Modifier.padding(horizontal = LocalSidePad.current, vertical = 6.dp)) {
                            ActionButton("Category: ${ui.genre ?: "All"}  \u25BE", Modifier.widthIn(min = 220.dp, max = 360.dp), accent = ui.genre != null) { menuOpen = true }
                        }
                        if (menuOpen) {
                            val firstItem = remember { FocusRequester() }
                            LaunchedEffect(Unit) {
                                delay(150)
                                runCatching { firstItem.requestFocus() }
                            }
                            androidx.compose.ui.window.Dialog(
                                onDismissRequest = { menuOpen = false },
                                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
                            ) {
                                Column(
                                    Modifier
                                        .width(340.dp)
                                        .fillMaxHeight(0.9f)
                                        .background(Color(0xF2181820), RoundedCornerShape(14.dp))
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text("Choose a category", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        item {
                                            ActionButton("All", Modifier.fillMaxWidth().focusRequester(firstItem), accent = ui.genre == null) {
                                                ui.genre = null
                                                menuOpen = false
                                            }
                                        }
                                        items(genreList.size) { i ->
                                            val g = genreList[i]
                                            ActionButton(g, Modifier.fillMaxWidth(), accent = ui.genre.equals(g, true)) {
                                                ui.genre = g
                                                menuOpen = false
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (tab == TAB_MY_LIST && favourites.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = LocalSidePad.current, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ActionButton(MyListTypeNames[ui.listType], Modifier.width(150.dp)) { ui.listType = (ui.listType + 1) % MyListTypeNames.size }
                            ActionButton("Sort: " + MyListSortNames[ui.listSort], Modifier.width(220.dp)) { ui.listSort = (ui.listSort + 1) % MyListSortNames.size }
                        }
                    }
                }
                if (tab == TAB_SEARCH) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(horizontal = LocalSidePad.current), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LabeledField("Search movies and series", ui.query) { ui.query = it }
                            // Say the title instead of typing it (shown when the device has speech recognition).
                            val ctx = androidx.compose.ui.platform.LocalContext.current
                            val speech = remember {
                                android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Say a movie or series")
                            }
                            val canSpeak = remember { runCatching { ctx.packageManager.resolveActivity(speech, 0) != null }.getOrDefault(false) }
                            val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                                androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
                            ) { r ->
                                val said = r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                                if (!said.isNullOrBlank()) ui.query = said
                            }
                            if (canSpeak) {
                                ActionButton("Search by voice", Modifier.width(200.dp)) { runCatching { launcher.launch(speech) } }
                            }
                        }
                    }
                }
                if (tab == TAB_SEARCH && !searching) {
                    // Ideas for when you don't know what to type: what's trending today, or else the first titles on Home.
                    val ideas: List<StremioItem> = run {
                        val live = interleave(trending?.movies.orEmpty(), trending?.series.orEmpty())
                        val pool = if (live.size >= 5) live else (state as? StremioState.Ready)?.home?.rows.orEmpty().flatMap { it.items.take(4) }
                        pool.distinctBy { it.name.lowercase() }.take(10)
                    }
                    if (ideas.isNotEmpty()) {
                        item { Note("Trending right now") }
                        item {
                            androidx.compose.foundation.lazy.LazyRow(
                                contentPadding = PaddingValues(horizontal = LocalSidePad.current),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(ideas.size) { i ->
                                    ActionButton(ideas[i].name, Modifier.widthIn(min = 110.dp, max = 260.dp)) { onOpen(ideas[i]) }
                                }
                            }
                        }
                    }
                    if (recent.isNotEmpty()) {
                        item { Note("Recent searches") }
                        item {
                            androidx.compose.foundation.lazy.LazyRow(
                                contentPadding = PaddingValues(horizontal = LocalSidePad.current),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(recent.size) { i ->
                                    ActionButton(recent[i], Modifier.widthIn(min = 110.dp, max = 260.dp)) { ui.query = recent[i] }
                                }
                                item { ActionButton("Clear", Modifier.width(100.dp)) { recentStore.clear(); recent = emptyList() } }
                            }
                        }
                    }
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
                    val back = if (phone) null else ui.lastTile
                    if (spec.top10) Top10Row(spec.rail.title, spec.rail.tiles, spec.width, spec.height, back) { ui.lastTile = it }
                    else RailRow(spec.rail, spec.width, spec.height, back) { ui.lastTile = it }
                }
            }
            // Pull the list down from the top (on a touch screen) to refresh Home.
            val pull = remember { mutableFloatStateOf(0f) }
            val pullConnection = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (pull.floatValue > 0f && available.y < 0f) {
                            val d = maxOf(available.y, -pull.floatValue)
                            pull.floatValue += d
                            return Offset(0f, d)
                        }
                        return Offset.Zero
                    }

                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        if (source == NestedScrollSource.Drag && available.y > 0f) {
                            pull.floatValue = (pull.floatValue + available.y).coerceAtMost(420f)
                        }
                        return Offset.Zero
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        if (pull.floatValue > 220f) onRefresh()
                        pull.floatValue = 0f
                        return Velocity.Zero
                    }
                }
            }
            val dens = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(
                LocalSidePad provides (if (phone) 16.dp else 48.dp),
                // A phone set to a very large text size would push the tabs and the strip off the screen, so cap it a little.
                androidx.compose.ui.platform.LocalDensity provides (
                    if (phone) androidx.compose.ui.unit.Density(dens.density, minOf(dens.fontScale, 1.1f)) else dens
                ),
            ) {
                Column(Modifier.fillMaxSize()) {
                    headerBox()
                    Box(Modifier.weight(1f).fillMaxWidth().then(if (phone) Modifier.nestedScroll(pullConnection) else Modifier)) {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            state = ui.list,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            content = listBody,
                        )
                        if (pull.floatValue > 30f) {
                            Text(
                                if (pull.floatValue > 220f) "Release to refresh" else "Pull down to refresh",
                                fontSize = 13.sp,
                                color = Palette.TextPrimary,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 6.dp)
                                    .background(Color(0xCC000000), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                            )
                        }
                    }
                    if (phone) PhoneBottomBar(tab, selectTab, onDownloads)
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
    onShuffle: () -> Unit,
    onDownloads: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // On a phone there isn't room for the full name and wide buttons, so everything shrinks to fit.
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val compact = maxWidth < 900.dp
        val itemWidth = if (compact) 66.dp else 100.dp
        val itemFont = if (compact) 14.sp else 16.sp
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                .padding(horizontal = if (compact) 20.dp else 40.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp),
        ) {
            Text(
                if (compact) "R" else "REELHOUSE",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Palette.Accent,
                letterSpacing = if (compact) 0.sp else 2.sp,
                modifier = Modifier.padding(end = if (compact) 8.dp else 20.dp),
            )
            // The tabs scroll sideways if the screen is still too narrow, so Settings and the profile never get pushed off.
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp),
            ) {
                TabNames.forEachIndexed { i, label -> NavItem(label, tab == i, itemWidth, itemFont) { onTab(i) } }
                NavItem("Shuffle", false, itemWidth, itemFont) { onShuffle() }
                NavItem("Downloads", false, if (compact) 96.dp else 120.dp, itemFont) { onDownloads() }
            }
            NavItem("Settings", false, if (compact) 76.dp else 100.dp, itemFont) { onSettings() }
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
}

/** The phone's top strip: the name, Shuffle, Settings and who's watching. The tabs live along the bottom instead. */
@Composable
private fun PhoneTopBar(profile: Profile, onSettings: () -> Unit, onSwitchProfile: () -> Unit, onShuffle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("REELHOUSE", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Accent, letterSpacing = 1.sp, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
        NavItem("Shuffle", false, 74.dp, 13.sp) { onShuffle() }
        NavItem("Settings", false, 78.dp, 13.sp) { onSettings() }
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

/** The phone's tabs along the bottom, where a thumb reaches them. */
@Composable
private fun PhoneBottomBar(tab: Int, onTab: (Int) -> Unit, onDownloads: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xF2101014)).navigationBarsPadding()) {
        TabNames.forEachIndexed { i, label ->
            val on = tab == i
            FocusTile(
                onClick = { onTab(i) },
                modifier = Modifier.weight(1f).height(54.dp),
                corner = 0.dp,
                focusedScale = 1f,
                backgroundColor = Color.Transparent,
            ) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.width(26.dp).height(3.dp).background(if (on) Palette.Accent else Color.Transparent))
                    Box(Modifier.height(7.dp))
                    Text(label, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) Color.White else Color(0xFF9A9AA6), maxLines = 1)
                }
            }
        }
        FocusTile(
            onClick = onDownloads,
            modifier = Modifier.weight(1.25f).height(54.dp),
            corner = 0.dp,
            focusedScale = 1f,
            backgroundColor = Color.Transparent,
        ) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(26.dp).height(3.dp))
                Box(Modifier.height(7.dp))
                Text("Downloads", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF9A9AA6), maxLines = 1)
            }
        }
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, width: Dp, fontSize: TextUnit, onClick: () -> Unit) {
    FocusTile(
        onClick = onClick,
        modifier = Modifier.width(width).height(38.dp),
        corner = 19.dp,
        focusedScale = 1.05f,
        backgroundColor = if (selected) Color(0x40FFFFFF) else Color.Transparent,
    ) {
        Text(
            label,
            fontSize = fontSize,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else Color(0xFFD0D0D0),
            maxLines = 1,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

// ---------------------------------------------------------------------------- billboard

/** A short strip of facts about the selected title: name, year and rating, genres, and a couple of lines of story. */
@Composable
private fun InfoStrip(item: StremioItem, compact: Boolean, onOpen: (() -> Unit)? = null) {
    // On a phone the strip is big: it takes over a third of the screen so the whole story can be read,
    // leaving room for just one row of posters underneath.
    val cfg = LocalConfiguration.current
    val h = if (compact) (cfg.screenHeightDp * (if (cfg.screenHeightDp > cfg.screenWidthDp) 0.36f else 0.42f)).dp else 150.dp
    Column(
        Modifier
            .fillMaxWidth()
            .height(h)
            .then(if (onOpen != null) Modifier.clickable { onOpen() } else Modifier)
            .padding(horizontal = LocalSidePad.current, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 2.dp),
    ) {
        Text(
            item.name,
            fontSize = if (compact) 24.sp else 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            maxLines = if (compact) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (item.type == "series") "SERIES" else "FILM", fontSize = 12.sp, color = Palette.Accent, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            MetaLine(item.year, if (compact) item.runtime else "", item.rating, fontSize = if (compact) 14.sp else 13.sp)
            if (onOpen != null) Text("Open ›", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
            if (!compact && item.genres.isNotEmpty()) {
                Text(item.genres.take(3).joinToString("  •  "), fontSize = 13.sp, color = Color(0xFFD0D0D8), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (compact && item.genres.isNotEmpty()) {
            Text(item.genres.take(4).joinToString("  •  "), fontSize = 14.sp, color = Color(0xFFD0D0D8), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.cast.isNotEmpty()) {
            Text(
                "Starring  " + item.cast.take(if (compact) 5 else 4).joinToString(", "),
                fontSize = if (compact) 14.sp else 13.sp,
                color = Color(0xFFE6E6EC),
                maxLines = if (compact) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.director.isNotBlank()) {
            Text(
                (if (item.type == "series") "Created by  " else "Directed by  ") + item.director,
                fontSize = if (compact) 14.sp else 13.sp,
                color = Color(0xFFB8B8C2),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.description.isNotBlank()) {
            if (compact) {
                // The whole story; if it's very long, it scrolls inside the strip.
                Text(
                    item.description,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = Color(0xFFD4D4DC),
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                )
            } else {
                Text(item.description, fontSize = 13.sp, color = Color(0xFFC0C0C8), maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    Box(Modifier.fillMaxWidth().padding(horizontal = LocalSidePad.current, vertical = 8.dp)) {
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

private fun tileFor(
    item: StremioItem,
    onOpen: (StremioItem) -> Unit,
    onFavourite: (StremioItem) -> Unit,
    topPostersKey: String,
    onFocus: (StremioItem) -> Unit,
    trailer: (suspend () -> String?)? = null,
) =
    HomeTile(
        id = item.type + ":" + item.id,
        title = item.name,
        subtitle = item.year,
        imageUrl = TopPosters.poster(topPostersKey, item.id, item.poster),
        onLongClick = { onFavourite(item) },
        onFocus = { onFocus(item) },
        trailer = trailer,
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
