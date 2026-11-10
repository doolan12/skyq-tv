package com.skyqlike.iptv.ui.downloads

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.download.OfflineHelper
import com.skyqlike.iptv.data.download.OfflineItem
import com.skyqlike.iptv.ui.common.ActionButton
import com.skyqlike.iptv.ui.common.Artwork
import com.skyqlike.iptv.ui.common.pagePad
import com.skyqlike.iptv.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private fun sizeText(bytes: Long): String {
    val gb = bytes / 1_000_000_000.0
    return if (gb >= 1.0) "%.1f GB".format(gb) else "${bytes / 1_000_000} MB"
}

/** Everything saved for offline viewing: watch it, see how far a download has got, or delete it. */
@Composable
fun DownloadsScreen(
    items: List<OfflineItem>,
    helper: OfflineHelper,
    onPlay: (OfflineItem) -> Unit,
    onDelete: (OfflineItem) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var progress by remember { mutableStateOf<Map<String, OfflineHelper.Progress>>(emptyMap()) }
    LaunchedEffect(items) {
        while (true) {
            progress = withContext(Dispatchers.IO) { items.associate { it.key to helper.progress(it) } }
            delay(1500)
        }
    }
    var armed by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = pagePad(), vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Downloads", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary) }
        item {
            Text(
                "Saved on this device, so they play with no internet. Downloads carry on in the background.",
                fontSize = 14.sp,
                color = Palette.TextSecondary,
            )
        }
        if (items.isEmpty()) {
            item {
                Text(
                    "Nothing downloaded yet. Open a movie or episode and choose Download.",
                    fontSize = 16.sp,
                    color = Palette.TextSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        items(items.sortedByDescending { it.addedAt }, key = { it.key }) { o ->
            val p = progress[o.key]
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x14FFFFFF))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Artwork(o.imageUrl, o.title, Modifier.width(54.dp).height(80.dp).clip(RoundedCornerShape(6.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(o.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (o.subtitle.isNotBlank()) Text(o.subtitle, fontSize = 13.sp, color = Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    when {
                        p == null -> Text("Checking...", fontSize = 13.sp, color = Palette.TextSecondary)
                        p.done -> Text("Ready to watch  -  ${sizeText(p.total.takeIf { it > 0 } ?: p.bytes)}", fontSize = 13.sp, color = Palette.Accent, fontWeight = FontWeight.SemiBold)
                        p.failed -> Text("Didn't finish. Delete it and try again (the link may have expired).", fontSize = 13.sp, color = Palette.TextSecondary)
                        else -> {
                            Text(
                                "Downloading  ${(p.fraction * 100).toInt()}%" + if (p.total > 0) "  (${sizeText(p.bytes)} of ${sizeText(p.total)})" else "",
                                fontSize = 13.sp,
                                color = Palette.TextSecondary,
                            )
                            Box(Modifier.fillMaxWidth().height(4.dp).background(Color(0x44FFFFFF))) {
                                Box(Modifier.fillMaxWidth(p.fraction.coerceAtLeast(0.02f)).height(4.dp).background(Palette.Accent))
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (p != null && p.done) ActionButton("Play", Modifier.width(96.dp), accent = true) { onPlay(o) }
                    ActionButton(if (armed == o.key) "Sure?" else "Delete", Modifier.width(96.dp)) {
                        if (armed == o.key) {
                            armed = null
                            onDelete(o)
                        } else armed = o.key
                    }
                }
            }
        }
        item { ActionButton("Back", Modifier.width(140.dp)) { onBack() } }
    }
}
