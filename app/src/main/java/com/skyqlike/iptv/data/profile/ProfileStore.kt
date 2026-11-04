package com.skyqlike.iptv.data.profile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One person's profile. Each profile has its own My List and Continue Watching. */
data class Profile(val id: String, val name: String, val colorIndex: Int)

/** Saves the list of profiles on this device. */
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("profiles", Context.MODE_PRIVATE)

    fun all(): List<Profile> {
        val text = prefs.getString("list", null)
        if (text != null) {
            try {
                val arr = JSONArray(text)
                val list = (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    Profile(id, o.optString("name", "Profile"), o.optInt("color", 0))
                }
                if (list.isNotEmpty()) return list
            } catch (e: Exception) {
                // fall through to the default profile
            }
        }
        return listOf(Profile("p1", "Me", 0))
    }

    fun save(list: List<Profile>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("color", p.colorIndex))
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    /** A new, unused id for a profile. */
    fun newId(existing: List<Profile>): String {
        var n = existing.size + 1
        while (existing.any { it.id == "p$n" }) n++
        return "p$n"
    }
}
