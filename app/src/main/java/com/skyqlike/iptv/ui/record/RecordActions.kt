package com.skyqlike.iptv.ui.record

import android.content.Context
import android.widget.Toast
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.data.model.Programme
import com.skyqlike.iptv.data.record.Recorder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Records a show from the guide: starts now if it is on, or plans it if it is later. Tells the user what happened. */
fun recordProgramme(context: Context, channel: Channel, programme: Programme) {
    val now = System.currentTimeMillis()
    if (programme.stop <= now) {
        Toast.makeText(context, "That show has finished. Try Catch Up instead.", Toast.LENGTH_LONG).show()
        return
    }
    Recorder.schedule(context, channel.name, channel.logoUrl, channel.streamUrl, programme.title, programme.start, programme.stop)
    val whenText = if (programme.start <= now) "now" else "at " + SimpleDateFormat("EEE h:mm a", Locale.getDefault()).format(Date(programme.start))
    Toast.makeText(context, "Recording ${programme.title} $whenText on ${channel.name}", Toast.LENGTH_LONG).show()
}
