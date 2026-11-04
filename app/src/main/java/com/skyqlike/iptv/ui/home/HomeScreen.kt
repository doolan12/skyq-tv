package com.skyqlike.iptv.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.data.model.favKey
import com.skyqlike.iptv.ui.theme.Palette

/** The menu down the left side, in the order from the brief. */
val MenuItems = listOf(
    "Home", "TV Guide", "Live TV", "Catch Up", "Movies", "Sports",
    "Entertainment", "Documentaries", "Kids", "My Library", "Favourites", "Settings",
)

/** One row of tiles on the home screen. */
data class Rail(val title: String, val channels: List<Channel>)

/** Builds the home screen rows from whatever channels are loaded. Empty rows are left out. */
fun buildRails(channels: List<Channel>, favouriteKeys: Set<String> = emptySet()): List<Rail> = listOf(
    Rail("Currently Popular", channels.take(8)),
    Rail("Continue Watching", channels.drop(2).take(4)),
    Rail("Live Sports", channels.filter { it.group.contains("Sport", ignoreCase = true) }),
    Rail("Popular Movies", channels.filter { it.group.contains("Movie", ignoreCase = true) }),
    Rail("Favourite Channels", channels.filter { it.favKey in favouriteKeys }),
    Rail("Recently Added", channels.reversed().take(8)),
).filter { it.channels.isNotEmpty() }

@Composable
fun HomeScreen(
    channels: List<Channel>,
    onPlay: (Channel) -> Unit,
    settingsContent: @Composable () -> Unit = {},
    guideContent: @Composable () -> Unit = {},
    favouriteKeys: Set<String> = emptySet(),
) {
    var section by remember { mutableStateOf("Home") }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Row(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
    ) {
        SideMenu(selected = section, onSelect = { section = it }, firstFocus = firstFocus)

        Box(Modifier.weight(1f).fillMaxSize()) {
            if (section == "Home") {
                HomeContent(channels, favouriteKeys, onPlay)
            } else if (section == "Settings") {
                settingsContent()
            } else if (section == "TV Guide") {
                guideContent()
            } else {
                ComingLater(section)
            }
        }
    }
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
private fun HomeContent(channels: List<Channel>, favouriteKeys: Set<String>, onPlay: (Channel) -> Unit) {
    val rails = remember(channels, favouriteKeys) { buildRails(channels, favouriteKeys) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            channels.firstOrNull()?.let { FeaturedBanner(it, onPlay) }
        }
        items(rails) { rail ->
            Column {
                Text(
                    rail.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.TextPrimary,
                    modifier = Modifier.padding(start = 36.dp, bottom = 10.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 36.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(rail.channels, key = { rail.title + it.id }) { ch ->
                        ChannelTile(ch, onPlay)
                    }
                }
            }
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
private fun ChannelTile(channel: Channel, onPlay: (Channel) -> Unit) {
    Column(Modifier.width(250.dp)) {
        FocusTile(
            onClick = { onPlay(channel) },
            modifier = Modifier.fillMaxWidth().height(140.dp),
            corner = 12.dp,
            backgroundBrush = tileBrush(channel.name),
        ) {
            Text(
                channel.name.take(1).uppercase(),
                fontSize = 54.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Text(
            channel.name,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Palette.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp),
        )
        Text(channel.group, fontSize = 13.sp, color = Palette.TextSecondary, maxLines = 1)
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
        // The TV Surface only reacts to remote buttons. This lets a finger tap work too (phones, touch screens).
        modifier = modifier.onFocusChanged { if (it.hasFocus) onFocus() }.pointerInput(onClick) {
            detectTapGestures(onTap = { onClick() })
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

/** Gives each channel its own colour so the tiles look different until real artwork exists. */
internal fun tileBrush(seed: String): Brush {
    val hue = ((seed.hashCode() % 360) + 360) % 360
    return Brush.linearGradient(
        listOf(
            Color.hsv(hue.toFloat(), 0.55f, 0.50f),
            Color.hsv(((hue + 40) % 360).toFloat(), 0.60f, 0.18f),
        )
    )
}
