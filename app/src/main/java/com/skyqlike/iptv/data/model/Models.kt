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

/** The key used to remember a favourite channel (name plus stream address, so it's unique). */
val Channel.favKey: String get() = "$name|$streamUrl"

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
