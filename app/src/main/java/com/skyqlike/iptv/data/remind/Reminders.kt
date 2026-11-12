package com.skyqlike.iptv.data.remind

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.data.model.Programme
import com.skyqlike.iptv.data.model.favKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Plans and cancels "starting soon" alerts. They work with the app closed, the same way planned recordings do. */
object Reminders {

    /** The alert comes this long before the show starts. */
    const val LEAD_MS = 5 * 60_000L

    const val ACTION_FIRE = "com.skyqlike.iptv.remind.FIRE"
    const val EXTRA_ID = "id"

    /** Opening the app from an alert carries this, so the app can go straight to the channel. */
    const val EXTRA_PLAY_KEY = "play_channel_key"

    private const val NOTIFY_CHANNEL = "reminders"

    fun idFor(title: String, startMs: Long) = "${title.lowercase().filter { it.isLetterOrDigit() }.hashCode()}-${startMs / 60_000L}"

    /** Sets a reminder, or returns the one that is already set. Shows that start within 5 minutes alert straight away. */
    fun set(context: Context, channel: Channel, programme: Programme, auto: Boolean = false): Reminder {
        val app = context.applicationContext
        val store = ReminderStore(app)
        val id = idFor(programme.title, programme.start)
        store.get(id)?.let { return it }
        val r = Reminder(id, programme.title, channel.name, channel.favKey, programme.start, auto)
        store.put(r)
        arm(app, r)
        return r
    }

    /** Removes a reminder. [byPerson] stops My Team adding it again. */
    fun cancel(context: Context, id: String, byPerson: Boolean = true) {
        val app = context.applicationContext
        (app.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pendingFor(app, id))
        val store = ReminderStore(app)
        if (byPerson) store.skip(id)
        store.remove(id)
    }

    fun isSet(context: Context, title: String, startMs: Long) = ReminderStore(context).get(idFor(title, startMs)) != null

    private fun arm(context: Context, r: Reminder) {
        val at = (r.startMs - LEAD_MS).coerceAtLeast(System.currentTimeMillis() + 2_000L)
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingFor(context, r.id)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && canExact -> alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            else -> alarms.setExact(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    private fun pendingFor(context: Context, id: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_ID, id)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, flags)
    }

    /** After a restart: plan the alarms again and drop reminders for shows that are long over. */
    fun restore(context: Context) {
        val app = context.applicationContext
        val store = ReminderStore(app)
        val now = System.currentTimeMillis()
        for (r in store.all()) {
            if (r.startMs < now - 10 * 60_000L) store.remove(r.id) else arm(app, r)
        }
    }

    internal fun notify(context: Context, id: String) {
        val store = ReminderStore(context)
        val r = store.get(id) ?: return
        store.remove(id)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(NOTIFY_CHANNEL, "Show reminders", NotificationManager.IMPORTANCE_HIGH))
        }
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            putExtra(EXTRA_PLAY_KEY, r.channelKey)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val tap = open?.let {
            PendingIntent.getActivity(context, id.hashCode(), it, PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
        }
        val clock = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(r.startMs))
        val minutes = ((r.startMs - System.currentTimeMillis()) / 60_000L).toInt()
        val whenText = if (minutes <= 0) "Starting now" else "Starts in $minutes min ($clock)"
        val n = NotificationCompat.Builder(context, NOTIFY_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(r.title)
            .setContentText("$whenText on ${r.channelName}. Tap to watch.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        runCatching { manager.notify(id.hashCode(), n) }
    }
}

/** Fires when a reminder is due. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(Reminders.EXTRA_ID) ?: return
        Reminders.notify(context, id)
    }
}

/** After the device restarts, plans the reminder alarms again. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Reminders.restore(context)
    }
}
