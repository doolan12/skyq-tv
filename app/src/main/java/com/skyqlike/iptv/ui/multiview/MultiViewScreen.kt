@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.skyqlike.iptv.ui.multiview

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.epg.nowOn
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.data.model.Programme
import com.skyqlike.iptv.data.model.favKey
import com.skyqlike.iptv.ui.common.Artwork
import com.skyqlike.iptv.ui.guide.CategoryBar
import com.skyqlike.iptv.ui.guide.buildGuideFilters
import com.skyqlike.iptv.ui.home.FocusTile
import com.skyqlike.iptv.ui.home.ImmersiveEffect
import com.skyqlike.iptv.ui.providers.LabeledField
import com.skyqlike.iptv.ui.theme.Palette

private const val MAX_RETRIES = 3

/** One of the two pictures: its own video player, with a note of whether it is loading or has failed. */
private class Pane(val exo: ExoPlayer) {
    var buffering by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    private var retries = 0
    private val handler = Handler(Looper.getMainLooper())

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            buffering = state == Player.STATE_BUFFERING
            if (state == Player.STATE_READY) {
                retries = 0
                error = null
            }
        }

        override fun onPlayerError(e: PlaybackException) {
            if (retries < MAX_RETRIES) {
                retries++
                buffering = true
                handler.postDelayed({
                    exo.seekToDefaultPosition()
                    exo.prepare()
                    exo.play()
                }, 2000L * retries)
            } else {
                buffering = false
                error = "This channel won't play right now."
            }
        }
    }

    init {
        exo.addListener(listener)
    }

    fun play(url: String) {
        handler.removeCallbacksAndMessages(null)
        retries = 0
        error = null
        buffering = true
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        exo.playWhenReady = true
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        exo.removeListener(listener)
        exo.release()
    }
}

@Composable
private fun rememberPane(): Pane {
    val context = LocalContext.current
    val pane = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android) SkyQLikeIPTV/0.1")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
        val exo = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(http))
            .build()
        // Two pictures at once is a lot of work for a TV box, so each one is held to HD.
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon().setMaxVideoSize(1280, 720).build()
        Pane(exo)
    }
    DisposableEffect(pane) { onDispose { pane.release() } }
    return pane
}

/**
 * Two live channels on screen at once, for watching two matches together.
 * Sound comes from one picture at a time (the one with the blue outline).
 *
 * Remote: Left / Right moves the sound between the pictures, OK changes the channel in the
 * highlighted picture, Up / Down flips to the next or previous channel in it. Back leaves.
 * Touch: tap the other picture to move the sound there, tap the sound picture to change its channel.
 */
@Composable
fun MultiViewScreen(
    channels: List<Channel>,
    epg: Map<String, List<Programme>>,
    favouriteKeys: Set<String>,
    recentKeys: List<String>,
) {
    ImmersiveEffect(true)
    val left = rememberPane()
    val right = rememberPane()
    val panes = listOf(left, right)

    var slots by remember { mutableStateOf<List<Channel?>>(listOf(null, null)) }
    var active by remember { mutableIntStateOf(0) }
    // Which picture the picker is choosing for, or -1 when it is closed. Starts by choosing the first.
    var picking by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }

    // A channel was chosen or changed for a slot: start it.
    LaunchedEffect(slots[0]?.id) { slots[0]?.let { left.play(it.streamUrl) } }
    LaunchedEffect(slots[1]?.id) { slots[1]?.let { right.play(it.streamUrl) } }
    // Only the highlighted picture has sound.
    // (Both are silent while the channel picker is open.)
    LaunchedEffect(active, picking) {
        left.exo.volume = if (picking < 0 && active == 0) 1f else 0f
        right.exo.volume = if (picking < 0 && active == 1) 1f else 0f
    }
    LaunchedEffect(picking) { if (picking < 0) runCatching { focus.requestFocus() } }

    fun choose(slot: Int, channel: Channel) {
        slots = slots.toMutableList().also { it[slot] = channel }
        active = slot
        // After the first one, go straight on to choosing the second.
        picking = if (slot == 0 && slots[1] == null) 1 else -1
    }

    fun flip(delta: Int) {
        val current = slots[active] ?: return
        if (channels.size < 2) return
        val i = channels.indexOfFirst { it.id == current.id }
        if (i < 0) return
        choose(active, channels[(i + delta + channels.size) % channels.size])
    }

    // Back closes the picker once both pictures are chosen. Otherwise Back leaves Multi-view.
    BackHandler(enabled = picking >= 0 && slots[0] != null && slots[1] != null) { picking = -1 }

    if (picking >= 0) {
        ChannelPicker(
            title = if (picking == 0) "Choose the first channel" else "Choose the second channel",
            channels = channels,
            favouriteKeys = favouriteKeys,
            recentKeys = recentKeys,
            onPick = { choose(picking, it) },
        )
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { active = 0; true }
                    Key.DirectionRight -> { active = 1; true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { picking = active; true }
                    Key.DirectionUp, Key.ChannelUp -> { flip(1); true }
                    Key.DirectionDown, Key.ChannelDown -> { flip(-1); true }
                    else -> false
                }
            }
    ) {
        Row(Modifier.fillMaxSize().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0..1) {
                PaneView(
                    pane = panes[i],
                    channel = slots[i],
                    now = slots[i]?.let { epg.nowOn(it, System.currentTimeMillis()) },
                    hasSound = active == i,
                    onTap = { if (active == i) picking = i else active = i },
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun PaneView(
    pane: Pane,
    channel: Channel?,
    now: Programme?,
    hasSound: Boolean,
    onTap: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF05060F))
            .border(if (hasSound) 3.dp else 1.dp, if (hasSound) Palette.Accent else Color(0x33FFFFFF), RoundedCornerShape(10.dp))
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = pane.exo
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    keepScreenOn = true
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) })

        val note = pane.error ?: if (pane.buffering) "Loading..." else null
        if (note != null) {
            Text(
                note,
                fontSize = 16.sp,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).background(Color(0xAA000000), RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        // Name bar along the bottom of each picture.
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0xB3000000)).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(channel?.name ?: "", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (now != null) Text(now.title, fontSize = 12.sp, color = Color(0xCCFFFFFF), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                if (hasSound) "SOUND ON" else "MUTED",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (hasSound) Palette.Accent else Color(0x99FFFFFF),
            )
        }
    }
}

/** A simple list of channels to choose from: category buttons, a search box, favourites first. */
@Composable
private fun ChannelPicker(
    title: String,
    channels: List<Channel>,
    favouriteKeys: Set<String>,
    recentKeys: List<String>,
    onPick: (Channel) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filters = remember(channels, favouriteKeys, recentKeys) { buildGuideFilters(channels, favouriteKeys, recentKeys) }
    var filterIndex by remember { mutableIntStateOf(0) }
    val active = filters[filterIndex.coerceIn(0, filters.lastIndex)]
    val q = query.trim()
    val shown = remember(channels, active, q, favouriteKeys) {
        channels.filter(active.matches)
            .filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
            .sortedByDescending { it.favKey in favouriteKeys }
            .take(120)
    }
    val firstRow = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstRow.requestFocus() } }

    Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0D)).padding(top = 20.dp)) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary, modifier = Modifier.padding(horizontal = 36.dp))
        Box(Modifier.padding(horizontal = 36.dp, vertical = 10.dp)) { LabeledField("Search channels", query) { query = it } }
        CategoryBar(filters.map { it.label }, filterIndex.coerceIn(0, filters.lastIndex)) { filterIndex = it }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 36.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown, key = { it.id }) { ch ->
                val isFirst = ch.id == shown.first().id
                FocusTile(
                    onClick = { onPick(ch) },
                    modifier = Modifier.fillMaxWidth().height(60.dp).then(if (isFirst) Modifier.focusRequester(firstRow) else Modifier),
                    corner = 12.dp,
                    focusedScale = 1.02f,
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Artwork(ch.logoUrl, ch.name, Modifier.width(64.dp).height(44.dp).clip(RoundedCornerShape(8.dp)), ContentScale.Fit)
                        Text(ch.name, fontSize = 17.sp, color = Palette.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (ch.favKey in favouriteKeys) Text("Favourite", fontSize = 12.sp, color = Palette.Accent)
                    }
                }
            }
        }
    }
}
