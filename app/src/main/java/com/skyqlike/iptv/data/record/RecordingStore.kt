package com.skyqlike.iptv.data.record

import android.content.Context
import android.content.SharedPreferences
import com.skyqlike.iptv.data.Secure
import org.json.JSONArray
import org.json.JSONObject

enum class RecState { SCHEDULED, RECORDING, DONE, FAILED }

/** One recording: planned, in progress, saved, or failed. Times are milliseconds since 1970 (UTC). */
data class Recording(
    val id: String,
    val title: String,
    val channelName: String,
    val logoUrl: String?,
    /** The channel's web address. It can contain the login, so it is locked when saved. */
    val url: String,
    val startMs: Long,
    val stopMs: Long,
    val state: RecState,
    val filePath: String = "",
    val sizeBytes: Long = 0L,
    val message: String = "",
)

/** Remembers the recording list on the device. */
class RecordingStore(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("recordings", Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<Recording> {
        val text = prefs.getString("list", null) ?: return emptyList()
        return try {
            val arr = JSONArray(text)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Recording(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    channelName = o.optString("channelName"),
                    logoUrl = o.optString("logoUrl").ifBlank { null },
                    url = Secure.unlock(o.getString("url")),
                    startMs = o.getLong("startMs"),
                    stopMs = o.getLong("stopMs"),
                    state = RecState.valueOf(o.getString("state")),
                    filePath = o.optString("filePath"),
                    sizeBytes = o.optLong("sizeBytes"),
                    message = o.optString("message"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun get(id: String): Recording? = all().firstOrNull { it.id == id }

    @Synchronized
    fun put(r: Recording) = save(all().filter { it.id != r.id } + r)

    @Synchronized
    fun remove(id: String) = save(all().filter { it.id != id })

    private fun save(list: List<Recording>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("title", r.title)
                    .put("channelName", r.channelName)
                    .put("logoUrl", r.logoUrl ?: "")
                    .put("url", Secure.lock(r.url))
                    .put("startMs", r.startMs)
                    .put("stopMs", r.stopMs)
                    .put("state", r.state.name)
                    .put("filePath", r.filePath)
                    .put("sizeBytes", r.sizeBytes)
                    .put("message", r.message)
            )
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    /** Calls [onChange] whenever the list changes. Returns a function that stops listening. */
    fun observe(onChange: () -> Unit): () -> Unit {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        prefs.registerOnSharedPreferenceChangeListener(l)
        return { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }
}
