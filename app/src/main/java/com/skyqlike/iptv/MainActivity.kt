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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.skyqlike.iptv.data.FavouritesStore
import com.skyqlike.iptv.data.PlaylistRepository
import com.skyqlike.iptv.data.epg.EpgRepository
import com.skyqlike.iptv.data.m3u.M3uParser
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.data.model.Programme
import com.skyqlike.iptv.data.model.favKey
import com.skyqlike.iptv.data.provider.ProviderStore
import com.skyqlike.iptv.ui.guide.GuideScreen
import com.skyqlike.iptv.ui.home.HomeScreen
import com.skyqlike.iptv.ui.player.PlayerScreen
import com.skyqlike.iptv.ui.providers.ProvidersScreen
import com.skyqlike.iptv.ui.theme.SkyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SkyTheme {
                val store = remember { ProviderStore(this@MainActivity) }
                val repo = remember { PlaylistRepository(this@MainActivity) }
                val epgRepo = remember { EpgRepository(this@MainActivity) }
                val favStore = remember { FavouritesStore(this@MainActivity) }
                val sample = remember {
                    assets.open("sample.m3u").bufferedReader().use { M3uParser.parse(it.readText()) }
                }

                var providers by remember { mutableStateOf(store.all()) }
                var activeId by remember { mutableStateOf(store.activeId()) }
                var channels by remember { mutableStateOf<List<Channel>>(sample) }
                var epg by remember { mutableStateOf<Map<String, List<Programme>>>(emptyMap()) }
                var favourites by remember { mutableStateOf(favStore.all()) }
                var status by remember { mutableStateOf("Using the built-in sample playlist.") }
                var epgStatus by remember { mutableStateOf("") }
                var busy by remember { mutableStateOf(false) }
                var reloadTick by remember { mutableIntStateOf(0) }
                var forceNext by remember { mutableStateOf(false) }
                var playingIndex by remember { mutableStateOf<Int?>(null) }

                // Loads channels, then the TV guide, whenever the provider changes or a refresh is asked for.
                LaunchedEffect(activeId, reloadTick) {
                    val force = forceNext
                    forceNext = false
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

                // Starts the player on whichever channel was picked.
                val play: (Channel) -> Unit = { ch ->
                    val i = channels.indexOfFirst { it.id == ch.id }
                    if (i >= 0) playingIndex = i
                }

                Box(Modifier.fillMaxSize()) {
                    HomeScreen(
                        channels = channels,
                        onPlay = play,
                        favouriteKeys = favourites,
                        guideContent = {
                            GuideScreen(
                                channels = channels,
                                epg = epg,
                                status = epgStatus,
                                onPlay = play,
                            )
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
                    playingIndex?.let { start ->
                        PlayerScreen(
                            channels = channels,
                            startIndex = start,
                            epg = epg,
                            favouriteKeys = favourites,
                            onToggleFavourite = { ch ->
                                favourites = if (ch.favKey in favourites) favourites - ch.favKey else favourites + ch.favKey
                                favStore.save(favourites)
                            },
                            onClose = { playingIndex = null },
                        )
                    }
                }
            }
        }
    }
}
