package com.skyqlike.iptv.data.m3u

import com.skyqlike.iptv.data.model.Channel

/**
 * Reads standard M3U / M3U8 playlists (the kind IPTV providers give you).
 * Handles the usual tags: tvg-id, tvg-name, tvg-logo, group-title.
 * Skips lines it doesn't understand instead of crashing.
 */
object M3uParser {

    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(text: String): List<Channel> {
        val channels = ArrayList<Channel>()
        var pendingName: String? = null
        var pendingAttrs: Map<String, String> = emptyMap()

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                pendingAttrs = attrRegex.findAll(line).associate {
                    it.groupValues[1].lowercase() to it.groupValues[2]
                }
                val afterComma = if (line.contains(',')) line.substringAfterLast(',').trim() else ""
                pendingName = afterComma.ifEmpty { pendingAttrs["tvg-name"] ?: "Unnamed channel" }
            } else if (!line.startsWith("#") && pendingName != null) {
                channels += Channel(
                    id = (pendingAttrs["tvg-id"]?.takeIf { it.isNotBlank() } ?: pendingName!!) + "#" + channels.size,
                    name = pendingName!!,
                    logoUrl = pendingAttrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                    group = pendingAttrs["group-title"]?.takeIf { it.isNotBlank() } ?: "Other",
                    streamUrl = line,
                    epgId = pendingAttrs["tvg-id"]?.takeIf { it.isNotBlank() },
                    catchupDays = pendingAttrs["catchup-days"]?.toIntOrNull() ?: 0,
                    xtreamId = pendingAttrs["xtream-id"]?.takeIf { it.isNotBlank() },
                )
                pendingName = null
                pendingAttrs = emptyMap()
            }
        }
        return channels
    }
}
