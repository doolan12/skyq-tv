package com.skyqlike.iptv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.Color
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
        Text(
            rail.title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Palette.TextPrimary,
            modifier = Modifier.padding(start = 48.dp, bottom = 2.dp),
        )
        // The vertical padding leaves room for a tile to grow when the remote lands on it.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(rail.tiles, key = { i, t -> "$i|${rail.title}|${t.id}" }) { _, tile ->
                PosterTile(tile, tileWidth, tileHeight)
            }
        }
    }
}

@Composable
fun PosterTile(tile: HomeTile, tileWidth: Dp, tileHeight: Dp) {
    Column(Modifier.width(tileWidth)) {
        FocusTile(
            onClick = tile.onClick,
            onLongClick = tile.onLongClick,
            modifier = Modifier.fillMaxWidth().height(tileHeight),
            corner = 6.dp,
            focusedScale = 1.1f,
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
