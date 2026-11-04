package com.skyqlike.iptv.data

import android.content.Context
import com.skyqlike.iptv.data.m3u.M3uParser
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.data.provider.Provider
import com.skyqlike.iptv.data.provider.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException

/**
 * Gets the channel list for a provider.
 * Saves a copy on the device so the app opens fast and still works if the provider is down.
 */
class PlaylistRepository(private val context: Context) {

    data class Loaded(val channels: List<Channel>, val fromCache: Boolean)

    private fun cacheFile(id: String) = File(context.cacheDir, "playlist2_$id.m3u")

    fun clearCache(id: String) {
        cacheFile(id).delete()
    }

    suspend fun load(provider: Provider, forceRefresh: Boolean): Result<Loaded> =
        withContext(Dispatchers.IO) {
            val cache = cacheFile(provider.id)
            val isFresh = cache.exists() &&
                System.currentTimeMillis() - cache.lastModified() < CACHE_MAX_AGE_MS

            if (isFresh && !forceRefresh) {
                val cached = M3uParser.parse(cache.readText())
                if (cached.isNotEmpty()) return@withContext Result.success(Loaded(cached, true))
            }

            try {
                val text = when (provider.type) {
                    ProviderType.M3U -> httpGet(provider.url.trim())
                    ProviderType.XTREAM -> xtreamAsM3u(provider)
                }
                val channels = M3uParser.parse(text)
                if (channels.isEmpty()) throw IOException("No channels found. Check the link or login details.")
                cache.writeText(text)
                Result.success(Loaded(channels, false))
            } catch (e: Exception) {
                // Provider unreachable: fall back to the last saved copy if there is one.
                if (cache.exists()) {
                    val cached = M3uParser.parse(cache.readText())
                    if (cached.isNotEmpty()) return@withContext Result.success(Loaded(cached, true))
                }
                Result.failure(IOException(friendly(e)))
            }
        }

    private fun httpGet(address: String): String {
        val conn = URL(address).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 45_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) SkyQLikeIPTV/0.1")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Xtream Codes logins give channels as data, so we turn them into a normal playlist. */
    private fun xtreamAsM3u(p: Provider): String {
        val base = p.url.trim().trimEnd('/')
        val user = URLEncoder.encode(p.username.trim(), "UTF-8")
        val pass = URLEncoder.encode(p.password, "UTF-8")
        val api = "$base/player_api.php?username=$user&password=$pass"

        val cats = JSONArray(httpGet("$api&action=get_live_categories"))
        val catNames = HashMap<String, String>()
        for (i in 0 until cats.length()) {
            val o = cats.getJSONObject(i)
            catNames[o.optString("category_id")] = o.optString("category_name")
        }

        val streams = JSONArray(httpGet("$api&action=get_live_streams"))
        val sb = StringBuilder("#EXTM3U\n")
        for (i in 0 until streams.length()) {
            val o = streams.getJSONObject(i)
            val id = o.optString("stream_id")
            if (id.isEmpty()) continue
            val name = clean(o.optString("name")).replace(",", " ").ifBlank { "Channel $id" }
            val group = clean(catNames[o.optString("category_id")] ?: "Other")
            val archiveDays = if (o.optString("tv_archive") == "1") {
                o.optString("tv_archive_duration").toIntOrNull()?.takeIf { it > 0 } ?: 3
            } else {
                0
            }
            sb.append("#EXTINF:-1")
                .append(" tvg-id=\"").append(clean(o.optString("epg_channel_id"))).append('"')
                .append(" tvg-logo=\"").append(clean(o.optString("stream_icon"))).append('"')
                .append(" group-title=\"").append(group).append('"')
                .append(" xtream-id=\"").append(id).append('"')
                .append(" catchup-days=\"").append(archiveDays).append('"')
                .append(',').append(name).append('\n')
                .append("$base/live/$user/$pass/$id.m3u8").append('\n')
        }
        return sb.toString()
    }

    private fun clean(s: String) = s.replace("\"", "'").replace("\n", " ").trim()

    private fun friendly(e: Exception): String = when {
        e is UnknownHostException -> "Can't reach that server. Check the address and your internet."
        e is SocketTimeoutException -> "The server took too long to answer."
        e.message?.contains("401") == true || e.message?.contains("403") == true ->
            "The server refused the login. Check the username and password."
        e.message?.contains("404") == true -> "Nothing found at that address. Check the link."
        e is org.json.JSONException -> "The server's reply wasn't what a normal IPTV login returns. Check the details."
        else -> e.message ?: "Something went wrong loading the playlist."
    }

    private companion object {
        const val CACHE_MAX_AGE_MS = 6L * 60 * 60 * 1000 // 6 hours
    }
}
