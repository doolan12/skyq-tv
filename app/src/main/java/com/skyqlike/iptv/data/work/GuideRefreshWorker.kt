package com.skyqlike.iptv.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.skyqlike.iptv.data.PlaylistRepository
import com.skyqlike.iptv.data.epg.EpgRepository
import com.skyqlike.iptv.data.provider.ProviderStore
import java.util.concurrent.TimeUnit

/**
 * Downloads a fresh TV guide in the background every few hours,
 * so the guide is already up to date when you switch the TV on.
 */
class GuideRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = ProviderStore(applicationContext)
        val provider = store.all().firstOrNull { it.id == store.activeId() }
            ?: return Result.success() // sample playlist: nothing to refresh
        val channels = PlaylistRepository(applicationContext).load(provider, false).getOrNull()?.channels
            ?: return Result.retry()
        return if (EpgRepository(applicationContext).load(provider, channels, true).isSuccess) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "guide_refresh"

        /** Turns the automatic refresh on or off. Safe to call every time the app starts. */
        fun schedule(context: Context, enabled: Boolean) {
            val work = WorkManager.getInstance(context.applicationContext)
            if (!enabled) {
                work.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<GuideRefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
