package com.skyqlike.iptv.ui.common

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.skyqlike.iptv.ui.theme.Palette

/** The gap at the left and right edge of rows and text. Wide on a TV, narrow on a phone. */
val LocalSidePad = androidx.compose.runtime.compositionLocalOf { 48.dp }

/** True on a narrow screen such as a phone held upright. */
@Composable
fun isTvDevice(): Boolean = androidx.compose.ui.platform.LocalContext.current.let { c ->
    c.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) ||
        (c.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) ==
        android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
}

@Composable
fun isNarrowScreen(): Boolean = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600

/** The side gap for a whole page: slim on a phone, wide on a TV. */
@Composable
fun pagePad(): Dp = if (isNarrowScreen()) 16.dp else 48.dp

/** One tile in a row: a movie, a series, or something you were watching. */
data class HomeTile(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    /** Hold OK on the remote (or press and hold on a touch screen). Used for My List. */
    val onLongClick: (() -> Unit)? = null,
    /** 0 to 1: how far through it you are. Shows a thin bar along the bottom of the tile. */
    val progress: Float? = null,
    val progressColor: Color = Palette.Accent,
    /** Called when the remote lands on this tile (the Home banner follows it). */
    val onFocus: (() -> Unit)? = null,
    /** Looks up the YouTube trailer id, so the tile can play it when the remote rests on it (TV only). */
    val trailer: (suspend () -> String?)? = null,
    /** On a phone: this is the poster you tapped once (the banner shows it). It grows and plays its trailer. */
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

/** A titled row of tiles that scrolls sideways. */
data class Rail(val title: String, val tiles: List<HomeTile>)

@Composable
fun RailRow(
    rail: Rail,
    tileWidth: Dp = 150.dp,
    tileHeight: Dp = 225.dp,
    /** The tile the remote was on last time (when coming back to this screen), and a way to report the current one. */
    restoreId: String? = null,
    onTileFocused: (String) -> Unit = {},
) {
    Column {
        if (rail.title.isNotBlank()) {
            Text(
                rail.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Palette.TextPrimary,
                modifier = Modifier.padding(start = LocalSidePad.current, bottom = 2.dp),
            )
        }
        // The vertical padding leaves room for a tile to grow when the remote lands on it.
        // While the remote is in this row, the posters you're not on dim a little so the one you're on stands out.
        var rowFocused by remember { mutableStateOf(false) }
        val at = if (restoreId == null) -1 else rail.tiles.indexOfFirst { it.id == restoreId }
        val restoreFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        val rowState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, at - 1))
        if (at >= 0) {
            androidx.compose.runtime.LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(250)
                runCatching { restoreFocus.requestFocus() }
            }
        }
        // On a TV the focused poster grows a lot, so the row leaves more room above and below.
        val tvRow = isTvDevice()
        LazyRow(
            state = rowState,
            modifier = Modifier.onFocusChanged { rowFocused = it.hasFocus },
            contentPadding = PaddingValues(horizontal = LocalSidePad.current, vertical = if (tvRow) 48.dp else 34.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(rail.tiles, key = { i, t -> "$i|${rail.title}|${t.id}" }) { i, tile ->
                PosterTile(
                    tile.copy(onFocus = { tile.onFocus?.invoke(); onTileFocused(tile.id) }),
                    tileWidth,
                    tileHeight,
                    dimWhenOthersFocused = rowFocused,
                    focusRequester = if (i == at) restoreFocus else null,
                )
            }
        }
    }
}

/**
 * The "Top 10" row: a huge outlined number sits beside each poster, like Netflix.
 * Only the first ten tiles are shown.
 */
@Composable
fun Top10Row(
    title: String,
    tiles: List<HomeTile>,
    tileWidth: Dp = 130.dp,
    tileHeight: Dp = 195.dp,
    restoreId: String? = null,
    onTileFocused: (String) -> Unit = {},
) {
    Column {
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Palette.TextPrimary,
            modifier = Modifier.padding(start = LocalSidePad.current, bottom = 2.dp),
        )
        val at = if (restoreId == null) -1 else tiles.take(10).indexOfFirst { it.id == restoreId }
        val restoreFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        val rowState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, at - 1))
        if (at >= 0) {
            androidx.compose.runtime.LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(250)
                runCatching { restoreFocus.requestFocus() }
            }
        }
        val tv = isTvDevice()
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = LocalSidePad.current, vertical = if (tv) 48.dp else 34.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(tiles.take(10), key = { i, t -> "top10|$i|${t.id}" }) { i, tile ->
                val rank = i + 1
                Row(verticalAlignment = Alignment.Bottom) {
                    // "10" has two digits, so it needs a wider space than 1 to 9.
                    Box(
                        Modifier.width(if (rank == 10) 128.dp else 74.dp).height(tileHeight),
                        contentAlignment = Alignment.BottomEnd,
                    ) {
                        Text(
                            rank.toString(),
                            style = TextStyle(
                                fontSize = 120.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF9A9A9A),
                                drawStyle = Stroke(width = 5f),
                            ),
                        )
                    }
                    var topFocused by remember { mutableStateOf(false) }
                    var topTrailer by remember(tile.id) { mutableStateOf<String?>(null) }
                    val topActive = if (tv) topFocused else tile.selected
                    val topGrow by androidx.compose.animation.core.animateFloatAsState(if (!tv && topActive) 1.3f else 1f, label = "topGrow")
                    androidx.compose.runtime.LaunchedEffect(topActive, tile.id) {
                        topTrailer = null
                        if (!topActive || tile.trailer == null) return@LaunchedEffect
                        kotlinx.coroutines.delay(2000)
                        topTrailer = runCatching { tile.trailer.invoke() }.getOrNull()
                    }
                    Box(
                        Modifier.width(tileWidth).height(tileHeight)
                            .zIndex(if (topActive) 1f else 0f)
                            .onFocusChanged { topFocused = it.hasFocus },
                    ) {
                        FocusTile(
                            onClick = tile.onClick,
                            onLongClick = tile.onLongClick,
                            onFocus = { tile.onFocus?.invoke(); onTileFocused(tile.id) },
                            modifier = Modifier.fillMaxSize()
                                .graphicsLayer { scaleX = topGrow; scaleY = topGrow }
                                .then(if (i == at) Modifier.focusRequester(restoreFocus) else Modifier),
                            corner = 6.dp,
                            focusedScale = if (tv) 1.45f else 1.1f,
                        ) {
                            Artwork(url = tile.imageUrl, seed = tile.title, modifier = Modifier.fillMaxSize())
                            val vid = topTrailer
                            if (topActive && vid != null) {
                                com.skyqlike.iptv.ui.trailer.TrailerPreview(
                                    videoId = vid,
                                    modifier = Modifier.fillMaxSize(),
                                    cover = true,
                                    onPlaying = {},
                                    onEnded = { topTrailer = null },
                                    onFailed = { topTrailer = null },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PosterTile(
    tile: HomeTile,
    tileWidth: Dp,
    tileHeight: Dp,
    dimWhenOthersFocused: Boolean = false,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    var tileFocused by remember { mutableStateOf(false) }
    val tv = isTvDevice()
    // After a couple of seconds on a poster (TV only), its trailer plays right on the poster.
    var trailerId by remember(tile.id) { mutableStateOf<String?>(null) }
    var trailerOn by remember(tile.id) { mutableStateOf(false) }
    val active = if (tv) tileFocused else tile.selected
    val grow by androidx.compose.animation.core.animateFloatAsState(if (!tv && active) 1.3f else 1f, label = "grow")
    androidx.compose.runtime.LaunchedEffect(active, tile.id) {
        trailerOn = false
        trailerId = null
        if (!active || tile.trailer == null) { return@LaunchedEffect }
        kotlinx.coroutines.delay(2000)
        if (trailerId == null) trailerId = runCatching { tile.trailer.invoke() }.getOrNull()
    }
    Column(
        Modifier
            .width(tileWidth)
            .zIndex(if (active) 1f else 0f)
            .onFocusChanged { tileFocused = it.hasFocus }
            .alpha(if (dimWhenOthersFocused && !tileFocused) 0.6f else 1f),
    ) {
        FocusTile(
            onClick = tile.onClick,
            onLongClick = tile.onLongClick,
            onFocus = { tile.onFocus?.invoke() },
            modifier = Modifier.fillMaxWidth().height(tileHeight)
                .graphicsLayer { scaleX = grow; scaleY = grow }
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            corner = 6.dp,
            focusedScale = if (tv) 1.45f else 1.12f,
        ) {
            Box(Modifier.fillMaxSize()) {
                Artwork(
                    url = tile.imageUrl,
                    seed = tile.title,
                    modifier = Modifier.fillMaxSize(),
                )
                val vid = trailerId
                if (active && vid != null) {
                    com.skyqlike.iptv.ui.trailer.TrailerPreview(
                        videoId = vid,
                        modifier = Modifier.fillMaxSize(),
                        cover = true,
                        onPlaying = { trailerOn = true },
                        onEnded = { trailerId = null },
                        onFailed = { trailerId = null },
                    )
                }
                val done = tile.progress
                if (done != null && done > 0f) {
                    Box(
                        Modifier.align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color(0x66FFFFFF))
                    )
                    Box(
                        Modifier.align(Alignment.BottomStart)
                            .fillMaxWidth(done.coerceIn(0.03f, 1f))
                            .height(4.dp)
                            .background(tile.progressColor)
                    )
                }
            }
        }
        Text(
            tile.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Palette.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp),
        )
        if (tile.subtitle.isNotBlank()) {
            Text(
                tile.subtitle,
                fontSize = 12.sp,
                color = Palette.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}

/** Grey placeholder rows that gently pulse while Home is loading, so the screen doesn't sit empty. */
@Composable
fun ShimmerHome() {
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(900),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) {
            Box(
                Modifier.padding(start = LocalSidePad.current).width(180.dp).height(18.dp).alpha(pulse)
                    .background(Color(0xFF3A3A44), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            )
            Row(Modifier.padding(horizontal = LocalSidePad.current), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(8) {
                    Box(
                        Modifier.width(130.dp).height(195.dp).alpha(pulse)
                            .background(Color(0xFF2A2A32), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                    )
                }
            }
        }
    }
}
