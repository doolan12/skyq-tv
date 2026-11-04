package com.skyqlike.iptv.ui.stremio

import com.skyqlike.iptv.data.stremio.StremioItem
import com.skyqlike.iptv.data.stremio.StremioRow
import com.skyqlike.iptv.data.stremio.StremioStream
import com.skyqlike.iptv.data.stremio.StremioStreams
import com.skyqlike.iptv.data.stremio.StremioVideo

/** Everything the screens need from the rest of the app. */
class StremioActions(
    val onNeedData: () -> Unit,
    /** Returns an error message, or null if it worked. */
    val onSignIn: suspend (email: String, password: String) -> String?,
    val onSignOut: () -> Unit,
    val onAddAddon: suspend (link: String) -> String?,
    val onRemoveAddon: (link: String) -> Unit,
    val search: suspend (query: String) -> List<StremioItem>,
    val onToggleFavourite: (StremioItem) -> Unit,
    val byGenre: suspend (type: String, genre: String) -> List<StremioRow>,
    val loadDetails: suspend (StremioItem) -> StremioItem,
    val loadStreams: suspend (type: String, videoId: String) -> StremioStreams,
    val onPlay: (item: StremioItem, video: StremioVideo?, stream: StremioStream) -> Unit,
)
