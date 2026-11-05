package com.skyqlike.iptv.data.settings

import android.content.Context

/** Everything the user can change in Settings. */
data class AppSettings(
    /** 0 = Fit, 1 = Zoom, 2 = Stretch. */
    val aspect: Int = 1,
    val autoReconnect: Boolean = true,
    val resume: Boolean = true,
    val subtitlesOn: Boolean = false,
    /** Two-letter language code like "en", or empty for "whatever the stream starts with". */
    val audioLang: String = "en",
    /** Start the first stream the addons offer, instead of showing the list. */
    val quickPlay: Boolean = true,
    /** Show a Skip Intro button where the community database knows the times. */
    val skipIntro: Boolean = true,
    /** At the end of an episode, start the next one by itself. */
    val autoNext: Boolean = true,
    /** A silent trailer plays behind the Home banner. */
    val previews: Boolean = true,
    /** 0 = automatic, 1 = up to 1080p, 2 = up to 720p, 3 = best available (4K). */
    val quality: Int = 0,
    /** 0 = no limit, 1 = 4 GB, 2 = 8 GB, 3 = 15 GB. */
    val sizeCap: Int = 0,
    /** The user's free TMDB key. It switches on the Trending rows. Kept on this device only. */
    val tmdbKey: String = "",
    /** The user's free Top Posters key. It puts rating badges on posters. Kept on this device only. */
    val topPostersKey: String = "",
)

/** Saves settings on the device. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("reel_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        aspect = prefs.getInt("aspect", 1),
        autoReconnect = prefs.getBoolean("autoReconnect", true),
        resume = prefs.getBoolean("resume", true),
        subtitlesOn = prefs.getBoolean("subtitlesOn", false),
        audioLang = prefs.getString("audioLang", "en") ?: "",
        quickPlay = prefs.getBoolean("quickPlay", true),
        skipIntro = prefs.getBoolean("skipIntro", true),
        autoNext = prefs.getBoolean("autoNext", true),
        previews = prefs.getBoolean("previews", true),
        quality = prefs.getInt("quality", 0),
        sizeCap = prefs.getInt("sizeCap", 0),
        tmdbKey = prefs.getString("tmdbKey", "") ?: "",
        topPostersKey = prefs.getString("topPostersKey", "") ?: "",
    )

    fun save(s: AppSettings) {
        prefs.edit()
            .putInt("aspect", s.aspect)
            .putBoolean("autoReconnect", s.autoReconnect)
            .putBoolean("resume", s.resume)
            .putBoolean("subtitlesOn", s.subtitlesOn)
            .putString("audioLang", s.audioLang)
            .putBoolean("quickPlay", s.quickPlay)
            .putBoolean("skipIntro", s.skipIntro)
            .putBoolean("autoNext", s.autoNext)
            .putBoolean("previews", s.previews)
            .putInt("quality", s.quality)
            .putInt("sizeCap", s.sizeCap)
            .putString("tmdbKey", s.tmdbKey)
            .putString("topPostersKey", s.topPostersKey)
            .apply()
    }
}
