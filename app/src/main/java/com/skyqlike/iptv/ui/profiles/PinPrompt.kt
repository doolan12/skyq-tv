package com.skyqlike.iptv.ui.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.compose.ui.graphics.Color
import com.skyqlike.iptv.ui.common.ActionButton
import com.skyqlike.iptv.ui.common.LabeledField
import com.skyqlike.iptv.ui.theme.Palette

/**
 * Asks for the 4-digit PIN. With [expected] set, it checks the PIN and calls [onDone] when right.
 * With [expected] empty it is for choosing a new PIN, and calls [onDone] with the digits typed.
 */
@Composable
fun PinPrompt(title: String, expected: String, onDone: (String) -> Unit, onCancel: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    BackHandler { onCancel() }
    Column(
        Modifier.fillMaxSize().background(Color(0xF2101014)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
        Spacer(Modifier.height(18.dp))
        Box(Modifier.width(260.dp)) {
            LabeledField("4-digit PIN", pin, password = true) { v -> pin = v.filter { it.isDigit() }.take(4) }
        }
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(error, fontSize = 15.sp, color = Palette.Accent)
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton("OK", Modifier.width(120.dp), accent = true) {
                when {
                    pin.length != 4 -> error = "Enter 4 digits."
                    expected.isNotEmpty() && pin != expected -> {
                        error = "Wrong PIN."
                        pin = ""
                    }
                    else -> onDone(pin)
                }
            }
            ActionButton("Cancel", Modifier.width(120.dp)) { onCancel() }
        }
    }
}
