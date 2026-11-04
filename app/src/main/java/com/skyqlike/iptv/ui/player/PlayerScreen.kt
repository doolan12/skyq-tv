@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.skyqlike.iptv.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.data.model.Programme
import com.skyqlike.iptv.data.model.favKey
import com.skyqlike.iptv.ui.home.FocusTile
import com.skyqlike.iptv.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_RETRIES = 5
private val ResizeModes = intArrayOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT,
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    AspectRatioFrameLayout.RESIZE_MODE_FILL,
)
private val ResizeLabels = listOf("Fit", "Zoom", "Stretch")

private data class TrackOption(val label: String, val group: Tracks.Group, val index: Int, val selected: Boolean)
private data class PickerRow(val label: String, val selected: Boolean, val onPick: () -> Unit)

/**
 * The full-screen TV player.
 * Remote: Up/Down change channel, Left/Right skip 10 seconds, OK shows the controls, Back leaves.
 * Touch: tap the picture to show or hide the controls.
 */
@Composable
fun PlayerScreen(
    channels: List<Channel>,
    startIndex: Int,
    epg: Map<String, List<Programme>>,
    favouriteKeys: Set<String>,
    onToggleFavourite: (Channel) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, channels.lastIndex)) }
    var previousIndex by remember { mutableIntStateOf(-1) }
    var overlay by remember { mutableStateOf(true) }
    var overlayTick by remember { mutableIntStateOf(0) }
    var buffering by remember { mutableStateOf(true) }
    var wantPlay by remember { mutableStateOf(true) }
    var seekable by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var resizeIndex by remember { mutableIntStateOf(0) }
    var picker by remember { mutableStateOf<Int?>(null) } // C.TRACK_TYPE_AUDIO or C.TRACK_TYPE_TEXT
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val attempts = remember { intArrayOf(0) }
    val token = remember { intArrayOf(0) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    val exo = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android) SkyQLikeIPTV/0.1")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(http))
            .build()
    }

    // Listens to the player: buffering, errors (with automatic reconnecting), audio/subtitle tracks.
    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    attempts[0] = 0
                    failed = false
                    statusMessage = null
                }
                seekable = exo.isCurrentMediaItemSeekable
                live = exo.isCurrentMediaItemLive
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                seekable = exo.isCurrentMediaItemSeekable
                live = exo.isCurrentMediaItemLive
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                wantPlay = playWhenReady
            }

            override fun onTracksChanged(t: Tracks) {
                tracks = t
            }

            override fun onPlayerError(error: PlaybackException) {
                if (attempts[0] < MAX_RETRIES) {
                    attempts[0]++
                    val myToken = token[0]
                    val n = attempts[0]
                    statusMessage = "Connection lost. Reconnecting ($n of $MAX_RETRIES)..."
                    buffering = true
                    scope.launch {
                        delay(2000L * n)
                        if (myToken == token[0]) {
                            exo.seekToDefaultPosition()
                            exo.prepare()
                            exo.play()
                        }
                    }
                } else {
                    failed = true
                    buffering = false
                    statusMessage = "This channel isn't playing. The stream may be offline."
                    overlay = true
                }
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    // Pause when the app goes to the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) exo.pause()
            if (event == Lifecycle.Event.ON_START) exo.play()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Start (or switch to) the chosen channel.
    LaunchedEffect(index) {
        val ch = channels[index]
        token[0]++
        attempts[0] = 0
        failed = false
        statusMessage = null
        buffering = true
        exo.setMediaItem(MediaItem.fromUri(ch.streamUrl))
        exo.prepare()
        exo.play()
        overlay = true
        overlayTick++
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            nowMs = System.currentTimeMillis()
        }
    }

    // Controls hide by themselves after a few seconds.
    LaunchedEffect(overlay, overlayTick, picker) {
        if (overlay && picker == null && !failed) {
            delay(7_000)
            overlay = false
        }
    }

    val rootFocus = remember { FocusRequester() }
    val firstButtonFocus = remember { FocusRequester() }
    val pickerFocus = remember { FocusRequester() }

    LaunchedEffect(overlay, picker) {
        runCatching {
            when {
                picker != null -> pickerFocus.requestFocus()
                overlay -> firstButtonFocus.requestFocus()
                else -> rootFocus.requestFocus()
            }
        }
    }

    BackHandler {
        if (picker != null) picker = null else onClose()
    }

    val channel = channels[index]
    val listForChannel = channel.epgId?.let { epg[it] }
    val currentProg = listForChannel?.firstOrNull { nowMs >= it.start && nowMs < it.stop }
    val nextProg = listForChannel?.firstOrNull { it.start >= (currentProg?.stop ?: nowMs) }
    val isFavourite = channel.favKey in favouriteKeys

    fun bump() {
        overlayTick++
    }

    fun zap(delta: Int) {
        previousIndex = index
        index = (index + delta + channels.size) % channels.size
    }

    fun lastChannel() {
        if (previousIndex >= 0 && previousIndex != index) {
            val target = previousIndex
            previousIndex = index
            index = target
        }
    }

    fun seekBy(ms: Long): Boolean {
        if (!exo.isCurrentMediaItemSeekable) return false
        exo.seekTo((exo.currentPosition + ms).coerceAtLeast(0L))
        return true
    }

    fun togglePlay() {
        if (exo.playWhenReady) exo.pause() else exo.play()
    }

    val handleKey: (KeyEvent) -> Boolean = handle@{ ev ->
        if (ev.type != KeyEventType.KeyDown) return@handle false
        bump()
        when (ev.key) {
            Key.MediaPlayPause -> { togglePlay(); return@handle true }
            Key.MediaPlay -> { exo.play(); return@handle true }
            Key.MediaPause -> { exo.pause(); return@handle true }
            Key.MediaFastForward -> { seekBy(30_000); return@handle true }
            Key.MediaRewind -> { seekBy(-30_000); return@handle true }
            Key.ChannelUp -> { zap(1); return@handle true }
            Key.ChannelDown -> { zap(-1); return@handle true }
            else -> Unit
        }
        if (!overlay && picker == null) {
            when (ev.key) {
                Key.DirectionUp -> { zap(1); true }
                Key.DirectionDown -> { zap(-1); true }
                Key.DirectionLeft -> seekBy(-10_000)
                Key.DirectionRight -> seekBy(10_000)
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { overlay = true; true }
                else -> false
            }
        } else {
            false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent(handleKey)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = exo
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    keepScreenOn = true
                }
            },
            update = { it.resizeMode = ResizeModes[resizeIndex] },
            modifier = Modifier.fillMaxSize(),
        )

        // Invisible layer on top of the picture: a tap shows or hides the controls.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        overlay = !overlay
                        overlayTick++
                    })
                }
        )

        // Buffering / reconnecting / error message in the middle.
        if (buffering || statusMessage != null) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    statusMessage ?: "Buffering...",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color(0xAA000000), RoundedCornerShape(12.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
                if (failed) {
                    PlayerButton("Try again", Modifier.width(150.dp), accent = true) {
                        attempts[0] = 0
                        failed = false
                        statusMessage = null
                        buffering = true
                        exo.seekToDefaultPosition()
                        exo.prepare()
                        exo.play()
                    }
                }
            }
        }

        // Control bar along the bottom.
        if (overlay && picker == null) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF2000000))))
                    .padding(horizontal = 28.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "${index + 1}  ${channel.name}",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Palette.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (live) {
                        Text(
                            "LIVE",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier
                                .background(Color(0xFFD32F2F), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                if (currentProg != null) {
                    Text(
                        "Now: ${currentProg.title}  (${timeFormat.format(Date(currentProg.start))} - ${timeFormat.format(Date(currentProg.stop))})",
                        fontSize = 16.sp,
                        color = Palette.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val frac = ((nowMs - currentProg.start).toFloat() / (currentProg.stop - currentProg.start)).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(4.dp).background(Color(0x33FFFFFF))) {
                        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Palette.Accent))
                    }
                } else {
                    Text("No programme information", fontSize = 15.sp, color = Palette.TextSecondary)
                }
                if (nextProg != null) {
                    Text(
                        "Next: ${nextProg.title}  at ${timeFormat.format(Date(nextProg.start))}",
                        fontSize = 15.sp,
                        color = Palette.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PlayerButton(
                        if (wantPlay) "Pause" else "Play",
                        Modifier.width(110.dp).focusRequester(firstButtonFocus),
                        accent = true,
                    ) { bump(); togglePlay() }
                    if (seekable) {
                        PlayerButton("-10s", Modifier.width(80.dp)) { bump(); seekBy(-10_000) }
                        PlayerButton("+10s", Modifier.width(80.dp)) { bump(); seekBy(10_000) }
                    }
                    PlayerButton("Prev ch", Modifier.width(110.dp)) { zap(-1) }
                    PlayerButton("Next ch", Modifier.width(110.dp)) { zap(1) }
                    if (previousIndex >= 0 && previousIndex != index) {
                        PlayerButton("Last ch", Modifier.width(110.dp)) { lastChannel() }
                    }
                    PlayerButton(
                        if (isFavourite) "Remove fav" else "Add fav",
                        Modifier.width(130.dp),
                    ) { bump(); onToggleFavourite(channel) }
                    PlayerButton("Audio", Modifier.width(100.dp)) { picker = C.TRACK_TYPE_AUDIO }
                    PlayerButton("Subtitles", Modifier.width(120.dp)) { picker = C.TRACK_TYPE_TEXT }
                    PlayerButton("Aspect: ${ResizeLabels[resizeIndex]}", Modifier.width(160.dp)) {
                        bump()
                        resizeIndex = (resizeIndex + 1) % ResizeModes.size
                    }
                }
            }
        }

        // Audio / subtitle chooser on the right.
        picker?.let { type ->
            val options = trackOptions(tracks, type)
            val rows = ArrayList<PickerRow>()
            if (type == C.TRACK_TYPE_TEXT) {
                rows += PickerRow("Off", options.none { it.selected }) {
                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                    picker = null
                }
            }
            options.forEach { opt ->
                rows += PickerRow(opt.label, opt.selected) {
                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(opt.group.type, false)
                        .setOverrideForType(TrackSelectionOverride(opt.group.mediaTrackGroup, opt.index))
                        .build()
                    picker = null
                }
            }
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(24.dp)
                    .width(340.dp)
                    .background(Color(0xE6101014), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (type == C.TRACK_TYPE_AUDIO) "Audio" else "Subtitles",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Palette.TextPrimary,
                )
                if (rows.isEmpty()) {
                    Text("This channel has no other audio tracks.", fontSize = 15.sp, color = Palette.TextSecondary)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(rows) { i, row ->
                            PlayerButton(
                                (if (row.selected) "✓  " else "") + row.label,
                                Modifier
                                    .fillMaxWidth()
                                    .then(if (i == 0) Modifier.focusRequester(pickerFocus) else Modifier),
                                accent = row.selected,
                            ) { row.onPick() }
                        }
                    }
                }
                if (type == C.TRACK_TYPE_TEXT && options.isEmpty()) {
                    Text("This channel has no subtitles.", fontSize = 14.sp, color = Palette.TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun PlayerButton(
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    FocusTile(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        corner = 21.dp,
        focusedScale = 1.06f,
        backgroundColor = if (accent) Palette.Accent else Color(0x33FFFFFF),
    ) {
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 10.dp),
        )
    }
}

private fun trackOptions(tracks: Tracks, type: Int): List<TrackOption> {
    val out = ArrayList<TrackOption>()
    var n = 1
    for (group in tracks.groups) {
        if (group.type != type || !group.isSupported) continue
        for (i in 0 until group.length) {
            out += TrackOption(trackLabel(group.getTrackFormat(i), n), group, i, group.isTrackSelected(i))
            n++
        }
    }
    return out
}

private fun trackLabel(format: Format, number: Int): String {
    val language = format.language
        ?.takeIf { it != "und" }
        ?.let { Locale.forLanguageTag(it).displayLanguage }
        ?.takeIf { it.isNotBlank() }
    return format.label ?: language ?: "Track $number"
}
