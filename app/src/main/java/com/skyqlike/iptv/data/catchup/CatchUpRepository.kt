package com.skyqlike.iptv.data.catchup

import com.skyqlike.iptv.data.Http
import com.skyqlike.iptv.data.model.Channel
import com.skyqlike.iptv.data.model.Programme
import com.skyqlike.iptv.data.provider.Provider
import com.skyqlike.iptv.data.provider.ProviderType
import com.skyqlike.iptv.data.provider.XtreamAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** What the Catch Up screen is showing. */
sealed interface CatchUpState {
    data object Idle : CatchUpState
    data object Loading : CatchUpState
    data class Error(val message: String) : CatchUpState
    /** Past shows, by guide ID. */
    data class Ready(val shows: Map<String, List<Programme>>) : CatchUpState
}

/** Works out the web address that replays a past show. */
class CatchUpRepository {

    private val zones = HashMap<String, TimeZone>()

    suspend fun url(provider: Provider?, channel: Channel, show: Programme): Result<String> =
        withContext(Dispatchers.IO) {
            // Sample playlist: there is no real archive, so it just plays the sample video.
            if (provider == null) return@withContext Result.success(channel.streamUrl)

            if (provider.type != ProviderType.XTREAM) {
                return@withContext Result.failure(
                    Exception("Catch-up needs an Xtream login. This provider is a plain playlist link.")
                )
            }
            val id = channel.xtreamId
                ?: return@withContext Result.failure(
                    Exception("This channel has no provider ID. Refresh the playlist in Settings and try again.")
                )
            try {
                val access = XtreamAccess(provider)
                val zone = serverZone(provider, access)
                val format = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).apply { timeZone = zone }
                val minutes = (((show.stop - show.start) + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
                Result.success(access.timeshiftUrl(minutes, format.format(Date(show.start)), id))
            } catch (e: Exception) {
                Result.failure(Exception(Http.friendly(e)))
            }
        }

    /** Replay links are written in the provider's own time zone, which its account info tells us. */
    private fun serverZone(provider: Provider, access: XtreamAccess): TimeZone {
        zones[provider.id]?.let { return it }
        val zone = try {
            val name = JSONObject(Http.get(access.infoUrl()))
                .optJSONObject("server_info")?.optString("timezone").orEmpty()
            if (name.isBlank()) TimeZone.getDefault() else TimeZone.getTimeZone(name)
        } catch (e: Exception) {
            TimeZone.getDefault()
        }
        zones[provider.id] = zone
        return zone
    }
}
