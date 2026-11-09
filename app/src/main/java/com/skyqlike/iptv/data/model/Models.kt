package com.skyqlike.iptv.data.model

enum class PlayKind { MOVIE, EPISODE }

/** Anything the player can play: a movie or an episode. */
data class PlayItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val url: String,
    val kind: PlayKind,
    val imageUrl: String? = null,
    /** For an episode, its addon id like tt0944947:1:1. Used to look up intro times. */
    val externalId: String? = null,
    /** Subtitles found by addons such as OpenSubtitles. They show up in the player's Subtitles menu. */
    val subtitles: List<ExternalSubtitle> = emptyList(),
    /** Where the other streams for this title are kept ("type|videoId"), so another can be tried if this one fails. */
    val streamKey: String? = null,
    val cast: List<String> = emptyList(),
    val director: String = "",
)

/** A subtitle file from an addon: where it is, its language code, and the name shown in the menu. */
data class ExternalSubtitle(val url: String, val language: String, val label: String)
