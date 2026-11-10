package com.skyqlike.iptv.data.record

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.File

/** Plans, starts, stops and deletes recordings. */
object Recorder {

    /** Start a minute early and finish two minutes late, because shows rarely run to the guide's exact times. */
    const val EARLY_MS = 60_000L
    const val LATE_MS = 120_000L

    const val ACTION_START = "com.skyqlike.iptv.record.START"
    const val ACTION_STOP = "com.skyqlike.iptv.record.STOP"
    const val EXTRA_ID = "id"

    /**
     * Records [title] from [startMs] to [stopMs] (a start in the past means "start now").
     * Returns the recording, or the one already planned for the same show.
     */
    fun schedule(context: Context, channelName: String, logoUrl: String?, url: String, title: String, startMs: Long, stopMs: Long): Recording {
        val app = context.applicationContext
        val store = RecordingStore(app)
        val now = System.currentTimeMillis()
        val id = "${url.hashCode()}-${startMs / 60_000L}"
        store.get(id)?.let { if (it.state == RecState.SCHEDULED || it.state == RecState.RECORDING) return it }
        val rec = Recording(id, title, channelName, logoUrl, url, startMs, stopMs.coerceAtLeast(now + 60_000L), RecState.SCHEDULED)
        store.put(rec)
        arm(app, rec)
        return rec
    }

    private fun arm(context: Context, rec: Recording) {
        val now = System.currentTimeMillis()
        val at = rec.startMs - EARLY_MS
        if (at <= now + 5_000L) {
            startService(context, rec.id)
            return
        }
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingFor(context, rec.id)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && canExact -> alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            else -> alarms.setExact(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    private fun pendingFor(context: Context, id: String): PendingIntent {
        val intent = Intent(context, RecordingAlarmReceiver::class.java).setAction(ACTION_START).putExtra(EXTRA_ID, id)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, flags)
    }

    internal fun startService(context: Context, id: String) {
        val i = Intent(context, RecordingService::class.java).setAction(ACTION_START).putExtra(EXTRA_ID, id)
        ContextCompat.startForegroundService(context, i)
    }

    /** Stops a recording that is under way. What was saved so far is kept. */
    fun stop(context: Context, id: String) {
        val i = Intent(context, RecordingService::class.java).setAction(ACTION_STOP).putExtra(EXTRA_ID, id)
        context.startService(i)
    }

    /** Cancels a recording that has not started yet. */
    fun cancel(context: Context, id: String) {
        val app = context.applicationContext
        (app.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pendingFor(app, id))
        RecordingStore(app).remove(id)
    }

    /** Deletes a saved recording and its file. */
    fun delete(context: Context, id: String) {
        val store = RecordingStore(context.applicationContext)
        store.get(id)?.let { if (it.filePath.isNotBlank()) runCatching { File(it.filePath).delete() } }
        store.remove(id)
    }

    /** The recording of this channel that is under way or planned, if there is one. */
    fun activeFor(context: Context, url: String): Recording? =
        RecordingStore(context.applicationContext).all().firstOrNull {
            it.url == url && (it.state == RecState.RECORDING || it.state == RecState.SCHEDULED && it.startMs - EARLY_MS <= System.currentTimeMillis() + 5_000L)
        }

    /** After a restart: plan the alarms again, and tidy anything that was cut off. */
    fun restore(context: Context) {
        val app = context.applicationContext
        val store = RecordingStore(app)
        val now = System.currentTimeMillis()
        for (r in store.all()) {
            when (r.state) {
                RecState.SCHEDULED ->
                    if (r.stopMs + LATE_MS < now) store.put(r.copy(state = RecState.FAILED, message = "The device was off when this was due."))
                    else arm(app, r)
                RecState.RECORDING -> {
                    val size = if (r.filePath.isNotBlank()) File(r.filePath).length() else 0L
                    store.put(
                        if (size > 0L) r.copy(state = RecState.DONE, sizeBytes = size, message = "Stopped early (the device restarted).")
                        else r.copy(state = RecState.FAILED, message = "The device restarted.")
                    )
                }
                else -> Unit
            }
        }
    }
}
