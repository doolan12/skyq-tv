package com.skyqlike.iptv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** The colours of the app. Dark charcoal and black with a blue accent. */
object Palette {
    val Background = Color(0xFF0B0B0D)
    val Panel = Color(0xFF15151A)
    val Rail = Color(0xFF101014)
    val Accent = Color(0xFF3D7BFF)
    val TextPrimary = Color(0xFFF2F2F5)
    val TextSecondary = Color(0xFFA0A0AB)
}

@Composable
fun SkyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            background = Palette.Background,
            surface = Palette.Panel,
            onSurface = Palette.TextPrimary,
            onBackground = Palette.TextPrimary,
        ),
        content = content,
    )
}
