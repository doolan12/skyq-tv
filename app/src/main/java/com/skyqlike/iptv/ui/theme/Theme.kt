package com.skyqlike.iptv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** The colours of the app: near-black with a red accent. */
object Palette {
    val Background = Color(0xFF141414)
    val Panel = Color(0xFF1F1F1F)
    val Accent = Color(0xFFE23744)
    val TextPrimary = Color(0xFFF5F5F5)
    val TextSecondary = Color(0xFFA8A8A8)
}

@Composable
fun ReelTheme(content: @Composable () -> Unit) {
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
