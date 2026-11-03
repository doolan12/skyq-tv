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
