package com.skyqlike.iptv.data.history

import android.content.Context
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.skyqlike.iptv.data.model.PlayKind

/** Puts what you're partway through on the Android TV home screen's "Continue watching" row. Does nothing elsewhere. */
object WatchNextPublisher {
    fun publish(context: Context, entries: List<HistoryEntry>) {
        runCatching {
            val pm = context.packageManager
            if (!pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)) return
            val resolver = context.contentResolver
            resolver.delete(TvContractCompat.WatchNextPrograms.CONTENT_URI, null, null)
            entries.filter { !it.finished && it.positionMs > 10_000 && it.durationMs > 0 }
                .sortedByDescending { it.updatedAt }
                .take(5)
                .forEach { e ->
                    runCatching {
                        val b = WatchNextProgram.Builder()
                            .setType(if (e.kind == PlayKind.EPISODE) TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE else TvContractCompat.PreviewPrograms.TYPE_MOVIE)
                            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                            .setLastEngagementTimeUtcMillis(e.updatedAt)
                            .setTitle(e.title)
                            .setDescription(e.subtitle)
                            .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                            .setLastPlaybackPositionMillis(e.positionMs.toInt())
                            .setDurationMillis(e.durationMs.toInt())
                            .setInternalProviderId(e.key)
                            .setIntentUri(Uri.parse("reelhouse://resume?key=" + Uri.encode(e.key)))
                        e.imageUrl?.let { b.setPosterArtUri(Uri.parse(it)) }
                        resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, b.build().toContentValues())
                    }
                }
        }
    }
}
