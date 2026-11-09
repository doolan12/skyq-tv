package com.skyqlike.iptv.data.epg

import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.data.model.Programme

/** What is on this channel right now, or null if the guide has nothing for it. */
fun Map<String, List<Programme>>.nowOn(channel: Channel, nowMs: Long): Programme? {
    val id = channel.epgId ?: return null
    return this[id]?.firstOrNull { it.start <= nowMs && nowMs < it.stop }
}

/** The programme that follows what is on now, or null if the guide has nothing after it. */
fun Map<String, List<Programme>>.nextOn(channel: Channel, nowMs: Long): Programme? {
    val id = channel.epgId ?: return null
    return this[id]?.firstOrNull { it.start > nowMs }
}

/** How far through a programme we are, from 0 to 1. */
fun Programme.progressAt(nowMs: Long): Float =
    ((nowMs - start).toFloat() / (stop - start).coerceAtLeast(1L)).coerceIn(0f, 1f)
