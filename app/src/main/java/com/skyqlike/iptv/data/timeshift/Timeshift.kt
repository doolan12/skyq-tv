package com.skyqlike.iptv.data.timeshift

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Keeps the last stretch of a live HLS channel on disk, so it can be paused and rewound.
 *
 * It downloads each new piece of the channel into a folder and keeps a small local playlist (live.m3u8) listing
 * the pieces it holds. The player plays that local playlist instead of the channel directly. When the folder
 * grows past [maxBytes], the oldest pieces are deleted.
 *
 * It only handles plain HLS channels. Anything else (encrypted, fragmented, not HLS) sets [failed], and the
 * player carries on with the normal direct stream.
 */
class Timeshift(private val url: String, private val dir: File, private val maxBytes: Long) {

    private class Seg(val name: String, val seconds: Double, val bytes: Long, val gap: Boolean)

    @Volatile var failed = false
        private set
    /** True once enough is saved for the player to start. */
    @Volatile var ready = false
        private set
    /** True if the channel stopped arriving after it had been working. */
    @Volatile var broken = false
        private set
    @Volatile private var stopped = false

    private val segs = ArrayList<Seg>()
    private var firstSeq = 0L
    private var nextSeq = 0L
    private var thread: Thread? = null
    private val ua = "Mozilla/5.0 (Linux; Android) SkyQLikeIPTV/0.1"

    val playlistFile: File get() = File(dir, "live.m3u8")

    fun start() {
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        thread = Thread({
            try {
                run()
            } catch (e: Exception) {
                if (ready) broken = true else failed = true
            }
        }, "timeshift").also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        stopped = true
        thread?.interrupt()
        Thread {
            try {
                thread?.join(3_000)
                dir.listFiles()?.forEach { it.delete() }
                dir.delete()
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun resolve(base: String, ref: String): String = URI(base).resolve(ref.trim()).toString()

    private fun open(address: String): HttpURLConnection =
        com.skyqlike.iptv.data.provider.HostFailover.tryServers(address) { openOnce(it) }

    private fun openOnce(address: String): HttpURLConnection {
        var target = address
        repeat(6) {
            val c = URL(target).openConnection() as HttpURLConnection
            c.connectTimeout = 12_000
            c.readTimeout = 12_000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", ua)
            val code = c.responseCode
            when (code) {
                in 200..299 -> return c
                301, 302, 303, 307, 308 -> {
                    val next = c.getHeaderField("Location")
                    c.disconnect()
                    if (next.isNullOrBlank()) throw IOException("Redirect with nowhere to go.")
                    target = URI(target).resolve(next.trim()).toString()
                }
                else -> {
                    c.disconnect()
                    throw IOException("HTTP $code")
                }
            }
        }
        throw IOException("Too many redirects.")
    }

    private fun text(address: String): String {
        val c = open(address)
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun run() {
        var media = url
        var body = text(url)
        if (!body.trimStart().startsWith("#EXTM3U")) throw IOException("Not an HLS playlist.")
        if (body.contains("#EXT-X-STREAM-INF")) {
            val lines = body.lines()
            var best = -1L
            var bestUri: String? = null
            for (i in lines.indices) {
                if (lines[i].startsWith("#EXT-X-STREAM-INF")) {
                    val bw = Regex("BANDWIDTH=(\\d+)").find(lines[i])?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    val uri = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                    if (uri != null && bw > best) { best = bw; bestUri = uri }
                }
            }
            media = resolve(url, bestUri ?: throw IOException("No stream in the playlist."))
            body = ""
        }
        val seen = LinkedHashSet<String>()
        var failures = 0
        var target = 6.0
        while (!stopped) {
            try {
                if (body.isEmpty()) body = text(media)
                if (body.contains("#EXT-X-MAP") || body.contains("#EXT-X-BYTERANGE")) throw IOException("Format not supported.")
                if (Regex("#EXT-X-KEY:METHOD=(?!NONE)").containsMatchIn(body)) throw IOException("Encrypted.")
                target = Regex("#EXT-X-TARGETDURATION:(\\d+)").find(body)?.groupValues?.get(1)?.toDoubleOrNull() ?: target
                var dur = 0.0
                var gap = false
                for (line in body.lines()) {
                    if (stopped) return
                    val l = line.trim()
                    when {
                        l.startsWith("#EXTINF:") -> dur = l.removePrefix("#EXTINF:").substringBefore(',').toDoubleOrNull() ?: target
                        l.startsWith("#EXT-X-DISCONTINUITY") && !l.contains("SEQUENCE") -> gap = true
                        l.isEmpty() || l.startsWith("#") -> Unit
                        else -> {
                            val seg = resolve(media, l)
                            if (seen.add(seg)) {
                                download(seg, if (dur > 0) dur else target, gap)
                            }
                            dur = 0.0
                            gap = false
                        }
                    }
                }
                if (seen.size > 400) {
                    val keep = seen.toList().takeLast(200)
                    seen.clear(); seen.addAll(keep)
                }
                failures = 0
                broken = false
                body = ""
                Thread.sleep((target * 500).toLong().coerceIn(1_000L, 4_000L))
            } catch (e: InterruptedException) {
                return
            } catch (e: IOException) {
                failures++
                if (!ready && failures >= 3) throw e
                if (ready && failures >= 6) broken = true
                body = ""
                Thread.sleep(2_000)
            }
        }
    }

    private fun download(address: String, seconds: Double, gap: Boolean) {
        val ext = address.substringBefore('?').substringAfterLast('.', "ts").takeIf { it.length in 2..4 } ?: "ts"
        val name = "s$nextSeq.$ext"
        val tmp = File(dir, "$name.part")
        var bytes = 0L
        val c = open(address)
        try {
            FileOutputStream(tmp).use { out ->
                val buf = ByteArray(64 * 1024)
                c.inputStream.use { input ->
                    while (true) {
                        if (stopped) return
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        bytes += n
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        if (bytes == 0L) { tmp.delete(); throw IOException("Empty piece.") }
        val done = File(dir, name)
        if (!tmp.renameTo(done)) throw IOException("Could not save a piece.")
        synchronized(segs) {
            segs.add(Seg(name, seconds, bytes, gap))
            nextSeq++
            var total = segs.sumOf { it.bytes }
            while (total > maxBytes && segs.size > 3) {
                val old = segs.removeAt(0)
                File(dir, old.name).delete()
                total -= old.bytes
                firstSeq++
            }
            writePlaylist()
        }
        if (segs.size >= 2) ready = true
    }

    private fun writePlaylist() {
        val target = Math.ceil(segs.maxOf { it.seconds }).toInt().coerceAtLeast(1)
        val sb = StringBuilder()
        sb.append("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:").append(target).append('\n')
        sb.append("#EXT-X-MEDIA-SEQUENCE:").append(firstSeq).append('\n')
        for ((i, s) in segs.withIndex()) {
            if (s.gap && i > 0) sb.append("#EXT-X-DISCONTINUITY\n")
            sb.append("#EXTINF:").append(String.format(java.util.Locale.US, "%.3f", s.seconds)).append(",\n").append(s.name).append('\n')
        }
        val tmp = File(dir, "live.tmp")
        tmp.writeText(sb.toString())
        tmp.renameTo(playlistFile)
    }
}
