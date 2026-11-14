package com.skyqlike.iptv.data

import org.json.JSONException
import com.skyqlike.iptv.data.provider.HostFailover
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/** One simple way to fetch a web address as text, plus plain-English error messages. */
internal object Http {

    fun get(address: String, readTimeoutMs: Int = 60_000, userAgent: String? = null): String =
        HostFailover.tryServers(address) { getOnce(it, readTimeoutMs, userAgent) }

    private fun getOnce(address: String, readTimeoutMs: Int, userAgent: String?): String {
        val conn = URL(address).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = readTimeoutMs
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", userAgent ?: "Mozilla/5.0 (Linux; Android) SkyQLikeIPTV/0.1")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun friendly(e: Exception): String = when {
        e is UnknownHostException -> "Can't reach that server. Check the address and your internet."
        e is SocketTimeoutException -> "The server took too long to answer."
        e.message?.contains("401") == true || e.message?.contains("403") == true ->
            "The server refused the login. Check the username and password."
        e.message?.contains("404") == true -> "Nothing found at that address."
        e is JSONException -> "The server's reply wasn't what a normal IPTV login returns."
        else -> e.message ?: "Something went wrong."
    }
}
