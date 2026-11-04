package com.skyqlike.iptv.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.skyqlike.iptv.ui.theme.Palette

/**
 * The one building block every selectable thing uses.
 * It gets a white outline and grows slightly when the remote lands on it,
 * so you always know where you are.
 */
@Composable
internal fun FocusTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    corner: Dp = 8.dp,
    focusedScale: Float = 1.08f,
    backgroundColor: Color = Palette.Panel,
    backgroundBrush: Brush? = null,
    onFocus: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        // The TV Surface only reacts to remote buttons. This lets a finger tap (or long press) work too.
        modifier = modifier
            .shadow(
                if (focused) 18.dp else 0.dp,
                shape,
                clip = false,
                ambientColor = Color.White,
                spotColor = Color.White,
            )
            .onFocusChanged {
                focused = it.hasFocus
                if (it.hasFocus) onFocus()
            }
            .pointerInput(onClick, onLongClick) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = onLongClick?.let { lc -> { _ -> lc() } },
                )
            },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = backgroundColor,
            contentColor = Palette.TextPrimary,
            focusedContainerColor = backgroundColor,
            focusedContentColor = Palette.TextPrimary,
            pressedContainerColor = backgroundColor,
            pressedContentColor = Palette.TextPrimary,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape)
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (backgroundBrush != null) Modifier.background(backgroundBrush) else Modifier),
            content = content,
        )
    }
}

/** Gives each title its own colour so tiles look different until real artwork loads. */
internal fun tileBrush(seed: String): Brush {
    val hue = ((seed.hashCode() % 360) + 360) % 360
    return Brush.linearGradient(
        listOf(
            Color.hsv(hue.toFloat(), 0.55f, 0.50f),
            Color.hsv(((hue + 40) % 360).toFloat(), 0.60f, 0.18f),
        )
    )
}

@Composable
internal fun ActionButton(
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    accentColor: Color = Palette.Accent,
    textColor: Color = Palette.TextPrimary,
    onClick: () -> Unit,
) {
    FocusTile(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        corner = 22.dp,
        focusedScale = 1.06f,
        backgroundColor = if (accent) accentColor else Color(0x26FFFFFF),
    ) {
        Text(
            label,
            color = if (accent) textColor else Palette.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
internal fun LabeledField(
    label: String,
    value: String,
    password: Boolean = false,
    onChange: (String) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column {
        Text(label, fontSize = 14.sp, color = Palette.TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Palette.TextPrimary, fontSize = 18.sp),
            cursorBrush = SolidColor(Palette.Accent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .background(Palette.Panel, RoundedCornerShape(8.dp))
                .border(
                    if (focused) 3.dp else 1.dp,
                    if (focused) Color.White else Color(0x33FFFFFF),
                    RoundedCornerShape(8.dp),
                )
                .padding(12.dp),
        )
    }
}
