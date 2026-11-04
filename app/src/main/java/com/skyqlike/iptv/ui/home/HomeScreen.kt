package com.skyqlike.iptv.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.history.HistoryEntry
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.data.model.favKey
import com.skyqlike.iptv.ui.channels.ChannelBrowser
import com.skyqlike.iptv.ui.common.HomeTile
import com.skyqlike.iptv.ui.common.Rail
import com.skyqlike.iptv.ui.common.RailRow
import com.skyqlike.iptv.ui.theme.Palette

/** The menu down the left side, in the order from the brief. */
val MenuItems = listOf(
    "Stremio", "Home", "TV Guide", "Live TV", "All Channels", "Catch Up", "Movies", "Sports",
    "Entertainment", "News", "Music", "Movie Channels", "Documentaries", "Kids",
    "UK", "Australia", "Ireland", "USA", "My Library", "Favourites", "Settings",
)

/** Builds the home screen rows from whatever is loaded. Empty rows are left out. */
fun buildRails(
    channels: List<Channel>,
    favouriteKeys: Set<String>,
    recentKeys: List<String>,
    continueWatching: List<HistoryEntry>,
    onPlay: (Channel) -> Unit,
    onPlayHistory: (HistoryEntry) -> Unit,
    onToggleFavourite: (Channel) -> Unit = {},
): List<Rail> {
    fun channelTile(ch: Channel) =
        HomeTile(ch.id, ch.name, ch.group, ch.logoUrl, fit = true, onLongClick = { onToggleFavourite(ch) }) { onPlay(ch) }
    val byKey = channels.associateBy { it.favKey }

    return listOf(
        Rail(
            "Continue Watching",
            continueWatching.map { e ->
                HomeTile(e.key, e.title, e.subtitle, e.imageUrl) { onPlayHistory(e) }
            },
        ),
        Rail("Currently Popular", channels.take(8).map(::channelTile)),
        Rail("Recently Watched", recentKeys.mapNotNull { byKey[it] }.map(::channelTile)),
        Rail("Live Sports", channels.filter { it.group.contains("Sport", ignoreCase = true) }.map(::channelTile)),
        Rail("Popular Movies", channels.filter { it.group.contains("Movie", ignoreCase = true) }.map(::channelTile)),
        Rail("Favourite Channels", channels.filter { it.favKey in favouriteKeys }.map(::channelTile)),
        Rail("Recently Added", channels.reversed().take(8).map(::channelTile)),
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
    stremioContent: @Composable () -> Unit = {},
) {
    var section by remember { mutableStateOf("Home") }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    // Back from any other screen goes home. Back on Home leaves the app, like a normal TV app.
    BackHandler(enabled = section != "Home") {
        section = "Home"
        runCatching { firstFocus.requestFocus() }
    }

    Row(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
    ) {
        SideMenu(selected = section, onSelect = { section = it }, firstFocus = firstFocus)

        Box(Modifier.weight(1f).fillMaxSize()) {
            when (section) {
                "Home" -> {
                    val rails = remember(channels, favouriteKeys, recentKeys, continueWatching, onToggleFavourite) {
                        buildRails(channels, favouriteKeys, recentKeys, continueWatching, onPlay, onPlayHistory, onToggleFavourite)
                    }
                    HomeContent(rails, channels.firstOrNull(), onPlay)
                }
                "Settings" -> settingsContent()
                "TV Guide" -> guideContent()
                "Movies" -> moviesContent()
                "My Library" -> libraryContent()
                "Catch Up" -> catchUpContent()
                "Stremio" -> stremioContent()
                "Live TV" -> ChannelBrowser("Live TV", channels, "No channels loaded yet. Add a provider in Settings.", onPlay, onToggleFavourite)
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

@Composable
private fun SideMenu(selected: String, onSelect: (String) -> Unit, firstFocus: FocusRequester) {
    Column(
        Modifier
            .width(220.dp)
            .fillMaxSize()
            .background(Palette.Rail)
            .padding(horizontal = 14.dp, vertical = 24.dp),
    ) {
        Text(
            "TV",
            color = Palette.Accent,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp, bottom = 16.dp),
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(MenuItems) { item ->
                val isFirst = item == MenuItems.first()
                FocusTile(
                    onClick = { onSelect(item) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .then(if (isFirst) Modifier.focusRequester(firstFocus) else Modifier),
                    corner = 10.dp,
                    focusedScale = 1.04f,
                    backgroundColor = if (item == selected) Color(0x26FFFFFF) else Color.Transparent,
                ) {
                    Text(
                        item,
                        fontSize = 17.sp,
                        fontWeight = if (item == selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (item == selected) Palette.TextPrimary else Palette.TextSecondary,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeContent(rails: List<Rail>, featured: Channel?, onPlay: (Channel) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            featured?.let { FeaturedBanner(it, onPlay) }
        }
        items(rails, key = { it.title }) { rail ->
            RailRow(rail)
        }
    }
}

@Composable
private fun FeaturedBanner(channel: Channel, onPlay: (Channel) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(tileBrush(channel.name))
    ) {
        // Dark fade so the text stays readable, like the Sky Q hero banner.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Palette.Background.copy(alpha = 0.92f), Color.Transparent)
                    )
                )
        )
        Column(
            Modifier.align(Alignment.CenterStart).padding(start = 36.dp).width(520.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("FEATURED", color = Palette.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                channel.name,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(channel.group, fontSize = 16.sp, color = Palette.TextSecondary)
            FocusTile(
                onClick = { onPlay(channel) },
                modifier = Modifier.width(190.dp).height(48.dp),
                corner = 24.dp,
                backgroundColor = Palette.Accent,
            ) {
                Text(
                    "Watch Now",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center),
                )
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
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        // The TV Surface only reacts to remote buttons. This lets a finger tap (or long press) work too.
        modifier = modifier.onFocusChanged { if (it.hasFocus) onFocus() }.pointerInput(onClick, onLongClick) {
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
