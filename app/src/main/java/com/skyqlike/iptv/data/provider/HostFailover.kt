package com.skyqlike.iptv.data.provider

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Backup servers for Xtream logins. A group is the main server plus any backup servers of the same provider
 * (same username and password, only the address differs). Every address the app builds starts with the main
 * server's address; when that server stops answering, only that start is swapped for a backup server's.
 * Addresses that don't start with a known server are left exactly as they are.
 */
object HostFailover {

    /** How long a server that failed is put at the back of the queue. */
    private const val DOWN_MS = 2 * 60_000L

    private var groups: List<List<String>> = emptyList()
    private val failedAt = ConcurrentHashMap<String, Long>()

    fun normal(address: String): String = address.trim().trimEnd('/')

    /** Tells the app which servers belong together. Called whenever the provider list changes. */
    fun configure(providers: List<Provider>) {
        groups = providers
            .filter { it.type == ProviderType.XTREAM && it.backupUrls.isNotEmpty() }
            .map { p -> (listOf(normal(p.url)) + p.backupUrls.map(::normal)).filter { it.isNotBlank() }.distinct() }
            .filter { it.size > 1 }
    }

    private fun groupOf(url: String): List<String>? =
        groups.firstOrNull { g -> g.any { url.startsWith(it, ignoreCase = true) } }

    private fun baseOf(url: String, group: List<String>): String? =
        group.firstOrNull { url.startsWith(it, ignoreCase = true) }

    private fun isDown(base: String): Boolean {
        val at = failedAt[base.lowercase()] ?: return false
        return System.currentTimeMillis() - at < DOWN_MS
    }

    /**
     * Every address worth trying for [url], best first: servers that are working before servers that failed
     * lately. Without backup servers this is just the address itself.
     */
    fun candidates(url: String): List<String> {
        val group = groupOf(url) ?: return listOf(url)
        val current = baseOf(url, group) ?: return listOf(url)
        val tail = url.substring(current.length)
        val (up, down) = group.partition { !isDown(it) }
        // Keep the server already in the address first while it is working, so nothing flips around for no reason.
        val ordered = (up.sortedBy { if (it.equals(current, true)) 0 else 1 }) + down
        return ordered.map { it + tail }
    }

    /** Runs [block] on the best server, then on the others if it fails. Throws the last error if all fail. */
    fun <T> tryServers(url: String, block: (String) -> T): T {
        var last: IOException? = null
        for (candidate in candidates(url)) {
            try {
                val result = block(candidate)
                markWorking(candidate)
                return result
            } catch (e: IOException) {
                last = e
                markDown(candidate)
            }
        }
        throw last ?: IOException("No server answered.")
    }

    fun markDown(url: String) {
        val g = groupOf(url) ?: return
        baseOf(url, g)?.let { failedAt[it.lowercase()] = System.currentTimeMillis() }
    }

    fun markWorking(url: String) {
        val g = groupOf(url) ?: return
        baseOf(url, g)?.let { failedAt.remove(it.lowercase()) }
    }

    /** After a failed stream: marks that server down and gives the address on another server, or null if there is none. */
    fun alternativeAfterFailure(url: String): String? {
        markDown(url)
        val best = candidates(url).firstOrNull() ?: return null
        val g = groupOf(url) ?: return null
        val base = baseOf(best, g) ?: return null
        return if (!best.equals(url, true) && !isDown(base)) best else null
    }

    /** Servers (main first) with whether each is down right now. For the provider screen. */
    fun statusOf(provider: Provider): List<Pair<String, Boolean>> =
        (listOf(normal(provider.url)) + provider.backupUrls.map(::normal)).filter { it.isNotBlank() }.distinct().map { it to isDown(it) }
}
