package com.skyqlike.iptv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.model.PlayItem
import com.skyqlike.iptv.data.model.Programme
import com.skyqlike.iptv.ui.theme.Palette

/**
 * A strip of nearby channels along the bottom of the picture, each showing what is on now and next.
 * Opened with Right on the remote while watching live TV. Left / Right moves, OK switches, Up / Down closes.
 */
@Composable
internal fun MiniGuide(
    entries: List<PlayItem>,
    selected: Int,
    currentKey: String,
    epg: Map<String, List<Programme>>,
    nowMs: Long,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    LaunchedEffect(selected) { state.animateScrollToItem((selected - 1).coerceAtLeast(0)) }

    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
            .padding(top = 40.dp, bottom = 24.dp)
    ) {
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(entries, key = { i, e -> "$i|${e.key}" }) { i, e ->
                val list = e.epgId?.let { epg[it] }
                val now = list?.firstOrNull { nowMs >= it.start && nowMs < it.stop }
                val next = list?.firstOrNull { it.start >= (now?.stop ?: nowMs) }
                val isSelected = i == selected
                val isCurrent = e.key == currentKey
                Column(
                    Modifier
                        .width(220.dp)
                        .height(112.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) Color(0xFF26325F) else Color(0xCC15151A))
                        .border(if (isSelected) 3.dp else 1.dp, if (isSelected) Color.White else Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                        .clickable { onPick(i) }
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        e.title + if (isCurrent) "  (watching)" else "",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) Palette.Accent else Palette.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        now?.title ?: "No guide data",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Palette.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (now != null) {
                        val progress = ((nowMs - now.start).toFloat() / (now.stop - now.start).coerceAtLeast(1L)).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF))) {
                            Box(Modifier.fillMaxWidth(progress.coerceAtLeast(0.02f)).height(3.dp).background(Palette.Accent))
                        }
                    }
                    if (next != null) {
                        Text("Next: ${next.title}", fontSize = 12.sp, color = Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
