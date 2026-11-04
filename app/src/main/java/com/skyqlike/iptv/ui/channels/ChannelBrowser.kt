package com.skyqlike.iptv.ui.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.epg.progressAt
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.data.model.Programme
import com.skyqlike.iptv.ui.common.HomeTile
import com.skyqlike.iptv.ui.common.Rail
import com.skyqlike.iptv.ui.common.RailRow
import com.skyqlike.iptv.ui.providers.LabeledField
import com.skyqlike.iptv.ui.theme.Palette

/** Lets any channel page show what is on right now. Provided by the home screen. */
val LocalNowOn = compositionLocalOf<(Channel) -> Programme?> { { null } }

/** A channel card: logo, channel name, what is on now, and a bar showing how far through it is. */
fun channelTile(
    ch: Channel,
    nowFor: (Channel) -> Programme?,
    onPlay: (Channel) -> Unit,
    onToggleFavourite: (Channel) -> Unit,
): HomeTile {
    val p = nowFor(ch)
    return HomeTile(
        id = ch.id,
        title = ch.name,
        subtitle = p?.title ?: ch.group,
        imageUrl = ch.logoUrl,
        fit = true,
        onLongClick = { onToggleFavourite(ch) },
        progress = p?.progressAt(System.currentTimeMillis()),
    ) { onPlay(ch) }
}

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
    var query by remember { mutableStateOf("") }
    // Every word typed must appear in the channel's name or its category, so "uk sport" finds "UK | Sky Sports".
    val shown = remember(channels, query) {
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) channels
        else channels.filter { ch -> words.all { ch.name.contains(it, ignoreCase = true) || ch.group.contains(it, ignoreCase = true) } }
    }
    val nowFor = LocalNowOn.current
    val rails = remember(shown, nowFor) {
        shown.groupBy { it.group.ifBlank { "Other" } }.map { (group, list) ->
            Rail(group, list.map { ch -> channelTile(ch, nowFor, onPlay, onToggleFavourite) })
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
            if (query.isBlank()) "${channels.size} channels" else "${shown.size} of ${channels.size} channels",
            fontSize = 15.sp,
            color = Palette.TextSecondary,
            modifier = Modifier.padding(start = 36.dp, bottom = 10.dp),
        )
        if (channels.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().padding(start = 36.dp, end = 36.dp, bottom = 14.dp)) {
                LabeledField("Search channels", query) { query = it }
            }
        }
        if (rails.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(36.dp), contentAlignment = Alignment.TopStart) {
                Text(
                    if (channels.isNotEmpty() && query.isNotBlank()) "No channels match \"${query.trim()}\"." else emptyMessage,
                    fontSize = 17.sp,
                    color = Palette.TextSecondary,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(bottom = 40.dp),
            ) {
                items(rails, key = { it.title }) { RailRow(it, 250.dp, 140.dp) }
            }
        }
    }
}
