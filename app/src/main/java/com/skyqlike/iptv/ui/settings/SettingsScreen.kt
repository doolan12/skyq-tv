package com.skyqlike.iptv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.data.model.favKey
import com.skyqlike.iptv.data.settings.AppSettings
import com.skyqlike.iptv.data.settings.ChannelRules
import com.skyqlike.iptv.ui.home.FocusTile
import com.skyqlike.iptv.ui.providers.ActionButton
import com.skyqlike.iptv.ui.providers.LabeledField
import com.skyqlike.iptv.ui.theme.Palette

/** The Settings area: a short menu, and a page for each group of settings. */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    allChannels: List<Channel>,
    visibleCount: Int,
    adultUnlocked: Boolean,
    onAdultUnlockedChange: (Boolean) -> Unit,
    providerName: String,
    status: String,
    busy: Boolean,
    onRefreshPlaylist: () -> Unit,
    onRefreshGuide: () -> Unit,
    onClearCache: () -> Unit,
    onClearHistory: () -> Unit,
    onTestTmdb: suspend (String) -> String,
    providersContent: @Composable () -> Unit,
) {
    var page by remember { mutableStateOf("main") }
    BackHandler(enabled = page != "main") { page = "main" }

    when (page) {
        "providers" -> providersContent()
        "playback" -> PlaybackPage(settings, onChange)
        "parental" -> ParentalPage(settings, onChange, adultUnlocked, onAdultUnlockedChange)
        "team" -> TeamPage(settings, onChange)
        "artwork" -> ArtworkPage(settings, onChange, onTestTmdb)
        "channels" -> ChannelsPage(settings, onChange, allChannels)
        "data" -> DataPage(status, busy, settings, onChange, onRefreshPlaylist, onRefreshGuide, onClearCache, onClearHistory)
        "about" -> AboutPage(providerName, visibleCount, allChannels.size)
        else -> Page("Settings") {
            val parentalSummary = when {
                settings.pin.isEmpty() -> "No PIN set"
                adultUnlocked -> "PIN set - adult categories unlocked"
                else -> "PIN set - adult categories hidden"
            }
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { SettingRow("IPTV providers", summary = "In use: $providerName") { page = "providers" } }
                item { SettingRow("Playback", summary = "Picture size, subtitles, audio language, reconnecting") { page = "playback" } }
                item { SettingRow("My Team", value = settings.myTeam.split(',').first().trim(), summary = "Follow your team: games on the Home page and reminders") { page = "team" } }
                item { SettingRow("Artwork (TMDB)", value = if (settings.tmdbKey.isBlank()) "Off" else "On", summary = "Posters and banners for movies and shows") { page = "artwork" } }
                item { SettingRow("Channels", summary = "$visibleCount of ${allChannels.size} channels shown. Hide and reorder.") { page = "channels" } }
                item { SettingRow("Parental controls", summary = parentalSummary) { page = "parental" } }
                item { SettingRow("Data and refresh", summary = "Refresh the playlist or TV guide, clear saved data") { page = "data" } }
                item { SettingRow("About", summary = "Version and details") { page = "about" } }
            }
        }
    }
}

// ---------------------------------------------------------------- building blocks

@Composable
private fun Page(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 28.dp)) {
        Text(title, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
        if (subtitle != null) {
            Text(subtitle, fontSize = 15.sp, color = Palette.TextSecondary, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun SettingRow(title: String, value: String = "", summary: String = "", onClick: () -> Unit) {
    FocusTile(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(if (summary.isBlank()) 56.dp else 70.dp),
        corner = 10.dp,
        focusedScale = 1.02f,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (summary.isNotBlank()) {
                    Text(summary, fontSize = 13.sp, color = Palette.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (value.isNotBlank()) {
                Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Accent)
            }
        }
    }
}

private fun onOff(value: Boolean) = if (value) "On" else "Off"

// ---------------------------------------------------------------- playback

private val AspectNames = listOf("Fit", "Zoom", "Stretch")
private val AudioLanguages = listOf(
    "" to "Any", "en" to "English", "es" to "Spanish", "fr" to "French",
    "de" to "German", "it" to "Italian", "pt" to "Portuguese",
)

@Composable
private fun PlaybackPage(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    val langIndex = AudioLanguages.indexOfFirst { it.first == settings.audioLang }.coerceAtLeast(0)
    Page("Playback", "Changes apply the next time you start playing something.") {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                SettingRow(
                    "Picture size",
                    AspectNames[settings.aspect.coerceIn(0, 2)],
                    "Fit shows the whole picture. Zoom fills the screen. Stretch fills it by stretching.",
                ) { onChange(settings.copy(aspect = (settings.aspect + 1) % 3)) }
            }
            item {
                SettingRow("Reconnect automatically", onOff(settings.autoReconnect), "If a stream drops, try again by itself.") {
                    onChange(settings.copy(autoReconnect = !settings.autoReconnect))
                }
            }
            item {
                SettingRow("Resume movies and episodes", onOff(settings.resume), "Start from where you stopped last time.") {
                    onChange(settings.copy(resume = !settings.resume))
                }
            }
            item {
                SettingRow("Open on last channel", onOff(settings.openOnLastChannel), "When the app starts, go straight to the live channel you watched last.") {
                    onChange(settings.copy(openOnLastChannel = !settings.openOnLastChannel))
                }
            }
            item {
                SettingRow("Subtitles on by default", onOff(settings.subtitlesOn), "When a stream has subtitles, show them straight away.") {
                    onChange(settings.copy(subtitlesOn = !settings.subtitlesOn))
                }
            }
            item {
                SettingRow(
                    "Preferred audio language",
                    AudioLanguages[langIndex].second,
                    "Used when a stream offers more than one language.",
                ) { onChange(settings.copy(audioLang = AudioLanguages[(langIndex + 1) % AudioLanguages.size].first)) }
            }
        }
    }
}

// ---------------------------------------------------------------- my team

@Composable
private fun TeamPage(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    var team by remember { mutableStateOf(settings.myTeam) }
    var saved by remember { mutableStateOf("") }
    Page("My Team", "Games that name your team in the TV guide show up on the Home page, with a reminder before kick-off.") {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { LabeledField("Team name", team) { team = it } }
            item {
                Text(
                    "Type the name the guide uses, like Liverpool. To follow more than one name, separate them with commas: Liverpool, LFC.",
                    fontSize = 13.sp, color = Palette.TextSecondary,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton("Save", Modifier.width(120.dp), accent = true) {
                        onChange(settings.copy(myTeam = team.trim()))
                        saved = if (team.isBlank()) "My Team is off." else "Saved. Your games will show on Home in a moment."
                    }
                    if (settings.myTeam.isNotBlank()) {
                        ActionButton("Remove", Modifier.width(120.dp)) {
                            team = ""
                            onChange(settings.copy(myTeam = ""))
                            saved = "My Team is off."
                        }
                    }
                }
            }
            if (saved.isNotBlank()) item { Text(saved, fontSize = 16.sp, color = Palette.Accent) }
            item {
                SettingRow(
                    "Remind me before every game",
                    onOff(settings.teamReminders),
                    "An alert 5 minutes before kick-off. Tap it to open the channel.",
                ) { onChange(settings.copy(teamReminders = !settings.teamReminders)) }
            }
        }
    }
}

// ---------------------------------------------------------------- artwork

@Composable
private fun ArtworkPage(settings: AppSettings, onChange: (AppSettings) -> Unit, onTest: suspend (String) -> String) {
    var key by remember { mutableStateOf(settings.tmdbKey) }
    var result by remember { mutableStateOf("") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Page("Artwork (TMDB)", "Paste your free key from themoviedb.org to get proper posters and wide banners on the Home page.") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            LabeledField("TMDB key", key) { key = it.trim() }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton("Save", Modifier.width(120.dp), accent = true) {
                    onChange(settings.copy(tmdbKey = key))
                    result = if (key.isBlank()) "Artwork is off." else "Saved."
                }
                ActionButton("Test key", Modifier.width(140.dp)) {
                    result = "Testing..."
                    scope.launch { result = onTest(key) }
                }
                if (key.isNotEmpty()) {
                    ActionButton("Remove", Modifier.width(120.dp)) {
                        key = ""
                        onChange(settings.copy(tmdbKey = ""))
                        result = "Key removed. Artwork is off."
                    }
                }
            }
            if (result.isNotBlank()) Text(result, fontSize = 16.sp, color = Palette.Accent)
            Text(
                "Works with either kind of TMDB key (the short API key or the long read access token). It is kept on this device only.",
                fontSize = 13.sp, color = Palette.TextSecondary,
            )
            Text("This product uses the TMDB API but is not endorsed or certified by TMDB.", fontSize = 12.sp, color = Palette.TextSecondary)
        }
    }
}

// ---------------------------------------------------------------- parental controls

private sealed interface PinFlow {
    data object SetFirst : PinFlow
    data class SetConfirm(val first: String) : PinFlow
    data object Unlock : PinFlow
    data object ChangeCheck : PinFlow
    data object RemoveCheck : PinFlow
}

@Composable
private fun ParentalPage(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    adultUnlocked: Boolean,
    onAdultUnlockedChange: (Boolean) -> Unit,
) {
    var flow by remember { mutableStateOf<PinFlow?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun close() {
        flow = null
        error = null
    }

    BackHandler(enabled = flow != null) { close() }

    val current = flow
    if (current != null) {
        val title = when (current) {
            PinFlow.SetFirst -> "Choose a 4-digit PIN"
            is PinFlow.SetConfirm -> "Enter the PIN again"
            PinFlow.Unlock -> "Enter your PIN to unlock"
            PinFlow.ChangeCheck, PinFlow.RemoveCheck -> "Enter your current PIN"
        }
        PinPad(
            title = title,
            error = error,
            onCancel = { close() },
            onComplete = { entered ->
                when (current) {
                    PinFlow.SetFirst -> {
                        flow = PinFlow.SetConfirm(entered)
                        error = null
                    }
                    is PinFlow.SetConfirm -> {
                        if (entered == current.first) {
                            onChange(settings.copy(pin = entered))
                            onAdultUnlockedChange(false)
                            close()
                        } else {
                            error = "Those didn't match. Start again."
                            flow = PinFlow.SetFirst
                        }
                    }
                    PinFlow.Unlock -> {
                        if (entered == settings.pin) {
                            onAdultUnlockedChange(true)
                            close()
                        } else {
                            error = "Wrong PIN. Try again."
                        }
                    }
                    PinFlow.ChangeCheck -> {
                        if (entered == settings.pin) {
                            flow = PinFlow.SetFirst
                            error = null
                        } else {
                            error = "Wrong PIN. Try again."
                        }
                    }
                    PinFlow.RemoveCheck -> {
                        if (entered == settings.pin) {
                            onChange(settings.copy(pin = ""))
                            onAdultUnlockedChange(false)
                            close()
                        } else {
                            error = "Wrong PIN. Try again."
                        }
                    }
                }
            },
        )
        return
    }

    Page(
        "Parental controls",
        "With a PIN set, adult categories (names with words like adult, xxx or 18+) are hidden everywhere until you unlock them here.",
    ) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (settings.pin.isEmpty()) {
                item { SettingRow("Set a PIN", summary = "Hides adult categories until the PIN is entered") { flow = PinFlow.SetFirst } }
            } else {
                item {
                    SettingRow(
                        "Adult categories",
                        if (adultUnlocked) "Unlocked" else "Hidden",
                        if (adultUnlocked) "Tap to hide them again" else "Tap and enter your PIN to show them",
                    ) {
                        if (adultUnlocked) {
                            onAdultUnlockedChange(false)
                        } else {
                            error = null
                            flow = PinFlow.Unlock
                        }
                    }
                }
                item { SettingRow("Change PIN") { error = null; flow = PinFlow.ChangeCheck } }
                item { SettingRow("Remove PIN", summary = "Adult categories will show like any other") { error = null; flow = PinFlow.RemoveCheck } }
            }
        }
    }
}

private fun digitOf(key: Key): Char? = when (key) {
    Key.Zero, Key.NumPad0 -> '0'
    Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'
    Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'
    Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}

/** A number pad that works with the remote, a touch screen, or number keys. */
@Composable
private fun PinPad(title: String, error: String?, onComplete: (String) -> Unit, onCancel: () -> Unit) {
    var digits by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    LaunchedEffect(digits) {
        if (digits.length == 4) {
            val entered = digits
            digits = ""
            onComplete(entered)
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("Delete", "0", "Cancel"),
    )

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
            .onPreviewKeyEvent { ev ->
                val d = if (ev.type == KeyEventType.KeyDown) digitOf(ev.key) else null
                if (d != null && digits.length < 4) {
                    digits += d
                    true
                } else {
                    false
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
        Text(
            (0 until 4).joinToString("  ") { if (it < digits.length) "●" else "○" },
            fontSize = 26.sp,
            color = Palette.TextPrimary,
        )
        Text(error ?: " ", fontSize = 15.sp, color = Palette.Accent)
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { label ->
                    FocusTile(
                        onClick = {
                            when (label) {
                                "Delete" -> digits = digits.dropLast(1)
                                "Cancel" -> onCancel()
                                else -> if (digits.length < 4) digits += label
                            }
                        },
                        modifier = Modifier
                            .width(110.dp)
                            .height(44.dp)
                            .then(if (label == "5") Modifier.focusRequester(focus) else Modifier),
                        corner = 22.dp,
                        focusedScale = 1.06f,
                    ) {
                        Text(
                            label,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- channels

@Composable
private fun ChannelsPage(settings: AppSettings, onChange: (AppSettings) -> Unit, allChannels: List<Channel>) {
    var tab by remember { mutableIntStateOf(0) }
    Page("Channels", "Hide what you don't watch, and put categories in the order you like.") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton("Categories", Modifier.width(150.dp), accent = tab == 0) { tab = 0 }
            ActionButton("Channels", Modifier.width(150.dp), accent = tab == 1) { tab = 1 }
        }
        Spacer(Modifier.height(12.dp))
        if (tab == 0) CategoryList(settings, onChange, allChannels) else ChannelList(settings, onChange, allChannels)
    }
}

@Composable
private fun CategoryList(settings: AppSettings, onChange: (AppSettings) -> Unit, allChannels: List<Channel>) {
    val groups = remember(allChannels, settings.groupOrder) { ChannelRules.groupsInOrder(allChannels, settings) }
    val counts = remember(allChannels) { allChannels.groupingBy { it.group }.eachCount() }

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (target !in groups.indices) return
        val list = groups.toMutableList()
        java.util.Collections.swap(list, index, target)
        onChange(settings.copy(groupOrder = list))
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(groups, key = { _, g -> g }) { i, g ->
            val hidden = g in settings.hiddenGroups
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Palette.Panel, RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        g,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (hidden) Palette.TextSecondary else Palette.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${counts[g] ?: 0} channels" + if (hidden) "  -  hidden" else "",
                        fontSize = 13.sp,
                        color = Palette.TextSecondary,
                    )
                }
                ActionButton(if (hidden) "Show" else "Hide", Modifier.width(90.dp)) {
                    onChange(
                        settings.copy(
                            hiddenGroups = if (hidden) settings.hiddenGroups - g else settings.hiddenGroups + g
                        )
                    )
                }
                ActionButton("Up", Modifier.width(70.dp)) { move(i, -1) }
                ActionButton("Down", Modifier.width(90.dp)) { move(i, 1) }
            }
        }
    }
}

@Composable
private fun ChannelList(settings: AppSettings, onChange: (AppSettings) -> Unit, allChannels: List<Channel>) {
    var query by remember { mutableStateOf("") }
    val matches = remember(allChannels, query) {
        val q = query.trim()
        if (q.isEmpty()) allChannels else allChannels.filter { it.name.contains(q, ignoreCase = true) }
    }
    LabeledField("Search channels", query) { query = it }
    Text(
        "${matches.size} channels" + if (matches.size > 100) " (showing the first 100, type to narrow it down)" else "",
        fontSize = 14.sp,
        color = Palette.TextSecondary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(matches.take(100), key = { it.id }) { ch ->
            val hidden = ch.favKey in settings.hiddenChannels
            SettingRow(ch.name, if (hidden) "Hidden" else "Shown", ch.group) {
                onChange(
                    settings.copy(
                        hiddenChannels = if (hidden) settings.hiddenChannels - ch.favKey else settings.hiddenChannels + ch.favKey
                    )
                )
            }
        }
    }
}

// ---------------------------------------------------------------- data and about

@Composable
private fun DataPage(
    status: String,
    busy: Boolean,
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    onRefreshPlaylist: () -> Unit,
    onRefreshGuide: () -> Unit,
    onClearCache: () -> Unit,
    onClearHistory: () -> Unit,
) {
    var confirmHistory by remember { mutableStateOf(false) }
    Page("Data and refresh", status) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                SettingRow(
                    "Refresh playlist",
                    if (busy) "Working..." else "",
                    "Download the channel list again. The TV guide reloads after it.",
                ) { if (!busy) onRefreshPlaylist() }
            }
            item {
                SettingRow(
                    "Refresh TV guide",
                    if (busy) "Working..." else "",
                    "Download only the TV guide again. Channels stay as they are.",
                ) { if (!busy) onRefreshGuide() }
            }
            item {
                SettingRow(
                    "Update TV guide automatically",
                    onOff(settings.autoGuideUpdate),
                    "Downloads a fresh guide in the background every few hours, so it is ready when you turn on.",
                ) { onChange(settings.copy(autoGuideUpdate = !settings.autoGuideUpdate)) }
            }
            item {
                SettingRow(
                    "Clear saved data",
                    summary = "Deletes saved copies of playlists and guides, then downloads them fresh.",
                ) { if (!busy) onClearCache() }
            }
            item {
                SettingRow(
                    "Clear watch history",
                    if (confirmHistory) "Tap again" else "",
                    "Forgets Continue Watching and Recently Watched.",
                ) {
                    if (confirmHistory) {
                        onClearHistory()
                        confirmHistory = false
                    } else {
                        confirmHistory = true
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutPage(providerName: String, visibleCount: Int, totalCount: Int) {
    val context = LocalContext.current
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
    }
    Page("About") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AboutLine("Version", version)
            AboutLine("Provider in use", providerName)
            AboutLine("Channels shown", "$visibleCount of $totalCount")
            Text(
                "A personal IPTV player. It ships with no channels or branding of its own. " +
                    "The sample playlist uses public test videos.",
                fontSize = 15.sp,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun AboutLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, fontSize = 17.sp, color = Palette.TextSecondary, modifier = Modifier.width(190.dp))
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextPrimary)
    }
}
