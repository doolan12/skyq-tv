package com.skyqlike.iptv.data.provider

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Remembers the user's providers (and which one is in use) between app launches. */
class ProviderStore(context: Context) {

    private val prefs = context.getSharedPreferences("providers", Context.MODE_PRIVATE)

    fun all(): List<Provider> {
        val text = prefs.getString("list", null) ?: return emptyList()
        return try {
            val arr = JSONArray(text)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Provider(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    type = ProviderType.valueOf(o.getString("type")),
                    url = o.getString("url"),
                    username = o.optString("username"),
                    password = o.optString("password"),
                    epgUrl = o.optString("epgUrl"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAll(list: List<Provider>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("type", p.type.name)
                    .put("url", p.url)
                    .put("username", p.username)
                    .put("password", p.password)
                    .put("epgUrl", p.epgUrl)
            )
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    fun activeId(): String? = prefs.getString("active", null)

    fun setActive(id: String?) {
        prefs.edit().putString("active", id).apply()
    }
}
