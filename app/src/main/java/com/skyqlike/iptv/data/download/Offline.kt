package com.skyqlike.iptv.data.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.skyqlike.iptv.data.model.ExternalSubtitle
import com.skyqlike.iptv.data.model.PlayItem
import com.skyqlike.iptv.data.model.PlayKind
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL
import kotlin.math.abs

/** A subtitle file saved next to a downloaded video. */
data class OfflineSub(val language: String, val label: String, val fileName: String)

/** A movie or episode saved on this device so it plays with no internet. */
data class OfflineItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val kind: String,
    val externalId: String?,
    val fileName: String,
    val downloadId: Long,
    val subs: List<OfflineSub>,
    val addedAt: Long,
)

/** The list of downloads, remembered between runs of the app. */
class OfflineStore(context: Context) {
    private val prefs = context.getSharedPreferences("offline_downloads", Context.MODE_PRIVATE)

    fun all(): List<OfflineItem> = runCatching {
        val arr = JSONArray(prefs.getString("items", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val subs = o.optJSONArray("subs") ?: JSONArray()
            OfflineItem(
                key = o.getString("key"),
                title = o.optString("title"),
                subtitle = o.optString("subtitle"),
                imageUrl = o.optString("imageUrl").ifBlank { null },
                kind = o.optString("kind", "MOVIE"),
                externalId = o.optString("externalId").ifBlank { null },
                fileName = o.getString("fileName"),
                downloadId = o.optLong("downloadId", -1L),
                subs = (0 until subs.length()).map { j ->
                    val s = subs.getJSONObject(j)
                    OfflineSub(s.optString("language"), s.optString("label"), s.getString("fileName"))
                },
                addedAt = o.optLong("addedAt"),
            )
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<OfflineItem>) {
        val arr = JSONArray()
        list.forEach { o ->
            arr.put(JSONObject().apply {
                put("key", o.key)
                put("title", o.title)
                put("subtitle", o.subtitle)
                put("imageUrl", o.imageUrl ?: "")
                put("kind", o.kind)
                put("externalId", o.externalId ?: "")
                put("fileName", o.fileName)
                put("downloadId", o.downloadId)
                put("addedAt", o.addedAt)
                put("subs", JSONArray().apply {
                    o.subs.forEach { s -> put(JSONObject().put("language", s.language).put("label", s.label).put("fileName", s.fileName)) }
                })
            })
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    fun find(key: String): OfflineItem? = all().firstOrNull { it.key == key }
    fun add(item: OfflineItem) = save(all().filter { it.key != item.key } + item)
    fun remove(key: String) = save(all().filter { it.key != key })
}

/** Starts, checks, plays and deletes downloads. The phone's own download manager does the actual downloading, even with the app closed. */
class OfflineHelper(private val context: Context) {
    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    class Progress(val status: Int, val bytes: Long, val total: Long) {
        val done get() = status == DownloadManager.STATUS_SUCCESSFUL
        val failed get() = status == DownloadManager.STATUS_FAILED || status < 0
        val fraction get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    private fun videoDir(): File = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
    private fun subsDir(): File = File(context.filesDir, "offline_subs").apply { mkdirs() }
    fun file(o: OfflineItem) = File(videoDir(), o.fileName)

    /** Queues the video, and saves a few subtitle files beside it. Call from a background thread. */
    fun start(item: PlayItem): OfflineItem {
        val path = item.url.substringBefore('?').lowercase()
        val ext = listOf("mkv", "mp4", "m4v", "webm", "avi", "mov", "ts").firstOrNull { path.endsWith(".$it") } ?: "mp4"
        val stamp = abs(item.key.hashCode())
        val name = "dl_$stamp.$ext"
        File(videoDir(), name).delete()
        val req = DownloadManager.Request(Uri.parse(item.url))
            .setTitle(item.title)
            .setDescription(item.subtitle.ifBlank { "Reelhouse download" })
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MOVIES, name)
        val id = dm.enqueue(req)

        val saved = ArrayList<OfflineSub>()
        item.subtitles.distinctBy { it.language }.take(8).forEachIndexed { i, s ->
            runCatching {
                val p = s.url.substringBefore('?').lowercase()
                val sx = if (p.endsWith(".vtt")) "vtt" else if (p.endsWith(".ass") || p.endsWith(".ssa")) "ass" else "srt"
                val f = "sub_${stamp}_$i.$sx"
                val conn = URL(s.url).openConnection().apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Reelhouse/1.0")
                }
                conn.getInputStream().use { input -> File(subsDir(), f).outputStream().use { input.copyTo(it) } }
                saved += OfflineSub(s.language, s.label, f)
            }
        }
        return OfflineItem(
            key = item.key,
            title = item.title,
            subtitle = item.subtitle,
            imageUrl = item.imageUrl,
            kind = item.kind.name,
            externalId = item.externalId,
            fileName = name,
            downloadId = id,
            subs = saved,
            addedAt = System.currentTimeMillis(),
        )
    }

    fun progress(o: OfflineItem): Progress {
        // A finished file counts even if the download manager has forgotten it.
        if (file(o).exists() && o.downloadId < 0) return Progress(DownloadManager.STATUS_SUCCESSFUL, file(o).length(), file(o).length())
        return runCatching {
            dm.query(DownloadManager.Query().setFilterById(o.downloadId)).use { c ->
                if (c.moveToFirst()) {
                    Progress(
                        c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                        c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                        c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                    )
                } else if (file(o).exists()) Progress(DownloadManager.STATUS_SUCCESSFUL, file(o).length(), file(o).length())
                else Progress(-1, 0, 0)
            }
        }.getOrDefault(Progress(-1, 0, 0))
    }

    fun remove(o: OfflineItem) {
        runCatching { dm.remove(o.downloadId) }
        runCatching { file(o).delete() }
        o.subs.forEach { runCatching { File(subsDir(), it.fileName).delete() } }
    }

    /** What the player needs to play it from this device. */
    fun toPlayItem(o: OfflineItem): PlayItem = PlayItem(
        key = o.key,
        title = o.title,
        subtitle = o.subtitle,
        url = Uri.fromFile(file(o)).toString(),
        kind = runCatching { PlayKind.valueOf(o.kind) }.getOrDefault(PlayKind.MOVIE),
        imageUrl = o.imageUrl,
        externalId = o.externalId,
        subtitles = o.subs.map { ExternalSubtitle(Uri.fromFile(File(subsDir(), it.fileName)).toString(), it.language, it.label) },
        streamKey = null,
    )
}
