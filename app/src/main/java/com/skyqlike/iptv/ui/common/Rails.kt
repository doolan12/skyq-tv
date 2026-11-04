package com.skyqlike.iptv.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.skyqlike.iptv.ui.home.FocusTile
import com.skyqlike.iptv.ui.theme.Palette

/** One tile in a row: a channel, a movie, a series, or something you were watching. */
data class HomeTile(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val fit: Boolean = false,
    val onClick: () -> Unit,
)

/** A titled row of tiles that scrolls sideways, like on Sky Q. */
data class Rail(val title: String, val tiles: List<HomeTile>)

@Composable
fun RailRow(
    rail: Rail,
    tileWidth: Dp = 250.dp,
    tileHeight: Dp = 140.dp,
) {
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
            items(rail.tiles, key = { rail.title + "|" + it.id }) { tile ->
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
            modifier = Modifier.fillMaxWidth().height(tileHeight),
            corner = 12.dp,
        ) {
            Artwork(
                url = tile.imageUrl,
                seed = tile.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = if (tile.fit) ContentScale.Fit else ContentScale.Crop,
            )
        }
        Text(
            tile.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Palette.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp),
        )
        Text(
            tile.subtitle,
            fontSize = 13.sp,
            color = Palette.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}
