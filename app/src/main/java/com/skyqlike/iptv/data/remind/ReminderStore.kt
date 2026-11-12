package com.skyqlike.iptv.data.remind

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** A "tell me when this starts" note. Times are milliseconds since 1970 (UTC). */
data class Reminder(
    val id: String,
    val title: String,
    val channelName: String,
    /** Identifies the channel inside the app (name and address), so tapping the alert can open it. */
    val channelKey: String,
    val startMs: Long,
    /** True when My Team added it by itself. */
    val auto: Boolean = false,
)

/** Remembers reminders on the device. */
class ReminderStore(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<Reminder> {
        val text = prefs.getString("list", null) ?: return emptyList()
        return try {
            val arr = JSONArray(text)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Reminder(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    channelName = o.optString("channelName"),
                    channelKey = o.optString("channelKey"),
                    startMs = o.getLong("startMs"),
                    auto = o.optBoolean("auto", false),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun get(id: String): Reminder? = all().firstOrNull { it.id == id }

    @Synchronized
    fun put(r: Reminder) = save(all().filter { it.id != r.id } + r)

    @Synchronized
    fun remove(id: String) = save(all().filter { it.id != id })

    /** Reminders the person cancelled by hand. My Team won't add these back. */
    @Synchronized
    fun skipped(): Set<String> = prefs.getStringSet("skipped", emptySet())?.toSet() ?: emptySet()

    @Synchronized
    fun skip(id: String) {
        val now = skipped() + id
        // Keep this list from growing forever.
        prefs.edit().putStringSet("skipped", now.toList().takeLast(300).toSet()).apply()
    }

    private fun save(list: List<Reminder>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("title", r.title)
                    .put("channelName", r.channelName)
                    .put("channelKey", r.channelKey)
                    .put("startMs", r.startMs)
                    .put("auto", r.auto)
            )
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    fun observe(onChange: () -> Unit): () -> Unit {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "list") onChange() }
        prefs.registerOnSharedPreferenceChangeListener(l)
        return { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }
}
