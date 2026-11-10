@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.skyqlike.iptv.ui.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import com.skyqlike.iptv.data.record.RecState
import com.skyqlike.iptv.data.record.Recorder
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.model.PlayItem
import com.skyqlike.iptv.data.model.PlayKind
import com.skyqlike.iptv.data.model.Programme
import com.skyqlike.iptv.ui.home.FocusTile
import com.skyqlike.iptv.ui.common.isPhoneLike
import com.skyqlike.iptv.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_RETRIES = 5

/** The sleep timer choices, in minutes. */
private val SleepChoicesMin = listOf(15, 30, 60, 90)

/** Finds the Activity that holds this screen. */
private fun Context.findActivity(): android.app.Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

/** The web status code (like 403 or 404) behind a playback error, or null if it wasn't a web error. */
private fun httpStatusOf(error: PlaybackException): Int? {
    var cause: Throwable? = error
    while (cause != null) {
        if (cause is HttpDataSource.InvalidResponseCodeException) return cause.responseCode
        cause = cause.cause
    }
    return null
}

/** Turns a playback error into a message a person can act on. */
private fun friendlyPlayerError(error: PlaybackException): String {
    when (httpStatusOf(error)) {
        401, 403 -> return "The provider refused this stream. Check your login, or your subscription may have expired."
        404 -> return "This channel isn't available right now. The provider may have removed it."
        429 -> return "Too many connections on this account. Close the provider on other devices and try again."
        in 500..599 -> return "The provider's server has a problem. Try again in a few minutes."
    }
    return when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Can't reach the stream. Check your internet connection."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->
            "This stream's format isn't supported, or the provider sent something that isn't video."
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
            "This device couldn't play this video format."
        else -> "This isn't playing. The stream may be offline."
    }
}
private val ResizeModes = intArrayOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT,
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    AspectRatioFrameLayout.RESIZE_MODE_FILL,
)
private val ResizeLabels = listOf("Fit", "Zoom", "Stretch")

private data class TrackOption(val label: String, val group: Tracks.Group, val index: Int, val selected: Boolean)
private data class PickerRow(val label: String, val selected: Boolean, val onPick: () -> Unit)

/**
 * The full-screen TV player. Plays live channels, movies, episodes and catch-up shows.
 * Remote: Up/Down change channel (live only), Left/Right skip 10 seconds, OK shows the controls, Back leaves.
 * Touch: tap the picture to show or hide the controls.
 */
@Composable
fun PlayerScreen(
    items: List<PlayItem>,
    startIndex: Int,
    epg: Map<String, List<Programme>>,
    favouriteKeys: Set<String>,
    startPositionFor: (PlayItem) -> Long,
    onToggleFavourite: (PlayItem) -> Unit,
    onProgress: (PlayItem, Long, Long) -> Unit,
    onChannelWatched: (PlayItem) -> Unit,
    onClose: () -> Unit,
    aspectDefault: Int = 0,
    autoReconnect: Boolean = true,
    subtitlesOn: Boolean = false,
    audioLang: String = "",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, items.lastIndex)) }
    var previousIndex by remember { mutableIntStateOf(-1) }
    var overlay by remember { mutableStateOf(true) }
    var overlayTick by remember { mutableIntStateOf(0) }
    var buffering by remember { mutableStateOf(true) }
    var wantPlay by remember { mutableStateOf(true) }
    var seekable by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var resizeIndex by remember { mutableIntStateOf(aspectDefault.coerceIn(0, ResizeModes.lastIndex)) }
    var picker by remember { mutableStateOf<Int?>(null) } // C.TRACK_TYPE_AUDIO or C.TRACK_TYPE_TEXT
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var endedTick by remember { mutableIntStateOf(0) }
    var sleepEndsAt by remember { mutableLongStateOf(0L) } // 0 = sleep timer off
    var sleepStep by remember { mutableIntStateOf(0) } // 0 = off, then 1..4 = the choices in SleepChoicesMin
    var inPip by remember { mutableStateOf(false) }
    var digits by remember { mutableStateOf("") } // channel number being typed on the remote
    var digitTick by remember { mutableIntStateOf(0) }
    // The strip of nearby channels (mini guide): which channels it holds, which one is highlighted.
    var miniOpen by remember { mutableStateOf(false) }
    var miniEntries by remember { mutableStateOf<List<Int>>(emptyList()) }
    var miniSel by remember { mutableIntStateOf(0) }
    var miniTick by remember { mutableIntStateOf(0) }

    val attempts = remember { intArrayOf(0) }
    val token = remember { intArrayOf(0) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    val exo = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android) SkyQLikeIPTV/0.1")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
        // Starts playing after about a second of picture instead of two and a half, so channel changes are quicker.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 1_000, 3_000)
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(androidx.media3.datasource.DefaultDataSource.Factory(context, http)))
            .setLoadControl(loadControl)
            .build()
            .also { player ->
                // Starting subtitle / audio choices from Settings.
                val builder = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesOn)
                if (audioLang.isNotBlank()) builder.setPreferredAudioLanguage(audioLang)
                player.trackSelectionParameters = builder.build()
            }
    }

    // Remembers how far through a movie or episode we are, so it can be resumed later.
    fun saveProgress(finished: Boolean = false) {
        val cur = items[index]
        if (cur.kind != PlayKind.MOVIE && cur.kind != PlayKind.EPISODE) return
        val dur = exo.duration
        if (dur == C.TIME_UNSET || dur <= 0L) return
        onProgress(cur, if (finished) dur else exo.currentPosition, dur)
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
                if (state == Player.STATE_ENDED) endedTick++
                seekable = exo.isCurrentMediaItemSeekable
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                seekable = exo.isCurrentMediaItemSeekable
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                wantPlay = playWhenReady
            }

            override fun onTracksChanged(t: Tracks) {
                tracks = t
            }

            override fun onPlayerError(error: PlaybackException) {
                // A refused login or a missing stream won't fix itself, so don't keep retrying those.
                val worthRetrying = httpStatusOf(error) !in setOf(401, 403, 404)
                if (autoReconnect && worthRetrying && attempts[0] < MAX_RETRIES) {
                    attempts[0]++
                    val myToken = token[0]
                    val n = attempts[0]
                    statusMessage = "Connection lost. Reconnecting ($n of $MAX_RETRIES)..."
                    buffering = true
                    scope.launch {
                        delay(2000L * n)
                        if (myToken == token[0]) {
                            if (items[index].kind == PlayKind.LIVE) exo.seekToDefaultPosition()
                            exo.prepare()
                            exo.play()
                        }
                    }
                } else {
                    failed = true
                    buffering = false
                    statusMessage = friendlyPlayerError(error)
                    overlay = true
                }
            }
        }
        exo.addListener(listener)
        onDispose {
            saveProgress()
            exo.removeListener(listener)
            exo.release()
        }
    }

    // Sleep timer: when the time is up, stop playing and leave the player.
    LaunchedEffect(sleepEndsAt) {
        if (sleepEndsAt > 0L) {
            val wait = sleepEndsAt - System.currentTimeMillis()
            if (wait > 0L) delay(wait)
            saveProgress()
            exo.pause()
            onClose()
        }
    }

    // Picture-in-picture: a small floating window, where the device supports it.
    val activity = remember(context) { context.findActivity() }
    // On a phone the picture is wide, so the player turns sideways while it is open, then lets go again.
    DisposableEffect(activity) {
        val phone = context.isPhoneLike()
        if (phone) activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            if (phone) activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    val pipSupported = remember(context) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }
    DisposableEffect(activity, pipSupported) {
        val pipActivity = activity as? ComponentActivity
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            inPip = info.isInPictureInPictureMode
            if (inPip) {
                overlay = false
                picker = null
            }
        }
        pipActivity?.addOnPictureInPictureModeChangedListener(listener)
        // On Android 12 and newer, pressing Home while watching shrinks the video into the window by itself.
        if (pipSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activity?.setPictureInPictureParams(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).setAutoEnterEnabled(true).build()
            )
        }
        onDispose {
            pipActivity?.removeOnPictureInPictureModeChangedListener(listener)
            if (pipSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                activity?.setPictureInPictureParams(
                    PictureInPictureParams.Builder().setAutoEnterEnabled(false).build()
                )
            }
        }
    }

    // Pause when the app goes to the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                saveProgress()
                exo.pause()
            }
            if (event == Lifecycle.Event.ON_START) exo.play()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Start (or switch to) the chosen item.
    LaunchedEffect(index) {
        val cur = items[index]
        token[0]++
        attempts[0] = 0
        failed = false
        statusMessage = null
        buffering = true
        positionMs = 0L
        durationMs = 0L
        val mediaItem = MediaItem.fromUri(cur.url)
        val resumeAt = if (cur.kind == PlayKind.MOVIE || cur.kind == PlayKind.EPISODE) startPositionFor(cur) else 0L
        if (resumeAt > 0L) exo.setMediaItem(mediaItem, resumeAt) else exo.setMediaItem(mediaItem)
        exo.prepare()
        exo.play()
        if (cur.kind == PlayKind.LIVE) onChannelWatched(cur)
        overlay = true
        overlayTick++
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            nowMs = System.currentTimeMillis()
        }
    }

    // Keeps the position bar moving, and saves progress every 10 seconds.
    LaunchedEffect(index) {
        var ticks = 0
        while (true) {
            delay(1000)
            ticks++
            if (items[index].kind != PlayKind.LIVE) {
                positionMs = exo.currentPosition
                durationMs = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
                if (ticks % 10 == 0) saveProgress()
            }
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
        if (miniOpen) miniOpen = false else if (picker != null) picker = null else onClose()
    }

    val item = items[index]
    // Whether the channel on screen is being recorded.
    var recActive by remember(item.url) { mutableStateOf(Recorder.activeFor(context, item.url) != null) }
    val isLive = item.kind == PlayKind.LIVE
    val listForChannel = if (isLive) item.epgId?.let { epg[it] } else null
    val currentProg = listForChannel?.firstOrNull { nowMs >= it.start && nowMs < it.stop }
    val nextProg = listForChannel?.firstOrNull { it.start >= (currentProg?.stop ?: nowMs) }
    val isFavourite = item.favKey != null && item.favKey in favouriteKeys

    fun bump() {
        overlayTick++
    }

    fun step(delta: Int): Boolean {
        if (items.size < 2) return false
        return if (items[index].kind == PlayKind.LIVE) {
            previousIndex = index
            index = (index + delta + items.size) % items.size
            true
        } else {
            val n = index + delta
            if (n in items.indices) {
                index = n
                true
            } else {
                false
            }
        }
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

    // When a movie or episode ends: remember it as finished, then go on to the next episode if there is one.
    LaunchedEffect(endedTick) {
        if (endedTick > 0) {
            saveProgress(finished = true)
            val cur = items[index]
            if (cur.kind == PlayKind.EPISODE && index < items.lastIndex) index++ else overlay = true
        }
    }

    // Typing a channel number: 5 then 1 goes to channel 51. It jumps by itself after a moment, or at once on OK.
    fun commitDigits() {
        val n = digits.toIntOrNull()
        digits = ""
        if (n != null && n in 1..items.size && n - 1 != index && items[index].kind == PlayKind.LIVE) {
            previousIndex = index
            index = n - 1
        }
    }

    fun openMini() {
        val n = minOf(items.size, 9)
        val first = if (items.size >= 9) -2 else 0
        miniEntries = (0 until n).map { (index + first + it).mod(items.size) }
        miniSel = -first
        overlay = false
        miniOpen = true
        miniTick++
    }

    fun pickMini(i: Int) {
        val target = miniEntries.getOrNull(i)
        miniOpen = false
        if (target != null && target != index) {
            previousIndex = index
            index = target
        }
    }

    // The strip tidies itself away after a few seconds.
    LaunchedEffect(miniOpen, miniTick) {
        if (miniOpen) {
            delay(7_000)
            miniOpen = false
        }
    }

    LaunchedEffect(digitTick) {
        if (digits.isNotEmpty()) {
            delay(1800)
            commitDigits()
        }
    }

    val handleKey: (KeyEvent) -> Boolean = handle@{ ev ->
        if (ev.type != KeyEventType.KeyDown) return@handle false
        val typed = when (ev.key) {
            Key.Zero, Key.NumPad0 -> '0'
            Key.One, Key.NumPad1 -> '1'
            Key.Two, Key.NumPad2 -> '2'
            Key.Three, Key.NumPad3 -> '3'
            Key.Four, Key.NumPad4 -> '4'
            Key.Five, Key.NumPad5 -> '5'
            Key.Six, Key.NumPad6 -> '6'
            Key.Seven, Key.NumPad7 -> '7'
            Key.Eight, Key.NumPad8 -> '8'
            Key.Nine, Key.NumPad9 -> '9'
            else -> null
        }
        if (typed != null && isLive && items.size > 1) {
            if (digits.length < 4) digits += typed
            digitTick++
            return@handle true
        }
        if (digits.isNotEmpty() && (ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter)) {
            commitDigits()
            return@handle true
        }
        if (miniOpen) {
            miniTick++
            return@handle when (ev.key) {
                Key.DirectionLeft -> { miniSel = (miniSel - 1).coerceAtLeast(0); true }
                Key.DirectionRight -> { miniSel = (miniSel + 1).coerceAtMost(miniEntries.lastIndex); true }
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { pickMini(miniSel); true }
                Key.DirectionUp, Key.DirectionDown, Key.ChannelUp, Key.ChannelDown -> { miniOpen = false; true }
                else -> false
            }
        }
        bump()
        when (ev.key) {
            Key.MediaPlayPause -> { togglePlay(); return@handle true }
            Key.MediaPlay -> { exo.play(); return@handle true }
            Key.MediaPause -> { exo.pause(); return@handle true }
            Key.MediaFastForward -> { seekBy(30_000); return@handle true }
            Key.MediaRewind -> { seekBy(-30_000); return@handle true }
            Key.ChannelUp -> { return@handle step(1) }
            Key.ChannelDown -> { return@handle step(-1) }
            else -> Unit
        }
        if (!overlay && picker == null) {
            when (ev.key) {
                Key.DirectionUp -> if (isLive) step(1) else false
                Key.DirectionDown -> if (isLive) step(-1) else false
                Key.DirectionLeft -> seekBy(-10_000)
                Key.DirectionRight -> if (isLive && items.size > 1) { openMini(); true } else seekBy(10_000)
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
                    // Keep the last picture on screen while the next channel starts, instead of flashing black.
                    setKeepContentOnPlayerReset(true)
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
                // On a phone: swipe up for the next channel, down for the one before.
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (items[index].kind == PlayKind.LIVE && kotlin.math.abs(total) > 120f) {
                                if (step(if (total < 0) 1 else -1)) bump()
                            }
                        },
                    ) { _, amount -> total += amount }
                }
        )

        // The mini guide: nearby channels along the bottom, with what is on now and next.
        if (miniOpen && miniEntries.isNotEmpty()) {
            MiniGuide(
                entries = miniEntries.map { items[it] },
                selected = miniSel,
                currentKey = item.key,
                epg = epg,
                nowMs = nowMs,
                onPick = { pickMini(it) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // The channel number being typed, top right.
        if (digits.isNotEmpty()) {
            Text(
                digits,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(32.dp)
                    .background(Color(0xAA000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

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
                        if (isLive) exo.seekToDefaultPosition()
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
                        if (isLive && items.size > 1) "${index + 1}  ${item.title}" else item.title,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Palette.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isLive) {
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
                if (!isLive && item.subtitle.isNotBlank()) {
                    Text(item.subtitle, fontSize = 16.sp, color = Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                } else if (isLive) {
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
                // Movies, episodes and catch-up: how far through, like a progress bar.
                if (!isLive && durationMs > 0L) {
                    Text(
                        "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                        fontSize = 15.sp,
                        color = Palette.TextPrimary,
                    )
                    val frac = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(4.dp).background(Color(0x33FFFFFF))) {
                        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Palette.Accent))
                    }
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
                    if (isLive) {
                        PlayerButton(if (recActive) "Stop rec" else "Record", Modifier.width(110.dp)) {
                            bump()
                            val running = Recorder.activeFor(context, item.url)
                            if (running != null) {
                                if (running.state == RecState.RECORDING) Recorder.stop(context, running.id) else Recorder.cancel(context, running.id)
                                recActive = false
                                Toast.makeText(context, "Recording stopped. What was saved is in Recordings.", Toast.LENGTH_LONG).show()
                            } else {
                                val nowShow = item.epgId?.let { id -> epg[id] }?.firstOrNull { nowMs >= it.start && nowMs < it.stop }
                                val stopAt = nowShow?.stop ?: (nowMs + 60 * 60_000L)
                                Recorder.schedule(context, item.title, item.imageUrl, item.url, nowShow?.title ?: item.title, nowShow?.start ?: nowMs, stopAt)
                                recActive = true
                                Toast.makeText(context, "Recording ${nowShow?.title ?: item.title} until " + java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(stopAt)), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    if (isLive && items.size > 1) {
                        PlayerButton("Guide", Modifier.width(100.dp)) { openMini() }
                        PlayerButton("Prev ch", Modifier.width(110.dp)) { step(-1) }
                        PlayerButton("Next ch", Modifier.width(110.dp)) { step(1) }
                        if (previousIndex >= 0 && previousIndex != index) {
                            PlayerButton("Last ch", Modifier.width(110.dp)) { lastChannel() }
                        }
                    }
                    if (item.kind == PlayKind.EPISODE) {
                        if (index > 0) PlayerButton("Prev ep", Modifier.width(110.dp)) { step(-1) }
                        if (index < items.lastIndex) PlayerButton("Next ep", Modifier.width(110.dp)) { step(1) }
                    }
                    if (item.favKey != null) {
                        PlayerButton(
                            if (isFavourite) "Remove fav" else "Add fav",
                            Modifier.width(130.dp),
                        ) { bump(); onToggleFavourite(item) }
                    }
                    PlayerButton("Audio", Modifier.width(100.dp)) { picker = C.TRACK_TYPE_AUDIO }
                    PlayerButton("Subtitles", Modifier.width(120.dp)) { picker = C.TRACK_TYPE_TEXT }
                    PlayerButton("Aspect: ${ResizeLabels[resizeIndex]}", Modifier.width(160.dp)) {
                        bump()
                        resizeIndex = (resizeIndex + 1) % ResizeModes.size
                    }
                    val sleepLeftMin = if (sleepEndsAt > 0L) ((sleepEndsAt - nowMs + 59_999L) / 60_000L).coerceAtLeast(1L) else 0L
                    PlayerButton(
                        if (sleepEndsAt > 0L) "Sleep: ${sleepLeftMin}m" else "Sleep: Off",
                        Modifier.width(140.dp),
                    ) {
                        bump()
                        // Each press moves to the next choice: off, 15, 30, 60, 90 minutes, then off again.
                        sleepStep = (sleepStep + 1) % (SleepChoicesMin.size + 1)
                        sleepEndsAt = if (sleepStep == 0) 0L
                        else System.currentTimeMillis() + SleepChoicesMin[sleepStep - 1] * 60_000L
                        nowMs = System.currentTimeMillis()
                    }
                    if (pipSupported) {
                        PlayerButton("Mini player", Modifier.width(140.dp)) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                activity?.enterPictureInPictureMode(
                                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                                )
                            }
                        }
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
                    Text("This one has no other audio tracks.", fontSize = 15.sp, color = Palette.TextSecondary)
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
                    Text("This one has no subtitles.", fontSize = 14.sp, color = Palette.TextSecondary)
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

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
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
