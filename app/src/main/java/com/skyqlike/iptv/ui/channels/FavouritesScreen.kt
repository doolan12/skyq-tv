package com.skyqlike.iptv.ui.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.ui.common.Artwork
import com.skyqlike.iptv.ui.home.FocusTile
import com.skyqlike.iptv.ui.theme.Palette

enum class FavMove { UP, DOWN, TOP }

/**
 * Your favourite channels in the order you chose, with buttons to move each one.
 * The same order is used on Home and for changing channels while you watch from here.
 */
@Composable
fun FavouritesScreen(
    favourites: List<Channel>,
    onPlay: (Channel) -> Unit,
    onMove: (Channel, FavMove) -> Unit,
    onRemove: (Channel) -> Unit,
    compact: Boolean = false,
    title: String = "Favourites",
    emptyText: String = "No favourites yet. While watching a channel, choose Add fav, or hold OK on a channel tile (press and hold on a phone).",
) {
    val edge = if (compact) 16.dp else 36.dp
    val nowFor = LocalNowOn.current
    Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
        Text(title, fontSize = if (compact) 26.sp else 32.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary, modifier = Modifier.padding(horizontal = edge))
        Text(
            if (favourites.isEmpty()) "" else (if (favourites.size == 1) "1 channel" else "${favourites.size} channels") + ". Use the arrows to put them in the order you like.",
            fontSize = 13.sp,
            color = Palette.TextSecondary,
            modifier = Modifier.padding(horizontal = edge, vertical = 4.dp),
        )
        if (favourites.isEmpty()) {
            Text(
                emptyText,
                fontSize = 16.sp,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(edge),
            )
            return@Column
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = edge, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(favourites, key = { _, ch -> ch.id }) { index, ch ->
                val now = nowFor(ch)
                Row(Modifier.fillMaxWidth().height(if (compact) 64.dp else 68.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusTile(onClick = { onPlay(ch) }, modifier = Modifier.weight(1f).fillMaxSize(), corner = 12.dp, focusedScale = 1.02f) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!compact) Text("${index + 1}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.Accent, modifier = Modifier.width(24.dp))
                            Box(Modifier.size(if (compact) 36.dp else 44.dp)) {
                                Artwork(ch.logoUrl, ch.name, Modifier.fillMaxSize(), ContentScale.Fit)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(ch.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(now?.title ?: ch.group, fontSize = 12.sp, color = Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    val w = if (compact) 38.dp else 64.dp
                    SmallButton("Up", "↑", compact, w, enabled = index > 0) { onMove(ch, FavMove.UP) }
                    SmallButton("Down", "↓", compact, w, enabled = index < favourites.lastIndex) { onMove(ch, FavMove.DOWN) }
                    if (!compact) SmallButton("Top", "Top", false, w, enabled = index > 0) { onMove(ch, FavMove.TOP) }
                    SmallButton("Remove", "×", compact, if (compact) 38.dp else 100.dp, enabled = true) { onRemove(ch) }
                }
            }
        }
    }
}

@Composable
private fun SmallButton(label: String, short: String, compact: Boolean, width: androidx.compose.ui.unit.Dp, enabled: Boolean, onClick: () -> Unit) {
    FocusTile(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.width(width).fillMaxSize(),
        corner = 12.dp,
        focusedScale = 1.05f,
    ) {
        Text(
            if (compact) short else label,
            fontSize = if (compact) 18.sp else 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) Palette.TextPrimary else Color(0x55FFFFFF),
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
