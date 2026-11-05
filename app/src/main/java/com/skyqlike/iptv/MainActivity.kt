package com.skyqlike.iptv

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.skyqlike.iptv.data.history.WatchHistoryStore
import com.skyqlike.iptv.data.Http
import com.skyqlike.iptv.data.model.ExternalSubtitle
import com.skyqlike.iptv.data.model.PlayItem
import com.skyqlike.iptv.data.model.PlayKind
import com.skyqlike.iptv.data.profile.Profile
import com.skyqlike.iptv.data.profile.ProfileStore
import com.skyqlike.iptv.data.settings.SettingsStore
import com.skyqlike.iptv.data.stremio.supports
import com.skyqlike.iptv.data.stremio.StremioFavouritesStore
import com.skyqlike.iptv.data.stremio.StremioHome
import com.skyqlike.iptv.data.stremio.StremioItem
import com.skyqlike.iptv.data.stremio.StremioRepository
import com.skyqlike.iptv.data.stremio.StremioState
import com.skyqlike.iptv.data.stremio.StremioStore
import com.skyqlike.iptv.data.stremio.StremioVideo
import com.skyqlike.iptv.data.tmdb.TmdbRepository
import com.skyqlike.iptv.data.tmdb.Trending
import com.skyqlike.iptv.ui.browse.BrowseScreen
import com.skyqlike.iptv.ui.browse.BrowseUi
import com.skyqlike.iptv.ui.detail.DetailScreen
import com.skyqlike.iptv.ui.player.PlayerScreen
import com.skyqlike.iptv.ui.profiles.ProfilePicker
import com.skyqlike.iptv.ui.settings.SettingsScreen
import com.skyqlike.iptv.ui.stremio.AccountPage
import com.skyqlike.iptv.ui.stremio.StremioActions
import com.skyqlike.iptv.ui.theme.Palette
import com.skyqlike.iptv.ui.theme.ReelTheme
import java.io.File
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the player has been asked to play: a list of things, and which one to start on. */
private data class PlayRequest(
    val items: List<PlayItem>,
    val start: Int,
    val next: (suspend (PlayItem) -> PlayItem?)? = null,
)

/** A title that has been opened, and whether Play was pressed to open it. */
private data class DetailRequest(val item: StremioItem, val autoPlay: Boolean, val episodeId: String? = null)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Everything reads about 20 percent bigger, which suits a TV across the room.
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * 1.2f)
            ) {
                ReelTheme {
                    ReelApp(this@MainActivity)
                }
            }
        }
    }
}

@Composable
private fun ReelApp(context: Context) {
    val scope = rememberCoroutineScope()
    val profileStore = remember { ProfileStore(context) }
    val settingsStore = remember { SettingsStore(context) }
    val stremioRepo = remember { StremioRepository() }
    val tmdbRepo = remember { TmdbRepository() }
    val stremioStore = remember { StremioStore(context) }
    val browseUi = remember { BrowseUi() }

    var profiles by remember { mutableStateOf(profileStore.all()) }
    // With only one profile there is nobody to choose between, so go straight in.
    var profile by remember { mutableStateOf<Profile?>(if (profiles.size == 1) profiles.first() else null) }
    var settings by remember { mutableStateOf(settingsStore.load()) }
    var stremio by remember { mutableStateOf(stremioStore.load()) }
    var stremioState by remember { mutableStateOf<StremioState>(StremioState.Idle) }
    var detail by remember { mutableStateOf<DetailRequest?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var playRequest by remember { mutableStateOf<PlayRequest?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var trending by remember { mutableStateOf<Trending?>(null) }
    // Saved copies of addon pages, so Home can appear instantly next time.
    remember(context) { Http.cacheDir = File(context.cacheDir, "http"); true }
    val loadGen = remember { intArrayOf(0) }
    val subtitleJobs = remember { HashMap<String, Deferred<List<ExternalSubtitle>>>() }

    // Each profile has its own Continue Watching and My List.
    val current = profile
    val profileId = current?.id ?: "p1"
    val historyStore = remember(profileId) { WatchHistoryStore(context, profileId) }
    val favStore = remember(profileId) { StremioFavouritesStore(context, profileId) }
    var history by remember(profileId) { mutableStateOf(historyStore.entries()) }
    var favourites by remember(profileId) { mutableStateOf(favStore.all()) }

    val continueEntries = remember(history) { history.filter { !it.finished && it.positionMs > 10_000 } }

    // A short message at the bottom of the screen that clears itself.
    LaunchedEffect(note) {
        if (note != null) {
            delay(2500)
            note = null
        }
    }

    // With a TMDB key saved in Settings, fetch what's trending today for the Home rows.
    LaunchedEffect(settings.tmdbKey) {
        trending = null
        val key = settings.tmdbKey.trim()
        if (key.isNotBlank()) {
            // Last time's trending row first (instant), then today's.
            tmdbRepo.trending(key, cacheOnly = true).getOrNull()?.let {
                if (it.movies.size >= 5 || it.series.size >= 5) trending = it
            }
            tmdbRepo.trending(key).fold(
                onSuccess = { trending = it },
                onFailure = { if (trending == null) note = it.message ?: "Couldn't load trending titles." },
            )
        }
    }

    val loadAddons: () -> Unit = {
        if (stremioState is StremioState.Idle || stremioState is StremioState.Error) {
            stremioState = StremioState.Loading
            val gen = ++loadGen[0]
            scope.launch {
                // Show last time's Home straight away, then refresh it in the background.
                val saved = stremioRepo.loadHome(stremio, cacheOnly = true).getOrNull()
                if (saved != null && gen == loadGen[0]) stremioState = StremioState.Ready(saved)
                var finished = false
                // With nothing saved, let rows appear one by one as they arrive instead of waiting for all of them.
                val partialUpdate: (StremioHome) -> Unit = { partial ->
                    scope.launch { if (!finished && gen == loadGen[0]) stremioState = StremioState.Ready(partial) }
                }
                val fresh = stremioRepo.loadHome(stremio, onPartial = if (saved != null) null else partialUpdate)
                finished = true
                if (gen != loadGen[0]) return@launch
                stremioState = fresh.fold(
                    onSuccess = { StremioState.Ready(it) },
                    onFailure = {
                        if (saved != null) StremioState.Ready(saved)
                        else StremioState.Error(it.message ?: "Couldn't load your addons.")
                    },
                )
            }
        }
    }
    LaunchedEffect(stremioState, stremio, current) {
        if (current != null && stremioState is StremioState.Idle && stremio.hasAnything) loadAddons()
    }

    val resetNavigation: () -> Unit = {
        detail = null
        showSettings = false
        browseUi.tab = 0
        browseUi.genre = null
        browseUi.query = ""
    }

    val actions = StremioActions(
        onNeedData = loadAddons,
        onSignIn = { email, password ->
            stremioRepo.login(email, password).fold(
                onSuccess = { key ->
                    stremio = stremio.copy(email = email.trim(), authKey = key)
                    stremioStore.save(stremio)
                    stremioState = StremioState.Idle
                    null
                },
                onFailure = { it.message ?: "Couldn't sign in." },
            )
        },
        onSignOut = {
            stremio = stremio.copy(email = "", authKey = "")
            stremioStore.save(stremio)
            stremioState = StremioState.Idle
        },
        onAddAddon = { link ->
            stremioRepo.addonFromLink(link).fold(
                onSuccess = { found ->
                    val url = found.first
                    if (url !in stremio.manualAddons) {
                        stremio = stremio.copy(manualAddons = stremio.manualAddons + url)
                        stremioStore.save(stremio)
                    }
                    stremioState = StremioState.Idle
                    null
                },
                onFailure = { it.message ?: "Couldn't add that addon." },
            )
        },
        onRemoveAddon = { url ->
            stremio = stremio.copy(manualAddons = stremio.manualAddons - url)
            stremioStore.save(stremio)
            stremioState = StremioState.Idle
        },
        search = { query ->
            val addons = (stremioState as? StremioState.Ready)?.home?.addons ?: emptyList()
            stremioRepo.search(addons, query)
        },
        onToggleFavourite = { title ->
            val had = favourites.any { it.type == title.type && it.id == title.id }
            favourites = if (had) {
                favourites.filterNot { it.type == title.type && it.id == title.id }
            } else {
                listOf(title) + favourites
            }
            favStore.save(favourites)
            note = if (had) "Removed ${title.name} from My List" else "Added ${title.name} to My List"
        },
        byGenre = { type, genre ->
            val addons = (stremioState as? StremioState.Ready)?.home?.addons ?: emptyList()
            stremioRepo.byGenre(addons, type, genre)
        },
        loadDetails = { title ->
            val addons = (stremioState as? StremioState.Ready)?.home?.addons ?: emptyList()
            stremioRepo.details(addons, title)
        },
        loadStreams = { type, videoId, onUpdate ->
            val addons = (stremioState as? StremioState.Ready)?.home?.addons ?: emptyList()
            // Look for subtitles at the same time as the streams, so playing doesn't wait for them afterwards.
            subtitleJobs.getOrPut("$type|$videoId") {
                scope.async { runCatching { stremioRepo.subtitles(addons, type, videoId) }.getOrDefault(emptyList()) }
            }
            stremioRepo.streams(addons, type, videoId, onUpdate)
        },
        onPlay = { title, video, stream ->
            val addons = (stremioState as? StremioState.Ready)?.home?.addons ?: emptyList()
            val videoId = video?.id ?: title.id
            val subType = if (video != null) "series" else title.type
            // Works out the episode after this one, with its stream and subtitles, for the "Up next" card.
            suspend fun resolve(nv: StremioVideo): PlayItem? {
                val found = stremioRepo.streams(addons, "series", nv.id)
                val s = found.playable.firstOrNull() ?: return null
                val moreSubs = runCatching {
                    withTimeoutOrNull(5000L) { stremioRepo.subtitles(addons, "series", nv.id) }
                }.getOrNull() ?: emptyList()
                return PlayItem(
                    key = "stremio:series:${title.id}|${nv.id}",
                    title = title.name,
                    subtitle = "S${nv.season} E${nv.episode}  ${nv.title}",
                    url = s.url,
                    kind = PlayKind.EPISODE,
                    imageUrl = title.poster,
                    externalId = nv.id,
                    subtitles = moreSubs,
                )
            }
            var nextResolver: (suspend (PlayItem) -> PlayItem?)? = null
            if (video != null) {
                val ordered = title.videos.filter { it.season > 0 }.sortedWith(compareBy({ it.season }, { it.episode }))
                val at = ordered.indexOfFirst { it.id == video.id }
                val nv = if (at >= 0) ordered.getOrNull(at + 1) else null
                if (nv != null) nextResolver = { _ -> resolve(nv) }
            }
            val canFind = addons.any { it.supports("subtitles", subType, videoId) }
            scope.launch {
                // Subtitles were already being fetched while the streams loaded; wait only briefly for them.
                val job = if (canFind) subtitleJobs.getOrPut("$subType|$videoId") {
                    scope.async { runCatching { stremioRepo.subtitles(addons, subType, videoId) }.getOrDefault(emptyList()) }
                } else null
                if (job != null && !job.isCompleted) note = "Finding subtitles..."
                val subs = if (job != null) withTimeoutOrNull(3500L) { job.await() } ?: emptyList() else emptyList()
                if (job != null) note = null
                playRequest = PlayRequest(
                    listOf(
                        PlayItem(
                            key = if (video != null) "stremio:series:${title.id}|${video.id}" else "stremio:${title.type}:${title.id}",
                            title = title.name,
                            subtitle = if (video != null) "S${video.season} E${video.episode}  ${video.title}" else title.year,
                            url = stream.url,
                            kind = if (video != null) PlayKind.EPISODE else PlayKind.MOVIE,
                            imageUrl = title.poster,
                            externalId = video?.id,
                            subtitles = subs,
                        )
                    ),
                    0,
                    nextResolver,
                )
            }
        },
    )

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        val opened = detail
        when {
            current == null -> ProfilePicker(
                profiles = profiles,
                onPick = {
                    resetNavigation()
                    profile = it
                },
                onAdd = { name ->
                    val p = Profile(profileStore.newId(profiles), name, profiles.size)
                    profiles = profiles + p
                    profileStore.save(profiles)
                },
                onRemove = { p ->
                    context.getSharedPreferences("history_${p.id}", Context.MODE_PRIVATE).edit().clear().apply()
                    context.getSharedPreferences("mylist_${p.id}", Context.MODE_PRIVATE).edit().clear().apply()
                    profiles = profiles.filterNot { it.id == p.id }
                    profileStore.save(profiles)
                },
            )
            // Nothing connected yet: the first thing to do is sign in or add an addon.
            !stremio.hasAnything -> AccountPage(
                session = stremio,
                actions = actions,
                canClose = false,
                onClose = {},
            )
            showSettings -> SettingsScreen(
                settings = settings,
                onChange = {
                    settings = it
                    settingsStore.save(it)
                },
                session = stremio,
                actions = actions,
                profileName = current.name,
                onClearHistory = {
                    historyStore.clear()
                    history = historyStore.entries()
                    note = "Continue Watching cleared"
                },
                onSwitchProfile = {
                    resetNavigation()
                    profile = null
                },
                onBack = { showSettings = false },
            )
            opened != null -> DetailScreen(
                start = opened.item,
                actions = actions,
                isFavourite = favourites.any { it.type == opened.item.type && it.id == opened.item.id },
                autoPlay = opened.autoPlay,
                quickPlay = settings.quickPlay,
                autoEpisodeId = opened.episodeId,
                onOpen = { detail = DetailRequest(it, false) },
                onBack = { detail = null },
            )
            else -> BrowseScreen(
                ui = browseUi,
                profile = current,
                state = stremioState,
                continueEntries = continueEntries,
                watched = history,
                favourites = favourites,
                trending = trending,
                topPostersKey = settings.topPostersKey,
                actions = actions,
                onOpen = { detail = DetailRequest(it, false) },
                onPlay = { detail = DetailRequest(it, true) },
                previewsOn = settings.previews,
                onRemoveContinue = {
                    historyStore.removeTitle(it.type, it.id)
                    history = historyStore.entries()
                    note = "Removed ${it.name} from Continue Watching"
                },
                onOpenEpisode = { item, episodeId -> detail = DetailRequest(item, false, episodeId) },
                onSettings = { showSettings = true },
                onSwitchProfile = {
                    resetNavigation()
                    profile = null
                },
            )
        }

        note?.let { text ->
            Text(
                text,
                fontSize = 18.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
                    .background(Color(0xE6101014), RoundedCornerShape(24.dp))
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }

        // The player sits on top of everything. Back returns to the screen you came from.
        playRequest?.let { request ->
            PlayerScreen(
                items = request.items,
                startIndex = request.start,
                startPositionFor = { if (settings.resume) historyStore.positionFor(it.key) else 0L },
                onProgress = { item, position, duration ->
                    historyStore.update(item, position, duration)
                    history = historyStore.entries()
                },
                onClose = { playRequest = null },
                aspectDefault = settings.aspect,
                autoReconnect = settings.autoReconnect,
                subtitlesOn = settings.subtitlesOn,
                audioLang = settings.audioLang,
                skipIntroOn = settings.skipIntro,
                nextEpisode = request.next,
                autoNext = settings.autoNext,
            )
        }
    }
}
