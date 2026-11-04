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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.profile.Profile
import com.skyqlike.iptv.ui.common.ActionButton
import com.skyqlike.iptv.ui.common.FocusTile
import com.skyqlike.iptv.ui.common.LabeledField
import com.skyqlike.iptv.ui.theme.Palette
import kotlinx.coroutines.delay

/** The colours profiles can have. */
val ProfileColors = listOf(
    Color(0xFFE23744),
    Color(0xFF3D7BFF),
    Color(0xFF2EB67D),
    Color(0xFFF2A93B),
    Color(0xFF9B59D0),
)

private const val MAX_PROFILES = 5

/** A coloured square with the profile's first letter. */
@Composable
fun ProfileAvatar(profile: Profile, modifier: Modifier = Modifier, letterSize: TextUnit = 18.sp) {
    val n = ProfileColors.size
    val color = ProfileColors[((profile.colorIndex % n) + n) % n]
    Box(modifier.background(color, RoundedCornerShape(6.dp))) {
        Text(
            profile.name.trim().take(1).uppercase(),
            fontSize = letterSize,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** "Who's watching?" Pick a profile, add one, or hold OK on one to remove it. */
@Composable
fun ProfilePicker(
    profiles: List<Profile>,
    onPick: (Profile) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (Profile) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Profile?>(null) }
    var name by remember { mutableStateOf("") }
    val firstFocus = remember { FocusRequester() }

    BackHandler(enabled = adding || removing != null) {
        adding = false
        removing = null
    }
    LaunchedEffect(adding, removing) {
        if (!adding && removing == null) {
            delay(200)
            runCatching { firstFocus.requestFocus() }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Palette.Background).padding(horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "REELHOUSE",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Palette.Accent,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(16.dp))

        val pending = removing
        when {
            adding -> {
                Text("Add a profile", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
                Spacer(Modifier.height(20.dp))
                Box(Modifier.width(420.dp)) { LabeledField("Name", name) { name = it } }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton("Save", Modifier.width(130.dp), accent = true) {
                        val clean = name.trim()
                        if (clean.isNotEmpty()) {
                            onAdd(clean)
                            name = ""
                            adding = false
                        }
                    }
                    ActionButton("Cancel", Modifier.width(130.dp)) {
                        name = ""
                        adding = false
                    }
                }
            }
            pending != null -> {
                Text("Remove ${pending.name}?", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Their My List and Continue Watching will be deleted.",
                    fontSize = 16.sp,
                    color = Palette.TextSecondary,
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton("Remove", Modifier.width(130.dp), accent = true) {
                        onRemove(pending)
                        removing = null
                    }
                    ActionButton("Keep", Modifier.width(130.dp)) { removing = null }
                }
            }
            else -> {
                Text("Who's watching?", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                    profiles.forEachIndexed { i, p ->
                        val removeAction: (() -> Unit)? = if (profiles.size > 1) ({ removing = p }) else null
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FocusTile(
                                onClick = { onPick(p) },
                                onLongClick = removeAction,
                                modifier = Modifier
                                    .size(110.dp)
                                    .then(if (i == 0) Modifier.focusRequester(firstFocus) else Modifier),
                                corner = 10.dp,
                                focusedScale = 1.1f,
                                backgroundColor = Color.Transparent,
                            ) {
                                ProfileAvatar(p, Modifier.fillMaxSize(), 44.sp)
                            }
                            Text(
                                p.name,
                                fontSize = 17.sp,
                                color = Palette.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(110.dp).padding(top = 10.dp),
                            )
                        }
                    }
                    if (profiles.size < MAX_PROFILES) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FocusTile(
                                onClick = { adding = true },
                                modifier = Modifier.size(110.dp),
                                corner = 10.dp,
                                focusedScale = 1.1f,
                                backgroundColor = Palette.Panel,
                            ) {
                                Text(
                                    "+",
                                    fontSize = 44.sp,
                                    color = Palette.TextSecondary,
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }
                            Text(
                                "Add profile",
                                fontSize = 17.sp,
                                color = Palette.TextSecondary,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                }
                if (profiles.size > 1) {
                    Spacer(Modifier.height(24.dp))
                    Text("Hold OK on a profile to remove it.", fontSize = 13.sp, color = Palette.TextSecondary)
                }
            }
        }
    }
}
