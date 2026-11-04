package com.skyqlike.iptv.data.stremio

import android.content.Context
import org.json.JSONArray

/**
 * Saves the Stremio sign-in key and hand-added addon links on this device only.
 * The password is never saved. Addon links can contain your debrid key, so they stay private to the app.
 */
class StremioStore(context: Context) {
    private val prefs = context.getSharedPreferences("stremio", Context.MODE_PRIVATE)

    fun load(): StremioSession {
        val manual = try {
            val arr = JSONArray(prefs.getString("manual", "[]"))
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
        return StremioSession(
            email = prefs.getString("email", "") ?: "",
            authKey = prefs.getString("authKey", "") ?: "",
            manualAddons = manual,
        )
    }

    fun save(s: StremioSession) {
        val arr = JSONArray()
        s.manualAddons.forEach { arr.put(it) }
        prefs.edit()
            .putString("email", s.email)
            .putString("authKey", s.authKey)
            .putString("manual", arr.toString())
            .apply()
    }
}
