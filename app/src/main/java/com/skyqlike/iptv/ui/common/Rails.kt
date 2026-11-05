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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
    val onClick: () -> Unit,
)

/** A titled row of tiles that scrolls sideways. */
data class Rail(val title: String, val tiles: List<HomeTile>)

@Composable
fun RailRow(
    rail: Rail,
    tileWidth: Dp = 150.dp,
    tileHeight: Dp = 225.dp,
) {
    Column {
        if (rail.title.isNotBlank()) {
            Text(
                rail.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Palette.TextPrimary,
                modifier = Modifier.padding(start = 48.dp, bottom = 2.dp),
            )
        }
        // The vertical padding leaves room for a tile to grow when the remote lands on it.
        // While the remote is in this row, the posters you're not on dim a little so the one you're on stands out.
        var rowFocused by remember { mutableStateOf(false) }
        LazyRow(
            modifier = Modifier.onFocusChanged { rowFocused = it.hasFocus },
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(rail.tiles, key = { i, t -> "$i|${rail.title}|${t.id}" }) { _, tile ->
                PosterTile(tile, tileWidth, tileHeight, dimWhenOthersFocused = rowFocused)
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
) {
    Column {
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Palette.TextPrimary,
            modifier = Modifier.padding(start = 48.dp, bottom = 2.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
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
                    Box(Modifier.width(tileWidth).height(tileHeight)) {
                        FocusTile(
                            onClick = tile.onClick,
                            onLongClick = tile.onLongClick,
                            onFocus = { tile.onFocus?.invoke() },
                            modifier = Modifier.fillMaxSize(),
                            corner = 6.dp,
                            focusedScale = 1.1f,
                        ) {
                            Artwork(url = tile.imageUrl, seed = tile.title, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PosterTile(tile: HomeTile, tileWidth: Dp, tileHeight: Dp, dimWhenOthersFocused: Boolean = false) {
    var tileFocused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .width(tileWidth)
            .onFocusChanged { tileFocused = it.hasFocus }
            .alpha(if (dimWhenOthersFocused && !tileFocused) 0.6f else 1f),
    ) {
        FocusTile(
            onClick = tile.onClick,
            onLongClick = tile.onLongClick,
            onFocus = { tile.onFocus?.invoke() },
            modifier = Modifier.fillMaxWidth().height(tileHeight),
            corner = 6.dp,
            focusedScale = 1.12f,
        ) {
            Box(Modifier.fillMaxSize()) {
                Artwork(
                    url = tile.imageUrl,
                    seed = tile.title,
                    modifier = Modifier.fillMaxSize(),
                )
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
                Modifier.padding(start = 48.dp).width(180.dp).height(18.dp).alpha(pulse)
                    .background(Color(0xFF3A3A44), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            )
            Row(Modifier.padding(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
