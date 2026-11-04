package com.skyqlike.iptv.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.history.HistoryEntry
import com.skyqlike.iptv.ui.common.HomeTile
import com.skyqlike.iptv.ui.common.Rail
import com.skyqlike.iptv.ui.common.RailRow
import com.skyqlike.iptv.ui.theme.Palette

/** My Library: what you were in the middle of, and what you've watched recently. */
@Composable
fun LibraryScreen(history: List<HistoryEntry>, onPlay: (HistoryEntry) -> Unit) {
    fun tile(e: HistoryEntry) = HomeTile(e.key, e.title, e.subtitle, e.imageUrl) { onPlay(e) }

    val rails = listOf(
        Rail("Continue Watching", history.filter { !it.finished && it.positionMs > 10_000 }.map(::tile)),
        Rail("Recently Watched", history.take(20).map(::tile)),
    ).filter { it.tiles.isNotEmpty() }

    Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
        Text(
            "My Library",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.TextPrimary,
            modifier = Modifier.padding(start = 36.dp, bottom = 16.dp),
        )
        if (rails.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(36.dp), contentAlignment = Alignment.TopStart) {
                Text(
                    "Nothing here yet. Movies and episodes you watch will show up here, so you can pick up where you left off.",
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
                items(rails, key = { it.title }) { RailRow(it, 150.dp, 225.dp) }
            }
        }
    }
}
