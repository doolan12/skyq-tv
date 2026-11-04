package com.skyqlike.iptv.data.provider

import java.net.URLEncoder

/** Builds the web addresses an Xtream Codes provider uses, from the user's login. */
class XtreamAccess(provider: Provider) {
    val base: String = provider.url.trim().trimEnd('/')
    val user: String = URLEncoder.encode(provider.username.trim(), "UTF-8")
    val pass: String = URLEncoder.encode(provider.password, "UTF-8")

    fun api(action: String, extra: String = ""): String =
        "$base/player_api.php?username=$user&password=$pass&action=$action$extra"

    /** The account info address (includes the server's time zone). */
    fun infoUrl(): String = "$base/player_api.php?username=$user&password=$pass"

    /** Replay link: [startText] is the start time written in the server's own time zone, like 2026-11-04:19-30. */
    fun timeshiftUrl(minutes: Int, startText: String, streamId: String): String =
        "$base/timeshift/$user/$pass/$minutes/$startText/$streamId.m3u8"

    fun movieUrl(id: String, extension: String): String = "$base/movie/$user/$pass/$id.$extension"

    fun seriesUrl(id: String, extension: String): String = "$base/series/$user/$pass/$id.$extension"
}
