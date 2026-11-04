package com.skyqlike.iptv.data.provider

import java.net.URLEncoder

/** Builds the web addresses an Xtream Codes provider uses, from the user's login. */
class XtreamAccess(provider: Provider) {
    val base: String = provider.url.trim().trimEnd('/')
    val user: String = URLEncoder.encode(provider.username.trim(), "UTF-8")
    val pass: String = URLEncoder.encode(provider.password, "UTF-8")

    fun api(action: String, extra: String = ""): String =
        "$base/player_api.php?username=$user&password=$pass&action=$action$extra"

    fun movieUrl(id: String, extension: String): String = "$base/movie/$user/$pass/$id.$extension"

    fun seriesUrl(id: String, extension: String): String = "$base/series/$user/$pass/$id.$extension"
}
