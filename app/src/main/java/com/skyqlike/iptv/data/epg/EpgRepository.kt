package com.skyqlike.iptv.data.epg

import android.content.Context
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.data.model.Programme
import com.skyqlike.iptv.data.provider.Provider
import com.skyqlike.iptv.data.provider.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.zip.GZIPInputStream

/** Time helpers shared by the guide data and the guide screen. */
object EpgTime {
    const val SLOT_MS = 30 * 60_000L
    const val GUIDE_HOURS = 24

    /** The guide starts at the half hour before now, so the current show is easy to see. */
    fun guideStart(now: Long): Long = (now / SLOT_MS) * SLOT_MS - SLOT_MS
}

/**
 * Gets the TV guide for the channels we have.
 * Real providers: downloads the XMLTV file (from the EPG link, or the Xtream guide address).
 * No provider selected: makes a clearly labelled sample guide so the screen can be tested.
 */
class EpgRepository(private val context: Context) {

    suspend fun load(
        provider: Provider?,
        channels: List<Channel>,
        forceRefresh: Boolean,
    ): Result<Map<String, List<Programme>>> = withContext(Dispatchers.IO) {
        val from = EpgTime.guideStart(System.currentTimeMillis())

        if (provider == null) {
            return@withContext Result.success(SampleEpg.generate(channels, from))
        }

        val address = guideAddress(provider)
            ?: return@withContext Result.failure(
                IOException("This provider has no TV guide link. Add an EPG link in its settings.")
            )

        val wanted = channels.mapNotNull { it.epgId }.toHashSet()
        if (wanted.isEmpty()) {
            return@withContext Result.failure(
                IOException("Your channels have no guide IDs (tvg-id), so the guide can't match them.")
            )
        }

        val file = File(context.cacheDir, "epg_${provider.id}.xml")
        val isFresh = file.exists() &&
            System.currentTimeMillis() - file.lastModified() < CACHE_MAX_AGE_MS

        try {
            if (!isFresh || forceRefresh) {
                try {
                    download(address, file)
                } catch (e: Exception) {
                    if (!file.exists()) throw e // no saved copy to fall back on
                }
            }
            val data = openMaybeGzip(file).use {
                XmltvParser.parse(it, wanted, from, from + EpgTime.GUIDE_HOURS * 60 * 60_000L)
            }
            if (data.isEmpty()) {
                Result.failure(IOException("The TV guide loaded, but none of its shows match your channels."))
            } else {
                Result.success(data)
            }
        } catch (e: Exception) {
            Result.failure(IOException(friendly(e)))
        }
    }

    /**
     * Shows that have already finished, for channels that offer catch-up.
     * Read from the same saved guide file, only when the Catch Up screen is opened.
     */
    suspend fun loadPast(
        provider: Provider?,
        channels: List<Channel>,
    ): Result<Map<String, List<Programme>>> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val replayable = channels.filter { it.catchupDays > 0 && it.epgId != null }
        if (replayable.isEmpty()) {
            return@withContext Result.failure(
                IOException("None of your channels offer catch-up. It needs an Xtream login with archive channels.")
            )
        }
        if (provider == null) {
            return@withContext Result.success(SampleEpg.generatePast(replayable, now))
        }

        val address = guideAddress(provider)
            ?: return@withContext Result.failure(
                IOException("Catch-up needs the TV guide, and this provider has no guide link.")
            )
        val wanted = replayable.mapNotNull { it.epgId }.toHashSet()
        val maxDays = replayable.maxOf { it.catchupDays }
        val file = File(context.cacheDir, "epg_${provider.id}.xml")

        try {
            if (!file.exists()) download(address, file)
            val data = openMaybeGzip(file).use {
                XmltvParser.parse(it, wanted, now - maxDays * 24L * 60 * 60_000L, now)
            }
            // Each channel only keeps as many days as it actually offers.
            val limits = replayable.associate { it.epgId!! to it.catchupDays }
            val trimmed = data.mapValues { (id, list) ->
                val earliest = now - (limits[id] ?: 0) * 24L * 60 * 60_000L
                list.filter { it.start >= earliest && it.stop <= now }
            }.filterValues { it.isNotEmpty() }
            if (trimmed.isEmpty()) {
                Result.failure(IOException("The TV guide has no past shows for your catch-up channels."))
            } else {
                Result.success(trimmed)
            }
        } catch (e: Exception) {
            Result.failure(IOException(friendly(e)))
        }
    }

    private fun guideAddress(p: Provider): String? {
        if (p.epgUrl.isNotBlank()) return p.epgUrl.trim()
        if (p.type == ProviderType.XTREAM) {
            val base = p.url.trim().trimEnd('/')
            val user = URLEncoder.encode(p.username.trim(), "UTF-8")
            val pass = URLEncoder.encode(p.password, "UTF-8")
            return "$base/xmltv.php?username=$user&password=$pass"
        }
        return null
    }

    private fun download(address: String, target: File) {
        val conn = URL(address).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 120_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) SkyQLikeIPTV/0.1")
        val tmp = File(target.path + ".tmp")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            conn.inputStream.use { input -> tmp.outputStream().use { out -> input.copyTo(out) } }
            target.delete()
            if (!tmp.renameTo(target)) throw IOException("Couldn't save the TV guide.")
        } finally {
            conn.disconnect()
            tmp.delete()
        }
    }

    /** Some providers send the guide zipped (.gz). This opens either kind. */
    private fun openMaybeGzip(file: File): InputStream {
        val buffered = BufferedInputStream(FileInputStream(file))
        buffered.mark(2)
        val b0 = buffered.read()
        val b1 = buffered.read()
        buffered.reset()
        return if (b0 == 0x1f && b1 == 0x8b) GZIPInputStream(buffered) else buffered
    }

    private fun friendly(e: Exception): String = when {
        e is UnknownHostException -> "Can't reach the TV guide server."
        e is SocketTimeoutException -> "The TV guide server took too long to answer."
        e.message?.contains("401") == true || e.message?.contains("403") == true ->
            "The TV guide server refused access."
        e.message?.contains("404") == true -> "No TV guide found at that address."
        else -> "Couldn't read the TV guide: ${e.message ?: "unknown problem"}"
    }

    private companion object {
        const val CACHE_MAX_AGE_MS = 6L * 60 * 60 * 1000 // 6 hours
    }
}
