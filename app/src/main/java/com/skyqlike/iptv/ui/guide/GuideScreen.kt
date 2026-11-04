package com.skyqlike.iptv.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.skyqlike.iptv.data.epg.EpgTime
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.data.model.Programme
import com.skyqlike.iptv.ui.home.FocusTile
import com.skyqlike.iptv.ui.home.tileBrush
import com.skyqlike.iptv.ui.theme.Palette
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// How wide the timeline is: every minute of TV is 6dp wide, so 30 minutes is 180dp.
private val DpPerMinute = 6.dp
private val ChannelColumnWidth = 190.dp
private val RowHeight = 68.dp

private data class Selection(val channel: Channel, val programme: Programme?)

@Composable
fun GuideScreen(
    channels: List<Channel>,
    epg: Map<String, List<Programme>>,
    status: String,
    onPlay: (Channel) -> Unit,
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMs = System.currentTimeMillis()
        }
    }

    val gridStart = remember { EpgTime.guideStart(System.currentTimeMillis()) }
    val scroll = rememberScrollState()
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    var selected by remember { mutableStateOf<Selection?>(null) }

    if (epg.isEmpty()) {
        NoGuide(status)
    } else {
        // Until the user moves, show the first channel's current programme.
        val shown = selected ?: channels.firstOrNull()?.let { ch ->
            val list = ch.epgId?.let { epg[it] }
            Selection(ch, list?.firstOrNull { nowMs >= it.start && nowMs < it.stop })
        }
        val totalWidth = DpPerMinute * (EpgTime.GUIDE_HOURS * 60)

        Column(Modifier.fillMaxSize()) {
            InfoPanel(shown, nowMs, timeFormat, onPlay)

            // Time labels along the top, scrolling together with the rows below.
            Row(Modifier.fillMaxWidth().height(30.dp).background(Palette.Rail)) {
                Spacer(Modifier.width(ChannelColumnWidth))
                Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
                    Box(Modifier.width(totalWidth).fillMaxHeight()) {
                        for (i in 0 until EpgTime.GUIDE_HOURS * 2) {
                            val label = timeFormat.format(Date(gridStart + i * EpgTime.SLOT_MS))
                            Text(
                                label,
                                fontSize = 13.sp,
                                color = Palette.TextSecondary,
                                modifier = Modifier
                                    .offset(x = DpPerMinute * (i * 30f))
                                    .padding(start = 8.dp, top = 6.dp),
                            )
                        }
                    }
                }
            }

            LazyColumn(Modifier.fillMaxSize()) {
                items(channels, key = { it.id }) { channel ->
                    val list = channel.epgId?.let { epg[it] }
                    Row(Modifier.fillMaxWidth().height(RowHeight)) {
                        Box(
                            Modifier
                                .width(ChannelColumnWidth)
                                .fillMaxHeight()
                                .background(Palette.Rail)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                channel.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Palette.TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // All rows share one scroll position, so the whole grid moves together.
                        Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
                            Box(Modifier.width(totalWidth).fillMaxHeight()) {
                                if (list.isNullOrEmpty()) {
                                    Text(
                                        "No guide data for this channel",
                                        fontSize = 14.sp,
                                        color = Palette.TextSecondary,
                                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
                                    )
                                } else {
                                    list.forEach { p ->
                                        ProgrammeCell(
                                            channel = channel,
                                            programme = p,
                                            gridStart = gridStart,
                                            nowMs = nowMs,
                                            timeFormat = timeFormat,
                                            onSelect = { selected = it },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgrammeCell(
    channel: Channel,
    programme: Programme,
    gridStart: Long,
    nowMs: Long,
    timeFormat: SimpleDateFormat,
    onSelect: (Selection) -> Unit,
) {
    val startMin = ((programme.start - gridStart) / 60_000f).coerceAtLeast(0f)
    val endMin = ((programme.stop - gridStart) / 60_000f).coerceAtMost(EpgTime.GUIDE_HOURS * 60f)
    if (endMin - startMin < 5f) return

    val isNow = nowMs >= programme.start && nowMs < programme.stop
    val progress = ((nowMs - programme.start).toFloat() / (programme.stop - programme.start)).coerceIn(0f, 1f)
    val select = { onSelect(Selection(channel, programme)) }

    FocusTile(
        onClick = select,
        onFocus = select,
        modifier = Modifier
            .offset(x = DpPerMinute * startMin)
            .width((DpPerMinute * (endMin - startMin) - 4.dp).coerceAtLeast(16.dp))
            .fillMaxHeight()
            .padding(vertical = 3.dp),
        corner = 8.dp,
        focusedScale = 1.03f,
        backgroundColor = if (isNow) Color(0xFF26304D) else Palette.Panel,
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                programme.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Palette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                timeFormat.format(Date(programme.start)),
                fontSize = 12.sp,
                color = Palette.TextSecondary,
                maxLines = 1,
            )
        }
        // Blue bar along the bottom shows how far through the show we are.
        if (isNow) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .background(Palette.Accent)
            )
        }
    }
}

@Composable
private fun InfoPanel(
    sel: Selection?,
    nowMs: Long,
    timeFormat: SimpleDateFormat,
    onPlay: (Channel) -> Unit,
) {
    val p = sel?.programme
    Row(
        Modifier.fillMaxWidth().height(170.dp).background(Palette.Panel).padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            Modifier
                .width(240.dp)
                .fillMaxHeight()
                .background(tileBrush(p?.title ?: sel?.channel?.name ?: "guide")),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                (p?.title ?: sel?.channel?.name ?: "TV").take(1).uppercase(),
                fontSize = 54.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.35f),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (sel == null || p == null) {
                Text(
                    sel?.channel?.name ?: "TV Guide",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Palette.TextPrimary,
                )
                Text("No programme information for this channel.", fontSize = 15.sp, color = Palette.TextSecondary)
            } else {
                Text(
                    p.title,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Palette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val genre = if (p.genre.isBlank()) "" else "  |  ${p.genre}"
                Text(
                    "${sel.channel.name}  |  ${timeFormat.format(Date(p.start))} - ${timeFormat.format(Date(p.stop))}$genre",
                    fontSize = 15.sp,
                    color = Palette.Accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (p.description.isNotBlank()) {
                    Text(
                        p.description,
                        fontSize = 15.sp,
                        color = Palette.TextSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (nowMs >= p.start && nowMs < p.stop) {
                    FocusTile(
                        onClick = { onPlay(sel.channel) },
                        modifier = Modifier.width(170.dp).height(42.dp),
                        corner = 21.dp,
                        backgroundColor = Palette.Accent,
                    ) {
                        Text(
                            "Watch Now",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                } else {
                    Text(
                        if (nowMs < p.start) "Starts at ${timeFormat.format(Date(p.start))}" else "Finished",
                        fontSize = 14.sp,
                        color = Palette.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoGuide(status: String) {
    Box(Modifier.fillMaxSize().padding(36.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("TV Guide", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
            Text(
                if (status.isBlank()) "No TV guide is loaded yet." else status,
                fontSize = 16.sp,
                color = Palette.TextSecondary,
            )
            Text(
                "If your provider has a guide, add its EPG link in Settings > IPTV Providers.",
                fontSize = 14.sp,
                color = Palette.TextSecondary,
            )
        }
    }
}
