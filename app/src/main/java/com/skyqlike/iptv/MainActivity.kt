package com.skyqlike.iptv

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.skyqlike.iptv.data.PlaylistRepository
import com.skyqlike.iptv.data.m3u.M3uParser
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.data.provider.ProviderStore
import com.skyqlike.iptv.ui.home.HomeScreen
import com.skyqlike.iptv.ui.providers.ProvidersScreen
import com.skyqlike.iptv.ui.theme.SkyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SkyTheme {
                val store = remember { ProviderStore(this@MainActivity) }
                val repo = remember { PlaylistRepository(this@MainActivity) }
                val sample = remember {
                    assets.open("sample.m3u").bufferedReader().use { M3uParser.parse(it.readText()) }
                }

                var providers by remember { mutableStateOf(store.all()) }
                var activeId by remember { mutableStateOf(store.activeId()) }
                var channels by remember { mutableStateOf<List<Channel>>(sample) }
                var status by remember { mutableStateOf("Using the built-in sample playlist.") }
                var busy by remember { mutableStateOf(false) }
                var reloadTick by remember { mutableIntStateOf(0) }
                var forceNext by remember { mutableStateOf(false) }

                // Loads channels whenever the active provider changes or a refresh is asked for.
                LaunchedEffect(activeId, reloadTick) {
                    val force = forceNext
                    forceNext = false
                    val provider = providers.firstOrNull { it.id == activeId }
                    if (provider == null) {
                        channels = sample
                        status = "Using the built-in sample playlist."
                    } else {
                        busy = true
                        status = "Loading ${provider.name}..."
                        repo.load(provider, force)
                            .onSuccess {
                                channels = it.channels
                                status = "${provider.name}: ${it.channels.size} channels" +
                                    if (it.fromCache) " (saved copy)." else " loaded."
                            }
                            .onFailure {
                                status = "Couldn't load ${provider.name}: ${it.message}"
                            }
                        busy = false
                    }
                }

                HomeScreen(
                    channels = channels,
                    onPlay = {
                        Toast.makeText(this@MainActivity, "Player arrives in Stage 5: ${it.name}", Toast.LENGTH_SHORT).show()
                    },
                    settingsContent = {
                        ProvidersScreen(
                            providers = providers,
                            activeId = activeId,
                            status = status,
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
            }
        }
    }
}
