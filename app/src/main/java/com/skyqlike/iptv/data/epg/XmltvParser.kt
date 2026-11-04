package com.skyqlike.iptv.data.epg

import android.util.Xml
import com.skyqlike.iptv.data.model.Programme
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Reads XMLTV TV guide files, the standard format IPTV providers use.
 * It reads the file as a stream and keeps only shows for channels we have
 * that fall inside the time window, so big guide files don't use up the memory.
 */
object XmltvParser {

    fun parse(
        input: InputStream,
        wanted: Set<String>,
        from: Long,
        to: Long,
    ): Map<String, List<Programme>> {
        val withZone = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        val noZone = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        fun time(raw: String?): Long? {
            val t = raw?.trim() ?: return null
            return try {
                if (t.length > 14) withZone.parse(t)?.time else noZone.parse(t)?.time
            } catch (e: Exception) {
                null
            }
        }

        val parser = Xml.newPullParser()
        parser.setInput(input, null)
        val out = HashMap<String, MutableList<Programme>>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                val channel = parser.getAttributeValue(null, "channel") ?: ""
                val startRaw = parser.getAttributeValue(null, "start")
                val stopRaw = parser.getAttributeValue(null, "stop")

                var title = ""
                var desc = ""
                var category = ""
                var icon: String? = null

                // Read everything inside this <programme> ... </programme>
                while (true) {
                    val e = parser.next()
                    if (e == XmlPullParser.END_DOCUMENT) break
                    if (e == XmlPullParser.END_TAG && parser.name == "programme") break
                    if (e == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "title" -> {
                                val s = parser.nextText()
                                if (title.isEmpty()) title = s
                            }
                            "desc" -> desc = parser.nextText()
                            "category" -> {
                                val s = parser.nextText()
                                if (category.isEmpty()) category = s
                            }
                            "icon" -> icon = parser.getAttributeValue(null, "src")
                        }
                    }
                }

                if (wanted.isEmpty() || channel in wanted) {
                    val start = time(startRaw)
                    val stop = time(stopRaw)
                    if (start != null && stop != null && stop > from && start < to && stop > start) {
                        out.getOrPut(channel) { ArrayList() } += Programme(
                            channelEpgId = channel,
                            title = title.trim().ifBlank { "Untitled" },
                            description = desc.trim(),
                            genre = category.trim(),
                            start = start,
                            stop = stop,
                            iconUrl = icon?.takeIf { it.isNotBlank() },
                        )
                    }
                }
            }
            event = parser.next()
        }
        return out.mapValues { (_, list) -> list.sortedBy { it.start } }
    }
}
