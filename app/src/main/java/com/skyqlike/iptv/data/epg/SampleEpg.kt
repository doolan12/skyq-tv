package com.skyqlike.iptv.data.epg

import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.data.model.Programme
import java.util.Random

/** Made-up guide data for the sample playlist, so the TV guide can be tried without a provider. */
object SampleEpg {

    private val titles = listOf(
        "Morning Brief", "Wildlife Wonders", "Cooking Hour", "The Late Film",
        "Match Day Live", "Kids Zone", "Evening News", "Mystery Theatre",
        "Travel Diaries", "Comedy Night", "Deep Blue", "Sports Roundup",
    )

    private val lengthsMinutes = intArrayOf(30, 60, 90, 60)

    /** Made-up past shows (up to each channel's catch-up days) for testing the Catch Up screen. */
    fun generatePast(channels: List<Channel>, now: Long): Map<String, List<Programme>> {
        val out = HashMap<String, List<Programme>>()
        val slot = 30L * 60_000
        for (ch in channels) {
            val key = ch.epgId ?: continue
            val rnd = Random(key.hashCode().toLong() + 7)
            var t = ((now - ch.catchupDays * 24L * 60 * 60_000) / slot) * slot + slot
            var i = rnd.nextInt(titles.size)
            val list = ArrayList<Programme>()
            while (true) {
                val stop = t + lengthsMinutes[rnd.nextInt(lengthsMinutes.size)] * 60_000L
                if (stop > now) break
                list += Programme(
                    channelEpgId = key,
                    title = titles[i % titles.size],
                    description = "Sample programme for testing catch-up. This is not a real broadcast.",
                    genre = ch.group,
                    start = t,
                    stop = stop,
                    iconUrl = null,
                )
                t = stop
                i++
            }
            out[key] = list
        }
        return out
    }

    fun generate(channels: List<Channel>, from: Long): Map<String, List<Programme>> {
        val out = HashMap<String, List<Programme>>()
        val end = from + 30L * 60 * 60_000
        for (ch in channels) {
            val key = ch.epgId ?: continue
            val rnd = Random(key.hashCode().toLong())
            var t = from - 3L * 60 * 60_000
            var i = rnd.nextInt(titles.size)
            val list = ArrayList<Programme>()
            while (t < end) {
                val stop = t + lengthsMinutes[rnd.nextInt(lengthsMinutes.size)] * 60_000L
                list += Programme(
                    channelEpgId = key,
                    title = titles[i % titles.size],
                    description = "Sample programme for testing the TV guide. This is not a real broadcast.",
                    genre = ch.group,
                    start = t,
                    stop = stop,
                    iconUrl = null,
                )
                t = stop
                i++
            }
            out[key] = list
        }
        return out
    }
}
