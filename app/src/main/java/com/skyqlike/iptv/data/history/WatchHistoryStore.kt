package com.skyqlike.iptv.data.history

import android.content.Context
import com.skyqlike.iptv.data.model.PlayItem
import com.skyqlike.iptv.data.model.PlayKind
import org.json.JSONArray
import org.json.JSONObject

/** One movie or episode the user has watched (or part-watched). */
data class HistoryEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val url: String,
    val imageUrl: String?,
    val kind: PlayKind,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val finished: Boolean,
)

/** Remembers where one profile got up to in movies and episodes (for Continue Watching). */
class WatchHistoryStore(context: Context, profileId: String) {

    private val prefs = context.getSharedPreferences("history_$profileId", Context.MODE_PRIVATE)

    fun entries(): List<HistoryEntry> {
        val text = prefs.getString("entries", null) ?: return emptyList()
        return try {
            val arr = JSONArray(text)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                HistoryEntry(
                    key = o.getString("key"),
                    title = o.getString("title"),
                    subtitle = o.optString("subtitle"),
                    url = o.getString("url"),
                    imageUrl = o.optString("imageUrl").takeIf { it.isNotBlank() },
                    kind = PlayKind.valueOf(o.getString("kind")),
                    positionMs = o.optLong("positionMs"),
                    durationMs = o.optLong("durationMs"),
                    updatedAt = o.optLong("updatedAt"),
                    finished = o.optBoolean("finished"),
                )
            }.sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Where to resume this movie or episode from (0 if not started or already finished). */
    fun positionFor(key: String): Long =
        entries().firstOrNull { it.key == key }?.takeIf { !it.finished }?.positionMs ?: 0L

    fun update(item: PlayItem, positionMs: Long, durationMs: Long) {
        val finished = durationMs > 0 && positionMs >= durationMs * 0.95
        if (!finished && positionMs < 10_000) return // barely started, so nothing to remember

        val list = entries().filterNot { it.key == item.key }.toMutableList()
        list.add(
            0,
            HistoryEntry(
                key = item.key,
                title = item.title,
                subtitle = item.subtitle,
                url = item.url,
                imageUrl = item.imageUrl,
                kind = item.kind,
                positionMs = if (finished) 0L else positionMs,
                durationMs = durationMs,
                updatedAt = System.currentTimeMillis(),
                finished = finished,
            )
        )
        val arr = JSONArray()
        list.take(MAX_ENTRIES).forEach { e ->
            arr.put(
                JSONObject()
                    .put("key", e.key)
                    .put("title", e.title)
                    .put("subtitle", e.subtitle)
                    .put("url", e.url)
                    .put("imageUrl", e.imageUrl ?: "")
                    .put("kind", e.kind.name)
                    .put("positionMs", e.positionMs)
                    .put("durationMs", e.durationMs)
                    .put("updatedAt", e.updatedAt)
                    .put("finished", e.finished)
            )
        }
        prefs.edit().putString("entries", arr.toString()).apply()
    }

    /** Forgets everything watched (Continue Watching). */
    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val MAX_ENTRIES = 30
    }
}
