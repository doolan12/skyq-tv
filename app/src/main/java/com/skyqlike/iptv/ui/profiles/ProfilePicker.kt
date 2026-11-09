package com.skyqlike.iptv.ui.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

/** Picture choices for a profile. The first one means "just the first letter of the name". */
private val AvatarChoices = listOf("", "\uD83E\uDD81", "\uD83D\uDC3C", "\uD83E\uDD8A", "\uD83D\uDC38", "\uD83D\uDC2F", "\uD83D\uDC35", "\uD83E\uDD84", "\uD83D\uDC19", "\uD83D\uDE80", "\uD83D\uDC51", "\u26BD", "\uD83C\uDFAC", "\uD83C\uDFAE", "\uD83C\uDF08", "\uD83E\uDD16", "\uD83D\uDC7B")

/** A coloured square with the profile's first letter. */
@Composable
fun ProfileAvatar(profile: Profile, modifier: Modifier = Modifier, letterSize: TextUnit = 18.sp) {
    val n = ProfileColors.size
    val color = ProfileColors[((profile.colorIndex % n) + n) % n]
    Box(modifier.background(color, RoundedCornerShape(6.dp))) {
        Text(
            if (profile.avatar.isNotBlank()) profile.avatar else profile.name.trim().take(1).uppercase(),
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
    onAdd: (name: String, kids: Boolean, avatar: String, color: Int) -> Unit,
    onEdit: (Profile) -> Unit,
    onRemove: (Profile) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    var avatar by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(0) }
    var removing by remember { mutableStateOf<Profile?>(null) }
    var name by remember { mutableStateOf("") }
    var kids by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }

    BackHandler(enabled = adding || editing != null || removing != null) {
        adding = false
        editing = null
        removing = null
    }
    LaunchedEffect(adding, editing, removing) {
        if (!adding && editing == null && removing == null) {
            delay(200)
            runCatching { firstFocus.requestFocus() }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Palette.Background).verticalScroll(rememberScrollState()).padding(horizontal = com.skyqlike.iptv.ui.common.pagePad(), vertical = 12.dp),
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
            adding || editing != null -> {
                val isEdit = editing != null
                val preview = Profile("preview", name.ifBlank { "?" }, color, kids, avatar)
                Text(if (isEdit) "Edit profile" else "Add a profile", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
                    ProfileAvatar(preview, Modifier.size(64.dp), 30.sp)
                    Box(Modifier.weight(1f).widthIn(max = 360.dp)) { LabeledField("Name", name) { name = it } }
                }
                Spacer(Modifier.height(8.dp))
                Text("Picture", fontSize = 13.sp, color = Palette.TextSecondary)
                Spacer(Modifier.height(4.dp))
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(AvatarChoices.size) { i ->
                        val choice = AvatarChoices[i]
                        FocusTile(
                            onClick = { avatar = choice },
                            modifier = Modifier.size(48.dp),
                            corner = 8.dp,
                            focusedScale = 1.1f,
                            backgroundColor = if (avatar == choice) Palette.Accent else Palette.Panel,
                        ) {
                            Text(
                                if (choice.isEmpty()) "Aa" else choice,
                                fontSize = if (choice.isEmpty()) 16.sp else 24.sp,
                                color = Color.White,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    ActionButton("Colour", Modifier.width(100.dp)) { color = (color + 1) % ProfileColors.size }
                    if (!isEdit) {
                        ActionButton(if (kids) "Kids: On" else "Kids: Off", Modifier.width(110.dp), accent = kids) { kids = !kids }
                    }
                    ActionButton("Save", Modifier.width(100.dp), accent = true) {
                        val clean = name.trim()
                        if (clean.isNotEmpty()) {
                            val e = editing
                            if (e != null) onEdit(e.copy(name = clean, avatar = avatar, colorIndex = color))
                            else onAdd(clean, kids, avatar, color)
                            kids = false
                            name = ""
                            adding = false
                            editing = null
                        }
                    }
                    ActionButton("Cancel", Modifier.width(100.dp)) {
                        kids = false
                        name = ""
                        adding = false
                        editing = null
                    }
                    if (isEdit && profiles.size > 1) {
                        ActionButton("Remove", Modifier.width(100.dp)) {
                            removing = editing
                            editing = null
                        }
                    }
                }
                if (kids && !isEdit) {
                    Spacer(Modifier.height(6.dp))
                    Text("Kids: child-friendly titles only. A PIN is needed for Settings.", fontSize = 13.sp, color = Palette.TextSecondary)
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
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top, modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    profiles.forEachIndexed { i, p ->
                        val editAction: () -> Unit = {
                            name = p.name
                            avatar = p.avatar
                            color = p.colorIndex
                            editing = p
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FocusTile(
                                onClick = { onPick(p) },
                                onLongClick = editAction,
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
                                if (p.kids) p.name + " (Kids)" else p.name,
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
                                onClick = {
                                    name = ""
                                    avatar = ""
                                    color = profiles.size
                                    kids = false
                                    adding = true
                                },
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
                Spacer(Modifier.height(24.dp))
                Text("Hold OK on a profile to change its name or picture.", fontSize = 13.sp, color = Palette.TextSecondary)
            }
        }
    }
}
