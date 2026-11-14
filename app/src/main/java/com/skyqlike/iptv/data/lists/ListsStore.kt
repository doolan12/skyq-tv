package com.skyqlike.iptv.data.lists

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A channel list the user made, with its channels in the order they chose. */
data class ChannelList(val id: String, val name: String, val keys: List<String>)

/** Saves the user's own channel lists. */
class ListsStore(context: Context) {
    private val prefs = context.getSharedPreferences("channel_lists", Context.MODE_PRIVATE)

    fun load(): List<ChannelList> = try {
        val arr = JSONArray(prefs.getString("lists", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val k = o.getJSONArray("keys")
            ChannelList(o.getString("id"), o.getString("name"), (0 until k.length()).map { k.getString(it) })
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(lists: List<ChannelList>) {
        val arr = JSONArray()
        lists.forEach { l ->
            arr.put(JSONObject().put("id", l.id).put("name", l.name).put("keys", JSONArray(l.keys)))
        }
        prefs.edit().putString("lists", arr.toString()).apply()
    }
}
