package com.skyqlike.iptv.data.record

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Saves a live channel to a file until a stop time, or until [stop] is set.
 *
 * Most providers can send a channel as one plain continuous stream (".ts"), which is copied straight to the file.
 * Channels that only come as an HLS playlist (".m3u8") are saved by fetching each new piece as it appears.
 */
class StreamCapture(private val url: String, private val file: File, private val endAtMs: Long) {

    @Volatile
    var stop = false

    private val ua = "Mozilla/5.0 (Linux; Android) SkyQLikeIPTV/0.1"
    private var total = 0L

    private fun done() = stop || System.currentTimeMillis() >= endAtMs

    /** Runs until finished. Returns how many bytes were saved. Throws if nothing could be saved. */
    fun run(): Long {
        val path = url.substringBefore('?')
        val xtreamLive = path.contains("/live/") && path.endsWith(".m3u8")
        when {
            xtreamLive -> {
                // Xtream providers can also send the same channel as a plain stream, which is the more reliable thing to save.
                try {
                    copyRaw(path.removeSuffix(".m3u8") + ".ts")
                } catch (e: IOException) {
                    if (total > 0L) throw e
                    hls(url)
                }
            }
            path.endsWith(".m3u8") -> hls(url)
            else -> copyRaw(url)
        }
        return total
    }

    private fun open(address: String): HttpURLConnection {
        val c = URL(address).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 15_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", ua)
        val code = c.responseCode
        if (code !in 200..299) {
            c.disconnect()
            throw IOException("HTTP $code")
        }
        return c
    }

    private fun pump(input: InputStream, out: FileOutputStream, checkTime: Boolean) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            if (checkTime && done()) return
            if (stop) return
            val n = input.read(buffer)
            if (n < 0) return
            out.write(buffer, 0, n)
            total += n
        }
    }

    private fun copyRaw(address: String) {
        FileOutputStream(file, true).use { out ->
            var failures = 0
            while (!done()) {
                val before = total
                try {
                    val c = open(address)
                    try {
                        if (c.contentType?.contains("mpegurl", ignoreCase = true) == true) throw IOException("playlist, not a stream")
                        c.inputStream.use { pump(it, out, true) }
                    } finally {
                        c.disconnect()
                    }
                    // The server ended the stream early: reconnect and carry on, unless we are done.
                    failures = if (total > before) 0 else failures + 1
                } catch (e: IOException) {
                    failures = if (total > before) 1 else failures + 1
                    if (total == 0L && failures >= 2) throw e
                }
                if (failures >= 6) throw IOException("The channel keeps dropping out.")
                if (!done()) Thread.sleep(2_000)
            }
            out.flush()
        }
    }

    private fun text(address: String): String {
        val c = open(address)
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun resolve(base: String, ref: String): String = URI(base).resolve(ref.trim()).toString()

    private fun hls(address: String) {
        var media = address
        var master = text(address)
        // A master playlist lists several qualities. Save the best one.
        if (master.contains("#EXT-X-STREAM-INF")) {
            val lines = master.lines()
            var best = -1L
            var bestUri: String? = null
            for (i in lines.indices) {
                if (lines[i].startsWith("#EXT-X-STREAM-INF")) {
                    val bw = Regex("BANDWIDTH=(\\d+)").find(lines[i])?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    val uri = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                    if (uri != null && bw > best) {
                        best = bw
                        bestUri = uri
                    }
                }
            }
            media = resolve(address, bestUri ?: throw IOException("No stream found in the playlist."))
            master = ""
        }
        val seen = LinkedHashSet<String>()
        FileOutputStream(file, true).use { out ->
            var failures = 0
            while (!done()) {
                try {
                    val body = if (master.isNotEmpty()) master else text(media)
                    master = ""
                    if (body.contains("#EXT-X-MAP")) throw IOException("This channel's stream format can't be recorded.")
                    val target = Regex("#EXT-X-TARGETDURATION:(\\d+)").find(body)?.groupValues?.get(1)?.toLongOrNull() ?: 6L
                    for (line in body.lines()) {
                        if (done()) break
                        if (line.isBlank() || line.startsWith("#")) continue
                        val seg = resolve(media, line)
                        if (!seen.add(seg)) continue
                        val c = open(seg)
                        try {
                            c.inputStream.use { pump(it, out, false) }
                        } finally {
                            c.disconnect()
                        }
                    }
                    if (seen.size > 400) {
                        val keep = seen.toList().takeLast(200)
                        seen.clear()
                        seen.addAll(keep)
                    }
                    failures = 0
                    if (body.contains("#EXT-X-ENDLIST")) break
                    Thread.sleep((target * 500L).coerceIn(1_000L, 4_000L))
                } catch (e: IOException) {
                    failures++
                    if (total == 0L && failures >= 2) throw e
                    if (failures >= 8) throw IOException("The channel keeps dropping out.")
                    Thread.sleep(2_000)
                }
            }
            out.flush()
        }
    }
}
