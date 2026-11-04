package com.skyqlike.iptv.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.ui.home.tileBrush
import com.skyqlike.iptv.ui.theme.Palette
import kotlinx.coroutines.delay

/**
 * A small live picture of the channel the guide is pointing at.
 * It waits a moment after you stop moving, so flicking through the guide doesn't open a stream for every channel.
 * Pressing on it opens the channel full screen.
 */
@Composable
fun GuidePreview(
    channel: Channel?,
    enabled: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 0 = loading, 1 = playing, 2 = couldn't play
    var state by remember { mutableIntStateOf(0) }

    val exo = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android) SkyQLikeIPTV/1.1")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(10_000)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(http))
            .build()
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) state = 1
            }

            override fun onPlayerError(error: PlaybackException) {
                state = 2
            }
        }
        exo.addListener(listener)
        // Always let go of the stream when the guide closes, or the provider may count it as a second viewer.
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    LaunchedEffect(channel?.id, enabled) {
        exo.stop()
        exo.clearMediaItems()
        state = 0
        if (!enabled || channel == null) return@LaunchedEffect
        delay(1200)
        exo.setMediaItem(MediaItem.fromUri(channel.streamUrl))
        exo.prepare()
        exo.play()
    }

    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onTap = { onOpen() }) }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = exo
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (state != 1) {
            Box(
                Modifier.fillMaxSize().background(tileBrush(channel?.name ?: "guide")),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        channel == null -> "Live preview"
                        state == 2 -> "Preview unavailable.\nPress Watch Now."
                        else -> channel.name + "\nLoading preview..."
                    },
                    fontSize = 13.sp,
                    color = Palette.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}
