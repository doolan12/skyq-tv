package com.skyqlike.iptv.data.model

/** One live channel from a playlist. */
data class Channel(
    val id: String,
    val name: String,
    val logoUrl: String?,
    val group: String,
    val streamUrl: String,
    val epgId: String?,
)

enum class PlayKind { LIVE, MOVIE, EPISODE, CATCHUP }

/** Anything the player can play: a live channel, a movie, an episode or a catch-up show. */
data class PlayItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val url: String,
    val kind: PlayKind,
    val epgId: String? = null,
    val favKey: String? = null,
    val imageUrl: String? = null,
)

/** The key used to remember a favourite channel (name plus stream address, so it's unique). */
val Channel.favKey: String get() = "$name|$streamUrl"

fun Channel.toPlayItem() = PlayItem(
    key = "live:$id",
    title = name,
    subtitle = group,
    url = streamUrl,
    kind = PlayKind.LIVE,
    epgId = epgId,
    favKey = favKey,
    imageUrl = logoUrl,
)

/** One TV show in the guide. Times are milliseconds since 1970 (UTC). */
data class Programme(
    val channelEpgId: String,
    val title: String,
    val description: String,
    val genre: String,
    val start: Long,
    val stop: Long,
    val iconUrl: String?,
)
