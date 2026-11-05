package com.skyqlike.iptv.data.settings

import android.content.Context

/** Remembers your last few searches on this device, newest first. */
class RecentSearchStore(context: Context) {
    private val prefs = context.getSharedPreferences("reel_recent_searches", Context.MODE_PRIVATE)

    fun load(): List<String> = (prefs.getString("list", "") ?: "").split('\n').filter { it.isNotBlank() }

    fun add(query: String): List<String> {
        val q = query.trim()
        if (q.length < 3) return load()
        val next = (listOf(q) + load().filterNot { it.equals(q, ignoreCase = true) }).take(8)
        prefs.edit().putString("list", next.joinToString("\n")).apply()
        return next
    }

    fun clear() {
        prefs.edit().remove("list").apply()
    }
}

/**
 * Remembers, for each show, where its intro starts and how long it is, learned from when you pressed
 * "Skip ahead" on an earlier episode. Used on episodes the community database knows nothing about.
 */
class LearnedIntroStore(context: Context) {
    private val prefs = context.getSharedPreferences("reel_learned_intro", Context.MODE_PRIVATE)

    /** Returns (startMs, endMs) or null. */
    fun get(showId: String): Pair<Long, Long>? {
        val s = prefs.getLong("$showId.s", -1L)
        val e = prefs.getLong("$showId.e", -1L)
        return if (s >= 0 && e > s) s to e else null
    }

    fun put(showId: String, startMs: Long, endMs: Long) {
        prefs.edit().putLong("$showId.s", startMs).putLong("$showId.e", endMs).apply()
    }
}
