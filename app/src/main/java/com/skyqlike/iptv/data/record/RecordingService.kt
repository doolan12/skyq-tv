package com.skyqlike.iptv.data.record

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.skyqlike.iptv.data.Http
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Keeps the app alive in the background (with a notification) while one or more recordings are being saved. */
class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val captures = ConcurrentHashMap<String, StreamCapture>()
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android requires the notification straight away after a foreground start.
        showNotification()
        val id = intent?.getStringExtra(Recorder.EXTRA_ID)
        when (intent?.action) {
            Recorder.ACTION_STOP -> id?.let { captures[it]?.stop = true }
            Recorder.ACTION_START -> if (id != null && !captures.containsKey(id)) begin(id)
        }
        if (captures.isEmpty()) {
            stopForegroundCompat()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun begin(id: String) {
        val store = RecordingStore(this)
        val rec = store.get(id) ?: return
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir, "Recordings").apply { mkdirs() }
        if (dir.usableSpace < 300L * 1024 * 1024) {
            store.put(rec.copy(state = RecState.FAILED, message = "Not enough storage space on this device."))
            return
        }
        val stamp = SimpleDateFormat("yyyy-MM-dd HHmm", Locale.US).format(Date(rec.startMs))
        val name = "${rec.title} - ${rec.channelName}".replace(Regex("[^A-Za-z0-9 _-]"), "").trim().take(70)
        val file = File(dir, "$name $stamp.ts")
        val capture = StreamCapture(rec.url, file, rec.stopMs + Recorder.LATE_MS)
        captures[id] = capture
        acquireWake()
        store.put(rec.copy(state = RecState.RECORDING, filePath = file.absolutePath, message = ""))
        showNotification()

        scope.launch {
            var message = ""
            try {
                capture.run()
            } catch (e: Exception) {
                message = Http.friendly(e)
            }
            val size = if (file.exists()) file.length() else 0L
            val latest = store.get(id) ?: rec
            store.put(
                if (size > 0L) latest.copy(state = RecState.DONE, sizeBytes = size, message = if (message.isNotBlank()) "Stopped early: $message" else "")
                else {
                    runCatching { file.delete() }
                    latest.copy(state = RecState.FAILED, message = message.ifBlank { "Nothing could be recorded from this channel." })
                }
            )
            captures.remove(id)
            if (captures.isEmpty()) {
                releaseWake()
                stopForegroundCompat()
                stopSelf()
            } else {
                showNotification()
            }
        }
    }

    private fun showNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel("recordings", "Recordings", NotificationManager.IMPORTANCE_LOW))
        }
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
        }
        val count = captures.size
        val notification: Notification = NotificationCompat.Builder(this, "recordings")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(if (count <= 1) "Recording a channel" else "Recording $count channels")
            .setContentText("Tap to open. Stop it from Recordings in the app.")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, 4711, notification, type)
    }

    private fun stopForegroundCompat() = ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)

    private fun acquireWake() {
        if (wake?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "skyqlike:recording").apply { acquire(8L * 60 * 60 * 1000) }
    }

    private fun releaseWake() {
        runCatching { if (wake?.isHeld == true) wake?.release() }
        wake = null
    }

    override fun onDestroy() {
        captures.values.forEach { it.stop = true }
        releaseWake()
        scope.cancel()
        super.onDestroy()
    }
}

/** Fires when a planned recording is due. */
class RecordingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(Recorder.EXTRA_ID) ?: return
        if (RecordingStore(context).get(id) != null) Recorder.startService(context, id)
    }
}

/** After the device restarts, plans the alarms again. */
class RecordingBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Recorder.restore(context)
    }
}
