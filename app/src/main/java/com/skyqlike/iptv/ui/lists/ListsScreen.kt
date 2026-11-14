package com.skyqlike.iptv.ui.lists

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.lists.ChannelList
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.ui.channels.FavMove
import com.skyqlike.iptv.ui.channels.FavouritesScreen
import com.skyqlike.iptv.ui.home.FocusTile
import com.skyqlike.iptv.ui.providers.LabeledField
import com.skyqlike.iptv.ui.theme.Palette

/**
 * The person's own channel lists: make one, pick channels for it, put them in order.
 * Each list also shows up as a row on the Home page.
 */
@Composable
fun ListsScreen(
    lists: List<ChannelList>,
    channels: List<Channel>,
    onChange: (List<ChannelList>) -> Unit,
    onPlay: (List<Channel>, Int) -> Unit,
    compact: Boolean = false,
) {
    val edge = if (compact) 16.dp else 36.dp
    var openId by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf(false) }
    val open = lists.firstOrNull { it.id == openId }
    val byKey = remember(channels) { channels.associateBy { it.favKey } }

    BackHandler(enabled = adding || renaming || open != null) {
        when {
            adding -> adding = false
            renaming -> renaming = false
            else -> openId = null
        }
    }

    fun update(l: ChannelList) = onChange(lists.map { if (it.id == l.id) l else it })

    if (open == null) {
        Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
            Text("My Lists", fontSize = if (compact) 26.sp else 32.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary, modifier = Modifier.padding(horizontal = edge))
            Text("Make your own channel lists. Each one also appears as a row on Home.", fontSize = 13.sp, color = Palette.TextSecondary, modifier = Modifier.padding(horizontal = edge, vertical = 4.dp))
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = edge, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LabeledField("New list name", newName) { newName = it }
                        Button("Create list", Modifier.width(200.dp), enabled = newName.isNotBlank()) {
                            val l = ChannelList(System.currentTimeMillis().toString(), newName.trim(), emptyList())
                            onChange(lists + l)
                            newName = ""
                            openId = l.id
                            adding = true
                        }
                    }
                }
                items(lists, key = { it.id }) { l ->
                    FocusTile(onClick = { openId = l.id }, modifier = Modifier.fillMaxWidth().height(64.dp), corner = 12.dp, focusedScale = 1.02f) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(l.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${l.keys.count { it in byKey }} channels", fontSize = 14.sp, color = Palette.TextSecondary)
                        }
                    }
                }
            }
        }
        return
    }

    if (adding) {
        var query by remember { mutableStateOf("") }
        val q = query.trim()
        val shown = remember(channels, q) {
            (if (q.isEmpty()) channels else channels.filter { it.name.contains(q, true) || it.group.contains(q, true) }).take(150)
        }
        Column(Modifier.fillMaxSize().padding(top = 20.dp, start = edge, end = edge)) {
            Text("Add channels to ${open.name}", fontSize = if (compact) 22.sp else 28.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
            Text("Type to search, then press OK on a channel to add or remove it.", fontSize = 13.sp, color = Palette.TextSecondary, modifier = Modifier.padding(vertical = 4.dp))
            LabeledField("Search channels", query) { query = it }
            Row(Modifier.padding(vertical = 8.dp)) { Button("Done", Modifier.width(140.dp), accent = true) { adding = false } }
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(shown, key = { it.id }) { ch ->
                    val inList = ch.favKey in open.keys
                    FocusTile(
                        onClick = { update(open.copy(keys = if (inList) open.keys - ch.favKey else open.keys + ch.favKey)) },
                        modifier = Modifier.fillMaxWidth().height(52.dp), corner = 10.dp, focusedScale = 1.02f,
                    ) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(ch.name, fontSize = 16.sp, color = Palette.TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (inList) "Added  ✓" else ch.group, fontSize = 13.sp, color = if (inList) Palette.Accent else Palette.TextSecondary, maxLines = 1)
                        }
                    }
                }
            }
        }
        return
    }

    val members = open.keys.mapNotNull { byKey[it] }
    Column(Modifier.fillMaxSize()) {
        if (renaming) {
            var name by remember { mutableStateOf(open.name) }
            Column(Modifier.padding(horizontal = edge, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledField("List name", name) { name = it }
                Button("Save name", Modifier.width(180.dp), enabled = name.isNotBlank(), accent = true) {
                    update(open.copy(name = name.trim()))
                    renaming = false
                }
            }
            return@Column
        }
        Row(Modifier.padding(horizontal = edge, vertical = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button("Add channels", Modifier.width(if (compact) 130.dp else 170.dp), accent = true) { adding = true }
            Button("Rename", Modifier.width(if (compact) 90.dp else 120.dp)) { renaming = true }
            Button("Delete list", Modifier.width(if (compact) 100.dp else 140.dp)) {
                onChange(lists.filter { it.id != open.id })
                openId = null
            }
        }
        Box(Modifier.weight(1f)) {
            FavouritesScreen(
                favourites = members,
                onPlay = { ch -> onPlay(members, members.indexOfFirst { it.id == ch.id }.coerceAtLeast(0)) },
                onMove = { ch, move ->
                    val keys = open.keys.toMutableList()
                    val i = keys.indexOf(ch.favKey)
                    if (i >= 0) {
                        keys.removeAt(i)
                        val to = when (move) { FavMove.UP -> (i - 1).coerceAtLeast(0); FavMove.DOWN -> (i + 1).coerceAtMost(keys.size); FavMove.TOP -> 0 }
                        keys.add(to, ch.favKey)
                        update(open.copy(keys = keys))
                    }
                },
                onRemove = { ch -> update(open.copy(keys = open.keys - ch.favKey)) },
                compact = compact,
                title = open.name,
                emptyText = "This list is empty. Choose Add channels to fill it.",
            )
        }
    }
}

@Composable
private fun Button(label: String, modifier: Modifier, enabled: Boolean = true, accent: Boolean = false, onClick: () -> Unit) {
    FocusTile(
        onClick = { if (enabled) onClick() },
        modifier = modifier.height(48.dp),
        corner = 12.dp,
        focusedScale = 1.04f,
        backgroundColor = if (accent) Palette.Accent else Palette.Panel,
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) Palette.TextPrimary else androidx.compose.ui.graphics.Color(0x55FFFFFF), modifier = Modifier.align(Alignment.Center))
    }
}
