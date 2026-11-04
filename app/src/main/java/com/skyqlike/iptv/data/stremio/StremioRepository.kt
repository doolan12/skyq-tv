package com.skyqlike.iptv.data.stremio

import com.skyqlike.iptv.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Talks to Stremio: signs in, finds your addons, and asks them for movies, series and streams.
 * Stremio addons are just web addresses that answer with JSON, so no Stremio app is needed.
 *
 * [apiBase] is Stremio's account service. It is a parameter only so it can be tested.
 */
class StremioRepository(private val apiBase: String = "https://api.strem.io") {

    // ------------------------------------------------------------------ account

    /** Signs in and returns the sign-in key. The password is used once and never kept. */
    suspend fun login(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        val bodies = listOf(
            JSONObject().put("email", email.trim()).put("password", password),
            JSONObject().put("type", "Login").put("email", email.trim()).put("password", password).put("facebook", false),
        )
        var firstError: String? = null
        for (body in bodies) {
            try {
                val reply = JSONObject(Http.post("$apiBase/api/login", body.toString()))
                val key = reply.optJSONObject("result")?.let { str(it, "authKey") }
                if (key != null) return@withContext Result.success(key)
                if (firstError == null) firstError = apiError(reply)
            } catch (e: Exception) {
                if (firstError == null) firstError = Http.friendly(e)
            }
        }
        Result.failure(Exception(friendlyLoginError(firstError)))
    }

    private fun friendlyLoginError(msg: String?): String {
        val m = msg?.lowercase().orEmpty()
        return when {
            msg == null ->
                "Stremio didn't accept that. If you sign in to Stremio with Facebook or Apple, add your addon links by hand instead."
            "passphrase" in m || "password" in m -> "Wrong email or password."
            "user" in m && ("not found" in m || "exist" in m) -> "There's no Stremio account with that email."
            else -> msg
        }
    }

    /** The addon links saved in your Stremio account. */
    suspend fun accountAddonUrls(authKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        val bodies = listOf(
            JSONObject().put("authKey", authKey).put("update", true).put("addFromURL", JSONArray()),
            JSONObject().put("type", "AddonCollectionGet").put("authKey", authKey).put("update", true),
        )
        var firstError: String? = null
        for (body in bodies) {
            try {
                val reply = JSONObject(Http.post("$apiBase/api/addonCollectionGet", body.toString()))
                val arr = reply.optJSONObject("result")?.optJSONArray("addons")
                if (arr != null) {
                    val urls = (0 until arr.length()).mapNotNull { i ->
                        val a = arr.optJSONObject(i) ?: return@mapNotNull null
                        str(a, "transportUrl") ?: str(a, "manifestUrl")
                    }
                    return@withContext Result.success(urls)
                }
                if (firstError == null) firstError = apiError(reply)
            } catch (e: Exception) {
                if (firstError == null) firstError = Http.friendly(e)
            }
        }
        val hint = if (firstError?.contains("session", ignoreCase = true) == true) {
            "Your Stremio sign-in has expired. Sign out and sign in again."
        } else {
            firstError ?: "Couldn't read your addons from Stremio."
        }
        Result.failure(Exception(hint))
    }

    // ------------------------------------------------------------------ addons

    /** Loads an addon from a pasted link (https://, or stremio://). Returns its tidy link and the addon. */
    suspend fun addonFromLink(link: String): Result<Pair<String, StremioAddon>> = withContext(Dispatchers.IO) {
        if (link.isBlank()) return@withContext Result.failure(Exception("Paste an addon link first."))
        val url = normalizeManifestUrl(link)
        try {
            Result.success(url to loadAddon(url))
        } catch (e: Exception) {
            Result.failure(Exception("Couldn't open that addon link. " + Http.friendly(e)))
        }
    }

    /** Everything for the Stremio home page: your addons, and a row of titles for each of their lists. */
    suspend fun loadHome(session: StremioSession): Result<StremioHome> = withContext(Dispatchers.IO) {
        try {
            val urls = LinkedHashSet<String>()
            if (session.signedIn) {
                accountAddonUrls(session.authKey)
                    .onSuccess { list -> list.forEach { urls.add(normalizeManifestUrl(it)) } }
                    .onFailure { if (session.manualAddons.isEmpty()) return@withContext Result.failure(it) }
            }
            session.manualAddons.forEach { urls.add(normalizeManifestUrl(it)) }
            // Stremio's own "local files" addon lives inside the Stremio app on your computer, so it can't be reached from here.
            val reachable = urls.filterNot { "127.0.0.1:11470" in it || "localhost:11470" in it }
            if (reachable.isEmpty()) {
                return@withContext Result.failure(Exception("No addons found yet. Add an addon link, or sign in."))
            }

            val loaded = coroutineScope {
                reachable.map { u -> async { runCatching { loadAddon(u) } } }.awaitAll()
            }
            val addons = loaded.mapNotNull { it.getOrNull() }
            if (addons.isEmpty()) {
                return@withContext Result.failure(Exception("Couldn't reach any of your addons. Check your internet connection."))
            }
            Result.success(StremioHome(addons, loadRows(addons), loaded.size - addons.size))
        } catch (e: Exception) {
            Result.failure(Exception(Http.friendly(e)))
        }
    }

    private fun loadAddon(manifestUrl: String): StremioAddon {
        val o = JSONObject(Http.get(manifestUrl, 20_000))
        val types = strList(o.optJSONArray("types"))
        val prefixes = strList(o.optJSONArray("idPrefixes"))

        val resources = ArrayList<StremioResource>()
        val resArr = o.optJSONArray("resources")
        if (resArr != null) {
            for (i in 0 until resArr.length()) {
                when (val r = resArr.opt(i)) {
                    is String -> resources += StremioResource(r, types, prefixes)
                    is JSONObject -> {
                        val name = str(r, "name") ?: continue
                        resources += StremioResource(
                            name,
                            strList(r.optJSONArray("types")).ifEmpty { types },
                            strList(r.optJSONArray("idPrefixes")).ifEmpty { prefixes },
                        )
                    }
                }
            }
        }

        val catalogs = ArrayList<StremioCatalogDef>()
        val catArr = o.optJSONArray("catalogs")
        if (catArr != null) {
            for (i in 0 until catArr.length()) {
                val c = catArr.optJSONObject(i) ?: continue
                val type = str(c, "type") ?: continue
                val id = str(c, "id") ?: continue
                val supported = HashSet<String>()
                val required = HashSet<String>()
                var genres: List<String> = emptyList()
                val extra = c.optJSONArray("extra")
                if (extra != null) {
                    for (k in 0 until extra.length()) {
                        val e = extra.optJSONObject(k) ?: continue
                        val n = str(e, "name") ?: continue
                        supported += n
                        if (n == "genre") genres = strList(e.optJSONArray("options"))
                        if (e.optBoolean("isRequired", false)) required += n
                    }
                }
                supported += strList(c.optJSONArray("extraSupported"))
                required += strList(c.optJSONArray("extraRequired"))
                catalogs += StremioCatalogDef(
                    type = type,
                    id = id,
                    name = str(c, "name") ?: id,
                    supportsSearch = "search" in supported && required.all { it == "search" },
                    needsExtra = required.isNotEmpty(),
                    genreOptions = genres,
                    genreOnly = required.all { it == "genre" },
                )
            }
        }
        return StremioAddon(
            name = str(o, "name") ?: "Addon",
            baseUrl = baseOf(manifestUrl),
            types = types,
            idPrefixes = prefixes,
            resources = resources,
            catalogs = catalogs,
        )
    }

    // ------------------------------------------------------------------ browsing

    private suspend fun loadRows(addons: List<StremioAddon>): List<StremioRow> = coroutineScope {
        val jobs = addons.flatMap { a ->
            a.catalogs
                .filter { isShown(it.type) && !it.needsExtra }
                .take(8)
                .map { a to it }
        }
        val fetched = jobs.map { (a, c) ->
            async { runCatching { fetchCatalog(a, c, null) }.getOrDefault(emptyList()) }
        }.awaitAll()

        val rows = ArrayList<Triple<StremioAddon, StremioCatalogDef, List<StremioItem>>>()
        jobs.forEachIndexed { i, (a, c) -> if (fetched[i].isNotEmpty()) rows += Triple(a, c, fetched[i]) }

        fun label(c: StremioCatalogDef) = "${c.name} - ${if (c.type == "movie") "Movies" else "Series"}"
        val counts = rows.groupingBy { label(it.second) }.eachCount()
        rows.map { (a, c, items) ->
            val title = label(c).let { if ((counts[it] ?: 0) > 1) "$it (${a.name})" else it }
            StremioRow(title, c.type, items.take(30))
        }
    }

    private fun isShown(type: String) = type == "movie" || type == "series"

    private fun fetchCatalog(addon: StremioAddon, cat: StremioCatalogDef, extra: String?): List<StremioItem> {
        val tail = if (extra == null) "" else "/$extra"
        val url = "${addon.baseUrl}/catalog/${enc(cat.type)}/${enc(cat.id)}$tail.json"
        val metas = JSONObject(Http.get(url, 20_000)).optJSONArray("metas") ?: return emptyList()
        val out = ArrayList<StremioItem>()
        for (i in 0 until metas.length()) {
            val m = metas.optJSONObject(i) ?: continue
            val item = parseItem(m, addon.baseUrl, cat.type) ?: continue
            if (isShown(item.type)) out += item
        }
        return out
    }

    /** Lists of movies or series narrowed to one genre, from every addon list that supports that genre. */
    suspend fun byGenre(addons: List<StremioAddon>, type: String, genre: String): List<StremioRow> = withContext(Dispatchers.IO) {
        val jobs = addons.flatMap { a ->
            a.catalogs.filter { it.type == type && it.genreOnly && it.genreOptions.any { g -> g.equals(genre, true) } }.map { a to it }
        }.take(8)
        val fetched = coroutineScope {
            jobs.map { (a, c) ->
                async {
                    val exact = c.genreOptions.first { it.equals(genre, true) }
                    runCatching { fetchCatalog(a, c, "genre=" + enc(exact)) }.getOrDefault(emptyList())
                }
            }.awaitAll()
        }
        val titles = jobs.map { (_, c) -> "${c.name} - ${c.genreOptions.first { it.equals(genre, true) }}" }
        val dup = titles.groupingBy { it }.eachCount()
        jobs.mapIndexedNotNull { i, (a, _) ->
            if (fetched[i].isEmpty()) null
            else StremioRow(if ((dup[titles[i]] ?: 0) > 1) "${titles[i]} (${a.name})" else titles[i], type, fetched[i].take(40))
        }
    }

    /** Searches every addon that offers search for movies or series. */
    suspend fun search(addons: List<StremioAddon>, query: String): List<StremioItem> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val jobs = addons.flatMap { a ->
            a.catalogs.filter { isShown(it.type) && it.supportsSearch }.map { a to it }
        }.take(12)
        coroutineScope {
            jobs.map { (a, c) ->
                async { runCatching { fetchCatalog(a, c, "search=" + enc(q)) }.getOrDefault(emptyList()) }
            }.awaitAll()
        }.flatten().distinctBy { it.type + ":" + it.id }.take(60)
    }

    /** Full details (and the episode list for a series). Falls back to what we already know. */
    suspend fun details(addons: List<StremioAddon>, item: StremioItem): StremioItem = withContext(Dispatchers.IO) {
        val candidates = addons
            .filter { it.supports("meta", item.type, item.id) }
            .sortedBy { if (it.baseUrl == item.sourceBase) 0 else 1 }
        for (a in candidates) {
            val full = runCatching { fetchMeta(a, item) }.getOrNull()
            if (full != null) return@withContext full
        }
        item
    }

    private fun fetchMeta(addon: StremioAddon, item: StremioItem): StremioItem? {
        val url = "${addon.baseUrl}/meta/${enc(item.type)}/${enc(item.id)}.json"
        val m = JSONObject(Http.get(url, 25_000)).optJSONObject("meta") ?: return null
        val videos = ArrayList<StremioVideo>()
        val arr = m.optJSONArray("videos")
        if (arr != null && item.type == "series") {
            for (i in 0 until arr.length()) {
                val v = arr.optJSONObject(i) ?: continue
                val id = str(v, "id") ?: continue
                val season = if (v.has("season") && !v.isNull("season")) v.optInt("season", -1) else -1
                val ep = when {
                    v.has("episode") && !v.isNull("episode") -> v.optInt("episode", -1)
                    v.has("number") && !v.isNull("number") -> v.optInt("number", -1)
                    else -> -1
                }
                if (season < 0 || ep < 0) continue
                videos += StremioVideo(
                    id = id,
                    title = str(v, "title") ?: str(v, "name") ?: "Episode $ep",
                    season = season,
                    episode = ep,
                    thumbnail = str(v, "thumbnail"),
                )
            }
        }
        return StremioItem(
            id = item.id,
            type = item.type,
            name = str(m, "name") ?: item.name,
            poster = str(m, "poster") ?: item.poster,
            background = str(m, "background") ?: item.background,
            description = str(m, "description") ?: item.description,
            year = str(m, "releaseInfo") ?: str(m, "year") ?: item.year,
            rating = str(m, "imdbRating") ?: item.rating,
            genres = strList(m.optJSONArray("genres")).ifEmpty { strList(m.optJSONArray("genre")) }.ifEmpty { item.genres },
            runtime = str(m, "runtime") ?: item.runtime,
            sourceBase = item.sourceBase,
            videos = videos.sortedWith(compareBy({ it.season }, { it.episode })),
            trailerId = trailerFrom(m) ?: item.trailerId,
        )
    }

    /** Finds the YouTube trailer an addon lists, in either of the two formats addons use. */
    private fun trailerFrom(m: JSONObject): String? {
        val streams = m.optJSONArray("trailerStreams")
        if (streams != null) {
            for (i in 0 until streams.length()) {
                val t = streams.optJSONObject(i) ?: continue
                val id = str(t, "ytId")
                if (id != null) return id
            }
        }
        val trailers = m.optJSONArray("trailers")
        if (trailers != null) {
            for (i in 0 until trailers.length()) {
                val t = trailers.optJSONObject(i) ?: continue
                val type = str(t, "type")
                if (type != null && !type.equals("Trailer", ignoreCase = true)) continue
                val id = str(t, "source")
                if (id != null) return id
            }
        }
        return null
    }

    // ------------------------------------------------------------------ streams

    /**
     * Asks every addon that can give streams for this title.
     * [videoId] is the movie's id, or for an episode its episode id like tt0944947:1:1.
     */
    suspend fun streams(addons: List<StremioAddon>, type: String, videoId: String): StremioStreams =
        withContext(Dispatchers.IO) {
            val targets = addons.filter { it.supports("stream", type, videoId) }
            val results = coroutineScope {
                targets.map { a -> async { runCatching { fetchStreams(a, type, videoId) } } }.awaitAll()
            }
            val playable = ArrayList<StremioStream>()
            var torrents = 0
            var failed = 0
            for (r in results) {
                val pair = r.getOrNull()
                if (pair == null) {
                    failed++
                } else {
                    playable += pair.first
                    torrents += pair.second
                }
            }
            // Best first, so quick play (and the top of the list) picks the most watchable one.
            StremioStreams(playable.sortedByDescending { score(it) }, torrents, failed)
        }

    /**
     * A rough "how good is this link" score from its name and description.
     * 1080p is favoured because it plays smoothly on nearly every TV; 4K comes next, then 720p.
     * Cinema recordings and Dolby Vision (which many sets can't show properly) are pushed down.
     */
    private fun score(s: StremioStream): Int {
        val t = (s.name + " " + s.description).lowercase()
        var n = when {
            "1080" in t -> 400
            "2160" in t || "4k" in t || "uhd" in t -> 300
            "720" in t -> 200
            "480" in t -> 100
            else -> 150
        }
        if (Regex("\\b(cam|camrip|hdcam|ts|telesync|tc|hdts)\\b").containsMatchIn(t)) n -= 350
        if ("dolby vision" in t || Regex("\\bdv\\b").containsMatchIn(t)) n -= 60
        if ("remux" in t) n -= 20
        if (Regex("\\b(h264|x264|avc)\\b").containsMatchIn(t)) n += 15
        // A bigger file within the same quality is usually a better picture, but only a nudge.
        s.sizeBytes?.let { n += (it / 2_000_000_000L).toInt().coerceAtMost(8) }
        return n
    }

    private fun fetchStreams(addon: StremioAddon, type: String, id: String): Pair<List<StremioStream>, Int> {
        val url = "${addon.baseUrl}/stream/${enc(type)}/${enc(id)}.json"
        val arr = JSONObject(Http.get(url, 30_000)).optJSONArray("streams") ?: return emptyList<StremioStream>() to 0
        val playable = ArrayList<StremioStream>()
        var torrents = 0
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            val link = str(s, "url")
            if (link != null && (link.startsWith("http://", true) || link.startsWith("https://", true))) {
                val size = s.optJSONObject("behaviorHints")?.optLong("videoSize", 0L)?.takeIf { it > 0 }
                playable += StremioStream(
                    addonName = addon.name,
                    name = str(s, "name") ?: "",
                    description = str(s, "description") ?: str(s, "title") ?: "",
                    url = link,
                    sizeBytes = size,
                )
            } else if (str(s, "infoHash") != null) {
                torrents++
            }
        }
        return playable to torrents
    }

    // ------------------------------------------------------------------ helpers

    private fun parseItem(o: JSONObject, base: String, fallbackType: String): StremioItem? {
        val id = str(o, "id") ?: return null
        val name = str(o, "name") ?: return null
        return StremioItem(
            id = id,
            type = str(o, "type") ?: fallbackType,
            name = name,
            poster = str(o, "poster"),
            background = str(o, "background"),
            description = str(o, "description") ?: "",
            year = str(o, "releaseInfo") ?: str(o, "year") ?: "",
            rating = str(o, "imdbRating") ?: "",
            genres = strList(o.optJSONArray("genres")),
            sourceBase = base,
        )
    }

    private fun apiError(o: JSONObject): String? = when (val e = o.opt("error")) {
        is JSONObject -> str(e, "message")
        is String -> e.takeIf { it.isNotBlank() }
        else -> null
    }

    companion object {
        /** Turns what you pasted into the address of the addon's manifest. */
        fun normalizeManifestUrl(link: String): String {
            var u = link.trim().substringBefore('#')
            if (u.startsWith("stremio://", ignoreCase = true)) u = "https://" + u.substring("stremio://".length)
            if (!u.contains("manifest.json")) u = u.trimEnd('/') + "/manifest.json"
            return u
        }

        fun baseOf(manifestUrl: String): String = manifestUrl.substringBefore("/manifest.json").trimEnd('/')

        private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        /** Reads a text field, treating missing, null and blank as "not there". */
        private fun str(o: JSONObject, key: String): String? {
            if (!o.has(key) || o.isNull(key)) return null
            return o.optString(key).trim().takeIf { it.isNotEmpty() }
        }

        private fun strList(arr: JSONArray?): List<String> {
            if (arr == null) return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                if (arr.isNull(i)) null else arr.optString(i).trim().takeIf { it.isNotEmpty() }
            }
        }
    }
}
