package com.skyqlike.iptv.ui.phone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.epg.nextOn
import com.skyqlike.iptv.data.epg.nowOn
import com.skyqlike.iptv.data.epg.progressAt
import com.skyqlike.iptv.data.history.HistoryEntry
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.data.model.Programme
import com.skyqlike.iptv.data.model.favKey
import com.skyqlike.iptv.ui.providers.LabeledField
import com.skyqlike.iptv.ui.common.Artwork
import com.skyqlike.iptv.ui.common.MenuIcon
import com.skyqlike.iptv.ui.common.RailRow
import com.skyqlike.iptv.ui.guide.CategoryBar
import com.skyqlike.iptv.ui.guide.buildGuideFilters
import com.skyqlike.iptv.ui.home.FocusTile
import com.skyqlike.iptv.ui.home.buildRails
import com.skyqlike.iptv.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private val TabNames = listOf("Home", "Guide", "Sport", "Search", "More")
private val TabIcons = listOf("home", "guide", "sports", "search", "categories")

/** The pages under the More tab, with the icon shown beside each. */
private val MorePages = listOf("Movies & Series" to "movies", "Catch Up" to "catchup", "Recordings" to "record", "Multi-view" to "all", "Favourites" to "star", "My Lists" to "all", "My Library" to "library", "Settings" to "settings")

/**
 * The phone layout: one screen at a time with a menu along the bottom.
 * It reuses the same Movies, Library and Settings screens as the TV, which already scroll up and down.
 */
@Composable
fun PhoneShell(
    channels: List<Channel>,
    epg: Map<String, List<Programme>>,
    onPlay: (Channel) -> Unit,
    favouriteKeys: Set<String>,
    recentKeys: List<String>,
    continueWatching: List<HistoryEntry>,
    onPlayHistory: (HistoryEntry) -> Unit,
    onToggleFavourite: (Channel) -> Unit,
    moviesContent: @Composable () -> Unit,
    libraryContent: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit,
    searchContent: @Composable () -> Unit,
    matchCentreContent: @Composable () -> Unit,
    catchUpContent: @Composable () -> Unit,
    recordingsContent: @Composable () -> Unit,
    favouritesContent: @Composable () -> Unit,
    listsContent: @Composable () -> Unit = {},
    multiViewContent: @Composable () -> Unit = {},
    extraRails: List<com.skyqlike.iptv.ui.common.Rail> = emptyList(),
    extras: com.skyqlike.iptv.ui.home.HomeExtras = com.skyqlike.iptv.ui.home.HomeExtras(),
) {
    var tab by remember { mutableIntStateOf(0) }
    // Which page is open under More, or -1 for the list of pages.
    var morePage by remember { mutableIntStateOf(-1) }
    // Back goes to Home first, so it never throws you out of the app from the middle of a screen.
    BackHandler(enabled = tab != 0) { tab = 0 }
    // Inside a More page, Back returns to the list first.
    BackHandler(enabled = tab == 4 && morePage >= 0) { morePage = -1; tab = 0 }

    var railOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF14246B), Color(0xFF0B1238), Color(0xFF05060F))))
    ) {
        PhoneSidebar(
            open = railOpen,
            selected = if (tab < 4) tab else 4 + morePage,
            onToggle = { railOpen = !railOpen },
            onPick = { i ->
                if (i < 4) { tab = i; morePage = -1 } else { tab = 4; morePage = i - 4 }
            },
        )
        Box(Modifier.weight(1f).fillMaxHeight().statusBarsPadding()) {
            when (tab) {
                0 -> PhoneHome(channels, epg, onPlay, favouriteKeys, recentKeys, continueWatching, onPlayHistory, onToggleFavourite, extraRails, extras)
                1 -> PhoneGuide(channels, epg, onPlay, favouriteKeys, recentKeys, onToggleFavourite)
                2 -> matchCentreContent()
                3 -> searchContent()
                else -> when (morePage) {
                    0 -> moviesContent()
                    1 -> catchUpContent()
                    2 -> recordingsContent()
                    3 -> multiViewContent()
                    4 -> favouritesContent()
                    5 -> listsContent()
                    6 -> libraryContent()
                    7 -> settingsContent()
                    else -> PhoneMore { morePage = it }
                }
            }
        }
    }
}

/** The menu down the left side, like the IPVIO sidebar: icons only, tap the TV badge to show the names. */
@Composable
private fun PhoneSidebar(open: Boolean, selected: Int, onToggle: () -> Unit, onPick: (Int) -> Unit) {
    val entries = TabNames.take(4).zip(TabIcons.take(4)) + MorePages
    val w by androidx.compose.animation.core.animateDpAsState(if (open) 190.dp else 64.dp, label = "phoneSide")
    Column(
        Modifier
            .width(w)
            .fillMaxHeight()
            .background(Color(0xFF05070D))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.padding(start = 2.dp, bottom = 10.dp).size(44.dp)
                .clip(RoundedCornerShape(13.dp)).background(Palette.Accent).clickable { onToggle() },
            contentAlignment = Alignment.Center,
        ) { Text("TV", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        entries.forEachIndexed { i, (name, icon) ->
            val on = i == selected
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) Palette.Accent.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onPick(i) }
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MenuIcon(icon, if (on) Palette.Accent else Color(0xFFCFCFD6), 24.dp)
                if (open) Text(name, fontSize = 15.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 14.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- home

@Composable
private fun PhoneHome(
    channels: List<Channel>,
    epg: Map<String, List<Programme>>,
    onPlay: (Channel) -> Unit,
    favouriteKeys: Set<String>,
    recentKeys: List<String>,
    continueWatching: List<HistoryEntry>,
    onPlayHistory: (HistoryEntry) -> Unit,
    onToggleFavourite: (Channel) -> Unit,
    extraRails: List<com.skyqlike.iptv.ui.common.Rail>,
    extras: com.skyqlike.iptv.ui.home.HomeExtras,
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMs = System.currentTimeMillis()
        }
    }
    val nowFor: (Channel) -> Programme? = remember(epg, nowMs) { { ch: Channel -> epg.nowOn(ch, nowMs) } }
    val hour = remember(nowMs) { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val live = remember(extras.scores, channels, nowFor) { com.skyqlike.iptv.ui.home.liveGames(extras.scores, channels, nowFor) }
    val rails = remember(channels, favouriteKeys, recentKeys, continueWatching, nowFor, extraRails, extras, live, hour) {
        com.skyqlike.iptv.ui.home.buildHomeRails(channels, favouriteKeys, recentKeys, continueWatching, onPlay, onPlayHistory, onToggleFavourite, nowFor, extras, live, extraRails, hour)
    }
    val spotlights = remember(channels, favouriteKeys, continueWatching, nowFor, live, extras, hour) {
        com.skyqlike.iptv.ui.home.buildSpotlights(channels, nowFor, favouriteKeys, continueWatching, live, extras, hour, onPlay, onPlayHistory)
    }
    val featured = remember(channels, favouriteKeys, nowFor) {
        channels.firstOrNull { it.favKey in favouriteKeys && nowFor(it) != null }
            ?: channels.firstOrNull { nowFor(it) != null }
            ?: channels.firstOrNull()
    }
    val timeText = remember(nowMs) { SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(nowMs)) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Home", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
                Text(timeText, fontSize = 20.sp, fontWeight = FontWeight.Light, color = Palette.TextPrimary)
            }
        }
        if (spotlights.isNotEmpty()) {
            item { com.skyqlike.iptv.ui.home.PhoneSpotlight(com.skyqlike.iptv.ui.home.rememberArtworkSpotlights(spotlights, extras)) }
        } else {
            featured?.let { ch -> item { PhoneHero(ch, nowFor(ch), onPlay) } }
        }
        items(rails, key = { it.title }) { rail -> RailRow(rail, 170.dp, 96.dp, edge = 16.dp) }
    }
}

/** The "on now" card at the top of the phone home page. Tap it to watch. */
@Composable
private fun PhoneHero(channel: Channel, now: Programme?, onPlay: (Channel) -> Unit) {
    val clock = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    FocusTile(
        onClick = { onPlay(channel) },
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(190.dp),
        corner = 18.dp,
        focusedScale = 1.02f,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(Color(0xFF2236C8), Color(0xFF6A2BB8), Color(0xFFC2306A))))
        ) {
            Artwork(
                url = now?.iconUrl ?: channel.logoUrl,
                seed = channel.name,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).width(120.dp).height(70.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = if (now?.iconUrl != null) ContentScale.Crop else ContentScale.Fit,
            )
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    "ON NOW  |  ${channel.name}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xCCFFFFFF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    now?.title ?: channel.name,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (now != null) {
                    Text(
                        clock.format(Date(now.start)) + " - " + clock.format(Date(now.stop)),
                        fontSize = 13.sp,
                        color = Color(0xCCFFFFFF),
                    )
                    ProgressBar(now.progressAt(System.currentTimeMillis()), Color.White, Color(0x40FFFFFF))
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(progress: Float, fill: Color, track: Color) {
    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(track)) {
        Box(Modifier.fillMaxWidth(progress.coerceAtLeast(0.02f)).fillMaxHeight().background(fill))
    }
}

// ---------------------------------------------------------------- guide

/**
 * The phone's TV guide: a list of channels, each showing what is on now and what is next.
 * Search looks at channel names and at the names of shows coming up.
 */
@Composable
private fun PhoneGuide(
    channels: List<Channel>,
    epg: Map<String, List<Programme>>,
    onPlay: (Channel) -> Unit,
    favouriteKeys: Set<String>,
    recentKeys: List<String>,
    onToggleFavourite: (Channel) -> Unit,
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMs = System.currentTimeMillis()
        }
    }
    var query by remember { mutableStateOf("") }
    val filters = remember(channels, favouriteKeys, recentKeys) { buildGuideFilters(channels, favouriteKeys, recentKeys) }
    var filterIndex by remember { mutableIntStateOf(0) }
    val selected = filterIndex.coerceIn(0, filters.lastIndex)
    val active = filters[selected]
    // The same numbers you type on the remote, and see in the TV guide.
    val numbers = remember(channels) { channels.withIndex().associate { it.value.id to it.index + 1 } }
    val q = query.trim()
    val visible = remember(channels, epg, active, q) {
        channels.filter(active.matches).filter { ch ->
            q.isEmpty() ||
                ch.name.contains(q, ignoreCase = true) ||
                (ch.epgId?.let { id -> epg[id]?.any { it.title.contains(q, ignoreCase = true) } } == true)
        }
    }
    val clock = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    // The channel whose upcoming shows are open (to watch or record one), or null for the channel list.
    var scheduleFor by remember { mutableStateOf<Channel?>(null) }
    BackHandler(enabled = scheduleFor != null) { scheduleFor = null }
    scheduleFor?.let { ch ->
        ChannelSchedule(ch, epg, nowMs, { onPlay(ch) }) { scheduleFor = null }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "TV Guide",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.TextPrimary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
        )
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            LabeledField("Search channels and shows", query) { query = it }
        }
        CategoryBar(filters.map { it.label }, selected) { filterIndex = it }
        if (visible.isEmpty()) {
            Text(
                "No channels match.",
                fontSize = 16.sp,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.id }) { channel ->
                    val now = epg.nowOn(channel, nowMs)
                    val next = epg.nextOn(channel, nowMs)
                    // While searching, show the first upcoming show that matches, if it isn't on right now.
                    val match = if (q.isEmpty()) null else channel.epgId?.let { id ->
                        epg[id]?.firstOrNull { it.stop > nowMs && it != now && it.title.contains(q, ignoreCase = true) }
                    }
                    val extra = match ?: next
                    FocusTile(
                        onClick = { onPlay(channel) },
                        modifier = Modifier.fillMaxWidth().height(84.dp),
                        corner = 12.dp,
                        focusedScale = 1.02f,
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                "${numbers[channel.id] ?: ""}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Palette.Accent,
                                modifier = Modifier.width(30.dp),
                            )
                            Artwork(
                                url = channel.logoUrl,
                                seed = channel.name,
                                modifier = Modifier.width(56.dp).height(40.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit,
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(channel.name, fontSize = 12.sp, color = Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    now?.title ?: "No guide data",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Palette.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (now != null) ProgressBar(now.progressAt(nowMs), Palette.Accent, Color(0x33FFFFFF))
                                if (extra != null) {
                                    Text(
                                        (if (match != null) "Later  " else "Next  ") + clock.format(Date(extra.start)) + "  " + extra.title,
                                        fontSize = 12.sp,
                                        color = Palette.TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            val isFavourite = channel.favKey in favouriteKeys
                            val recordContext = androidx.compose.ui.platform.LocalContext.current
                            FocusTile(
                                onClick = { scheduleFor = channel },
                                modifier = Modifier.size(40.dp),
                                corner = 20.dp,
                                focusedScale = 1.1f,
                                backgroundColor = Color(0x1AFFFFFF),
                            ) {
                                MenuIcon("guide", Palette.TextSecondary, 20.dp, Modifier.align(Alignment.Center))
                            }
                            FocusTile(
                                onClick = { onToggleFavourite(channel) },
                                modifier = Modifier.size(40.dp),
                                corner = 20.dp,
                                focusedScale = 1.1f,
                                backgroundColor = Color(0x1AFFFFFF),
                            ) {
                                MenuIcon(
                                    "star",
                                    if (isFavourite) Palette.Accent else Palette.TextSecondary,
                                    20.dp,
                                    Modifier.align(Alignment.Center),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The list of extra pages on the phone: Movies, Catch Up, Recordings, Library and Settings. */
@Composable
private fun PhoneMore(onOpen: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 14.dp)) {
        Text("More", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        MorePages.forEachIndexed { i, (name, icon) ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(i) }.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MenuIcon(icon, Palette.Accent, 26.dp)
                Text(name, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = Palette.TextPrimary)
            }
        }
    }
}

/** What is on, and coming up, on one channel. Tap Record on any show, or Watch on the one that is on now. */
@Composable
private fun ChannelSchedule(
    channel: Channel,
    epg: Map<String, List<Programme>>,
    nowMs: Long,
    onWatch: () -> Unit,
    onClose: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clock = remember { SimpleDateFormat("EEE h:mm a", Locale.getDefault()) }
    val shows = remember(channel, epg, nowMs / 60_000L) {
        channel.epgId?.let { epg[it] }.orEmpty()
            .filter { it.stop > nowMs && it.start < nowMs + 24L * 3600_000L }
            .sortedBy { it.start }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(channel.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Next 24 hours", fontSize = 13.sp, color = Palette.TextSecondary)
            }
            FocusTile(onClick = onClose, modifier = Modifier.width(80.dp).height(40.dp), corner = 20.dp, focusedScale = 1.05f, backgroundColor = Color(0x26FFFFFF)) {
                Text("Back", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
            }
        }
        if (shows.isEmpty()) {
            Text("No guide information for this channel.", fontSize = 16.sp, color = Palette.TextSecondary, modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shows, key = { it.start }) { show ->
                    val isNow = show.start <= nowMs
                    Row(
                        Modifier.fillMaxWidth().height(86.dp).background(Color(0x1AFFFFFF), RoundedCornerShape(12.dp)).padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                if (isNow) "ON NOW" else clock.format(Date(show.start)),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isNow) Color(0xFFFF5A5A) else Palette.Accent,
                            )
                            Text(show.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (isNow) {
                            FocusTile(onClick = onWatch, modifier = Modifier.padding(start = 6.dp).width(70.dp).height(52.dp), corner = 12.dp, focusedScale = 1.05f, backgroundColor = Color(0x26FFFFFF)) {
                                Text("Watch", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
                            }
                        }
                        if (!isNow) {
                            FocusTile(
                                onClick = { com.skyqlike.iptv.ui.record.remindProgramme(context, channel, show) },
                                modifier = Modifier.padding(start = 6.dp).width(62.dp).height(52.dp),
                                corner = 12.dp,
                                focusedScale = 1.05f,
                                backgroundColor = Color(0x26FFFFFF),
                            ) {
                                Text("Remind", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
                            }
                        }
                        FocusTile(
                            onClick = { com.skyqlike.iptv.ui.record.recordProgramme(context, channel, show) },
                            modifier = Modifier.padding(horizontal = 6.dp).width(70.dp).height(52.dp),
                            corner = 12.dp,
                            focusedScale = 1.05f,
                            backgroundColor = Color(0x33FF5A5A),
                        ) {
                            Text("Record", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF8A8A), modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
        }
    }
}
