package com.skyqlike.iptv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.stremio.AddonHealth
import com.skyqlike.iptv.data.stremio.StreamStat
import com.skyqlike.iptv.ui.common.ActionButton
import com.skyqlike.iptv.ui.theme.Palette
import kotlinx.coroutines.delay

private val Good = Color(0xFF4CC38A)
private val Slow = Color(0xFFE8B84A)
private val Bad = Color(0xFFE5484D)

private fun seconds(ms: Long) = "%.1fs".format(ms / 1000.0)

/**
 * Shows how each of your addons did the last time Home loaded, and the last time streams were looked up.
 * When something is slow or empty, this says which addon is the cause.
 */
@Composable
fun AddonHealthPage(health: List<AddonHealth>, streamStats: List<StreamStat>, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(200)
        runCatching { focus.requestFocus() }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Addon health", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary) }
        item {
            Text(
                "How each addon did the last time Home loaded. Green is fine, amber is slow, red didn't answer or gave nothing.",
                fontSize = 14.sp,
                color = Palette.TextSecondary,
            )
        }
        item { ActionButton("Back", Modifier.width(130.dp).focusRequester(focus)) { onBack() } }
        if (health.isEmpty()) {
            item {
                Text(
                    "Nothing to show yet. Open Home and let it finish loading, then come back here.",
                    fontSize = 16.sp,
                    color = Palette.TextSecondary,
                )
            }
        }
        items(health, key = { it.name + it.manifestMs }) { h ->
            val stream = streamStats.firstOrNull { it.name == h.name }
            val color = when {
                !h.reachable -> Bad
                h.listsTried > 0 && h.listsWithTitles == 0 -> Bad
                h.manifestMs > 4_000 || h.slowestListMs > 6_000 || (h.listsTried > 0 && h.listsWithTitles < h.listsTried) -> Slow
                else -> Good
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(h.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary)
                if (!h.reachable) {
                    Text("Not responding. ${h.note}", fontSize = 14.sp, color = color)
                } else {
                    Text("Opened in ${seconds(h.manifestMs)}", fontSize = 14.sp, color = color)
                    if (h.listsTried > 0) {
                        Text(
                            "${h.listsWithTitles} of ${h.listsTried} lists gave titles" +
                                if (h.slowestListMs > 0) " (slowest ${seconds(h.slowestListMs)})" else "",
                            fontSize = 14.sp,
                            color = color,
                        )
                    } else {
                        Text("No title lists (it may only give streams or subtitles).", fontSize = 14.sp, color = Palette.TextSecondary)
                    }
                    if (stream != null) {
                        Text(
                            if (stream.ok) "Last stream lookup: ${seconds(stream.ms)}, ${stream.count} result(s)"
                            else "Last stream lookup failed after ${seconds(stream.ms)}",
                            fontSize = 14.sp,
                            color = if (!stream.ok) Bad else if (stream.ms > 8_000) Slow else Palette.TextSecondary,
                        )
                    }
                }
            }
        }
    }
}
