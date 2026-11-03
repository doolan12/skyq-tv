package com.skyqlike.iptv.ui.providers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.provider.Provider
import com.skyqlike.iptv.data.provider.ProviderType
import com.skyqlike.iptv.ui.home.FocusTile
import com.skyqlike.iptv.ui.theme.Palette
import java.util.UUID

/** Settings > IPTV providers: add, edit, remove, switch and refresh. */
@Composable
fun ProvidersScreen(
    providers: List<Provider>,
    activeId: String?,
    status: String,
    busy: Boolean,
    onSave: (Provider) -> Unit,
    onRemove: (Provider) -> Unit,
    onUse: (Provider?) -> Unit,
    onRefresh: () -> Unit,
) {
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Provider?>(null) }
    var confirmId by remember { mutableStateOf<String?>(null) }

    if (showForm) {
        BackHandler { showForm = false }
        ProviderForm(
            initial = editing,
            onSave = {
                onSave(it)
                showForm = false
            },
            onCancel = { showForm = false },
        )
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 28.dp)) {
            Text("IPTV Providers", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
            Text(
                status,
                fontSize = 15.sp,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton("Add provider", Modifier.width(170.dp), accent = true) {
                    editing = null
                    showForm = true
                }
                ActionButton(if (busy) "Loading..." else "Refresh now", Modifier.width(160.dp)) {
                    if (!busy) onRefresh()
                }
                if (activeId != null) {
                    ActionButton("Use sample", Modifier.width(150.dp)) { onUse(null) }
                }
            }
            Spacer(Modifier.height(20.dp))
            if (providers.isEmpty()) {
                Text(
                    "No providers yet. The built-in sample playlist is being used. Add your own provider to see your channels.",
                    fontSize = 16.sp,
                    color = Palette.TextSecondary,
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(providers, key = { it.id }) { p ->
                    val isActive = p.id == activeId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Palette.Panel, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary)
                            Text(
                                (if (p.type == ProviderType.XTREAM) "Xtream login" else "M3U link") +
                                    if (isActive) "  -  In use" else "",
                                fontSize = 14.sp,
                                color = if (isActive) Palette.Accent else Palette.TextSecondary,
                            )
                        }
                        ActionButton(if (isActive) "In use" else "Use", Modifier.width(100.dp), accent = isActive) {
                            if (!isActive) onUse(p)
                        }
                        ActionButton("Edit", Modifier.width(90.dp)) {
                            editing = p
                            showForm = true
                        }
                        ActionButton(if (confirmId == p.id) "Sure?" else "Remove", Modifier.width(110.dp)) {
                            if (confirmId == p.id) {
                                onRemove(p)
                                confirmId = null
                            } else {
                                confirmId = p.id
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderForm(initial: Provider?, onSave: (Provider) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: ProviderType.M3U) }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var user by remember { mutableStateOf(initial?.username ?: "") }
    var pass by remember { mutableStateOf(initial?.password ?: "") }
    var epg by remember { mutableStateOf(initial?.epgUrl ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 36.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (initial == null) "Add provider" else "Edit provider",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.TextPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton("M3U link", Modifier.width(150.dp), accent = type == ProviderType.M3U) { type = ProviderType.M3U }
            ActionButton("Xtream login", Modifier.width(170.dp), accent = type == ProviderType.XTREAM) { type = ProviderType.XTREAM }
        }
        LabeledField("Name (anything you like)", name) { name = it }
        LabeledField(if (type == ProviderType.M3U) "Playlist link" else "Server URL (like http://server.com:8080)", url) { url = it }
        if (type == ProviderType.XTREAM) {
            LabeledField("Username", user) { user = it }
            LabeledField("Password", pass, password = true) { pass = it }
        }
        LabeledField("EPG (TV guide) link, optional", epg) { epg = it }
        error?.let { Text(it, fontSize = 15.sp, color = Color(0xFFFF6B6B)) }
        Text("Only add playlists you have the right to use.", fontSize = 13.sp, color = Palette.TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton("Save", Modifier.width(130.dp), accent = true) {
                val cleanUrl = url.trim()
                when {
                    cleanUrl.isEmpty() -> error = "Please enter the link or server address."
                    type == ProviderType.XTREAM && (user.isBlank() || pass.isEmpty()) ->
                        error = "Please enter the username and password."
                    else -> {
                        val withScheme = if (cleanUrl.startsWith("http", ignoreCase = true)) cleanUrl else "http://$cleanUrl"
                        onSave(
                            Provider(
                                id = initial?.id ?: UUID.randomUUID().toString(),
                                name = name.trim().ifBlank { "My provider" },
                                type = type,
                                url = withScheme,
                                username = user.trim(),
                                password = pass,
                                epgUrl = epg.trim(),
                            )
                        )
                    }
                }
            }
            ActionButton("Cancel", Modifier.width(130.dp)) { onCancel() }
        }
    }
}

@Composable
private fun LabeledField(
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

@Composable
private fun ActionButton(
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    FocusTile(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        corner = 22.dp,
        focusedScale = 1.06f,
        backgroundColor = if (accent) Palette.Accent else Color(0x26FFFFFF),
    ) {
        Text(
            label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
