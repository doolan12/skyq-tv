package com.skyqlike.iptv.data.stremio

/** What a Stremio addon says it can do (from its manifest.json). */
data class StremioResource(val name: String, val types: List<String>, val idPrefixes: List<String>)

data class StremioCatalogDef(
    val type: String,
    val id: String,
    val name: String,
    val supportsSearch: Boolean,
    /** True if the catalog can't be shown without choosing an extra option (like a genre). */
    val needsExtra: Boolean,
    /** The genres this list can be narrowed to (empty if it can't be filtered by genre). */
    val genreOptions: List<String> = emptyList(),
    /** True if a genre is the only extra choice it needs, so it can be asked for with just a genre. */
    val genreOnly: Boolean = false,
)

data class StremioAddon(
    val name: String,
    /** The address the addon lives at (its manifest address without the "/manifest.json" part). */
    val baseUrl: String,
    val types: List<String>,
    val idPrefixes: List<String>,
    val resources: List<StremioResource>,
    val catalogs: List<StremioCatalogDef>,
)

/** One episode of a series, from the addon's information about the series. */
data class StremioVideo(
    val id: String,
    val title: String,
    val season: Int,
    val episode: Int,
    val thumbnail: String?,
)

/** Can this addon answer [resource] ("meta", "stream"...) for this type and id? */
fun StremioAddon.supports(resource: String, type: String, id: String): Boolean =
    resources.any { r ->
        r.name == resource && type in r.types && (r.idPrefixes.isEmpty() || r.idPrefixes.any { id.startsWith(it) })
    }

/** A movie or series. */
data class StremioItem(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val description: String = "",
    val year: String = "",
    val rating: String = "",
    val genres: List<String> = emptyList(),
    val runtime: String = "",
    /** The addon this came from, so its own details are asked for first. */
    val sourceBase: String = "",
    val videos: List<StremioVideo> = emptyList(),
    /** The YouTube video id of the trailer, if the addon lists one. */
    val trailerId: String? = null,
    /** Actors and director from the addon, for the player's Cast panel. */
    val cast: List<String> = emptyList(),
    val director: String = "",
)

data class StremioRow(val title: String, val type: String, val items: List<StremioItem>)

data class StremioStream(
    val addonName: String,
    val name: String,
    val description: String,
    val url: String,
    val sizeBytes: Long?,
)

data class StremioStreams(
    val playable: List<StremioStream>,
    /** Streams that only exist as torrents. They can't be played here. */
    val torrentOnly: Int,
    /** Addons that didn't answer. */
    val failedAddons: Int,
)

data class StremioHome(
    val addons: List<StremioAddon>,
    val rows: List<StremioRow>,
    val failedAddons: Int,
    /** How each addon did while loading, for the Addon health page. */
    val health: List<AddonHealth> = emptyList(),
)

/** How one addon did the last time Home loaded. Never holds the addon's web address, which can contain a key. */
data class AddonHealth(
    val name: String,
    val reachable: Boolean,
    val manifestMs: Long,
    val listsTried: Int,
    val listsWithTitles: Int,
    val slowestListMs: Long,
    val note: String = "",
)

/** How one addon did the last time streams were looked up. */
data class StreamStat(val name: String, val ms: Long, val count: Int, val ok: Boolean)

/** Who is signed in, and any addon links added by hand. */
data class StremioSession(
    val email: String = "",
    val authKey: String = "",
    val manualAddons: List<String> = emptyList(),
) {
    val signedIn: Boolean get() = authKey.isNotBlank()
    val hasAnything: Boolean get() = signedIn || manualAddons.isNotEmpty()
}

sealed interface StremioState {
    data object Idle : StremioState
    data object Loading : StremioState
    data class Error(val message: String) : StremioState
    data class Ready(val home: StremioHome) : StremioState
}
