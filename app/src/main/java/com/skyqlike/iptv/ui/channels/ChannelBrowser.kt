package com.skyqlike.iptv.ui.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.ui.common.HomeTile
import com.skyqlike.iptv.ui.common.Rail
import com.skyqlike.iptv.ui.common.RailRow
import com.skyqlike.iptv.ui.theme.Palette

/**
 * A page of channels, one row per category. Used for Live TV, Favourites,
 * Sports, Entertainment, Documentaries and Kids.
 */
@Composable
fun ChannelBrowser(
    title: String,
    channels: List<Channel>,
    emptyMessage: String,
    onPlay: (Channel) -> Unit,
    onToggleFavourite: (Channel) -> Unit = {},
) {
    val rails = remember(channels) {
        channels.groupBy { it.group.ifBlank { "Other" } }.map { (group, list) ->
            Rail(group, list.map { ch -> HomeTile(ch.id, ch.name, ch.group, ch.logoUrl, fit = true, onLongClick = { onToggleFavourite(ch) }) { onPlay(ch) } })
        }
    }

    Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
        Text(
            title,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.TextPrimary,
            modifier = Modifier.padding(start = 36.dp),
        )
        Text(
            "${channels.size} channels",
            fontSize = 15.sp,
            color = Palette.TextSecondary,
            modifier = Modifier.padding(start = 36.dp, bottom = 16.dp),
        )
        if (rails.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(36.dp), contentAlignment = Alignment.TopStart) {
                Text(emptyMessage, fontSize = 17.sp, color = Palette.TextSecondary)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(bottom = 40.dp),
            ) {
                items(rails, key = { it.title }) { RailRow(it) }
            }
        }
    }
}
