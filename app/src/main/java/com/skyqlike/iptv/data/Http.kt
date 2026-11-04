package com.skyqlike.iptv.data

import org.json.JSONException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/** One simple way to fetch a web address as text, plus plain-English error messages. */
internal object Http {

    fun get(address: String, readTimeoutMs: Int = 60_000): String {
        val conn = URL(address).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = readTimeoutMs
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Reelhouse/1.0")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Sends JSON and returns the reply text, even for error codes (Stremio puts its error message in the reply). */
    fun post(address: String, jsonBody: String, readTimeoutMs: Int = 30_000): String {
        val conn = URL(address).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = readTimeoutMs
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Reelhouse/1.0")
        try {
            conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return stream?.bufferedReader()?.use { it.readText() } ?: ""
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
        e is JSONException -> "The server's reply wasn't in the format expected."
        else -> e.message ?: "Something went wrong."
    }
}
