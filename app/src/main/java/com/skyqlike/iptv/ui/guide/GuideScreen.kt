package com.skyqlike.iptv.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.epg.EpgTime
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.data.model.Programme
import com.skyqlike.iptv.data.model.favKey
import com.skyqlike.iptv.ui.common.Artwork
import com.skyqlike.iptv.ui.home.FocusTile
import com.skyqlike.iptv.ui.theme.Palette
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import com.skyqlike.iptv.ui.record.recordProgramme
import java.util.Locale

// How wide the timeline is: every minute of TV is 6dp wide, so 30 minutes is 180dp.
private val DpPerMinute = 6.dp
private val ChannelColumnWidth = 256.dp
private val RowHeight = 72.dp

private data class Selection(val channel: Channel, val programme: Programme?)

@Composable
fun GuideScreen(
    channels: List<Channel>,
    epg: Map<String, List<Programme>>,
    status: String,
    onPlay: (Channel) -> Unit,
    previewEnabled: Boolean = true,
    favouriteKeys: Set<String> = emptySet(),
    recentKeys: List<String> = emptyList(),
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

    // Channel filters: All, Favourites, Recently watched, the usual categories, then the provider's own groups.
    val filters = remember(channels, favouriteKeys, recentKeys) { buildGuideFilters(channels, favouriteKeys, recentKeys) }
    var filterIndex by remember { mutableIntStateOf(0) }
    val activeFilter = filters[filterIndex.coerceIn(0, filters.lastIndex)]
    val visible = remember(channels, activeFilter) { channels.filter(activeFilter.matches) }
    LaunchedEffect(filterIndex) { selected = null }

    // Channel numbers are the same ones you type on the remote while watching.
    val numbers = remember(channels) { channels.withIndex().associate { it.value.id to it.index + 1 } }

    if (epg.isEmpty()) {
        NoGuide(status)
    } else {
        // Until the user moves, show the first channel's current programme.
        val shown = selected ?: visible.firstOrNull()?.let { ch ->
            val list = ch.epgId?.let { epg[it] }
            Selection(ch, list?.firstOrNull { nowMs >= it.start && nowMs < it.stop })
        }
        val totalWidth = DpPerMinute * (EpgTime.GUIDE_HOURS * 60)

        Column(Modifier.fillMaxSize()) {
            InfoPanel(shown, nowMs, timeFormat, onPlay, previewEnabled)
            CategoryBar(filters.map { it.label }, filterIndex.coerceIn(0, filters.lastIndex)) { filterIndex = it }

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
                items(visible, key = { it.id }) { channel ->
                    val list = channel.epgId?.let { epg[it] }
                    Row(Modifier.fillMaxWidth().height(RowHeight)) {
                        Box(
                            Modifier
                                .width(ChannelColumnWidth)
                                .fillMaxHeight()
                                .background(Palette.Rail)
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "${numbers[channel.id] ?: ""}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Palette.Accent,
                                    modifier = Modifier.width(34.dp),
                                )
                                Artwork(
                                    url = channel.logoUrl,
                                    seed = channel.name,
                                    modifier = Modifier.width(52.dp).height(36.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Fit,
                                )
                                Text(
                                    channel.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Palette.TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
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
                                            isSelected = selected?.channel == channel && selected?.programme == p,
                                            onSelect = { selected = it },
                                            onWatch = { onPlay(channel) },
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

/** Colour for the edge of a programme box, by what kind of programme it is. */
private fun genreColor(genre: String): Color {
    val g = genre.lowercase()
    return when {
        g.contains("sport") || g.contains("football") || g.contains("soccer") || g.contains("racing") -> Color(0xFF22C55E)
        g.contains("movie") || g.contains("film") || g.contains("cinema") -> Color(0xFFF59E0B)
        g.contains("kid") || g.contains("child") || g.contains("cartoon") || g.contains("animation") -> Color(0xFF38BDF8)
        g.contains("news") || g.contains("current") || g.contains("weather") -> Color(0xFFE23744)
        g.contains("document") || g.contains("factual") || g.contains("nature") || g.contains("science") || g.contains("history") -> Color(0xFF14B8A6)
        g.contains("drama") || g.contains("series") || g.contains("soap") -> Color(0xFFA855F7)
        g.contains("music") || g.contains("concert") -> Color(0xFFEC4899)
        else -> Color(0xFF6B7280)
    }
}

@Composable
private fun ProgrammeCell(
    channel: Channel,
    programme: Programme,
    gridStart: Long,
    nowMs: Long,
    timeFormat: SimpleDateFormat,
    isSelected: Boolean,
    onSelect: (Selection) -> Unit,
    onWatch: () -> Unit,
) {
    val startMin = ((programme.start - gridStart) / 60_000f).coerceAtLeast(0f)
    val endMin = ((programme.stop - gridStart) / 60_000f).coerceAtMost(EpgTime.GUIDE_HOURS * 60f)
    if (endMin - startMin < 5f) return

    val isNow = nowMs >= programme.start && nowMs < programme.stop
    val progress = ((nowMs - programme.start).toFloat() / (programme.stop - programme.start)).coerceIn(0f, 1f)
    val select = { onSelect(Selection(channel, programme)) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // First press picks the programme (the remote does this just by landing on it).
    // Pressing OK again, or tapping it a second time, starts watching.
    FocusTile(
        onClick = { if (isSelected) onWatch() else select() },
        onFocus = select,
        onLongClick = { recordProgramme(context, channel, programme) },
        modifier = Modifier
            .offset(x = DpPerMinute * startMin)
            .width((DpPerMinute * (endMin - startMin) - 4.dp).coerceAtLeast(16.dp))
            .fillMaxHeight()
            .padding(vertical = 3.dp),
        corner = 8.dp,
        focusedScale = 1.03f,
        backgroundColor = if (isNow) Color(0xFF26304D) else Palette.Panel,
    ) {
        // A coloured edge shows the kind of programme at a glance.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(5.dp)
                .background(genreColor(programme.genre))
        )
        Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)) {
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
    previewEnabled: Boolean,
) {
    val p = sel?.programme
    Row(
        Modifier.fillMaxWidth().height(150.dp).background(Palette.Panel).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // A small live picture of the channel the guide is pointing at.
        GuidePreview(
            channel = sel?.channel,
            enabled = previewEnabled,
            onOpen = { sel?.channel?.let(onPlay) },
            modifier = Modifier.width(224.dp).fillMaxHeight(),
        )
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
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (nowMs >= p.start && nowMs < p.stop) {
                    Text(
                        "On now  |  Press OK to watch  |  Hold OK to record",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Palette.Accent,
                    )
                } else {
                    Text(
                        if (nowMs < p.start) "Starts at ${timeFormat.format(Date(p.start))}  |  Hold OK to record" else "Finished",
                        fontSize = 14.sp,
                        color = Palette.TextSecondary,
                    )
                }
            }
        }
    }
}

internal class GuideFilter(val label: String, val matches: (Channel) -> Boolean)

/** The usual TV categories, matched against each channel's group name from the provider. */
private val QuickCategories = listOf(
    "Entertainment" to listOf("entertainment", "general"),
    "Movies" to listOf("movie", "cinema", "film", "premiere"),
    "Sports" to listOf("sport"),
    "News" to listOf("news", "business", "weather"),
    "Documentary" to listOf("documentar", "factual", "nature", "science", "history"),
    "Kids" to listOf("kid", "child", "cartoon", "family"),
    "Lifestyle" to listOf("lifestyle", "food", "cook", "travel", "fashion", "home"),
    "Music" to listOf("music", "radio", "mtv", "concert"),
    "International" to listOf("international", "world", "foreign", "latin", "arabic", "asia", "europe", "africa", "india"),
)

/** Builds the filter buttons. Ones that would show no channels are left out. */
internal fun buildGuideFilters(
    channels: List<Channel>,
    favouriteKeys: Set<String>,
    recentKeys: List<String>,
): List<GuideFilter> {
    val out = ArrayList<GuideFilter>()
    out += GuideFilter("All channels") { true }
    if (channels.any { it.favKey in favouriteKeys }) {
        out += GuideFilter("Favourites") { it.favKey in favouriteKeys }
    }
    val recent = recentKeys.toHashSet()
    if (channels.any { it.favKey in recent }) {
        out += GuideFilter("Recently watched") { it.favKey in recent }
    }
    for ((label, words) in QuickCategories) {
        val filter = GuideFilter(label) { ch -> words.any { ch.group.contains(it, ignoreCase = true) } }
        if (channels.any(filter.matches)) out += filter
    }
    // Then the provider's own groups, exactly as they named them.
    channels.map { it.group.ifBlank { "Other" } }.distinct().forEach { group ->
        out += GuideFilter(group) { it.group.ifBlank { "Other" } == group }
    }
    return out
}

/** A scrolling row of filter buttons. Pick one to show only those channels in the guide. */
@Composable
internal fun CategoryBar(labels: List<String>, selectedIndex: Int, onPick: (Int) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().background(Palette.Rail),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(labels) { index, label ->
            val on = index == selectedIndex
            FocusTile(
                onClick = { onPick(index) },
                modifier = Modifier.width((label.length * 9 + 36).dp).height(34.dp),
                corner = 17.dp,
                focusedScale = 1.05f,
                backgroundColor = if (on) Palette.Accent else Color(0x26FFFFFF),
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 8.dp),
                )
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
