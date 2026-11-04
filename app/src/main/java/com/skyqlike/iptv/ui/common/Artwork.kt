package com.skyqlike.iptv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage

/**
 * A picture from the internet (poster, cover or logo).
 * If there's no picture, or it can't load, a coloured tile with a letter shows instead.
 */
@Composable
fun Artwork(
    url: String?,
    seed: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(modifier.background(tileBrush(seed))) {
        Text(
            seed.take(1).uppercase(),
            fontSize = 54.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.align(Alignment.Center),
        )
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
