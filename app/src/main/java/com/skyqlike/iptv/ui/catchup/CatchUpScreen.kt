package com.skyqlike.iptv.ui.catchup

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.skyqlike.iptv.data.catchup.CatchUpState
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.data.model.Programme
import com.skyqlike.iptv.ui.home.FocusTile
import com.skyqlike.iptv.ui.providers.ActionButton
import com.skyqlike.iptv.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Catch Up: pick a channel, pick a day, pick a show that has already aired, and watch it. */
@Composable
fun CatchUpScreen(
    channels: List<Channel>,
    state: CatchUpState,
    message: String?,
    onNeedData: () -> Unit,
    onPlay: (Channel, Programme) -> Unit,
) {
    LaunchedEffect(state) {
        if (state is CatchUpState.Idle) onNeedData()
    }

    Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
        Text(
            "Catch Up",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.TextPrimary,
            modifier = Modifier.padding(start = 36.dp, bottom = 12.dp),
        )
        when (state) {
            is CatchUpState.Idle, is CatchUpState.Loading -> Note("Loading catch-up shows...")
            is CatchUpState.Error -> Column(Modifier.padding(horizontal = 36.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.message, fontSize = 17.sp, color = Palette.TextSecondary)
                ActionButton("Try again", Modifier.width(150.dp)) { onNeedData() }
            }
            is CatchUpState.Ready -> {
                val replayable = channels.filter { it.catchupDays > 0 && it.epgId?.let { id -> state.shows[id] }?.isNotEmpty() == true }
                if (replayable.isEmpty()) {
                    Note("No catch-up shows found.")
                } else {
                    Browser(replayable, state.shows, message, onPlay)
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 36.dp)) {
        Text(text, fontSize = 17.sp, color = Palette.TextSecondary)
    }
}

@Composable
private fun Browser(
    channels: List<Channel>,
    shows: Map<String, List<Programme>>,
    message: String?,
    onPlay: (Channel, Programme) -> Unit,
) {
    var selectedId by remember { mutableStateOf(channels.first().id) }
    val channel = channels.firstOrNull { it.id == selectedId } ?: channels.first()
    var dayIndex by remember(channel.id) { mutableIntStateOf(0) }

    val all = channel.epgId?.let { id -> shows[id] }.orEmpty()
    val days = remember(all) { all.map { dayStart(it.start) }.distinct().sortedDescending() }
    val day = days.getOrNull(dayIndex) ?: days.firstOrNull()
    val list = remember(all, day) {
        all.filter { dayStart(it.start) == day }.sortedByDescending { it.start }
    }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.width(250.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = 36.dp, end = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(channels, key = { it.id }) { ch ->
                FocusTile(
                    onClick = { selectedId = ch.id },
                    onFocus = { selectedId = ch.id },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    corner = 10.dp,
                    focusedScale = 1.03f,
                    backgroundColor = if (ch.id == channel.id) Color0x26 else Palette.Panel,
                ) {
                    Text(
                        ch.name,
                        fontSize = 16.sp,
                        fontWeight = if (ch.id == channel.id) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 12.dp),
                    )
                }
            }
        }

        Column(Modifier.weight(1f).fillMaxSize()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(end = 24.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                days.forEachIndexed { i, d ->
                    ActionButton(dayLabel(d), Modifier.width(140.dp), accent = d == day) { dayIndex = i }
                }
            }
            if (message != null) {
                Text(message, fontSize = 15.sp, color = Palette.Accent, modifier = Modifier.padding(bottom = 8.dp, end = 24.dp))
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(end = 36.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list) { show ->
                    FocusTile(
                        onClick = { onPlay(channel, show) },
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                        corner = 10.dp,
                        focusedScale = 1.02f,
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(
                                timeFormat.format(Date(show.start)),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Palette.Accent,
                                modifier = Modifier.width(84.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    show.title,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Palette.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (show.description.isNotBlank()) {
                                    Text(
                                        show.description,
                                        fontSize = 13.sp,
                                        color = Palette.TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Text(
                                "${((show.stop - show.start) / 60_000L).toInt()} min",
                                fontSize = 14.sp,
                                color = Palette.TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val Color0x26 = androidx.compose.ui.graphics.Color(0x26FFFFFF)

/** Midnight (in the phone's time zone) at the start of the day this time falls in. */
private fun dayStart(ms: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ms
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun dayLabel(dayMs: Long): String {
    val today = dayStart(System.currentTimeMillis())
    return when (dayMs) {
        today -> "Today"
        dayStart(today - 1) -> "Yesterday"
        else -> SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(dayMs))
    }
}
