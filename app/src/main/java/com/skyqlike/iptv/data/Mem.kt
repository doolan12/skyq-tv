package com.skyqlike.iptv.data

import android.app.ActivityManager
import android.content.Context

/**
 * Fits the app to the device's memory. Many TV sticks have only 1 GB, and there the picture cache and
 * video buffer are kept smaller so Android doesn't close the app. Tiers: 0 = low, 1 = medium, 2 = plenty.
 */
object Mem {
    private var cached = -1

    fun totalMb(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0L
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem / (1024 * 1024)
    }

    fun tier(context: Context): Int {
        if (cached >= 0) return cached
        val mb = totalMb(context)
        cached = when {
            mb in 1..1536 -> 0
            mb in 1..3072 -> 1
            else -> 2
        }
        return cached
    }

    fun label(context: Context): String = when (tier(context)) {
        0 -> "Low-memory mode (${totalMb(context)} MB)"
        1 -> "Standard (${totalMb(context)} MB)"
        else -> "Standard (${totalMb(context)} MB)"
    }
}
