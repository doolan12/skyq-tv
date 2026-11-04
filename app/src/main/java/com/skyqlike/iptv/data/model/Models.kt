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
)
