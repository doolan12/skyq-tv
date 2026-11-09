package com.skyqlike.iptv.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.epg.nextOn
import com.skyqlike.iptv.data.epg.nowOn
import com.skyqlike.iptv.data.epg.progressAt
import com.skyqlike.iptv.data.history.HistoryEntry
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.data.model.Programme
import com.skyqlike.iptv.data.model.favKey
import com.skyqlike.iptv.ui.channels.ChannelBrowser
import com.skyqlike.iptv.ui.channels.LocalNowOn
import com.skyqlike.iptv.ui.channels.channelTile
import kotlinx.coroutines.delay
import com.skyqlike.iptv.ui.common.Artwork
import com.skyqlike.iptv.ui.common.HomeTile
import com.skyqlike.iptv.ui.common.MenuIcon
import com.skyqlike.iptv.ui.common.Rail
import com.skyqlike.iptv.ui.common.RailRow
import com.skyqlike.iptv.ui.theme.Palette

/**
 * Lets a screen ask for the whole display. While a screen is "immersive" (a movie page, say)
 * the side menu disappears completely so the picture can go edge to edge.
 */
val LocalImmersive = compositionLocalOf<(Boolean) -> Unit> { {} }

@Composable
fun ImmersiveEffect(active: Boolean = true) {
    val set = LocalImmersive.current
    DisposableEffect(active) {
        set(active)
        onDispose { set(false) }
    }
}

/** Builds the home screen rows from whatever is loaded. Empty rows are left out. */
fun buildRails(
    channels: List<Channel>,
    favouriteKeys: Set<String>,
    recentKeys: List<String>,
    continueWatching: List<HistoryEntry>,
    onPlay: (Channel) -> Unit,
    onPlayHistory: (HistoryEntry) -> Unit,
    onToggleFavourite: (Channel) -> Unit = {},
    nowFor: (Channel) -> Programme? = { null },
): List<Rail> {
    fun tile(ch: Channel) = channelTile(ch, nowFor, onPlay, onToggleFavourite)
    val byKey = channels.associateBy { it.favKey }
    // Channels with something on right now come first, so the home page feels live.
    val onAir = channels.filter { nowFor(it) != null }

    return listOf(
        Rail(
            "Continue Watching",
            continueWatching.map { e ->
                HomeTile(e.key, e.title, e.subtitle, e.imageUrl, progress = if (e.durationMs > 0) e.positionMs.toFloat() / e.durationMs else null) { onPlayHistory(e) }
            },
        ),
        Rail("On Now", onAir.take(20).map(::tile)),
        Rail("Favourite Channels", channels.filter { it.favKey in favouriteKeys }.map(::tile)),
        Rail("Recently Watched", recentKeys.mapNotNull { byKey[it] }.map(::tile)),
        Rail("Live Sports", channels.filter { it.group.contains("Sport", ignoreCase = true) }.take(30).map(::tile)),
        Rail("Movie Channels", channels.filter { it.group.contains("Movie", ignoreCase = true) || it.group.contains("Cinema", ignoreCase = true) }.take(30).map(::tile)),
        Rail("Popular Channels", channels.take(20).map(::tile)),
    ).filter { it.tiles.isNotEmpty() }
}

@Composable
fun HomeScreen(
    channels: List<Channel>,
    onPlay: (Channel) -> Unit,
    favouriteKeys: Set<String> = emptySet(),
    recentKeys: List<String> = emptyList(),
    continueWatching: List<HistoryEntry> = emptyList(),
    onPlayHistory: (HistoryEntry) -> Unit = {},
    onToggleFavourite: (Channel) -> Unit = {},
    settingsContent: @Composable () -> Unit = {},
    guideContent: @Composable () -> Unit = {},
    moviesContent: @Composable () -> Unit = {},
    libraryContent: @Composable () -> Unit = {},
    catchUpContent: @Composable () -> Unit = {},
    epg: Map<String, List<Programme>> = emptyMap(),
    searchContent: @Composable () -> Unit = {},
    multiViewContent: @Composable () -> Unit = {},
    matchCentreContent: @Composable () -> Unit = {},
    /** Extra rows added under the channel rows (new movies and series). */
    extraRails: List<Rail> = emptyList(),
    phone: Boolean = false,
) {
    if (phone) {
        com.skyqlike.iptv.ui.phone.PhoneShell(
            channels = channels,
            epg = epg,
            onPlay = onPlay,
            favouriteKeys = favouriteKeys,
            recentKeys = recentKeys,
            continueWatching = continueWatching,
            onPlayHistory = onPlayHistory,
            onToggleFavourite = onToggleFavourite,
            moviesContent = moviesContent,
            libraryContent = libraryContent,
            settingsContent = settingsContent,
            searchContent = searchContent,
            matchCentreContent = matchCentreContent,
            extraRails = extraRails,
        )
        return
    }
    var section by remember { mutableStateOf("Home") }
    val firstFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // The side menu: wide when the remote is on it, a slim strip of badges otherwise, gone on full-screen pages.
    var immersive by remember { mutableStateOf(false) }
    var menuFocused by remember { mutableStateOf(true) }
    var pinned by remember { mutableStateOf(false) }
    var goRight by remember { mutableIntStateOf(0) }
    var minute by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    LaunchedEffect(immersive) { if (immersive) menuFocused = false }
    // After picking something in the menu, the remote jumps across into the page that opened.
    LaunchedEffect(goRight) {
        if (goRight > 0) {
            delay(250)
            runCatching { focusManager.moveFocus(FocusDirection.Right) }
        }
    }
    // Keeps "on now" bars and times fresh.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            minute++
        }
    }

    // Back from any other screen goes home. Back on Home leaves the app, like a normal TV app.
    BackHandler(enabled = section != "Home") {
        section = "Home"
        runCatching { firstFocus.requestFocus() }
    }
    // Back from inside a page first opens the menu.
    BackHandler(enabled = !menuFocused && !immersive && !pinned) {
        runCatching { firstFocus.requestFocus() }
    }

    val nowFor: (Channel) -> Programme? = remember(epg, minute) {
        { ch -> epg.nowOn(ch, System.currentTimeMillis()) }
    }
    val expanded = menuFocused || pinned
    val railWidth by animateDpAsState(if (immersive) 0.dp else 68.dp, label = "rail")
    val menuWidth by animateDpAsState(if (immersive) 0.dp else if (expanded) 236.dp else 68.dp, label = "menu")
    // Deep blue to near-black, in the style of a satellite TV box.
    val background = Brush.verticalGradient(listOf(Color(0xFF14246B), Color(0xFF0B1238), Color(0xFF05060F)))

    val setImmersive: (Boolean) -> Unit = remember { { v: Boolean -> immersive = v } }
    CompositionLocalProvider(
        LocalImmersive provides setImmersive,
        LocalNowOn provides nowFor,
    ) {
    Box(Modifier.fillMaxSize().background(background)) {
        Box(Modifier.fillMaxSize().padding(start = railWidth)) {
            when (section) {
                "Home" -> {
                    val rails = remember(channels, favouriteKeys, recentKeys, continueWatching, onToggleFavourite, nowFor, extraRails) {
                        buildRails(channels, favouriteKeys, recentKeys, continueWatching, onPlay, onPlayHistory, onToggleFavourite, nowFor) + extraRails
                    }
                    val featured = remember(channels, favouriteKeys, nowFor) {
                        channels.firstOrNull { it.favKey in favouriteKeys && nowFor(it) != null }
                            ?: channels.firstOrNull { nowFor(it) != null }
                            ?: channels.firstOrNull()
                    }
                    HomeContent(
                        rails, featured, nowFor,
                        { ch -> epg.nextOn(ch, System.currentTimeMillis()) },
                        minute, onPlay, onToggleFavourite,
                    )
                }
                "Settings" -> settingsContent()
                "Search" -> searchContent()
                "Multi-view" -> multiViewContent()
                "Match Centre" -> matchCentreContent()
                "TV Guide" -> guideContent()
                "Movies" -> moviesContent()
                "My Library" -> libraryContent()
                "Catch Up" -> catchUpContent()
                "All Channels" -> ChannelBrowser("All Channels", channels, "No channels loaded yet. Add a provider in Settings.", onPlay, onToggleFavourite)
                "Favourites" -> ChannelBrowser(
                    "Favourites",
                    channels.filter { it.favKey in favouriteKeys },
                    "No favourites yet. While watching a channel, choose Add fav, or hold OK on a channel tile.",
                    onPlay,
                    onToggleFavourite,
                )
                "Sports" -> categoryPage("Sports", channels, listOf("sport"), onPlay, onToggleFavourite)
                "Entertainment" -> categoryPage("Entertainment", channels, listOf("entertainment", "general"), onPlay, onToggleFavourite)
                "Documentaries" -> categoryPage("Documentaries", channels, listOf("documentar", "factual", "nature", "science", "history"), onPlay, onToggleFavourite)
                "Kids" -> categoryPage("Kids", channels, listOf("kid", "child", "cartoon", "family"), onPlay, onToggleFavourite)
                "News" -> categoryPage("News", channels, listOf("news", "business", "weather"), onPlay, onToggleFavourite)
                "Music" -> categoryPage("Music", channels, listOf("music", "radio", "mtv", "concert"), onPlay, onToggleFavourite)
                "Movie Channels" -> categoryPage("Movie Channels", channels, listOf("cinema", "film", "movie", "premiere"), onPlay, onToggleFavourite)
                "UK" -> categoryPage("UK", channels, listOf("uk=", "gb=", "united kingdom", "british", "england", "scotland", "wales"), onPlay, onToggleFavourite)
                "Australia" -> categoryPage("Australia", channels, listOf("au=", "aus=", "australia"), onPlay, onToggleFavourite)
                "Ireland" -> categoryPage("Ireland", channels, listOf("ie=", "irl=", "ireland", "irish"), onPlay, onToggleFavourite)
                "USA" -> categoryPage("USA", channels, listOf("us=", "usa=", "united states", "american"), onPlay, onToggleFavourite)
                else -> ComingLater(section)
            }
        }
        if (!immersive || menuWidth > 1.dp) {
            SideMenu(
                selected = section,
                expanded = expanded,
                onSelect = {
                    section = it
                    pinned = false
                    goRight++
                },
                onTogglePin = { pinned = !pinned },
                firstFocus = firstFocus,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(menuWidth)
                    .onFocusChanged { menuFocused = it.hasFocus },
            )
        }
    }
    }
}

/**
 * Does this category name match the word? A word ending in "=" must be a whole word on its own
 * (so "uk=" matches "UK | Sports" but not "Ukraine"). Any other word just has to appear somewhere in the name.
 */
internal fun groupMatches(group: String, word: String): Boolean {
    if (word.endsWith("=")) {
        val w = word.dropLast(1)
        return group.split(Regex("[^A-Za-z0-9]+")).any { it.equals(w, ignoreCase = true) }
    }
    return group.contains(word, ignoreCase = true)
}

/** Picks out the channels whose category name contains any of the given words. */
@Composable
private fun categoryPage(
    title: String,
    channels: List<Channel>,
    words: List<String>,
    onPlay: (Channel) -> Unit,
    onToggleFavourite: (Channel) -> Unit,
) {
    val matches = channels.filter { ch -> words.any { groupMatches(ch.group, it) } }
    ChannelBrowser(
        title,
        matches,
        "No $title channels found. Channels are matched by their category name, so this fills in when your playlist has one.",
        onPlay,
        onToggleFavourite,
    )
}

private sealed interface MenuEntry
private data class MenuHeader(val text: String) : MenuEntry
private data class MenuLink(val name: String, val icon: String, val indent: Boolean = false) : MenuEntry
private data object MenuCategories : MenuEntry

private val CategoryLinks = listOf(
    MenuLink("Sports", "sports", true),
    MenuLink("Entertainment", "entertainment", true),
    MenuLink("News", "news", true),
    MenuLink("Music", "music", true),
    MenuLink("Movie Channels", "moviechannels", true),
    MenuLink("Documentaries", "docs", true),
    MenuLink("Kids", "kids", true),
    MenuLink("UK", "globe", true),
    MenuLink("Australia", "globe", true),
    MenuLink("Ireland", "globe", true),
    MenuLink("USA", "globe", true),
)
private val CategoryNames = CategoryLinks.map { it.name }.toSet()

/** The menu, grouped under small headings. The category pages sit inside one foldable list. */
private fun menuEntries(categoriesOpen: Boolean): List<MenuEntry> = buildList {
    add(MenuHeader("LIVE TV"))
    add(MenuLink("Home", "home"))
    add(MenuLink("TV Guide", "guide"))
    add(MenuLink("Search", "search"))
    add(MenuLink("Match Centre", "sports"))
    add(MenuLink("Multi-view", "all"))
    add(MenuLink("All Channels", "all"))
    add(MenuLink("Catch Up", "catchup"))
    add(MenuHeader("ON DEMAND"))
    add(MenuLink("Movies", "movies"))
    add(MenuLink("My Library", "library"))
    add(MenuHeader("CATEGORIES"))
    add(MenuCategories)
    if (categoriesOpen) addAll(CategoryLinks)
    add(MenuHeader("MY STUFF"))
    add(MenuLink("Favourites", "star"))
    add(MenuLink("Settings", "settings"))
}

@Composable
private fun SideMenu(
    selected: String,
    expanded: Boolean,
    onSelect: (String) -> Unit,
    onTogglePin: () -> Unit,
    firstFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var categoriesOpen by remember { mutableStateOf(false) }
    // Landing on a category page keeps its list open.
    LaunchedEffect(selected) { if (selected in CategoryNames) categoriesOpen = true }
    val entries = remember(categoriesOpen) { menuEntries(categoriesOpen) }

    Column(
        modifier
            .fillMaxSize()
            .background(Color(0xF2050819))
            .padding(horizontal = 14.dp, vertical = 18.dp),
    ) {
        Text(
            "TV",
            color = Palette.Accent,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(start = 12.dp, bottom = 4.dp)
                // A tap on the logo opens or closes the menu (for touch screens).
                .pointerInput(Unit) { detectTapGestures(onTap = { onTogglePin() }) },
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(entries) { entry ->
                when (entry) {
                    is MenuHeader -> if (expanded) {
                        Text(
                            entry.text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6F7AA8),
                            modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 2.dp),
                        )
                    } else {
                        Box(Modifier.padding(vertical = 8.dp, horizontal = 10.dp).fillMaxWidth().height(1.dp).background(Color(0x22FFFFFF)))
                    }
                    is MenuCategories -> MenuRow(
                        icon = "categories",
                        label = "Categories",
                        trailing = if (categoriesOpen) "v" else ">",
                        selected = false,
                        expanded = expanded,
                        indent = false,
                        modifier = Modifier,
                    ) { categoriesOpen = !categoriesOpen }
                    is MenuLink -> MenuRow(
                        icon = entry.icon,
                        label = entry.name,
                        trailing = "",
                        selected = entry.name == selected,
                        expanded = expanded,
                        indent = entry.indent,
                        modifier = if (entry.name == "Home") Modifier.focusRequester(firstFocus) else Modifier,
                    ) { onSelect(entry.name) }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    icon: String,
    label: String,
    trailing: String,
    selected: Boolean,
    expanded: Boolean,
    indent: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    FocusTile(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        corner = 16.dp,
        focusedScale = 1.02f,
        backgroundColor = if (selected) Color(0x2EFFFFFF) else Color.Transparent,
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = if (indent && expanded) 18.dp else 4.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (selected) Palette.Accent else Color(0x14FFFFFF)),
                contentAlignment = Alignment.Center,
            ) {
                MenuIcon(icon, if (selected) Color.White else Color(0xFFB9C2EA), 22.dp)
            }
            if (expanded) {
                Text(
                    label,
                    fontSize = 17.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Palette.TextPrimary else Color(0xFFC2C9EE),
                    maxLines = 1,
                    modifier = Modifier.padding(start = 14.dp).weight(1f),
                )
                if (trailing.isNotEmpty()) Text(trailing, fontSize = 16.sp, color = Color(0xFF8A93BD))
            }
        }
    }
}

@Composable
private fun HomeContent(
    rails: List<Rail>,
    featured: Channel?,
    nowFor: (Channel) -> Programme?,
    nextFor: (Channel) -> Programme?,
    minute: Int,
    onPlay: (Channel) -> Unit,
    onToggleFavourite: (Channel) -> Unit,
) {
    // "More Info" opens a details page in place of the home page, so the remote can't wander behind it.
    var infoFor by remember { mutableStateOf<Channel?>(null) }
    val info = infoFor
    if (info != null) {
        InfoPage(info, nowFor(info), nextFor(info), onWatch = { onPlay(info) }, onClose = { infoFor = null })
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item { SkyHeader(minute) }
        featured?.let { ch -> item { SkyHero(ch, nowFor(ch), onPlay, { infoFor = ch }, onToggleFavourite) } }
        items(rails, key = { it.title }) { rail ->
            RailRow(rail, 250.dp, 140.dp)
        }
    }
}

/** Full details of what is on now, and what is next, for one channel. */
@Composable
private fun InfoPage(
    channel: Channel,
    now: Programme?,
    next: Programme?,
    onWatch: () -> Unit,
    onClose: () -> Unit,
) {
    val clock = remember { java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()) }
    val watchFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { watchFocus.requestFocus() } }
    BackHandler { onClose() }

    Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "${channel.name}  |  ${channel.group}",
                color = Color(0xCCFFFFFF),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                now?.title ?: channel.name,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (now != null) {
                Text(
                    "ON NOW   " + clock.format(java.util.Date(now.start)) + " - " + clock.format(java.util.Date(now.stop)) +
                        if (now.genre.isNotBlank()) "   |   ${now.genre}" else "",
                    fontSize = 15.sp,
                    color = Color(0xCCFFFFFF),
                )
                if (now.description.isNotBlank()) {
                    Text(now.description, fontSize = 16.sp, color = Color(0xCCFFFFFF), maxLines = 8, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text("No programme information for this channel.", fontSize = 16.sp, color = Color(0xCCFFFFFF))
            }
            if (next != null) {
                Text(
                    "NEXT   " + clock.format(java.util.Date(next.start)) + "   " + next.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 14.dp)) {
                FocusTile(
                    onClick = onWatch,
                    modifier = Modifier.width(170.dp).height(46.dp).focusRequester(watchFocus),
                    corner = 23.dp,
                    backgroundColor = Color.White,
                ) {
                    Text("Watch Now", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF101030), modifier = Modifier.align(Alignment.Center))
                }
                FocusTile(
                    onClick = onClose,
                    modifier = Modifier.width(150.dp).height(46.dp),
                    corner = 23.dp,
                    backgroundColor = Color(0x40FFFFFF),
                ) {
                    Text("Back", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
        Spacer(Modifier.width(36.dp))
        Box(Modifier.width(380.dp).height(214.dp).clip(RoundedCornerShape(18.dp))) {
            Artwork(
                url = now?.iconUrl ?: channel.logoUrl,
                seed = channel.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = if (now?.iconUrl != null) ContentScale.Crop else ContentScale.Fit,
            )
        }
    }
}

/** Page title on the left, the time and date on the right. */
@Composable
private fun SkyHeader(minute: Int) {
    val now = remember(minute) { java.util.Date() }
    val time = remember(minute) { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(now) }
    val day = remember(minute) { java.text.SimpleDateFormat("EEEE d MMMM", java.util.Locale.getDefault()).format(now) }
    Row(
        Modifier.fillMaxWidth().padding(start = 36.dp, end = 36.dp, top = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Home", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(time, fontSize = 28.sp, fontWeight = FontWeight.Light, color = Palette.TextPrimary)
            Text(day, fontSize = 13.sp, color = Palette.TextSecondary)
        }
    }
}

/** The big "on now" card at the top of the home page. */
@Composable
private fun SkyHero(
    channel: Channel,
    now: Programme?,
    onPlay: (Channel) -> Unit,
    onMoreInfo: () -> Unit,
    onToggleFavourite: (Channel) -> Unit,
) {
    val clock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    Box(
        Modifier
            .padding(horizontal = 36.dp)
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2236C8), Color(0xFF6A2BB8), Color(0xFFC2306A))))
    ) {
        // The programme picture, or the channel logo, on the right.
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 32.dp)
                .width(300.dp)
                .height(170.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            Artwork(
                url = now?.iconUrl ?: channel.logoUrl,
                seed = channel.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = if (now?.iconUrl != null) ContentScale.Crop else ContentScale.Fit,
            )
        }
        Column(
            Modifier.align(Alignment.CenterStart).padding(start = 32.dp).width(500.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "ON NOW  |  ${channel.name}",
                color = Color(0xCCFFFFFF),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                now?.title ?: channel.name,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (now != null) {
                Text(
                    clock.format(java.util.Date(now.start)) + " - " + clock.format(java.util.Date(now.stop)) +
                        if (now.genre.isNotBlank()) "   |   ${now.genre}" else "",
                    fontSize = 14.sp,
                    color = Color(0xCCFFFFFF),
                )
                if (now.description.isNotBlank()) {
                    Text(now.description, fontSize = 14.sp, color = Color(0xB3FFFFFF), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    Modifier.fillMaxWidth(0.8f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x40FFFFFF))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(now.progressAt(System.currentTimeMillis()).coerceAtLeast(0.02f))
                            .height(5.dp)
                            .background(Color.White)
                    )
                }
            } else {
                Text(channel.group, fontSize = 15.sp, color = Color(0xCCFFFFFF))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
                FocusTile(
                    onClick = { onPlay(channel) },
                    modifier = Modifier.width(150.dp).height(46.dp),
                    corner = 23.dp,
                    backgroundColor = Color.White,
                ) {
                    Text("Watch Now", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF101030), modifier = Modifier.align(Alignment.Center))
                }
                FocusTile(
                    onClick = onMoreInfo,
                    modifier = Modifier.width(150.dp).height(46.dp),
                    corner = 23.dp,
                    backgroundColor = Color(0x40FFFFFF),
                ) {
                    Text("More Info", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
                }
                FocusTile(
                    onClick = { onToggleFavourite(channel) },
                    modifier = Modifier.width(130.dp).height(46.dp),
                    corner = 23.dp,
                    backgroundColor = Color(0x40FFFFFF),
                ) {
                    Text("Favourite", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
private fun ComingLater(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
            Text(
                "This screen is built in a later stage.",
                fontSize = 16.sp,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * The one building block every selectable thing uses.
 * It gets a white outline and grows slightly when the remote lands on it,
 * so you always know where you are.
 */
@Composable
internal fun FocusTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    corner: Dp = 12.dp,
    focusedScale: Float = 1.08f,
    backgroundColor: Color = Palette.Panel,
    backgroundBrush: Brush? = null,
    onFocus: () -> Unit = {},
    glowColor: Color = Color(0xFF3D7BFF),
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        // The TV Surface only reacts to remote buttons. This lets a finger tap (or long press) work too.
        // A soft coloured glow around whatever the remote is on (on newer Android versions).
        modifier = modifier
            .shadow(if (focused) 22.dp else 0.dp, shape, clip = false, ambientColor = glowColor, spotColor = glowColor)
            .onFocusChanged {
                focused = it.hasFocus
                if (it.hasFocus) onFocus()
            }.pointerInput(onClick, onLongClick) {
            detectTapGestures(
                onTap = { onClick() },
                onLongPress = onLongClick?.let { lc -> { _ -> lc() } },
            )
        },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = backgroundColor,
            contentColor = Palette.TextPrimary,
            focusedContainerColor = backgroundColor,
            focusedContentColor = Palette.TextPrimary,
            pressedContainerColor = backgroundColor,
            pressedContentColor = Palette.TextPrimary,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape)
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (backgroundBrush != null) Modifier.background(backgroundBrush) else Modifier),
            content = content,
        )
    }
}

/** Gives each title its own colour so tiles look different until real artwork loads. */
internal fun tileBrush(seed: String): Brush {
    val hue = ((seed.hashCode() % 360) + 360) % 360
    return Brush.linearGradient(
        listOf(
            Color.hsv(hue.toFloat(), 0.55f, 0.50f),
            Color.hsv(((hue + 40) % 360).toFloat(), 0.60f, 0.18f),
        )
    )
}
