package com.skyqlike.iptv.ui.player

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/**
 * Moves a subtitle file earlier or later in time (for subtitles that are out of step with the picture).
 * It downloads the file once, shifts every time stamp, and saves the result in the app's cache.
 */
object SubtitleShift {
    private val originals = HashMap<String, String>()
    private val stamp = Regex("(?:(\\d{1,2}):)?(\\d{2}):(\\d{2})([,.])(\\d{3})")

    suspend fun shifted(context: Context, url: String, offsetMs: Long): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val path = url.substringBefore('?').lowercase()
            if (path.endsWith(".ass") || path.endsWith(".ssa")) return@runCatching null
            val text = synchronized(originals) { originals[url] } ?: run {
                val conn = URL(url).openConnection().apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Reelhouse/1.0")
                }
                conn.getInputStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
                    .also { synchronized(originals) { originals[url] = it } }
            }
            val out = text.lineSequence().joinToString("\n") { line ->
                if (!line.contains("-->")) line
                else stamp.replace(line) { m ->
                    val h = m.groupValues[1].ifEmpty { "0" }.toLong()
                    val total = (((h * 60 + m.groupValues[2].toLong()) * 60 + m.groupValues[3].toLong()) * 1000 +
                        m.groupValues[5].toLong() + offsetMs).coerceAtLeast(0L)
                    val ms = total % 1000
                    val s = (total / 1000) % 60
                    val mi = (total / 60_000) % 60
                    val hr = total / 3_600_000
                    "%02d:%02d:%02d%s%03d".format(hr, mi, s, m.groupValues[4], ms)
                }
            }
            val ext = if (path.endsWith(".vtt")) "vtt" else "srt"
            val file = File(context.cacheDir, "subs_${url.hashCode()}_$offsetMs.$ext")
            file.writeText(out)
            Uri.fromFile(file)
        }.getOrNull()
    }
}
