package com.skyqlike.iptv.ui.trailer

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Text
import com.skyqlike.iptv.ui.common.ActionButton

/**
 * Plays a YouTube trailer full screen using YouTube's own embedded player inside a WebView.
 * Back closes it. Some videos don't allow embedding, and then a message says so.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TrailerPlayer(videoId: String, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var problem by remember(videoId) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    // YouTube wants embedded players to say which app they belong to, using the app's package name as the web address.
    val appOrigin = remember(context) { "https://" + context.packageName }
    val safeId = remember(videoId) { videoId.filter { it.isLetterOrDigit() || it == '_' || it == '-' } }

    val html = remember(safeId, appOrigin) {
        """
        <!DOCTYPE html><html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="referrer" content="strict-origin-when-cross-origin">
        <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{width:100%;height:100%}</style>
        </head><body><div id="p"></div>
        <script src="https://www.youtube.com/iframe_api"></script>
        <script>
        function onYouTubeIframeAPIReady(){
          new YT.Player('p',{width:'100%',height:'100%',videoId:'$safeId',
            playerVars:{autoplay:1,rel:0,playsinline:1,controls:1,fs:0,origin:'$appOrigin'},
            events:{
              onReady:function(e){e.target.playVideo();},
              onError:function(e){Reel.failed(String(e.data));},
              onStateChange:function(e){if(e.data===0){Reel.ended();}}
            }});
        }
        </script></body></html>
        """.trimIndent()
    }

    var webView by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            webView?.let {
                it.stopLoading()
                it.loadUrl("about:blank")
                it.destroy()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    keepScreenOn = true
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun failed(code: String) {
                            problem = when (code) {
                                "101", "150", "152", "153" -> "This trailer won't play inside the app (error $code)."
                                "100" -> "This trailer is no longer available."
                                else -> "This trailer couldn't be played (error $code)."
                            }
                        }

                        @JavascriptInterface
                        fun ended() {
                            post { onClose() }
                        }
                    }, "Reel")
                    // The page is given the app's own web address, which is what the embedded player checks for.
                    loadDataWithBaseURL(appOrigin, html, "text/html", "utf-8", null)
                    webView = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // If the trailer can't play in here, it can still be watched in the YouTube app or the browser.
        if (problem != null) {
            ActionButton(
                "Watch on YouTube",
                Modifier.align(Alignment.Center).width(220.dp),
                accent = true,
            ) {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=$safeId"))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                onClose()
            }
        }

        Text(
            problem ?: "Press Back to close the trailer",
            fontSize = 16.sp,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
                .background(Color(0xAA000000), RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 8.dp),
        )
    }
}
