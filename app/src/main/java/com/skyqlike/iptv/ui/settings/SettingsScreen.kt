package com.skyqlike.iptv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.settings.AppSettings
import com.skyqlike.iptv.data.stremio.StremioSession
import com.skyqlike.iptv.ui.common.ActionButton
import com.skyqlike.iptv.ui.common.FocusTile
import com.skyqlike.iptv.ui.common.LabeledField
import com.skyqlike.iptv.ui.stremio.AccountPage
import com.skyqlike.iptv.ui.stremio.StremioActions
import com.skyqlike.iptv.ui.theme.Palette
import kotlinx.coroutines.delay

private val AspectNames = listOf("Fit", "Zoom", "Stretch")

/** Spoken-language choices: a language code and what to call it. */
private val Languages = listOf(
    "" to "Automatic",
    "en" to "English",
    "es" to "Spanish",
    "fr" to "French",
    "de" to "German",
    "it" to "Italian",
)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    session: StremioSession,
    actions: StremioActions,
    profileName: String,
    onClearHistory: () -> Unit,
    onSwitchProfile: () -> Unit,
    onBack: () -> Unit,
) {
    var page by remember { mutableIntStateOf(0) } // 0 = the list, 1 = account and addons
    BackHandler(enabled = page == 0) { onBack() }

    if (page == 1) {
        AccountPage(session = session, actions = actions, canClose = true, onClose = { page = 0 })
        return
    }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(200)
        runCatching { firstFocus.requestFocus() }
    }

    val accountSummary = when {
        session.signedIn -> "Signed in as ${session.email.ifBlank { "your Stremio account" }}"
        session.manualAddons.isNotEmpty() -> "${session.manualAddons.size} addon link(s)"
        else -> "Not set up"
    }
    val languageName = Languages.firstOrNull { it.first == settings.audioLang }?.second ?: "Automatic"
    var tmdbText by remember { mutableStateOf(settings.tmdbKey) }
    var tmdbSaved by remember { mutableStateOf(false) }
    var tpText by remember { mutableStateOf(settings.topPostersKey) }
    var tpSaved by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Settings", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary) }
        item { Text("Watching as $profileName", fontSize = 15.sp, color = Palette.TextSecondary) }

        item {
            SettingRow("Account and addons", accountSummary, Modifier.focusRequester(firstFocus)) { page = 1 }
        }
        item {
            SettingRow("Pick the best stream automatically", onOff(settings.quickPlay)) {
                onChange(settings.copy(quickPlay = !settings.quickPlay))
            }
        }
        item {
            SettingRow("Skip Intro button (where known)", onOff(settings.skipIntro)) {
                onChange(settings.copy(skipIntro = !settings.skipIntro))
            }
        }
        item {
            SettingRow("Resume where I left off", onOff(settings.resume)) {
                onChange(settings.copy(resume = !settings.resume))
            }
        }
        item {
            SettingRow("Subtitles on by default", onOff(settings.subtitlesOn)) {
                onChange(settings.copy(subtitlesOn = !settings.subtitlesOn))
            }
        }
        item {
            SettingRow("Preferred audio language", languageName) {
                val i = Languages.indexOfFirst { it.first == settings.audioLang }
                onChange(settings.copy(audioLang = Languages[(i + 1).mod(Languages.size)].first))
            }
        }
        item {
            SettingRow("Picture size", AspectNames[settings.aspect.coerceIn(0, AspectNames.lastIndex)]) {
                onChange(settings.copy(aspect = (settings.aspect + 1).mod(AspectNames.size)))
            }
        }
        item {
            SettingRow("Reconnect automatically if a stream drops", onOff(settings.autoReconnect)) {
                onChange(settings.copy(autoReconnect = !settings.autoReconnect))
            }
        }
        item { Text("Trending rows (optional)", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary, modifier = Modifier.padding(top = 8.dp)) }
        item { LabeledField("TMDB key (free from themoviedb.org)", tmdbText) { tmdbText = it; tmdbSaved = false } }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton("Save key", Modifier.width(140.dp)) {
                    onChange(settings.copy(tmdbKey = tmdbText.trim()))
                    tmdbSaved = true
                }
                if (settings.tmdbKey.isNotBlank()) {
                    ActionButton("Remove key", Modifier.width(150.dp)) {
                        tmdbText = ""
                        onChange(settings.copy(tmdbKey = ""))
                        tmdbSaved = false
                    }
                }
                if (tmdbSaved) Text("Saved. Go back to Home to see it.", fontSize = 15.sp, color = Palette.TextSecondary)
            }
        }
        item {
            Text(
                "With a key, Home shows what is trending today. Without one it shows the popular lists from your addons. The key stays on this device.",
                fontSize = 13.sp,
                color = Palette.TextSecondary,
            )
        }
        item { Text("Rating badges on posters (optional)", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary, modifier = Modifier.padding(top = 8.dp)) }
        item { LabeledField("Top Posters key (free from top-posters.com)", tpText) { tpText = it; tpSaved = false } }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton("Save key", Modifier.width(140.dp)) {
                    onChange(settings.copy(topPostersKey = tpText.trim()))
                    tpSaved = true
                }
                if (settings.topPostersKey.isNotBlank()) {
                    ActionButton("Remove key", Modifier.width(150.dp)) {
                        tpText = ""
                        onChange(settings.copy(topPostersKey = ""))
                        tpSaved = false
                    }
                }
                if (tpSaved) Text("Saved. Go back to Home to see it.", fontSize = 15.sp, color = Palette.TextSecondary)
            }
        }
        item {
            Text(
                "With a key, posters show IMDb, Rotten Tomatoes and Metacritic badges. If the free monthly allowance runs out, the normal posters show instead. The key stays on this device.",
                fontSize = 13.sp,
                color = Palette.TextSecondary,
            )
        }
        item { SettingRow("Clear Continue Watching", "") { onClearHistory() } }
        item { SettingRow("Switch profile", "") { onSwitchProfile() } }
        item {
            Text(
                "Quick play starts the first stream your addons offer. If it doesn't work, press Back to see the full list.",
                fontSize = 13.sp,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        item { ActionButton("Done", Modifier.width(140.dp), accent = true) { onBack() } }
    }
}

private fun onOff(on: Boolean) = if (on) "On" else "Off"

@Composable
private fun SettingRow(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusTile(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(0.75f).height(56.dp),
        corner = 8.dp,
        focusedScale = 1.02f,
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 17.sp, color = Palette.TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (value.isNotBlank()) {
                Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
