package com.skyqlike.iptv.ui.trailer

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * A silent trailer that plays behind the Home banner, like the preview clips on Netflix.
 * It stays invisible until the video is really playing, so a trailer that can't play shows nothing
 * (and [onFailed] is called). It never takes touches or the remote's focus.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun TrailerPreview(
    videoId: String,
    modifier: Modifier = Modifier,
    onPlaying: () -> Unit,
    onEnded: () -> Unit,
    onFailed: () -> Unit,
) {
    val context = LocalContext.current
    val appOrigin = remember(context) { "https://" + context.packageName }
    val safeId = remember(videoId) { videoId.filter { it.isLetterOrDigit() || it == '_' || it == '-' } }
    val playingCb by rememberUpdatedState(onPlaying)
    val endedCb by rememberUpdatedState(onEnded)
    val failedCb by rememberUpdatedState(onFailed)
    var playing by remember(safeId) { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (playing) 1f else 0f, tween(900), label = "previewFade")

    val html = remember(safeId, appOrigin) {
        """
        <!DOCTYPE html><html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="referrer" content="strict-origin-when-cross-origin">
        <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{width:100%;height:100%;pointer-events:none}</style>
        </head><body><div id="p"></div>
        <script src="https://www.youtube.com/iframe_api"></script>
        <script>
        function onYouTubeIframeAPIReady(){
          new YT.Player('p',{width:'100%',height:'100%',videoId:'$safeId',
            playerVars:{autoplay:1,mute:1,controls:0,disablekb:1,rel:0,playsinline:1,fs:0,modestbranding:1,iv_load_policy:3,origin:'$appOrigin'},
            events:{
              onReady:function(e){e.target.mute();e.target.playVideo();},
              onError:function(e){Reel.failed(String(e.data));},
              onStateChange:function(e){
                if(e.data===1){Reel.playing();}
                if(e.data===0){Reel.ended();}
              }
            }});
        }
        </script></body></html>
        """.trimIndent()
    }

    var webView by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(safeId) {
        onDispose {
            webView?.let {
                it.stopLoading()
                it.loadUrl("about:blank")
                it.destroy()
            }
        }
    }

    // The video is 16:9, so it is drawn at that shape, centred, and the banner's edges trim it to fit.
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val w = maxWidth
        AndroidView(
            factory = { ctx ->
                object : WebView(ctx) {
                    // Touches fall straight through, so the page still scrolls when you swipe over the banner.
                    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean = false
                    override fun onTouchEvent(event: MotionEvent?): Boolean = false
                }.apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    isFocusable = false
                    isFocusableInTouchMode = false
                    isClickable = false
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun playing() {
                            post {
                                playing = true
                                playingCb()
                            }
                        }

                        @JavascriptInterface
                        fun ended() {
                            post {
                                playing = false
                                endedCb()
                            }
                        }

                        @JavascriptInterface
                        fun failed(code: String) {
                            post {
                                playing = false
                                failedCb()
                            }
                        }
                    }, "Reel")
                    loadDataWithBaseURL(appOrigin, html, "text/html", "utf-8", null)
                    webView = this
                }
            },
            modifier = Modifier
                .requiredSize(w, w * 9f / 16f)
                .graphicsLayer { this.alpha = alpha },
        )
    }
}
