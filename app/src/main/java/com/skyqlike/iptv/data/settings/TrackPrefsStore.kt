package com.skyqlike.iptv.data.settings

import android.content.Context

/**
 * Remembers the audio language and subtitle choice you made for each show or film,
 * so the next episode starts the way you left it. A subtitle choice of "off" means subtitles off.
 */
class TrackPrefsStore(context: Context) {
    private val prefs = context.getSharedPreferences("track_prefs", Context.MODE_PRIVATE)

    fun audio(base: String): String? = prefs.getString("a:$base", null)

    fun sub(base: String): String? = prefs.getString("s:$base", null)

    fun saveAudio(base: String, language: String) {
        prefs.edit().putString("a:$base", language).apply()
    }

    fun saveSub(base: String, language: String) {
        prefs.edit().putString("s:$base", language).apply()
    }
}
