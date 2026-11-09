package com.skyqlike.iptv.data.provider

import android.content.Context
import com.skyqlike.iptv.data.Secure
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remembers the user's providers (and which one is in use) between app launches.
 * The web address, username, password and guide link are locked before saving,
 * because IPTV addresses very often have the login built into them.
 */
class ProviderStore(context: Context) {

    private val prefs = context.getSharedPreferences("providers", Context.MODE_PRIVATE)

    fun all(): List<Provider> {
        val text = prefs.getString("list", null) ?: return emptyList()
        var foundUnlocked = false
        val list = try {
            val arr = JSONArray(text)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val rawUrl = o.getString("url")
                val rawUser = o.optString("username")
                val rawPass = o.optString("password")
                val rawEpg = o.optString("epgUrl")
                if (listOf(rawUrl, rawUser, rawPass, rawEpg).any { Secure.needsLocking(it) }) foundUnlocked = true
                Provider(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    type = ProviderType.valueOf(o.getString("type")),
                    url = Secure.unlock(rawUrl),
                    username = Secure.unlock(rawUser),
                    password = Secure.unlock(rawPass),
                    epgUrl = Secure.unlock(rawEpg),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
        // Providers saved by an older version of the app get locked the first time they are read.
        if (foundUnlocked && list.isNotEmpty()) saveAll(list)
        return list
    }

    fun saveAll(list: List<Provider>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("type", p.type.name)
                    .put("url", Secure.lock(p.url))
                    .put("username", Secure.lock(p.username))
                    .put("password", Secure.lock(p.password))
                    .put("epgUrl", Secure.lock(p.epgUrl))
            )
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    fun activeId(): String? = prefs.getString("active", null)

    fun setActive(id: String?) {
        prefs.edit().putString("active", id).apply()
    }
}
