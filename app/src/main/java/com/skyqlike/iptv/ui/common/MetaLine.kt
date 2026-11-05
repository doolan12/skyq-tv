package com.skyqlike.iptv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/**
 * The small line under a title, Netflix style: a yellow IMDb badge with the score,
 * then the year and how long it runs. Anything that's missing is simply left out.
 */
@Composable
fun MetaLine(
    year: String,
    runtime: String,
    rating: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 15.sp,
) {
    if (year.isBlank() && runtime.isBlank() && rating.isBlank()) return
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (rating.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .background(Color(0xFFF5C518), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text("IMDb", fontSize = fontSize * 0.8f, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                }
                Text(rating, fontSize = fontSize, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        if (year.isNotBlank()) Text(year, fontSize = fontSize, color = Color(0xFFD0D0D8))
        if (runtime.isNotBlank()) Text(runtime, fontSize = fontSize, color = Color(0xFFD0D0D8))
    }
}
