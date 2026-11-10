package com.skyqlike.iptv.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.record.RecState
import com.skyqlike.iptv.data.record.Recording
import com.skyqlike.iptv.ui.home.FocusTile
import com.skyqlike.iptv.ui.theme.Palette
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private fun sizeText(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "${bytes / 1_000_000L} MB"
    else -> "${bytes / 1_000L} KB"
}

/** Everything you have recorded, are recording, or have planned. */
@Composable
fun RecordingsScreen(
    recordings: List<Recording>,
    onPlay: (Recording) -> Unit,
    onStop: (Recording) -> Unit,
    onCancel: (Recording) -> Unit,
    onDelete: (Recording) -> Unit,
    compact: Boolean = false,
) {
    val edge = if (compact) 16.dp else 36.dp
    // Sizes of recordings in progress grow, so look again every few seconds.
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            tick++
        }
    }
    val dayFormat = remember { SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()) }
    val now = recordings.filter { it.state == RecState.RECORDING }.sortedBy { it.startMs }
    val planned = recordings.filter { it.state == RecState.SCHEDULED }.sortedBy { it.startMs }
    val saved = recordings.filter { it.state == RecState.DONE }.sortedByDescending { it.startMs }
    val failed = recordings.filter { it.state == RecState.FAILED }.sortedByDescending { it.startMs }

    Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
        Text("Recordings", fontSize = if (compact) 26.sp else 32.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary, modifier = Modifier.padding(horizontal = edge))
        Text(
            "Saved on this device. They are deleted if you uninstall the app.",
            fontSize = 13.sp,
            color = Palette.TextSecondary,
            modifier = Modifier.padding(horizontal = edge, vertical = 4.dp),
        )
        if (recordings.isEmpty()) {
            Text(
                "Nothing recorded yet. Press Record while watching a channel, or choose Record on a show in Match Centre or the TV Guide.",
                fontSize = 16.sp,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(edge),
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = edge, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                group("Recording now", now, tick, dayFormat, compact, "Stop", onPlay = {}, onAction = onStop)
                group("Planned", planned, tick, dayFormat, compact, "Cancel", onPlay = {}, onAction = onCancel)
                group("Saved", saved, tick, dayFormat, compact, "Delete", onPlay = onPlay, onAction = onDelete, confirm = true)
                group("Couldn't record", failed, tick, dayFormat, compact, "Remove", onPlay = {}, onAction = onDelete)
            }
        }
    }
}

private fun LazyListScope.group(
    title: String,
    list: List<Recording>,
    tick: Long,
    dayFormat: SimpleDateFormat,
    compact: Boolean,
    actionLabel: String,
    onPlay: (Recording) -> Unit,
    onAction: (Recording) -> Unit,
    confirm: Boolean = false,
) {
    if (list.isEmpty()) return
    item(key = "h:$title") {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary, modifier = Modifier.padding(top = 8.dp))
    }
    items(list, key = { it.id }) { r ->
        var sure by remember(r.id) { mutableStateOf(false) }
        val size = if (r.state == RecState.RECORDING && r.filePath.isNotBlank()) File(r.filePath).length().also { tick } else r.sizeBytes
        val detail = buildString {
            append(r.channelName)
            append("  |  ")
            append(dayFormat.format(Date(r.startMs)))
            if (size > 0L) append("  |  ${sizeText(size)}")
            if (r.message.isNotBlank()) append("  |  ${r.message}")
        }
        Row(Modifier.fillMaxWidth().height(if (r.message.isNotBlank()) 110.dp else if (compact) 72.dp else 68.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusTile(
                onClick = { if (r.state == RecState.DONE) onPlay(r) },
                modifier = Modifier.weight(1f).fillMaxSize(),
                corner = 12.dp,
                focusedScale = 1.02f,
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.Center) {
                    Text(r.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(detail, fontSize = 12.sp, color = Palette.TextSecondary, maxLines = if (r.message.isNotBlank()) 4 else 1, overflow = TextOverflow.Ellipsis)
                }
            }
            FocusTile(
                onClick = {
                    // Deleting a saved recording takes two presses, so it can't happen by accident.
                    if (confirm && !sure) sure = true else onAction(r)
                },
                modifier = Modifier.width(if (compact) 84.dp else 110.dp).fillMaxSize(),
                corner = 12.dp,
                focusedScale = 1.05f,
                backgroundColor = if (sure) Color(0xFF8A1F2B) else Palette.Panel,
            ) {
                Text(
                    if (sure) "Sure?" else actionLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.TextPrimary,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}
