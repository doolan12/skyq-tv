package com.skyqlike.iptv.data.stremio

import com.skyqlike.iptv.data.Http
import com.skyqlike.iptv.data.model.ExternalSubtitle
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

    /** How each addon did the last time streams were looked up, for the Addon health page. */
    val streamStats = java.util.concurrent.ConcurrentHashMap<String, StreamStat>()

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

    /**
     * The titles saved in your Stremio library (not removed, not just recently opened). Read only:
     * this never changes anything in your Stremio account.
     */
    suspend fun library(authKey: String): Result<List<StremioItem>> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("authKey", authKey).put("collection", "libraryItem")
                .put("ids", JSONArray()).put("all", true)
            val reply = JSONObject(Http.post("$apiBase/api/datastoreGet", body.toString(), 60_000))
            val arr = reply.optJSONArray("result")
                ?: return@withContext Result.failure(Exception(apiError(reply) ?: "Stremio didn't send your library."))
            val items = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                if (o.optBoolean("removed") || o.optBoolean("temp")) return@mapNotNull null
                val type = str(o, "type") ?: return@mapNotNull null
                if (type != "movie" && type != "series") return@mapNotNull null
                val id = str(o, "_id") ?: return@mapNotNull null
                val name = str(o, "name") ?: return@mapNotNull null
                StremioItem(id = id, type = type, name = name, poster = str(o, "poster"), background = str(o, "background"))
            }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(Exception(Http.friendly(e)))
        }
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
    suspend fun loadHome(
        session: StremioSession,
        cacheOnly: Boolean = false,
        /** Called with a growing Home each time another row arrives, so the screen can fill in as it goes. */
        onPartial: ((StremioHome) -> Unit)? = null,
    ): Result<StremioHome> = withContext(Dispatchers.IO) {
        try {
            val urls = LinkedHashSet<String>()
            if (cacheOnly) {
                // Instant start: rebuild Home from the copies saved last time, without touching the network.
                Http.readCache(HOME_URLS_KEY)?.lines()?.filter { it.isNotBlank() }?.let { urls.addAll(it) }
                if (urls.isEmpty()) return@withContext Result.failure(Exception("Nothing saved yet."))
            } else if (session.signedIn) {
                accountAddonUrls(session.authKey)
                    .onSuccess { list -> list.forEach { urls.add(normalizeManifestUrl(it)) } }
                    .onFailure { if (session.manualAddons.isEmpty()) return@withContext Result.failure(it) }
            }
            if (!cacheOnly) session.manualAddons.forEach { urls.add(normalizeManifestUrl(it)) }
            // Stremio's own "local files" addon lives inside the Stremio app on your computer, so it can't be reached from here.
            val reachable = urls.filterNot { "127.0.0.1:11470" in it || "localhost:11470" in it }
            if (reachable.isEmpty()) {
                return@withContext Result.failure(Exception("No addons found yet. Add an addon link, or sign in."))
            }

            val loaded = coroutineScope {
                reachable.map { u ->
                    async {
                        val started = System.currentTimeMillis()
                        val r = runCatching { loadAddon(u, cacheOnly) }
                        Triple(u, r, System.currentTimeMillis() - started)
                    }
                }.awaitAll()
            }
            val addons = loaded.mapNotNull { it.second.getOrNull() }
            if (addons.isEmpty()) {
                return@withContext Result.failure(Exception("Couldn't reach any of your addons. Check your internet connection."))
            }
            val failedCount = loaded.size - addons.size
            val catalogStats = ArrayList<CatalogStat>()
            val rows = loadRows(addons, cacheOnly, catalogStats) { partial -> onPartial?.invoke(StremioHome(addons, partial, failedCount)) }
            if (cacheOnly && rows.isEmpty()) return@withContext Result.failure(Exception("Nothing saved yet."))
            if (!cacheOnly) Http.writeCache(HOME_URLS_KEY, reachable.joinToString("\n"))
            val health = if (cacheOnly) emptyList() else loaded.map { (u, r, ms) ->
                val addon = r.getOrNull()
                if (addon == null) {
                    // Only the host is kept, because the full address can contain a key.
                    val host = runCatching { java.net.URI(u).host }.getOrNull() ?: "Unknown addon"
                    AddonHealth(host, false, ms, 0, 0, 0, (r.exceptionOrNull() as? Exception)?.let { Http.friendly(it) } ?: "")
                } else {
                    val mine = synchronized(catalogStats) { catalogStats.filter { it.addonBase == addon.baseUrl } }
                    AddonHealth(addon.name, true, ms, mine.size, mine.count { it.count > 0 }, mine.maxOfOrNull { it.ms } ?: 0L)
                }
            }
            Result.success(StremioHome(addons, rows, failedCount, health))
        } catch (e: Exception) {
            Result.failure(Exception(Http.friendly(e)))
        }
    }

    private fun loadAddon(manifestUrl: String, cacheOnly: Boolean = false): StremioAddon {
        val o = JSONObject(Http.getCached(manifestUrl, 10_000, 8_000, cacheOnly))
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

    private suspend fun loadRows(
        addons: List<StremioAddon>,
        cacheOnly: Boolean = false,
        /** Collects how long each list took and how many titles it gave, for the Addon health page. */
        stats: MutableList<CatalogStat>? = null,
        /** Called each time another row arrives, with every row that has arrived so far (in list order). */
        onPartial: ((List<StremioRow>) -> Unit)? = null,
    ): List<StremioRow> = coroutineScope {
        val jobs = addons.flatMap { a ->
            a.catalogs
                .filter { isShown(it.type) && !it.needsExtra }
                .take(8)
                .map { a to it }
        }

        fun label(c: StremioCatalogDef) = "${c.name} - ${if (c.type == "movie") "Movies" else "Series"}"
        // Titles that repeat get the addon's name added. Decided up front so a title never changes as rows arrive.
        val counts = jobs.groupingBy { label(it.second) }.eachCount()
        fun rowFor(i: Int, items: List<StremioItem>): StremioRow {
            val (a, c) = jobs[i]
            val title = label(c).let { if ((counts[it] ?: 0) > 1) "$it (${a.name})" else it }
            return StremioRow(title, c.type, items.take(30))
        }

        val results = arrayOfNulls<List<StremioItem>>(jobs.size)
        fun snapshot(): List<StremioRow> =
            jobs.indices.mapNotNull { i -> results[i]?.takeIf { it.isNotEmpty() }?.let { rowFor(i, it) } }

        jobs.mapIndexed { i, (a, c) ->
            async {
                val started = System.currentTimeMillis()
                val items = runCatching { fetchCatalog(a, c, null, cacheOnly, fast = true) }.getOrDefault(emptyList())
                stats?.let { st -> synchronized(st) { st += CatalogStat(a.baseUrl, System.currentTimeMillis() - started, items.size) } }
                if (onPartial != null) {
                    val rows = synchronized(results) {
                        results[i] = items
                        snapshot()
                    }
                    if (items.isNotEmpty()) onPartial(rows)
                } else {
                    results[i] = items
                }
            }
        }.awaitAll()

        snapshot()
    }

    private fun isShown(type: String) = type == "movie" || type == "series"

    private fun fetchCatalog(
        addon: StremioAddon,
        cat: StremioCatalogDef,
        extra: String?,
        cacheOnly: Boolean = false,
        fast: Boolean = false,
    ): List<StremioItem> {
        val tail = if (extra == null) "" else "/$extra"
        val url = "${addon.baseUrl}/catalog/${enc(cat.type)}/${enc(cat.id)}$tail.json"
        // Home rows give up on a slow addon sooner, and keep a saved copy for next time.
        val text = if (fast || cacheOnly) Http.getCached(url, 12_000, 6_000, cacheOnly) else Http.get(url, 20_000)
        val metas = JSONObject(text).optJSONArray("metas") ?: return emptyList()
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
    suspend fun streams(
        addons: List<StremioAddon>,
        type: String,
        videoId: String,
        /** The quality and size choices from Settings. */
        pref: StreamPrefs = StreamPrefs(),
        /** Called each time another addon answers, with everything found so far (best first). */
        onUpdate: ((StremioStreams) -> Unit)? = null,
    ): StremioStreams = withContext(Dispatchers.IO) {
        val targets = addons.filter { it.supports("stream", type, videoId) }
        val gathered = ArrayList<Result<Pair<List<StremioStream>, Int>>>()

        // Must be called while holding the lock on [gathered].
        fun build(): StremioStreams {
            val playable = ArrayList<StremioStream>()
            var torrents = 0
            var failed = 0
            for (r in gathered) {
                val pair = r.getOrNull()
                if (pair == null) {
                    failed++
                } else {
                    playable += pair.first
                    torrents += pair.second
                }
            }
            // Best first, so quick play (and the top of the list) picks the most watchable one.
            // Over the size limit is dropped, unless that would leave nothing to play.
            val limit = pref.maxGb * 1_000_000_000L
            val allowed = if (pref.maxGb > 0) playable.filter { it.sizeBytes == null || it.sizeBytes <= limit } else playable
            return StremioStreams((allowed.ifEmpty { playable }).sortedByDescending { score(it, pref) }, torrents, failed)
        }

        coroutineScope {
            targets.map { a ->
                async {
                    val started = System.currentTimeMillis()
                    val r = runCatching { fetchStreams(a, type, videoId) }
                    streamStats[a.name] = StreamStat(
                        a.name,
                        System.currentTimeMillis() - started,
                        r.getOrNull()?.let { it.first.size + it.second } ?: 0,
                        r.isSuccess,
                    )
                    val snapshot = synchronized(gathered) {
                        gathered += r
                        build()
                    }
                    onUpdate?.invoke(snapshot)
                }
            }.awaitAll()
        }
        synchronized(gathered) { build() }
    }

    /**
     * A rough "how good is this link" score from its name and description.
     * 1080p is favoured because it plays smoothly on nearly every TV; 4K comes next, then 720p.
     * Cinema recordings and Dolby Vision (which many sets can't show properly) are pushed down.
     */
    private fun score(s: StremioStream, pref: StreamPrefs = StreamPrefs()): Int {
        val t = (s.name + " " + s.description).lowercase()
        var n = when {
            "1080" in t -> 400
            "2160" in t || "4k" in t || "uhd" in t -> 300
            "720" in t -> 200
            "480" in t -> 100
            else -> 150
        }
        val is4k = "2160" in t || "4k" in t || "uhd" in t
        when (pref.quality) {
            1 -> if (is4k) n -= 500 // up to 1080p
            2 -> if (is4k || "1080" in t) n -= 500 // up to 720p
            3 -> if (is4k) n += 250 // best available
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

    // ------------------------------------------------------------------ subtitles

    /**
     * Asks every addon that offers subtitles (such as OpenSubtitles) for this movie or episode.
     * Keeps a few English ones and one of each other language, and drops any link that doesn't answer,
     * because a dead subtitle link could otherwise stop the picture.
     */
    suspend fun subtitles(addons: List<StremioAddon>, type: String, videoId: String): List<ExternalSubtitle> =
        withContext(Dispatchers.IO) {
            val targets = addons.filter { it.supports("subtitles", type, videoId) }
            if (targets.isEmpty()) return@withContext emptyList()

            val found = coroutineScope {
                targets.map { a -> async { runCatching { fetchSubtitles(a, type, videoId) }.getOrDefault(emptyList()) } }.awaitAll()
            }.flatten().distinctBy { it.url }

            val english = found.filter { isEnglish(it.language) }.take(3)
            val others = found.filterNot { isEnglish(it.language) }.distinctBy { it.language.lowercase() }.take(3)
            val picked = english + others

            val answered = coroutineScope {
                picked.map { s -> async { runCatching { Http.get(s.url, 8_000) }.isSuccess } }.awaitAll()
            }
            val good = picked.filterIndexed { i, _ -> answered[i] }

            // Repeated languages are numbered: English, English 2, English 3.
            val seen = HashMap<String, Int>()
            good.map { s ->
                val name = languageName(s.language)
                val n = (seen[name] ?: 0) + 1
                seen[name] = n
                s.copy(label = if (n > 1) "$name $n" else name)
            }
        }

    private fun fetchSubtitles(addon: StremioAddon, type: String, id: String): List<ExternalSubtitle> {
        val url = "${addon.baseUrl}/subtitles/${enc(type)}/${enc(id)}.json"
        val arr = JSONObject(Http.get(url, 10_000)).optJSONArray("subtitles") ?: return emptyList()
        val out = ArrayList<ExternalSubtitle>()
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            val link = str(s, "url") ?: continue
            if (!link.startsWith("http://", true) && !link.startsWith("https://", true)) continue
            out += ExternalSubtitle(link, str(s, "lang") ?: "und", "")
        }
        return out
    }

    private fun isEnglish(lang: String) =
        lang.equals("eng", true) || lang.equals("en", true) || lang.startsWith("en-", true)

    private fun languageName(code: String): String {
        val names = mapOf(
            "English" to "eng en",
            "Spanish" to "spa es",
            "French" to "fre fra fr",
            "German" to "ger deu de",
            "Italian" to "ita it",
            "Portuguese" to "por pob pt",
            "Dutch" to "dut nld nl",
            "Russian" to "rus ru",
            "Arabic" to "ara ar",
            "Chinese" to "chi zho zh",
            "Japanese" to "jpn ja",
            "Korean" to "kor ko",
            "Hindi" to "hin hi",
            "Turkish" to "tur tr",
            "Polish" to "pol pl",
            "Greek" to "gre ell el",
            "Swedish" to "swe sv",
            "Danish" to "dan da",
            "Norwegian" to "nor no",
            "Finnish" to "fin fi",
        )
        val c = code.lowercase().substringBefore('-')
        return names.entries.firstOrNull { c in it.value.split(' ') }?.key ?: code.uppercase()
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

        private const val HOME_URLS_KEY = "home-addon-urls"

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

/** What you asked for in Settings: [quality] 0 = automatic, 1 = up to 1080p, 2 = up to 720p, 3 = best (4K); [maxGb] 0 = no limit. */
data class StreamPrefs(val quality: Int = 0, val maxGb: Int = 0)

/** How long one list took and how many titles it gave. */
class CatalogStat(val addonBase: String, val ms: Long, val count: Int)
