package com.skyqlike.iptv

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import com.skyqlike.iptv.data.model.favKey
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.skyqlike.iptv.data.FavouritesStore
import com.skyqlike.iptv.data.PlaylistRepository
import com.skyqlike.iptv.data.epg.EpgRepository
import com.skyqlike.iptv.data.catchup.CatchUpRepository
import com.skyqlike.iptv.data.catchup.CatchUpState
import com.skyqlike.iptv.data.history.HistoryEntry
import com.skyqlike.iptv.data.history.WatchHistoryStore
import com.skyqlike.iptv.data.m3u.M3uParser
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.data.model.PlayItem
import com.skyqlike.iptv.data.model.PlayKind
import com.skyqlike.iptv.data.model.Programme
import com.skyqlike.iptv.data.model.toPlayItem
import com.skyqlike.iptv.data.provider.ProviderStore
import com.skyqlike.iptv.data.settings.ChannelRules
import com.skyqlike.iptv.data.settings.SettingsStore
import com.skyqlike.iptv.ui.settings.SettingsScreen
import com.skyqlike.iptv.data.vod.VodRepository
import com.skyqlike.iptv.data.vod.VodState
import com.skyqlike.iptv.ui.catchup.CatchUpScreen
import com.skyqlike.iptv.ui.common.isPhoneLike
import com.skyqlike.iptv.ui.guide.GuideScreen
import com.skyqlike.iptv.ui.home.HomeScreen
import com.skyqlike.iptv.ui.library.LibraryScreen
import com.skyqlike.iptv.ui.player.PlayerScreen
import com.skyqlike.iptv.ui.providers.ProvidersScreen
import com.skyqlike.iptv.ui.theme.SkyTheme
import com.skyqlike.iptv.ui.vod.VodScreen
import kotlinx.coroutines.launch

/** What the player has been asked to play: a list of things, and which one to start on. */
private data class PlayRequest(val items: List<PlayItem>, val start: Int)

class MainActivity : ComponentActivity() {

    /** Set when a reminder alert is tapped: the channel to open once the channel list is ready. */
    private var pendingPlayKey by androidx.compose.runtime.mutableStateOf<String?>(null)

    private val notifyPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    /** Newer Android versions ask once before an app may show alerts. */
    fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notifyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingPlayKey = intent.getStringExtra(com.skyqlike.iptv.data.remind.Reminders.EXTRA_PLAY_KEY)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingPlayKey = intent?.getStringExtra(com.skyqlike.iptv.data.remind.Reminders.EXTRA_PLAY_KEY)
        // Stremio has been removed. Forget anything it saved (sign-in key, addon links, favourites).
        listOf("stremio", "stremio_favourites").forEach {
            getSharedPreferences(it, MODE_PRIVATE).edit().clear().apply()
        }
        val phone = isPhoneLike()
        // Phones can be held either way up. TVs are always sideways.
        requestedOrientation = if (phone) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContent {
            // On a TV everything reads about 20 percent bigger, which suits sitting across the room.
            val baseDensity = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides
                    androidx.compose.ui.unit.Density(baseDensity.density, baseDensity.fontScale * (if (phone) 1.0f else 1.2f))
            ) {
            SkyTheme {
                val scope = rememberCoroutineScope()
                val store = remember { ProviderStore(this@MainActivity) }
                val repo = remember { PlaylistRepository(this@MainActivity) }
                val epgRepo = remember { EpgRepository(this@MainActivity) }
                val vodRepo = remember { VodRepository() }
                val scoresRepo = remember { com.skyqlike.iptv.data.scores.ScoresRepository() }
                val tmdbRepo = remember { com.skyqlike.iptv.data.tmdb.TmdbRepository(this@MainActivity) }
                val catchUpRepo = remember { CatchUpRepository() }
                val favStore = remember { FavouritesStore(this@MainActivity) }
                val settingsStore = remember { SettingsStore(this@MainActivity) }
                val historyStore = remember { WatchHistoryStore(this@MainActivity) }
                val sample = remember {
                    assets.open("sample.m3u").bufferedReader().use { M3uParser.parse(it.readText()) }
                }

                var providers by remember { mutableStateOf(store.all()) }
                var activeId by remember { mutableStateOf(store.activeId()) }
                var channels by remember { mutableStateOf<List<Channel>>(sample) }
                var epg by remember { mutableStateOf<Map<String, List<Programme>>>(emptyMap()) }
                var favourites by remember { mutableStateOf(favStore.all()) }
                var history by remember { mutableStateOf(historyStore.entries()) }
                var recentKeys by remember { mutableStateOf(historyStore.recentChannels()) }
                var vod by remember { mutableStateOf<VodState>(VodState.Idle) }
                var catchUp by remember { mutableStateOf<CatchUpState>(CatchUpState.Idle) }
                var catchUpMessage by remember { mutableStateOf<String?>(null) }
                var status by remember { mutableStateOf("Using the built-in sample playlist.") }
                var epgStatus by remember { mutableStateOf("") }
                var busy by remember { mutableStateOf(false) }
                var reloadTick by remember { mutableIntStateOf(0) }
                var forceNext by remember { mutableStateOf(false) }
                var playRequest by remember { mutableStateOf<PlayRequest?>(null) }
                // Recordings: the list lives on the device and updates the moment a recording starts, finishes or is removed.
                val recStore = remember { com.skyqlike.iptv.data.record.RecordingStore(applicationContext) }
                var recordings by remember { mutableStateOf(recStore.all()) }
                DisposableEffect(Unit) {
                    val stopListening = recStore.observe { recordings = recStore.all() }
                    onDispose { stopListening() }
                }
                // Reminders: "starting soon" alerts, kept on the device like recordings.
                val reminderStore = remember { com.skyqlike.iptv.data.remind.ReminderStore(applicationContext) }
                var reminders by remember { mutableStateOf(reminderStore.all()) }
                DisposableEffect(Unit) {
                    val stopListening = reminderStore.observe { reminders = reminderStore.all() }
                    onDispose { stopListening() }
                }
                var homeMovie by remember { mutableStateOf<com.skyqlike.iptv.data.vod.Movie?>(null) }
                var homeSeries by remember { mutableStateOf<com.skyqlike.iptv.data.vod.Series?>(null) }
                var note by remember { mutableStateOf<String?>(null) }
                var settings by remember { mutableStateOf(settingsStore.load()) }
                var adultUnlocked by remember { mutableStateOf(false) } // forgotten each time the app starts
                var guideTick by remember { mutableIntStateOf(0) }

                // Watch history left over from the removed Stremio section is never shown.
                val iptvHistory = remember(history) { history.filterNot { it.key.startsWith("stremio:") } }

                // The channels the user actually sees: hidden ones removed, categories in their order.
                val shownChannels = remember(channels, settings, adultUnlocked) {
                    ChannelRules.apply(channels, settings, adultUnlocked)
                }

                // Keeps the background TV guide refresh in line with the Settings switch.
                LaunchedEffect(settings.autoGuideUpdate) {
                    com.skyqlike.iptv.data.work.GuideRefreshWorker.schedule(this@MainActivity, settings.autoGuideUpdate)
                }

                // A short message at the bottom of the screen that clears itself.
                LaunchedEffect(note) {
                    if (note != null) {
                        delay(2500)
                        note = null
                    }
                }

                // Loads channels, then the TV guide, whenever the provider changes or a refresh is asked for.
                LaunchedEffect(activeId, reloadTick) {
                    val force = forceNext
                    forceNext = false
                    vod = VodState.Idle // movies and series get reloaded for the new provider when opened
                    catchUp = CatchUpState.Idle
                    val provider = providers.firstOrNull { it.id == activeId }
                    var channelsOk = true

                    if (provider == null) {
                        channels = sample
                        status = "Using the built-in sample playlist."
                    } else {
                        busy = true
                        status = "Loading ${provider.name}..."
                        val result = repo.load(provider, force)
                        result
                            .onSuccess {
                                channels = it.channels
                                status = "${provider.name}: ${it.channels.size} channels" +
                                    if (it.fromCache) " (saved copy)." else " loaded."
                            }
                            .onFailure {
                                status = "Couldn't load ${provider.name}: ${it.message}"
                            }
                        channelsOk = result.isSuccess
                    }

                    if (channelsOk) {
                        busy = true
                        epgStatus = "Loading TV guide..."
                        epgRepo.load(provider, channels, force)
                            .onSuccess {
                                epg = it
                                epgStatus = if (provider == null) {
                                    "TV guide: sample listings."
                                } else {
                                    "TV guide: listings for ${it.size} channels."
                                }
                            }
                            .onFailure {
                                epg = emptyMap()
                                epgStatus = it.message ?: "The TV guide couldn't be loaded."
                            }
                    }
                    busy = false
                }

                // "Refresh TV guide" in Settings: reload only the guide.
                LaunchedEffect(guideTick) {
                    if (guideTick > 0) {
                        val provider = providers.firstOrNull { it.id == activeId }
                        busy = true
                        epgStatus = "Loading TV guide..."
                        epgRepo.load(provider, channels, true)
                            .onSuccess {
                                epg = it
                                epgStatus = if (provider == null) "TV guide: sample listings." else "TV guide: listings for ${it.size} channels."
                            }
                            .onFailure {
                                epg = emptyMap()
                                epgStatus = it.message ?: "The TV guide couldn't be loaded."
                            }
                        catchUp = CatchUpState.Idle
                        busy = false
                    }
                }

                val activeProvider = providers.firstOrNull { it.id == activeId }

                // Starts the player on a live channel (the whole channel list is there for channel up/down).
                val playChannel: (Channel) -> Unit = { ch ->
                    val i = shownChannels.indexOfFirst { it.id == ch.id }
                    if (i >= 0) playRequest = PlayRequest(shownChannels.map { it.toPlayItem() }, i)
                }

                // Loads the movies and series list the first time it is needed (Movies page or Search).
                val loadCatalog: () -> Unit = {
                    if (vod is VodState.Idle || vod is VodState.Error) {
                        vod = VodState.Loading
                        scope.launch {
                            vod = vodRepo.catalog(activeProvider).fold(
                                onSuccess = { VodState.Ready(it) },
                                onFailure = { VodState.Error(it.message ?: "Couldn't load movies and series.") },
                            )
                        }
                    }
                }

                val playMovie: (com.skyqlike.iptv.data.vod.Movie) -> Unit = { m ->
                    playRequest = PlayRequest(
                        listOf(
                            PlayItem(
                                key = "movie:${m.id}",
                                title = m.name,
                                subtitle = m.year,
                                url = m.streamUrl,
                                kind = PlayKind.MOVIE,
                                imageUrl = m.posterUrl,
                            )
                        ),
                        0,
                    )
                }

                val playEpisodes: (com.skyqlike.iptv.data.vod.Series, List<com.skyqlike.iptv.data.vod.Episode>, Int) -> Unit = { series, episodes, start ->
                    playRequest = PlayRequest(
                        episodes.map { ep ->
                            PlayItem(
                                key = "ep:${series.id}:${ep.id}",
                                title = series.name,
                                subtitle = "S${ep.season} E${ep.number}  ${ep.title}",
                                url = ep.streamUrl,
                                kind = PlayKind.EPISODE,
                                imageUrl = ep.imageUrl ?: series.coverUrl,
                            )
                        },
                        start,
                    )
                }

                // Quietly loads the movie list a few seconds after the channels, so Home can show what's new.
                LaunchedEffect(channels) {
                    if (channels !== sample && activeProvider != null) {
                        delay(4000)
                        loadCatalog()
                    }
                }

                // "New movies" and "New TV series" rows for Home (only once the list has loaded).
                val vodRows = remember(vod) {
                    val ready = vod as? VodState.Ready
                    if (ready == null) emptyList() else listOf(
                        com.skyqlike.iptv.ui.common.Rail(
                            "New Movies",
                            ready.catalog.movies.sortedByDescending { it.addedEpoch }.take(20).map { m ->
                                com.skyqlike.iptv.ui.common.HomeTile(m.id, m.name, m.year.ifBlank { m.category }, m.posterUrl) { homeMovie = m }
                            },
                        ),
                        com.skyqlike.iptv.ui.common.Rail(
                            "New TV Series",
                            ready.catalog.series.sortedByDescending { it.addedEpoch }.take(20).map { s ->
                                com.skyqlike.iptv.ui.common.HomeTile(s.id, s.name, s.year.ifBlank { s.category }, s.coverUrl) { homeSeries = s }
                            },
                        ),
                    ).filter { it.tiles.isNotEmpty() }
                }

                // "Open on last channel": once the channel list is really loaded (not the sample), jump straight in.
                var openedOnStart by remember { mutableStateOf(false) }
                LaunchedEffect(channels) {
                    if (!openedOnStart && channels !== sample) {
                        openedOnStart = true
                        if (settings.openOnLastChannel && playRequest == null && pendingPlayKey == null) {
                            val last = recentKeys.firstOrNull()
                            val ch = shownChannels.firstOrNull { it.favKey == last }
                            if (ch != null) playChannel(ch)
                        }
                    }
                }

                // My Team: look through the guide for the team's games, and refresh now and then as time passes.
                var teamTick by remember { mutableIntStateOf(0) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(10 * 60_000L)
                        teamTick++
                    }
                }
                val teamMatches by androidx.compose.runtime.produceState<List<com.skyqlike.iptv.ui.home.TeamMatch>?>(null, epg, shownChannels, settings.myTeam, teamTick) {
                    value = if (settings.myTeam.isBlank() || epg.isEmpty()) emptyList()
                    else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        com.skyqlike.iptv.ui.home.findTeamMatches(settings.myTeam, shownChannels, epg, System.currentTimeMillis())
                    }
                }
                // Set a reminder for each coming game by itself, and tidy away ones that no longer apply.
                LaunchedEffect(teamMatches, settings.teamReminders, settings.myTeam) {
                    val found = teamMatches ?: return@LaunchedEffect
                    val now = System.currentTimeMillis()
                    val wantAuto = settings.teamReminders && settings.myTeam.isNotBlank()
                    val wanted = if (wantAuto) found.filter { it.programme.start > now + 2 * 60_000L } else emptyList()
                    val wantedIds = wanted.map { com.skyqlike.iptv.data.remind.Reminders.idFor(it.programme.title, it.programme.start) }.toSet()
                    reminderStore.all().filter { it.auto && it.id !in wantedIds }.forEach {
                        com.skyqlike.iptv.data.remind.Reminders.cancel(this@MainActivity, it.id, byPerson = false)
                    }
                    val skipped = reminderStore.skipped()
                    var added = 0
                    for (m in wanted.take(30)) {
                        val id = com.skyqlike.iptv.data.remind.Reminders.idFor(m.programme.title, m.programme.start)
                        if (id in skipped || reminderStore.get(id) != null) continue
                        com.skyqlike.iptv.data.remind.Reminders.set(this@MainActivity, m.channel, m.programme, auto = true)
                        added++
                    }
                    if (added > 0) askNotificationPermission()
                }
                // Tapping a reminder alert opens the channel.
                LaunchedEffect(pendingPlayKey, channels) {
                    val key = pendingPlayKey
                    if (key != null && channels !== sample) {
                        shownChannels.firstOrNull { it.favKey == key }?.let { playChannel(it) }
                        pendingPlayKey = null
                    }
                }

                // Live scores for the Home banner and the "Live Sport Now" row. Checked every couple of minutes, quietly.
                var liveScores by remember { mutableStateOf<List<com.skyqlike.iptv.data.scores.Score>>(emptyList()) }
                LaunchedEffect(channels) {
                    if (channels !== sample) {
                        delay(6_000)
                        while (true) {
                            liveScores = runCatching { scoresRepo.load() }.getOrDefault(liveScores)
                            delay(120_000)
                        }
                    }
                }
                val homeExtras = remember(liveScores, recentKeys, vod, settings.tmdbKey, settings.myTeam, teamMatches) {
                    val ready = vod as? VodState.Ready
                    com.skyqlike.iptv.ui.home.HomeExtras(
                        scores = liveScores,
                        tmdb = tmdbRepo,
                        teamName = settings.myTeam,
                        teamMatches = teamMatches.orEmpty(),
                        onRemind = { m -> com.skyqlike.iptv.ui.record.remindProgramme(this@MainActivity, m.channel, m.programme) },
                        tmdbKey = settings.tmdbKey,
                        watchCounts = historyStore.channelCounts(),
                        movies = ready?.catalog?.movies.orEmpty(),
                        series = ready?.catalog?.series.orEmpty(),
                        onOpenMovie = { homeMovie = it },
                        onOpenSeries = { homeSeries = it },
                    )
                }

                val playHistory: (HistoryEntry) -> Unit = { e ->
                    playRequest = PlayRequest(
                        listOf(PlayItem(e.key, e.title, e.subtitle, e.url, e.kind, imageUrl = e.imageUrl)),
                        0,
                    )
                }

                Box(Modifier.fillMaxSize()) {
                    HomeScreen(
                        phone = phone,
                        extraRails = vodRows,
                        extras = homeExtras,
                        epg = epg,
                        channels = shownChannels,
                        onPlay = playChannel,
                        favouriteKeys = favourites,
                        recentKeys = recentKeys,
                        continueWatching = iptvHistory.filter { !it.finished && it.positionMs > 10_000 },
                        onPlayHistory = playHistory,
                        onToggleFavourite = { ch ->
                            val key = ch.favKey
                            val adding = key !in favourites
                            favourites = if (adding) favourites + key else favourites - key
                            favStore.save(favourites)
                            note = if (adding) "Added ${ch.name} to Favourites" else "Removed ${ch.name} from Favourites"
                        },
                        guideContent = {
                            GuideScreen(
                                channels = shownChannels,
                                epg = epg,
                                status = epgStatus,
                                onPlay = playChannel,
                                favouriteKeys = favourites,
                                recentKeys = recentKeys,
                                // The little preview stops while the full player is open.
                                previewEnabled = playRequest == null,
                            )
                        },
                        recordingsContent = {
                            com.skyqlike.iptv.ui.record.RecordingsScreen(
                                recordings = recordings,
                                reminders = reminders,
                                onCancelReminder = { com.skyqlike.iptv.data.remind.Reminders.cancel(this@MainActivity, it.id) },
                                onPlay = { r ->
                                    playRequest = PlayRequest(
                                        listOf(
                                            PlayItem(
                                                key = "rec:${r.id}",
                                                title = r.title,
                                                subtitle = r.channelName,
                                                url = android.net.Uri.fromFile(java.io.File(r.filePath)).toString(),
                                                kind = PlayKind.MOVIE,
                                                imageUrl = r.logoUrl,
                                            )
                                        ),
                                        0,
                                    )
                                },
                                onStop = { com.skyqlike.iptv.data.record.Recorder.stop(applicationContext, it.id) },
                                onCancel = { com.skyqlike.iptv.data.record.Recorder.cancel(applicationContext, it.id) },
                                onDelete = { com.skyqlike.iptv.data.record.Recorder.delete(applicationContext, it.id) },
                                compact = phone,
                            )
                        },
                        matchCentreContent = {
                            com.skyqlike.iptv.ui.sports.MatchCentreScreen(
                                channels = shownChannels,
                                epg = epg,
                                favouriteKeys = favourites,
                                scoresRepo = scoresRepo,
                                onPlay = playChannel,
                                compact = phone,
                            )
                        },
                        multiViewContent = {
                            com.skyqlike.iptv.ui.multiview.MultiViewScreen(
                                channels = shownChannels,
                                epg = epg,
                                favouriteKeys = favourites,
                                recentKeys = recentKeys,
                            )
                        },
                        searchContent = {
                            com.skyqlike.iptv.ui.search.SearchScreen(
                                channels = shownChannels,
                                epg = epg,
                                vod = vod,
                                onNeedCatalog = loadCatalog,
                                onPlayChannel = playChannel,
                                onPlayMovie = playMovie,
                                loadMovieInfo = { vodRepo.movieInfo(activeProvider, it) },
                                loadEpisodes = { vodRepo.episodes(activeProvider, it) },
                                onPlayEpisodes = playEpisodes,
                            )
                        },
                        moviesContent = {
                            VodScreen(
                                state = vod,
                                onNeedCatalog = loadCatalog,
                                loadMovieInfo = { vodRepo.movieInfo(activeProvider, it) },
                                loadEpisodes = { vodRepo.episodes(activeProvider, it) },
                                onPlayMovie = playMovie,
                                onPlayEpisodes = playEpisodes,
                            )
                        },
                        catchUpContent = {
                            CatchUpScreen(
                                compact = phone,
                                channels = shownChannels,
                                state = catchUp,
                                message = catchUpMessage,
                                onNeedData = {
                                    if (catchUp is CatchUpState.Idle || catchUp is CatchUpState.Error) {
                                        catchUp = CatchUpState.Loading
                                        scope.launch {
                                            catchUp = epgRepo.loadPast(activeProvider, channels).fold(
                                                onSuccess = { CatchUpState.Ready(it) },
                                                onFailure = { CatchUpState.Error(it.message ?: "Couldn't load catch-up.") },
                                            )
                                        }
                                    }
                                },
                                onPlay = { ch, show ->
                                    catchUpMessage = "Opening ${show.title}..."
                                    scope.launch {
                                        catchUpRepo.url(activeProvider, ch, show)
                                            .onSuccess { url ->
                                                catchUpMessage = null
                                                playRequest = PlayRequest(
                                                    listOf(
                                                        PlayItem(
                                                            key = "catchup:${ch.id}:${show.start}",
                                                            title = show.title,
                                                            subtitle = ch.name,
                                                            url = url,
                                                            kind = PlayKind.CATCHUP,
                                                            imageUrl = ch.logoUrl,
                                                        )
                                                    ),
                                                    0,
                                                )
                                            }
                                            .onFailure { catchUpMessage = it.message ?: "Couldn't open that show." }
                                    }
                                },
                            )
                        },
                        libraryContent = {
                            LibraryScreen(history = iptvHistory, onPlay = playHistory)
                        },
                        settingsContent = {
                            SettingsScreen(
                                settings = settings,
                                onChange = {
                                    settings = it
                                    settingsStore.save(it)
                                },
                                allChannels = channels,
                                visibleCount = shownChannels.size,
                                adultUnlocked = adultUnlocked,
                                onAdultUnlockedChange = { adultUnlocked = it },
                                providerName = activeProvider?.name ?: "Sample playlist",
                                status = if (epgStatus.isBlank()) status else "$status\n$epgStatus",
                                busy = busy,
                                onRefreshPlaylist = {
                                    forceNext = true
                                    reloadTick++
                                },
                                onRefreshGuide = { guideTick++ },
                                onClearCache = {
                                    this@MainActivity.cacheDir.listFiles()
                                        ?.filter { it.name.startsWith("playlist2_") || it.name.startsWith("epg_") }
                                        ?.forEach { it.delete() }
                                    scope.launch { epgRepo.clearAllSaved() }
                                    forceNext = true
                                    reloadTick++
                                    note = "Saved data cleared. Loading fresh copies..."
                                },
                                onTestTmdb = { tmdbRepo.test(it) },
                                onClearHistory = {
                                    historyStore.clear()
                                    history = historyStore.entries()
                                    recentKeys = historyStore.recentChannels()
                                    note = "Watch history cleared"
                                },
                                providersContent = {
                            ProvidersScreen(
                                providers = providers,
                                activeId = activeId,
                                status = if (epgStatus.isBlank()) status else "$status\n$epgStatus",
                                busy = busy,
                                onSave = { p ->
                                    providers = if (providers.any { it.id == p.id }) {
                                        providers.map { if (it.id == p.id) p else it }
                                    } else {
                                        providers + p
                                    }
                                    store.saveAll(providers)
                                    repo.clearCache(p.id)
                                    store.setActive(p.id)
                                    activeId = p.id
                                    forceNext = true
                                    reloadTick++
                                },
                                onRemove = { p ->
                                    providers = providers.filterNot { it.id == p.id }
                                    store.saveAll(providers)
                                    repo.clearCache(p.id)
                                    scope.launch { epgRepo.forgetProvider(p.id) }
                                    if (activeId == p.id) {
                                        store.setActive(null)
                                        activeId = null
                                    }
                                },
                                onUse = { p ->
                                    store.setActive(p?.id)
                                    activeId = p?.id
                                },
                                onRefresh = {
                                    forceNext = true
                                    reloadTick++
                                },
                            )
                                },
                            )
                        },
                    )

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

                    // A movie or series opened from a Home row. Back returns to Home.
                    val pickedMovie = homeMovie
                    val pickedSeries = homeSeries
                    if (pickedMovie != null || pickedSeries != null) {
                        Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0D))) {
                            if (pickedMovie != null) {
                                com.skyqlike.iptv.ui.vod.MovieDetails(
                                    pickedMovie,
                                    { vodRepo.movieInfo(activeProvider, it) },
                                    playMovie,
                                ) { homeMovie = null }
                            } else if (pickedSeries != null) {
                                com.skyqlike.iptv.ui.vod.SeriesDetails(
                                    pickedSeries,
                                    { vodRepo.episodes(activeProvider, it) },
                                    playEpisodes,
                                ) { homeSeries = null }
                            }
                        }
                    }

                    // The player sits on top of everything. Back returns to the screen you came from.
                    playRequest?.let { request ->
                        PlayerScreen(
                            items = request.items,
                            startIndex = request.start,
                            epg = epg,
                            favouriteKeys = favourites,
                            startPositionFor = { if (settings.resume) historyStore.positionFor(it.key) else 0L },
                            onToggleFavourite = { item ->
                                val key = item.favKey
                                if (key != null) {
                                    favourites = if (key in favourites) favourites - key else favourites + key
                                    favStore.save(favourites)
                                }
                            },
                            onProgress = { item, position, duration ->
                                historyStore.update(item, position, duration)
                                history = historyStore.entries()
                            },
                            onChannelWatched = { item ->
                                item.favKey?.let {
                                    historyStore.addRecentChannel(it)
                                    recentKeys = historyStore.recentChannels()
                                }
                            },
                            onClose = { playRequest = null },
                            aspectDefault = settings.aspect,
                            autoReconnect = settings.autoReconnect,
                            subtitlesOn = settings.subtitlesOn,
                            audioLang = settings.audioLang,
                        )
                    }
                }
            }
            }
        }
    }
}
