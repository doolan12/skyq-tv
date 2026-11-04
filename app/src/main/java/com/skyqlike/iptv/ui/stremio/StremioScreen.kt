package com.skyqlike.iptv.ui.stremio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    actions: StremioActions,
) {
    var showManage by remember { mutableStateOf(false) }
    var openItem by remember { mutableStateOf<StremioItem?>(null) }

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
        item != null -> Details(item, actions) { openItem = null }
        else -> Browse(
            state = state,
            continueEntries = continueEntries,
            actions = actions,
            onOpen = { openItem = it },
            onManage = { showManage = true },
        )
    }
}

// ---------------------------------------------------------------------------- browse

@Composable
private fun Browse(
    state: StremioState,
    continueEntries: List<HistoryEntry>,
    actions: StremioActions,
    onOpen: (StremioItem) -> Unit,
    onManage: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<StremioItem>?>(null) }
    var genre by remember { mutableStateOf<String?>(null) }
    var genreRows by remember { mutableStateOf<List<StremioRow>?>(null) }
    val type = if (tab == 0) "movie" else "series"
    val searching = query.trim().length >= 2

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

    // Fetches the chosen genre from the addons whenever the genre or the Movies/Series tab changes.
    LaunchedEffect(genre, tab) {
        val g = genre
        genreRows = null
        if (g != null) genreRows = runCatching { actions.byGenre(type, g) }.getOrDefault(emptyList())
    }
    // A different tab has different genres, so start again from "All".
    LaunchedEffect(tab) { genre = null }

    Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
        Row(
            Modifier.padding(horizontal = 36.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionButton("Movies", Modifier.width(110.dp), accent = tab == 0) { tab = 0 }
            ActionButton("Series", Modifier.width(110.dp), accent = tab == 1) { tab = 1 }
            ActionButton("Manage", Modifier.width(110.dp)) { onManage() }
        }
        Spacer(Modifier.height(8.dp))

        when (state) {
            is StremioState.Idle, is StremioState.Loading -> Note("Loading your Stremio addons...")
            is StremioState.Error -> Column(
                Modifier.padding(horizontal = 36.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(state.message, fontSize = 17.sp, color = Palette.TextSecondary)
                ActionButton("Try again", Modifier.width(150.dp), accent = true) { actions.onNeedData() }
            }
            is StremioState.Ready -> {
                val rails = ArrayList<Rail>()
                val hits = results?.filter { it.type == type }
                if (searching) {
                    if (hits != null) {
                        rails += Rail("Results for \"${query.trim()}\"", hits.map { tileFor(it, onOpen) })
                    }
                } else if (genre != null) {
                    val g = genre!!
                    val fromAddons = genreRows
                    if (fromAddons != null) {
                        rails += fromAddons.map { row -> Rail(row.title, row.items.map { tileFor(it, onOpen) }) }
                        if (rails.isEmpty()) {
                            // The addons couldn't filter by genre, so filter what's already on screen.
                            val seen = HashSet<String>()
                            val local = state.home.rows.filter { it.type == type }.flatMap { it.items }
                                .filter { it.genres.any { x -> x.equals(g, true) } && seen.add(it.id) }
                            if (local.isNotEmpty()) rails += Rail(g, local.map { tileFor(it, onOpen) })
                        }
                    }
                } else {
                    // Things you were part-way through, for this tab.
                    val cont = continueEntries.mapNotNull { e -> entryToItem(e)?.let { e to it } }
                        .filter { it.second.type == type }
                    if (cont.isNotEmpty()) {
                        rails += Rail(
                            "Continue Watching",
                            cont.map { (e, found) -> HomeTile("cw:" + e.key, found.name, e.subtitle, found.poster) { onOpen(found) } },
                        )
                    }
                    rails += state.home.rows.filter { it.type == type }.map { row: StremioRow ->
                        Rail(row.title, row.items.map { tileFor(it, onOpen) })
                    }
                }
                val genreList: List<String> = run {
                    val fromLists = state.home.addons.flatMap { a ->
                        a.catalogs.filter { it.type == type && it.genreOnly }.flatMap { it.genreOptions }
                    }
                    val all = fromLists.ifEmpty {
                        state.home.rows.filter { it.type == type }.flatMap { it.items }.flatMap { it.genres }
                    }
                    all.distinctBy { it.lowercase() }.take(40)
                }
                val names = state.home.addons.joinToString(", ") { it.name }
                val addonNote = "${state.home.addons.size} addon(s) loaded: $names" +
                    if (state.home.failedAddons > 0) "  |  ${state.home.failedAddons} couldn't be reached" else ""

                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 40.dp),
                ) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 36.dp)) {
                            LabeledField("Search Stremio", query) { query = it }
                        }
                    }
                    if (genreList.isNotEmpty() && !searching) {
                        item {
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 36.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ActionButton("All", Modifier.width(80.dp), accent = genre == null) { genre = null }
                                genreList.forEach { g ->
                                    ActionButton(g, Modifier.width(150.dp), accent = genre.equals(g, true)) { genre = g }
                                }
                            }
                        }
                    }
                    item { Text(addonNote, fontSize = 13.sp, color = Palette.TextSecondary, modifier = Modifier.padding(horizontal = 36.dp)) }
                    if (searching && hits == null) {
                        item { Note("Searching...") }
                    } else if (!searching && genre != null && genreRows == null) {
                        item { Note("Loading ${genre}...") }
                    } else if (rails.isEmpty()) {
                        item {
                            Note(
                                if (searching) {
                                    "Nothing found."
                                } else if (genre != null) {
                                    "Nothing found for $genre. Try another genre."
                                } else {
                                    "None of your addons offer a plain list of ${if (tab == 0) "movies" else "series"}. " +
                                        "Try the search box, or add Cinemeta in Stremio."
                                }
                            )
                        }
                    }
                    itemsIndexed(rails, key = { i, r -> "$i|${r.title}" }) { _, rail -> RailRow(rail, 150.dp, 225.dp) }
                }
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

private fun tileFor(item: StremioItem, onOpen: (StremioItem) -> Unit) =
    HomeTile(item.type + ":" + item.id, item.name, item.year, item.poster) { onOpen(item) }

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
private fun Details(start: StremioItem, actions: StremioActions, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var info by remember(start.id) { mutableStateOf(start) }
    var picking by remember(start.id) { mutableStateOf<Pick?>(null) }
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
        .filter { it.isNotBlank() }.joinToString("  |  ")

    if (info.type == "series") {
        val seasons = info.videos.map { it.season }.distinct().sorted()
        val current = if (season in seasons) season else seasons.firstOrNull { it > 0 } ?: seasons.firstOrNull() ?: 1
        val episodes = info.videos.filter { it.season == current }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Artwork(info.poster, info.name, Modifier.width(110.dp).height(165.dp).clip(RoundedCornerShape(12.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(info.name, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
                        if (meta.isNotBlank()) Text(meta, fontSize = 15.sp, color = Palette.Accent)
                        Text(
                            info.description.ifBlank { "No description." },
                            fontSize = 15.sp,
                            color = Palette.TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ActionButton(
                                if (episodes.isEmpty()) "Loading..." else "Play S$current E${episodes.first().episode}",
                                Modifier.width(190.dp).focusRequester(playFocus),
                                accent = true,
                            ) { episodes.firstOrNull()?.let { picking = Pick(it) } }
                            ActionButton("Back", Modifier.width(120.dp)) { onBack() }
                        }
                    }
                }
            }
            if (seasons.size > 1) {
                item {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        seasons.forEach { s ->
                            ActionButton(if (s == 0) "Specials" else "Season $s", Modifier.width(130.dp), accent = s == current) { season = s }
                        }
                    }
                }
            }
            items(episodes, key = { it.id }) { ep ->
                FocusTile(
                    onClick = { picking = Pick(ep) },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    corner = 10.dp,
                    focusedScale = 1.02f,
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text("E${ep.episode}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Accent)
                        Text(
                            ep.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Palette.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (info.videos.isEmpty()) {
                item { Text("Loading episodes... If this stays empty, none of your addons list episodes for this series.", fontSize = 15.sp, color = Palette.TextSecondary) }
            }
        }
    } else {
        Row(Modifier.fillMaxSize().padding(36.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Artwork(info.poster, info.name, Modifier.width(200.dp).height(300.dp).clip(RoundedCornerShape(12.dp)))
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(info.name, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
                if (meta.isNotBlank()) Text(meta, fontSize = 16.sp, color = Palette.Accent)
                Text(info.description.ifBlank { "No description." }, fontSize = 16.sp, color = Palette.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    ActionButton("Play", Modifier.width(140.dp).focusRequester(playFocus), accent = true) { picking = Pick(null) }
                    ActionButton("Back", Modifier.width(120.dp)) { onBack() }
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
