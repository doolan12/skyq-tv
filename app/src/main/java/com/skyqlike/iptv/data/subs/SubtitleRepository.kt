package com.skyqlike.iptv.data.subs

import android.content.Context
import com.skyqlike.iptv.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

/** One subtitle file on offer. */
data class SubResult(val label: String, val language: String, val path: String)

/**
 * Finds subtitles on SubDL (needs a free key from subdl.com) and keeps the one you choose for each title,
 * so it is switched on again next time you play it.
 */
class SubtitleRepository(context: Context) {
    private val dir = File(context.filesDir, "subtitles").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("subtitles", Context.MODE_PRIVATE)

    suspend fun search(key: String, title: String, year: String?, season: Int, episode: Int, language: String): List<SubResult> =
        withContext(Dispatchers.IO) {
            if (key.isBlank() || title.isBlank()) return@withContext emptyList()
            val tv = season > 0 && episode > 0
            val sb = StringBuilder("https://api.subdl.com/api/v1/subtitles?subs_per_page=20&api_key=")
            sb.append(URLEncoder.encode(key, "UTF-8"))
            sb.append("&film_name=").append(URLEncoder.encode(title, "UTF-8"))
            sb.append("&type=").append(if (tv) "tv" else "movie")
            sb.append("&languages=").append(language.uppercase())
            if (tv) sb.append("&season_number=$season&episode_number=$episode")
            year?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) && !tv }?.let { sb.append("&year=$it") }
            val body = try {
                Http.get(sb.toString(), 20_000)
            } catch (e: Exception) {
                return@withContext emptyList()
            }
            val out = ArrayList<SubResult>()
            try {
                val arr = JSONObject(body).optJSONArray("subtitles") ?: return@withContext emptyList()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val path = o.optString("url")
                    if (path.isBlank()) continue
                    val label = o.optString("release_name").ifBlank { o.optString("name") }.ifBlank { "Subtitle ${i + 1}" }
                    out += SubResult(label, o.optString("language").ifBlank { o.optString("lang") }, path)
                }
            } catch (e: Exception) {
                return@withContext emptyList()
            }
            out
        }

    /** Downloads the chosen subtitle, remembers it for [itemKey], and returns the file. Null if it couldn't be fetched. */
    suspend fun download(itemKey: String, result: SubResult): File? = withContext(Dispatchers.IO) {
        try {
            val address = if (result.path.startsWith("http")) result.path else "https://dl.subdl.com" + result.path
            val conn = URL(address).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) SkyQLikeIPTV/0.1")
            val bytes = try {
                if (conn.responseCode !in 200..299) return@withContext null
                conn.inputStream.use { it.readBytes() }
            } finally {
                conn.disconnect()
            }
            val (name, data) = firstSubtitleIn(bytes) ?: return@withContext null
            val ext = name.substringAfterLast('.', "srt").lowercase()
            val file = File(dir, "${itemKey.hashCode().toUInt()}.$ext")
            // Media3 reads subtitles as UTF-8. Many files are in an older Western encoding, so convert those.
            val text = utf8(data)
            file.writeBytes(text.toByteArray(Charsets.UTF_8))
            prefs.edit().putString(itemKey, file.absolutePath).apply()
            tidy()
            file
        } catch (e: Exception) {
            null
        }
    }

    /** The subtitle chosen earlier for this title, if its file is still there. */
    fun saved(itemKey: String): File? = prefs.getString(itemKey, null)?.let(::File)?.takeIf { it.exists() }

    private fun firstSubtitleIn(bytes: ByteArray): Pair<String, ByteArray>? {
        // A zip file starts with "PK". Anything else is taken to be the subtitle itself.
        if (bytes.size < 4 || bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte()) return "sub.srt" to bytes
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val n = entry.name.lowercase()
                if (!entry.isDirectory && (n.endsWith(".srt") || n.endsWith(".vtt") || n.endsWith(".ass") || n.endsWith(".ssa"))) {
                    return entry.name to zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        return null
    }

    private fun utf8(data: ByteArray): String {
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(java.nio.ByteBuffer.wrap(data)).toString().removePrefix("﻿")
        } catch (e: CharacterCodingException) {
            String(data, charset("windows-1252"))
        }
    }

    /** Keeps the folder small: only the 60 newest subtitle files are kept. */
    private fun tidy() {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(60).forEach { runCatching { it.delete() } }
    }
}
