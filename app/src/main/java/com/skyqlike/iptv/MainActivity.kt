package com.skyqlike.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.skyqlike.iptv.data.vod.VodRepository
import com.skyqlike.iptv.data.vod.VodState
import com.skyqlike.iptv.ui.catchup.CatchUpScreen
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SkyTheme {
                val scope = rememberCoroutineScope()
                val store = remember { ProviderStore(this@MainActivity) }
                val repo = remember { PlaylistRepository(this@MainActivity) }
                val epgRepo = remember { EpgRepository(this@MainActivity) }
                val vodRepo = remember { VodRepository() }
                val catchUpRepo = remember { CatchUpRepository() }
                val favStore = remember { FavouritesStore(this@MainActivity) }
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

                val activeProvider = providers.firstOrNull { it.id == activeId }

                // Starts the player on a live channel (the whole channel list is there for channel up/down).
                val playChannel: (Channel) -> Unit = { ch ->
                    val i = channels.indexOfFirst { it.id == ch.id }
                    if (i >= 0) playRequest = PlayRequest(channels.map { it.toPlayItem() }, i)
                }

                val playHistory: (HistoryEntry) -> Unit = { e ->
                    playRequest = PlayRequest(
                        listOf(PlayItem(e.key, e.title, e.subtitle, e.url, e.kind, imageUrl = e.imageUrl)),
                        0,
                    )
                }

                Box(Modifier.fillMaxSize()) {
                    HomeScreen(
                        channels = channels,
                        onPlay = playChannel,
                        favouriteKeys = favourites,
                        recentKeys = recentKeys,
                        continueWatching = history.filter { !it.finished && it.positionMs > 10_000 },
                        onPlayHistory = playHistory,
                        guideContent = {
                            GuideScreen(
                                channels = channels,
                                epg = epg,
                                status = epgStatus,
                                onPlay = playChannel,
                            )
                        },
                        moviesContent = {
                            VodScreen(
                                state = vod,
                                onNeedCatalog = {
                                    if (vod is VodState.Idle || vod is VodState.Error) {
                                        vod = VodState.Loading
                                        scope.launch {
                                            vod = vodRepo.catalog(activeProvider).fold(
                                                onSuccess = { VodState.Ready(it) },
                                                onFailure = { VodState.Error(it.message ?: "Couldn't load movies and series.") },
                                            )
                                        }
                                    }
                                },
                                loadMovieInfo = { vodRepo.movieInfo(activeProvider, it) },
                                loadEpisodes = { vodRepo.episodes(activeProvider, it) },
                                onPlayMovie = { m ->
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
                                },
                                onPlayEpisodes = { series, episodes, start ->
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
                                },
                            )
                        },
                        catchUpContent = {
                            CatchUpScreen(
                                channels = channels,
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
                            LibraryScreen(history = history, onPlay = playHistory)
                        },
                        settingsContent = {
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

                    // The player sits on top of everything. Back returns to the screen you came from.
                    playRequest?.let { request ->
                        PlayerScreen(
                            items = request.items,
                            startIndex = request.start,
                            epg = epg,
                            favouriteKeys = favourites,
                            startPositionFor = { historyStore.positionFor(it.key) },
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
                        )
                    }
                }
            }
        }
    }
}
