package com.skyqlike.iptv.data.provider

enum class ProviderType { M3U, XTREAM }

/**
 * One IPTV provider the user has added.
 * M3U: [url] is the playlist link.
 * XTREAM: [url] is the server address, plus [username] and [password].
 */
data class Provider(
    val id: String,
    val name: String,
    val type: ProviderType,
    val url: String,
    val username: String = "",
    val password: String = "",
    val epgUrl: String = "",
)
