package com.skyqlike.iptv.data.stremio

import android.content.Context
import com.skyqlike.iptv.data.Secure
import org.json.JSONArray

/**
 * Saves the Stremio sign-in key and hand-added addon links on this device only.
 * The password is never saved. Addon links can contain your debrid key, so they stay private to the app.
 */
class StremioStore(context: Context) {
    private val prefs = context.getSharedPreferences("stremio", Context.MODE_PRIVATE)

    fun load(): StremioSession {
        val rawAuth = prefs.getString("authKey", "") ?: ""
        val rawManual = prefs.getString("manual", "[]") ?: "[]"
        val manual = try {
            val arr = JSONArray(Secure.unlock(rawManual).ifEmpty { "[]" })
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
        val session = StremioSession(
            email = prefs.getString("email", "") ?: "",
            authKey = Secure.unlock(rawAuth),
            manualAddons = manual,
        )
        // Keys saved by an older version of the app get locked the first time they are read.
        if (Secure.needsLocking(rawAuth) || (rawManual != "[]" && Secure.needsLocking(rawManual))) save(session)
        return session
    }

    fun save(s: StremioSession) {
        val arr = JSONArray()
        s.manualAddons.forEach { arr.put(it) }
        prefs.edit()
            .putString("email", s.email)
            .putString("authKey", Secure.lock(s.authKey))
            .putString("manual", Secure.lock(arr.toString()))
            .apply()
    }
}
