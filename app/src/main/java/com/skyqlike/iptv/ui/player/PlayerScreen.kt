@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.skyqlike.iptv.ui.player

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import kotlin.math.roundToInt
import com.skyqlike.iptv.data.intro.IntroRepository
import com.skyqlike.iptv.data.intro.SkipSegment
import com.skyqlike.iptv.data.model.PlayItem
import com.skyqlike.iptv.data.settings.TrackPrefsStore
import com.skyqlike.iptv.data.model.PlayKind
import com.skyqlike.iptv.ui.common.FocusTile
import com.skyqlike.iptv.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private const val MAX_RETRIES = 5

/** The show or film a play item belongs to, so choices carry over between its episodes. */
private fun trackBase(item: PlayItem): String = item.key.removePrefix("stremio:").substringBefore('|')

private val SpeedChoices = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

private fun speedLabel(v: Float) = (if (v % 1f == 0f) v.toInt().toString() else v.toString()) + "x"
private val ResizeModes = intArrayOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT,
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    AspectRatioFrameLayout.RESIZE_MODE_FILL,
)
private val ResizeLabels = listOf("Fit", "Zoom", "Stretch")

private data class TrackOption(val label: String, val group: Tracks.Group, val index: Int, val selected: Boolean)
private data class PickerRow(val label: String, val selected: Boolean, val onPick: () -> Unit)

/**
 * The full-screen player for movies and episodes.
 * Remote: Left/Right skip 10 seconds, OK or Up/Down show the controls, Back leaves.
 * Touch: tap the picture to show or hide the controls.
 */
@Composable
fun PlayerScreen(
    items: List<PlayItem>,
    startIndex: Int,
    startPositionFor: (PlayItem) -> Long,
    onProgress: (PlayItem, Long, Long) -> Unit,
    onClose: () -> Unit,
    aspectDefault: Int = 0,
    autoReconnect: Boolean = true,
    subtitlesOn: Boolean = false,
    audioLang: String = "",
    skipIntroOn: Boolean = true,
    /** Works out the episode after this one (stream and subtitles included), or null if there is none. */
    nextEpisode: (suspend (PlayItem) -> PlayItem?)? = null,
    autoNext: Boolean = true,
    /** The web addresses of the other streams for this item (best first), used when one stops working. */
    alternatesFor: (PlayItem) -> List<String> = { emptyList() },
) {
    val context = LocalContext.current
    val introRepo = remember { IntroRepository() }
    val scope = rememberCoroutineScope()

    // The list grows when the next episode is lined up.
    val playlist = remember { mutableStateListOf<PlayItem>().apply { addAll(items) } }
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, items.lastIndex)) }
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
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var endedTick by remember { mutableIntStateOf(0) }

    val attempts = remember { intArrayOf(0) }
    val token = remember { intArrayOf(0) }
    val startedOk = remember { booleanArrayOf(false) }
    val trackPrefs = remember { TrackPrefsStore(context) }
    val triedUrls = remember { HashSet<String>() }
    val alternates by rememberUpdatedState(alternatesFor)

    val exo = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android) Reelhouse/1.0")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
        // Starts playing after 1.5s of video instead of the 2.5s default, and keeps up to a minute buffered.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 60_000, 1_500, 3_000)
            .build()
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(http))
            .build()
            .also { player ->
                // Starting subtitle / audio choices from Settings.
                val builder = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesOn)
                if (audioLang.isNotBlank()) builder.setPreferredAudioLanguage(audioLang)
                player.trackSelectionParameters = builder.build()
            }
    }

    // Remembers how far through we are, so it can be resumed later.
    fun saveProgress(finished: Boolean = false) {
        val cur = playlist[index]
        val dur = exo.duration
        if (dur == C.TIME_UNSET || dur <= 0L) return
        onProgress(cur, if (finished) dur else exo.currentPosition, dur)
    }

    fun buildMediaItem(cur: PlayItem): MediaItem = MediaItem.Builder()
            .setUri(cur.url)
            .setSubtitleConfigurations(
                cur.subtitles.map { s ->
                    val path = s.url.substringBefore('?').lowercase()
                    val mime = when {
                        path.endsWith(".vtt") -> MimeTypes.TEXT_VTT
                        path.endsWith(".ass") || path.endsWith(".ssa") -> MimeTypes.TEXT_SSA
                        else -> MimeTypes.APPLICATION_SUBRIP
                    }
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.url))
                        .setMimeType(mime)
                        .setLanguage(s.language)
                        .setLabel(s.label)
                        .build()
                }
            )
            .build()

    // If a stream stops working, move on to the next best one (keeping your place if it had been playing).
    fun switchToNext(): Boolean {
        val cur = playlist[index]
        triedUrls += cur.url
        val next = alternates(cur).firstOrNull { it !in triedUrls } ?: return false
        triedUrls += next
        val resumeAt = if (startedOk[0]) exo.currentPosition else 0L
        playlist[index] = cur.copy(url = next)
        token[0]++
        attempts[0] = 0
        startedOk[0] = false
        failed = false
        buffering = true
        statusMessage = "That stream isn't working. Trying another one..."
        val mi = buildMediaItem(playlist[index])
        if (resumeAt > 0L) exo.setMediaItem(mi, resumeAt) else exo.setMediaItem(mi)
        exo.prepare()
        exo.play()
        return true
    }

    // Listens to the player: buffering, errors (with automatic reconnecting), audio/subtitle tracks.
    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    startedOk[0] = true
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
                // A stream that never started is a dead link, so go straight to the next one.
                if (!startedOk[0] && switchToNext()) return
                if (autoReconnect && attempts[0] < MAX_RETRIES) {
                    attempts[0]++
                    val myToken = token[0]
                    val n = attempts[0]
                    statusMessage = "Connection lost. Reconnecting ($n of $MAX_RETRIES)..."
                    buffering = true
                    scope.launch {
                        delay(2000L * n)
                        if (myToken == token[0]) {
                            exo.prepare()
                            exo.play()
                        }
                    }
                } else if (switchToNext()) {
                    // Moved on to another stream.
                } else {
                    failed = true
                    buffering = false
                    statusMessage = "This isn't playing. The stream may be offline."
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

    // Full screen: hide the status and navigation bars while playing, and bring them back after.
    DisposableEffect(Unit) {
        var c = context
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        val window = (c as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
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
        val cur = playlist[index]
        token[0]++
        attempts[0] = 0
        startedOk[0] = false
        triedUrls.clear()
        triedUrls += cur.url
        failed = false
        statusMessage = null
        buffering = true
        positionMs = 0L
        durationMs = 0L
        val mediaItem = buildMediaItem(cur)
        if (subtitlesOn && cur.subtitles.isNotEmpty()) {
            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                .setPreferredTextLanguage(audioLang.ifBlank { "en" })
                .build()
        }
        // Audio and subtitle choices you made earlier for this show or film.
        val base = trackBase(cur)
        val savedAudio = trackPrefs.audio(base)
        val savedSub = trackPrefs.sub(base)
        if (savedAudio != null || savedSub != null) {
            val b = exo.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            if (savedAudio != null) b.setPreferredAudioLanguage(savedAudio)
            if (savedSub == "off") {
                b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            } else if (savedSub != null) {
                b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setPreferredTextLanguage(savedSub)
            }
            exo.trackSelectionParameters = b.build()
        }
        val resumeAt = startPositionFor(cur)
        if (resumeAt > 0L) exo.setMediaItem(mediaItem, resumeAt) else exo.setMediaItem(mediaItem)
        exo.prepare()
        exo.play()
        overlay = true
        overlayTick++
    }

    // Keeps the position bar moving, and saves progress every 10 seconds.
    LaunchedEffect(index) {
        var ticks = 0
        while (true) {
            delay(1000)
            ticks++
            positionMs = exo.currentPosition
            durationMs = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
            if (ticks % 10 == 0) saveProgress()
        }
    }

    // Controls hide by themselves after a few seconds.
    LaunchedEffect(overlay, overlayTick, picker) {
        if (overlay && picker == null && !failed) {
            delay(7_000)
            overlay = false
        }
    }

    // Where the intro (and recap) are, if the community database knows this episode.
    var segments by remember { mutableStateOf<List<SkipSegment>>(emptyList()) }
    LaunchedEffect(index) {
        segments = emptyList()
        if (skipIntroOn) segments = introRepo.segmentsFor(playlist[index].externalId)
    }
    val activeSkip = segments.firstOrNull { positionMs >= it.startMs - 1_000 && positionMs < it.endMs - 1_000 }

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

    val item = playlist[index]

    // Up next: in the last minute of an episode, quietly line up the one after it.
    var upNext by remember(index) { mutableStateOf<PlayItem?>(null) }
    var upNextTried by remember(index) { mutableStateOf(false) }
    val remainingMs = if (durationMs > 0L) durationMs - positionMs else Long.MAX_VALUE
    val nearEnd = nextEpisode != null && autoNext && item.kind == PlayKind.EPISODE &&
        index == playlist.lastIndex && remainingMs < 60_000L
    LaunchedEffect(index, nearEnd) {
        if (nearEnd && !upNextTried) {
            upNextTried = true
            upNext = runCatching { nextEpisode?.invoke(item) }.getOrNull()
        }
    }
    val showUpNext = upNext != null && remainingMs < 20_000L && picker == null && !failed

    fun playNext() {
        val n = upNext ?: return
        saveProgress(finished = true)
        playlist.add(n)
        upNext = null
        index = playlist.lastIndex
    }

    fun bump() {
        overlayTick++
    }

    fun seekBy(ms: Long): Boolean {
        if (!exo.isCurrentMediaItemSeekable) return false
        exo.seekTo((exo.currentPosition + ms).coerceAtLeast(0L))
        return true
    }

    fun togglePlay() {
        if (exo.playWhenReady) exo.pause() else exo.play()
    }

    // Touch controls: double-tap to skip, swipe for brightness and volume, plus speed and pop-out.
    val activity = remember(context) {
        var c = context
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        c as? android.app.Activity
    }
    val audio = remember(context) { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    var hud by remember { mutableStateOf<String?>(null) }
    var hudTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(hudTick) {
        if (hudTick > 0) {
            delay(900)
            hud = null
        }
    }
    fun showHud(text: String) {
        hud = text
        hudTick++
    }
    val brightness = remember { floatArrayOf(-1f) }
    val volumeFrac = remember { floatArrayOf(0f) }
    var speedIndex by remember { mutableIntStateOf(1) }
    val canPop = remember {
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }
    // Hand the screen brightness back to the phone's own setting when leaving the player.
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { w ->
                val a = w.attributes
                a.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                w.attributes = a
            }
        }
    }

    // When it ends: remember it as finished and show the controls.
    LaunchedEffect(endedTick) {
        if (endedTick > 0) {
            saveProgress(finished = true)
            if (upNext != null) playNext() else overlay = true
        }
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
            else -> Unit
        }
        if (showUpNext && !overlay && (ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter)) {
            playNext()
            return@handle true
        }
        if (!overlay && picker == null) {
            // With the Skip button showing and the controls hidden, OK skips.
            val skip = segments.firstOrNull { positionMs >= it.startMs - 1_000 && positionMs < it.endMs - 1_000 }
            if (skip != null && (ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter)) {
                exo.seekTo(skip.endMs)
                positionMs = skip.endMs
                return@handle true
            }
            when (ev.key) {
                Key.DirectionUp, Key.DirectionDown -> { overlay = true; true }
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
                    detectTapGestures(
                        onTap = {
                            overlay = !overlay
                            overlayTick++
                        },
                        // Double-tap the left side to go back 10 seconds, the right side to go forward, the middle to pause.
                        onDoubleTap = { o ->
                            val w = size.width
                            when {
                                o.x < w * 0.4f -> if (seekBy(-10_000)) showHud("-10s")
                                o.x > w * 0.6f -> if (seekBy(10_000)) showHud("+10s")
                                else -> togglePlay()
                            }
                        },
                    )
                }
                // Swipe up or down: the left half is brightness, the right half is volume.
                .pointerInput(Unit) {
                    var leftSide = true
                    detectVerticalDragGestures(
                        onDragStart = { o ->
                            leftSide = o.x < size.width / 2f
                            if (brightness[0] < 0f) {
                                brightness[0] = activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
                            }
                            val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                            volumeFrac[0] = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / max
                        },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            val delta = -dy / size.height * 1.4f
                            if (leftSide) {
                                brightness[0] = (brightness[0] + delta).coerceIn(0.03f, 1f)
                                activity?.window?.let { w ->
                                    val a = w.attributes
                                    a.screenBrightness = brightness[0]
                                    w.attributes = a
                                }
                                showHud("Brightness ${(brightness[0] * 100).toInt()}%")
                            } else {
                                volumeFrac[0] = (volumeFrac[0] + delta).coerceIn(0f, 1f)
                                val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (volumeFrac[0] * max).roundToInt(), 0)
                                showHud("Volume ${(volumeFrac[0] * 100).toInt()}%")
                            }
                        },
                    )
                }
        )

        // Small message in the middle-top: "+10s", "Volume 60%" and so on.
        hud?.let { text ->
            Text(
                text,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 28.dp)
                    .background(Color(0xAA000000), RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }

        // Skip Intro / Skip Recap, shown while that part is playing.
        if (activeSkip != null && picker == null && !failed) {
            PlayerButton(
                if (overlay) activeSkip.label else activeSkip.label + "  (press OK)",
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 40.dp, bottom = if (overlay) 190.dp else 60.dp)
                    .width(if (overlay) 190.dp else 270.dp),
                accent = true,
            ) {
                exo.seekTo(activeSkip.endMs)
                positionMs = activeSkip.endMs
            }
        }

        // "Up next" card with a countdown. Tap it (or press OK) to start now.
        if (showUpNext) {
            val next = upNext
            val secs = ((remainingMs + 999) / 1000).coerceAtLeast(0)
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 40.dp, bottom = if (overlay) 190.dp else 60.dp)
                    .width(300.dp)
                    .background(Color(0xE6101014), RoundedCornerShape(12.dp))
                    .clickable { playNext() }
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("Up next", fontSize = 13.sp, color = Palette.TextSecondary)
                Text(
                    next?.subtitle?.ifBlank { next.title } ?: "",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("Starts in ${secs}s  -  tap to play now", fontSize = 13.sp, color = Palette.Accent)
            }
        }

        // Buffering / reconnecting / error message in the middle.
        if (buffering || statusMessage != null) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Netflix-style spinning circle while loading.
                if (buffering && !failed) BufferingSpinner()
                statusMessage?.let { msg ->
                    Text(
                        msg,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier
                            .background(Color(0xAA000000), RoundedCornerShape(12.dp))
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
                if (failed) {
                    PlayerButton("Try again", Modifier.width(150.dp), accent = true) {
                        attempts[0] = 0
                        failed = false
                        statusMessage = null
                        buffering = true
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
                    .padding(horizontal = 40.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    item.title,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Palette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.subtitle.isNotBlank()) {
                    Text(item.subtitle, fontSize = 16.sp, color = Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // How far through, like a progress bar.
                if (durationMs > 0L) {
                    val frac = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f).height(5.dp).background(Color(0x44FFFFFF))) {
                            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Palette.Accent))
                        }
                        Text(
                            "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                            fontSize = 15.sp,
                            color = Palette.TextPrimary,
                        )
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
                    PlayerButton("Audio", Modifier.width(100.dp)) { picker = C.TRACK_TYPE_AUDIO }
                    PlayerButton("Subtitles", Modifier.width(120.dp)) { picker = C.TRACK_TYPE_TEXT }
                    PlayerButton("Size: ${ResizeLabels[resizeIndex]}", Modifier.width(150.dp)) {
                        bump()
                        resizeIndex = (resizeIndex + 1) % ResizeModes.size
                    }
                    PlayerButton("Speed: ${speedLabel(SpeedChoices[speedIndex])}", Modifier.width(150.dp)) {
                        bump()
                        speedIndex = (speedIndex + 1) % SpeedChoices.size
                        exo.setPlaybackSpeed(SpeedChoices[speedIndex])
                    }
                    if (canPop) {
                        PlayerButton("Pop out", Modifier.width(110.dp)) {
                            overlay = false
                            runCatching {
                                activity?.enterPictureInPictureMode(
                                    android.app.PictureInPictureParams.Builder()
                                        .setAspectRatio(android.util.Rational(16, 9))
                                        .build()
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
                    trackPrefs.saveSub(trackBase(item), "off")
                    picker = null
                }
            }
            options.forEach { opt ->
                rows += PickerRow(opt.label, opt.selected) {
                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(opt.group.type, false)
                        .setOverrideForType(TrackSelectionOverride(opt.group.mediaTrackGroup, opt.index))
                        .build()
                    // Remember the language for the rest of this show.
                    opt.group.getTrackFormat(opt.index).language?.takeIf { it != "und" }?.let { lang ->
                        if (type == C.TRACK_TYPE_AUDIO) trackPrefs.saveAudio(trackBase(item), lang)
                        else trackPrefs.saveSub(trackBase(item), lang)
                    }
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

/** A red arc that spins round and round, like the buffering circle on Netflix. */
@Composable
private fun BufferingSpinner(size: androidx.compose.ui.unit.Dp = 64.dp) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "angle",
    )
    Canvas(Modifier.size(size)) {
        val stroke = 6.dp.toPx()
        val inset = stroke / 2
        val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        // A faint full ring behind, and the bright red arc on top.
        drawArc(Color(0x33FFFFFF), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        drawArc(Palette.Accent, angle, 100f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
