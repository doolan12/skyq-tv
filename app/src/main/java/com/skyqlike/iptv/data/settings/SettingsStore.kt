package com.skyqlike.iptv.data.settings

import android.content.Context
import com.skyqlike.iptv.data.Secure
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.data.model.favKey
import org.json.JSONArray

/** Everything the user can change in Settings. */
data class AppSettings(
    /** 0 = Fit, 1 = Zoom, 2 = Stretch. */
    val aspect: Int = 0,
    val autoReconnect: Boolean = true,
    /** Download a fresh TV guide in the background every few hours. */
    val autoGuideUpdate: Boolean = true,
    /** Start playing the channel you watched last as soon as the app opens. */
    val openOnLastChannel: Boolean = false,
    val resume: Boolean = true,
    val subtitlesOn: Boolean = false,
    /** Two-letter language code like "en", or empty for "whatever the stream starts with". */
    val audioLang: String = "",
    /** 4-digit parental PIN, or empty if none is set. */
    val pin: String = "",
    val hiddenGroups: Set<String> = emptySet(),
    val hiddenChannels: Set<String> = emptySet(),
    /** Category order chosen by the user. Categories not listed keep the provider's order, after these. */
    val groupOrder: List<String> = emptyList(),
    /** Free key from themoviedb.org, used to fetch posters and banners. Empty means off. */
    val tmdbKey: String = "",
    /** The team to follow, like "Liverpool". Several names can be separated with commas. */
    val myTeam: String = "",
    /** Set a reminder by itself before each game of your team. */
    val teamReminders: Boolean = true,
    /** Show a Skip intro button on episodes that have one. */
    val skipIntro: Boolean = true,
    /** Skip intros and recaps by itself. */
    val autoSkipIntro: Boolean = false,
    /** Start the next episode when one ends. */
    val autoNext: Boolean = true,
    /** Free key from subdl.com for subtitle search. Empty means off. */
    val subdlKey: String = "",
)

/** Saves settings on the device. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val settings = read()
        // A PIN saved by an older version is locked the first time it is read.
        if (Secure.needsLocking(prefs.getString("pin", "") ?: "")) save(settings)
        return settings
    }

    private fun read(): AppSettings = AppSettings(
        aspect = prefs.getInt("aspect", 0),
        autoReconnect = prefs.getBoolean("autoReconnect", true),
        autoGuideUpdate = prefs.getBoolean("autoGuideUpdate", true),
        openOnLastChannel = prefs.getBoolean("openOnLastChannel", false),
        resume = prefs.getBoolean("resume", true),
        subtitlesOn = prefs.getBoolean("subtitlesOn", false),
        audioLang = prefs.getString("audioLang", "") ?: "",
        pin = Secure.unlock(prefs.getString("pin", "") ?: ""),
        hiddenGroups = prefs.getStringSet("hiddenGroups", emptySet())?.toSet() ?: emptySet(),
        hiddenChannels = prefs.getStringSet("hiddenChannels", emptySet())?.toSet() ?: emptySet(),
        groupOrder = readList(prefs.getString("groupOrder", null)),
        tmdbKey = Secure.unlock(prefs.getString("tmdbKey", "") ?: ""),
        myTeam = prefs.getString("myTeam", "") ?: "",
        teamReminders = prefs.getBoolean("teamReminders", true),
        skipIntro = prefs.getBoolean("skipIntro", true),
        autoSkipIntro = prefs.getBoolean("autoSkipIntro", false),
        autoNext = prefs.getBoolean("autoNext", true),
        subdlKey = Secure.unlock(prefs.getString("subdlKey", "") ?: ""),
    )

    fun save(s: AppSettings) {
        val order = JSONArray()
        s.groupOrder.forEach { order.put(it) }
        prefs.edit()
            .putInt("aspect", s.aspect)
            .putBoolean("autoReconnect", s.autoReconnect)
            .putBoolean("autoGuideUpdate", s.autoGuideUpdate)
            .putBoolean("openOnLastChannel", s.openOnLastChannel)
            .putBoolean("resume", s.resume)
            .putBoolean("subtitlesOn", s.subtitlesOn)
            .putString("audioLang", s.audioLang)
            .putString("pin", Secure.lock(s.pin))
            .putStringSet("hiddenGroups", s.hiddenGroups)
            .putStringSet("hiddenChannels", s.hiddenChannels)
            .putString("groupOrder", order.toString())
            .putString("tmdbKey", Secure.lock(s.tmdbKey))
            .putString("myTeam", s.myTeam)
            .putBoolean("teamReminders", s.teamReminders)
            .putBoolean("skipIntro", s.skipIntro)
            .putBoolean("autoSkipIntro", s.autoSkipIntro)
            .putBoolean("autoNext", s.autoNext)
            .putString("subdlKey", Secure.lock(s.subdlKey))
            .apply()
    }

    private fun readList(text: String?): List<String> {
        if (text == null) return emptyList()
        return try {
            val arr = JSONArray(text)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/** Applies the user's hide / reorder / parental choices to the channel list. */
object ChannelRules {
    private val adultWords = listOf("adult", "xxx", "18+", "porn", "erotic")

    fun isAdultGroup(group: String): Boolean = adultWords.any { group.contains(it, ignoreCase = true) }

    fun apply(channels: List<Channel>, s: AppSettings, adultUnlocked: Boolean): List<Channel> {
        val hideAdult = s.pin.isNotEmpty() && !adultUnlocked
        val visible = channels.filter { c ->
            c.group !in s.hiddenGroups &&
                c.favKey !in s.hiddenChannels &&
                !(hideAdult && isAdultGroup(c.group))
        }
        if (s.groupOrder.isEmpty()) return visible
        val rank = s.groupOrder.withIndex().associate { it.value to it.index }
        // sortedBy keeps the original order inside each category.
        return visible.sortedBy { rank[it.group] ?: Int.MAX_VALUE }
    }

    /** Every category in the list, in the order they will appear. */
    fun groupsInOrder(channels: List<Channel>, s: AppSettings): List<String> {
        val groups = channels.map { it.group }.distinct()
        val rank = s.groupOrder.withIndex().associate { it.value to it.index }
        return groups.sortedBy { rank[it] ?: Int.MAX_VALUE }
    }
}
