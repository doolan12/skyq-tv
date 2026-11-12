package com.skyqlike.iptv.ui.record

import android.content.Context
import android.widget.Toast
import com.skyqlike.iptv.MainActivity
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.data.model.Programme
import com.skyqlike.iptv.data.remind.Reminders
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Sets a "starting soon" alert for a show, or tells the person why it can't. */
fun remindProgramme(context: Context, channel: Channel, programme: Programme) {
    val now = System.currentTimeMillis()
    if (programme.start <= now) {
        Toast.makeText(context, "${programme.title} is already on.", Toast.LENGTH_SHORT).show()
        return
    }
    if (Reminders.isSet(context, programme.title, programme.start)) {
        Toast.makeText(context, "You already have a reminder for ${programme.title}.", Toast.LENGTH_SHORT).show()
        return
    }
    Reminders.set(context, channel, programme)
    val clock = SimpleDateFormat("EEE h:mm a", Locale.getDefault()).format(Date(programme.start))
    Toast.makeText(context, "I'll remind you 5 minutes before ${programme.title} ($clock)", Toast.LENGTH_LONG).show()
    // Newer Android versions ask once before an app may show alerts.
    (context as? MainActivity)?.askNotificationPermission()
}
