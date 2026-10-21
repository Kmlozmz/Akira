package com.akira.app.core.online

import android.content.Context
import android.net.Uri
import com.akira.innertube.YouTube
import com.akira.innertube.NewPipeExtractor
import com.akira.innertube.models.*
import com.akira.app.core.model.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

data class OnlineAccount(val connected: Boolean = false, val name: String = "", val image: String? = null)
enum class OnlineShelfLayout { COVERS, TRACKS, RANKED_TRACKS, VIDEOS, ARTISTS }
data class OnlineShelf(val title: String, val items: List<YTItem>, val reason: String? = null,
    val thumbnail: String? = null, val layout: OnlineShelfLayout = OnlineShelfLayout.COVERS,
    val artistAlbums: Map<String, List<AlbumItem>> = emptyMap())
data class OnlineCollection(val item: YTItem, val description: String? = null, val stats: String? = null,
    val creators: List<Artist> = emptyList())
data class OnlinePage(val title: String, val shelves: List<OnlineShelf>, val continuation: String? = null,
    val collection: OnlineCollection? = null)
data class OnlineStream(val url: String, val headers: Map<String, String>)

/** Page's provider, kept separate from the MediaStore library and its database. */
class OnlineRepository private constructor(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("akira_online", Context.MODE_PRIVATE)
    private val sessionStore = OnlineSessionStore(app)
    private val _enabled = MutableStateFlow(prefs.getBoolean("enabled", false))
    val enabled = _enabled.asStateFlow()
    private val _account = MutableStateFlow(OnlineAccount())
    val account = _account.asStateFlow()
    private val _sessionReady = MutableStateFlow(false)
    val sessionReady = _sessionReady.asStateFlow()
    private val _likedIds = MutableStateFlow<Set<String>>(emptySet())
    val likedIds = _likedIds.asStateFlow()
    private val likeOverrides = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val likeLock = Mutex()
    private val visitorLock = Mutex()
    private var visitorAttemptAt = -60_000L
    private val historyLock = Mutex()
    private val _recentSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val recentSongs = _recentSongs.asStateFlow()
    @Volatile private var historyScope = "guest"
    private val _mostPlayed = MutableStateFlow<List<SongItem>>(emptyList())
    val mostPlayed = _mostPlayed.asStateFlow()
    private fun historyKey(scope: String) = if (scope == "guest") "guest_history" else "history_$scope"
    private fun loadHistory(scope: String) {
        historyScope = scope
        _recentSongs.value = guestFavorites(historyKey(scope))
        _mostPlayed.value = guestFavorites("plays_$scope")
    }
    private val knownSongs = java.util.concurrent.ConcurrentHashMap<String, SongItem>()
    private val artistPortraits = java.util.concurrent.ConcurrentHashMap<String, ArtistItem>()
    private val artistReleases = java.util.concurrent.ConcurrentHashMap<String, List<AlbumItem>>()
    private val initialized = CompletableDeferred<Unit>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val streams = mutableMapOf<String, Pair<Long, OnlineStream>>()
    private val rouletteWinners = ArrayDeque<String>()
    private val browsePages = linkedMapOf<String, Triple<OnlineAccount, OnlinePage, Long>>()
    private val _recentSearches = MutableStateFlow<List<String>>(runCatching {
        val stored = org.json.JSONArray(prefs.getString("search_history", "[]"))
        (0 until stored.length()).map { stored.getString(it) }.take(12)
    }.getOrDefault(emptyList()))
    val recentSearches = _recentSearches.asStateFlow()
    private val streamClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(8, java.util.concurrent.TimeUnit.SECONDS).build()
    init {
        scope.launch {
            try {
                val language = Locale.getDefault().language.ifBlank { "en" }
                val country = Locale.getDefault().country.ifBlank { "US" }
                YouTube.locale = YouTubeLocale(gl = country, hl = language)
                _likedIds.value = guestFavorites().map { it.id }.toSet()
                loadHistory("guest")
                sessionStore.read()?.let { saved ->
                    applySession(saved)
                    loadHistory(saved.optString("history_scope").ifBlank { accountHistoryScope(saved.optString("name")) })
                    _likedIds.value = emptySet()
                    _account.value = OnlineAccount(true, saved.optString("name"), saved.optString("image").takeIf { it.isNotBlank() })
                }
            } catch (_: Exception) {
                YouTube.cookie = null; YouTube.dataSyncId = null; YouTube.visitorData = null
                YouTube.useLoginForBrowse = false
            } finally { _sessionReady.value = true; initialized.complete(Unit) }
        }
        scope.launch {
            combine(enabled, account) { mode, user -> mode && user.connected }.collectLatest { connected ->
                if (connected) try { refreshLikes() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Existing feed and playback remain available if likes cannot load. */ }
            }
        }
    }
    fun setEnabled(value: Boolean) { prefs.edit().putBoolean("enabled", value).apply(); _enabled.value = value }
    fun rememberSearch(query: String) {
        val text = query.trim().take(200)
        if(text.isBlank()) return
        _recentSearches.value = (listOf(text) + _recentSearches.value.filterNot { it.equals(text, true) }).take(12)
        prefs.edit().putString("search_history", org.json.JSONArray(_recentSearches.value).toString()).apply()
    }
    fun clearSearches() {
        _recentSearches.value = emptyList()
        prefs.edit().remove("search_history").apply()
    }
    fun cachedPage(key: String, user: OnlineAccount): OnlinePage? = synchronized(browsePages) {
        browsePages[key]?.takeIf { it.first == user && _account.value == user }?.second
    }
    fun hasFreshPage(key: String, user: OnlineAccount): Boolean = synchronized(browsePages) {
        browsePages[key]?.let { it.first == user && _account.value == user &&
            android.os.SystemClock.elapsedRealtime() - it.third < 300_000L } == true
    }
    fun cachePage(key: String, page: OnlinePage, user: OnlineAccount) = synchronized(browsePages) {
        if(_account.value == user) {
            browsePages.remove(key)
            browsePages[key] = Triple(user, page, android.os.SystemClock.elapsedRealtime())
            if(browsePages.size > 20) browsePages.remove(browsePages.keys.first())
        }
    }
    fun recordRouletteChoice(song: Song) = synchronized(rouletteWinners) {
        rouletteWinners.remove(song.id.removePrefix("youtube:"))
        rouletteWinners.addLast(song.id.removePrefix("youtube:"))
        while(rouletteWinners.size > 8) rouletteWinners.removeFirst()
    }
    internal suspend fun awaitSessionInitialization() { initialized.await() }
    private fun applySession(saved: JSONObject) {
        val cookie = saved.getString("cookie")
        val cookieValues = cookie.split(';').map { it.trim().split('=', limit = 2) }
            .filter { it.size == 2 }.associate { it[0] to it[1] }
        YouTube.cookie = if ("SAPISID" !in cookieValues && "__Secure-3PAPISID" in cookieValues)
            "$cookie; SAPISID=${cookieValues["__Secure-3PAPISID"]}" else cookie
        YouTube.useLoginForBrowse = true
        YouTube.visitorData = saved.optString("visitor").takeIf { it.isNotBlank() && it != "null" && it != "undefined" }
        val data = saved.optString("sync").takeUnless { it == "null" || it == "undefined" }.orEmpty()
        YouTube.dataSyncId = data.takeIf { !it.contains("||") }?.takeIf { it.isNotBlank() }
            ?: if (data.endsWith("||")) data.substringBefore("||") else data.substringAfter("||").takeIf { it.isNotBlank() }
    }
    suspend fun connect(cookie: String, visitor: String?, sync: String?) = withContext(Dispatchers.IO) {
        initialized.await()
        lock.withLock {
            val saved = JSONObject().put("cookie", cookie).put("visitor", visitor.orEmpty()).put("sync", sync.orEmpty())
            applySession(saved)
            try {
                val info = YouTube.accountInfo().getOrThrow()
                val userScope = accountHistoryScope(info.channelHandle ?: info.email ?: info.name)
                saved.put("name", info.name).put("image", info.thumbnailUrl.orEmpty()).put("history_scope", userScope)
                sessionStore.write(saved)
                streams.clear()
                _likedIds.value = emptySet(); likeOverrides.clear(); knownSongs.clear()
                synchronized(rouletteWinners) { rouletteWinners.clear() }
                synchronized(browsePages) { browsePages.clear() }
                loadHistory(userScope)
                _account.value = OnlineAccount(true, info.name, info.thumbnailUrl)
            } catch (e: Exception) {
                sessionStore.read()?.let(::applySession) ?: run { YouTube.cookie = null; YouTube.dataSyncId = null; YouTube.useLoginForBrowse = false }
                throw e
            }
        }
    }
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        initialized.await()
        synchronized(browsePages) { browsePages.clear() }
        _account.value = OnlineAccount()
        _likedIds.value = emptySet(); likeOverrides.clear(); knownSongs.clear()
        synchronized(rouletteWinners) { rouletteWinners.clear() }
        lock.withLock {
            sessionStore.clear(); streams.clear()
            YouTube.cookie = null; YouTube.dataSyncId = null; YouTube.visitorData = null
            YouTube.useLoginForBrowse = false
            _account.value = OnlineAccount()
            SourceQueueStore.clearOnline(app)
            if (com.akira.app.core.widget.WidgetPlaybackStore.read(app)?.songs?.firstOrNull()
                    ?.contentUri?.startsWith("akira-youtube:") == true) {
                app.getSharedPreferences("akira_widget", Context.MODE_PRIVATE).edit().clear().apply()
            }
            _likedIds.value = guestFavorites().map { it.id }.toSet()
            loadHistory("guest")
        }
    }
    private suspend fun ready() {
        initialized.await()
        check(_enabled.value) { "Modo online desactivado" }
        if (YouTube.visitorData.isNullOrBlank()) visitorLock.withLock {
            val now = android.os.SystemClock.elapsedRealtime()
            if (YouTube.visitorData.isNullOrBlank() && now - visitorAttemptAt >= 60_000L) {
                visitorAttemptAt = now
                withTimeoutOrNull(3_000) { YouTube.refreshVisitorData().getOrNull() }
            }
        }
    }
    private fun guestFavorites(key: String = "guest_favorites"): List<SongItem> = runCatching {
        val array = org.json.JSONArray(prefs.getString(key, "[]"))
        (0 until array.length()).map { index ->
            val row = array.getJSONObject(index)
            val song = com.akira.app.core.widget.WidgetPlaybackStore.decodeSong(row)
            val authors = row.optJSONArray("authors")
            SongItem(song.id.removePrefix("youtube:"), song.title,
                if (authors == null) listOf(Artist(song.artist, null)) else (0 until authors.length()).map {
                    val author = authors.getJSONObject(it)
                    Artist(author.getString("name"), author.optString("id").takeIf(String::isNotBlank))
                }, album = row.optString("online_album_id").takeIf(String::isNotBlank)?.let { Album(song.album, it) },
                duration = song.durationSeconds, thumbnail = song.albumArtUri.orEmpty())
        }
    }.getOrDefault(emptyList())
    private fun accountHistoryScope(identity: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(identity.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** Playback history belongs to the active account, or to this device's guest session. */
    fun recordListening(song: Song) {
        if (!_sessionReady.value || !_enabled.value || !song.contentUri.startsWith("akira-youtube:")) return
        val id = song.id.removePrefix("youtube:")
        if (_recentSongs.value.firstOrNull()?.id == id) return
        val item = knownSongs[id] ?: SongItem(id, song.title, listOf(Artist(song.artist, null)),
            duration = song.durationSeconds, thumbnail = song.albumArtUri.orEmpty())
        val userScope = historyScope
        val history = (listOf(item) + _recentSongs.value.filterNot { it.id == id }).take(30)
        _recentSongs.value = history
        scope.launch { historyLock.withLock {
            val rows = org.json.JSONArray()
            history.forEach { track -> rows.put(encodeGuestSong(track)) }
            prefs.edit().putString(historyKey(userScope), rows.toString()).apply()
        } }
    }
    /** Called by the player's qualified-listen tracker, never on pause or resume. */
    fun recordQualifiedPlay(song: Song) {
        if (!_sessionReady.value || !_enabled.value || !song.contentUri.startsWith("akira-youtube:")) return
        val userScope = historyScope
        val id = song.id.removePrefix("youtube:")
        val item = knownSongs[id] ?: SongItem(id, song.title, listOf(Artist(song.artist, null)),
            duration = song.durationSeconds, thumbnail = song.albumArtUri.orEmpty())
        scope.launch { historyLock.withLock {
            val key = "plays_$userScope"
            val stored = runCatching { org.json.JSONArray(prefs.getString(key, "[]")) }.getOrDefault(org.json.JSONArray())
            val rows = (0 until stored.length()).map { stored.getJSONObject(it) }.toMutableList()
            val old = rows.firstOrNull { it.optString("id") == song.id }
            rows.remove(old)
            rows += encodeGuestSong(item).put("count", (old?.optInt("count") ?: 0) + 1)
            val sorted = org.json.JSONArray(rows.sortedByDescending { it.optInt("count") }.take(100))
            prefs.edit().putString(key, sorted.toString()).apply()
            if (historyScope == userScope) _mostPlayed.value = guestFavorites(key)
        } }
    }
    private fun encodeGuestSong(item: SongItem): JSONObject =
        com.akira.app.core.widget.WidgetPlaybackStore.encodeSong(item.asOnlineSong())
            .put("online_album_id", item.album?.id.orEmpty()).put("authors", org.json.JSONArray().apply {
                item.artists.forEach { put(JSONObject().put("name", it.name).put("id", it.id.orEmpty())) }
            })
    private suspend fun refreshLikes() {
        ready()
        val first = YouTube.playlist("LM").getOrThrow()
        val ids = first.songs.map { it.id }.toMutableSet()
        fun publish() {
            if (_account.value.connected) _likedIds.value = (ids + likeOverrides.filterValues { it }.keys) - likeOverrides.filterValues { !it }.keys
        }
        publish()
        var token = first.songsContinuation
        val seen = mutableSetOf<String>()
        while (token != null && seen.add(token)) {
            currentCoroutineContext().ensureActive(); ready()
            val next = YouTube.playlistContinuation(token).getOrThrow()
            ids.addAll(next.songs.map { it.id }); publish(); token = next.continuation
        }
    }
    suspend fun toggleLike(song: Song) = withContext(Dispatchers.IO) {
        likeLock.withLock {
            ready()
            val id = song.id.removePrefix("youtube:")
            require(id.matches(Regex("[A-Za-z0-9_-]{11}")))
            val liked = id !in _likedIds.value
            if (!_account.value.connected) {
                val saved = guestFavorites().associateBy { it.id }.toMutableMap()
                if (liked) saved[id] = knownSongs[id] ?: SongItem(id, song.title, listOf(Artist(song.artist, null)),
                    duration = song.durationSeconds, thumbnail = song.albumArtUri.orEmpty()) else saved.remove(id)
                val rows = org.json.JSONArray()
                saved.values.forEach { item ->
                    rows.put(encodeGuestSong(item))
                }
                prefs.edit().putString("guest_favorites", rows.toString()).apply()
                _likedIds.value = saved.keys.toSet()
                return@withLock
            }
            YouTube.likeVideo(id, liked).getOrThrow()
            ready()
            likeOverrides[id] = liked
            _likedIds.value = if (liked) _likedIds.value + id else _likedIds.value - id
        }
    }
    suspend fun collectionFor(song: Song, artist: Boolean): YTItem? = withContext(Dispatchers.IO) {
        ready()
        val id = song.id.removePrefix("youtube:")
        var item = knownSongs[id] ?: YouTube.queue(videoIds = listOf(id)).getOrNull()?.firstOrNull()?.also { knownSongs[id] = it }
            ?: return@withContext null
        if ((!artist && item.album == null) || (artist && item.artists.none { !it.id.isNullOrBlank() })) {
            YouTube.queue(videoIds = listOf(id)).getOrNull()?.firstOrNull()?.let { fetched ->
                item = item.copy(album = item.album ?: fetched.album,
                    artists = if (item.artists.any { !it.id.isNullOrBlank() }) item.artists else fetched.artists)
            }
        }
        if (!artist && item.album == null) {
            val wanted = item
            val title = cleanOnlineTitle(wanted.title)
            val candidates = YouTube.search("${wanted.artists.firstOrNull()?.name.orEmpty()} $title", YouTube.SearchFilter.FILTER_SONG)
                .getOrNull()?.items.orEmpty().filterIsInstance<SongItem>()
            val canonical = candidates.firstOrNull { candidate -> candidate.album != null &&
                onlineIdentity(cleanOnlineTitle(candidate.title)) == onlineIdentity(title) &&
                wanted.artists.any { credit -> candidate.artists.any { onlineIdentity(it.name) == onlineIdentity(credit.name) } } }
            canonical?.let { item = wanted.copy(album = it.album) }
        }
        knownSongs[id] = item
        if (artist) return@withContext item.artists.firstOrNull { !it.id.isNullOrBlank() }?.let {
            ArtistItem(it.id!!, it.name, item.thumbnail, shuffleEndpoint = null, radioEndpoint = null)
        }
        item.album?.let { AlbumItem(it.id, "", title = it.name, artists = item.artists, thumbnail = item.thumbnail) }
    }
    private fun catalog(page: OnlinePage): OnlinePage {
        if (knownSongs.size > 5000) knownSongs.clear()
        page.shelves.flatMap { it.items }.filterIsInstance<SongItem>().forEach { knownSongs[it.id] = it }
        return page
    }
    private fun com.akira.innertube.pages.HomePage.Section.asOnlineShelf(): OnlineShelf {
        val videos = items.filterIsInstance<SongItem>()
        val layout = when {
            videos.isNotEmpty() && videos.size == items.size && videos.all { it.isVideoSong } -> OnlineShelfLayout.VIDEOS
            compact -> OnlineShelfLayout.TRACKS
            items.all { it is ArtistItem } -> OnlineShelfLayout.ARTISTS
            else -> OnlineShelfLayout.COVERS
        }
        return OnlineShelf(title, items, label, thumbnail, layout)
    }
    private suspend fun artistCollections(shelves: List<OnlineShelf>): List<OnlineShelf> = supervisorScope {
        val artists = shelves.filter { it.layout == OnlineShelfLayout.ARTISTS }.flatMap { it.items }
            .filterIsInstance<ArtistItem>().distinctBy { it.id }.take(4)
        withTimeoutOrNull(4_000) {
            artists.map { artist -> async {
                if (!artistReleases.containsKey(artist.id)) YouTube.artist(artist.id).getOrNull()?.let { page ->
                    if (artistReleases.size >= 128) artistReleases.clear()
                    artistReleases[artist.id] = page.sections.flatMap { it.items }.filterIsInstance<AlbumItem>().distinctBy { it.id }.take(4)
                    artistPortraits[artist.id] = page.artist
                }
            } }.awaitAll()
        }
        shelves.map { shelf -> if (shelf.layout != OnlineShelfLayout.ARTISTS) shelf else shelf.copy(
            items = shelf.items.map { item -> artistPortraits[item.id]?.takeIf { item is ArtistItem } ?: item },
            artistAlbums = shelf.artistAlbums + shelf.items.mapNotNull { item ->
                artistReleases[item.id]?.takeIf { it.size >= 2 }?.let { item.id to it }
            }.toMap()) }
    }
    suspend fun home(continuation: String? = null, seed: Song? = null): OnlinePage = withContext(Dispatchers.IO) {
        ready()
        if (continuation != null) {
            val next = YouTube.home(continuation).getOrThrow()
            return@withContext catalog(OnlinePage("Home", artistCollections(next.sections.map { it.asOnlineShelf() }), next.continuation))
        }
        supervisorScope {
        val spanish = Locale.getDefault().language == "es"
        val initialSource = seed?.takeIf { it.contentUri.startsWith("akira-youtube:") }
            ?: _recentSongs.value.firstOrNull()?.asOnlineSong()
        val home = async {
            try { withTimeout(9_000) { YouTube.home() } }
            catch(timeout: TimeoutCancellationException) { Result.failure(timeout) }
        }
        val history = async { if (_account.value.connected) withTimeoutOrNull(6_000) {
            YouTube.musicHistory().getOrNull()?.sections.orEmpty().flatMap { it.songs }
        }.orEmpty() else emptyList() }
        val releases = async { withTimeoutOrNull(8_000) { YouTube.explore().getOrNull()?.newReleaseAlbums } }
        val charts = async { withTimeoutOrNull(8_000) { YouTube.getChartsPage().getOrNull() } }
        val related = async { withTimeoutOrNull(8_000) {
            val source = initialSource ?: history.await().firstOrNull()?.asOnlineSong()
            source?.let {
                val id = it.id.removePrefix("youtube:")
                // The same song radio used by Page: a bare video endpoint can be a one-song dead end for guests.
                val next = YouTube.next(WatchEndpoint(videoId = id, playlistId = "RDAMVM$id")).getOrNull()
                val details = next?.relatedEndpoint?.let { endpoint -> YouTube.related(endpoint).getOrNull() }
                Triple(source, next, details)
            }
        } }
        val result = home.await()
        val page = result.getOrNull()
        val shelves = mutableListOf<OnlineShelf>()
        val recent = (_recentSongs.value + history.await()).distinctBy { it.id }.take(30)
        if (recent.isNotEmpty()) shelves += OnlineShelf(if (spanish) "Escuchadas recientemente" else "Recently played", recent.take(12))
        if (_mostPlayed.value.isNotEmpty()) shelves += OnlineShelf(if (spanish) "Más escuchadas" else "Most played",
            _mostPlayed.value.take(18), layout = OnlineShelfLayout.RANKED_TRACKS)
        val (source, next, detail) = related.await() ?: Triple(initialSource, null, null)
        if (source != null) {
            val tracks = (detail?.songs.orEmpty().ifEmpty { next?.items.orEmpty() }).filterNot { "youtube:${it.id}" == source.id }.distinctBy { it.id }.take(18)
            if (tracks.isNotEmpty()) shelves += OnlineShelf(if (spanish) "Para seguir escuchando" else "Keep listening", tracks,
                if (spanish) "Porque escuchaste ${source.title}" else "Because you listened to ${source.title}",
                thumbnail = source.albumArtUri, layout = OnlineShelfLayout.TRACKS)
            // Some guest radio responses omit the Related tab. Its actual song metadata still identifies artists/albums.
            val artistCandidates = detail?.artists.orEmpty().ifEmpty { tracks.flatMap { song ->
                song.artists.mapNotNull { artist -> artist.id?.takeIf(String::isNotBlank)?.let { id ->
                    ArtistItem(id, artist.name, null, shuffleEndpoint = null, radioEndpoint = null)
                } }
            }.distinctBy { it.id } }.take(8)
            val portraits = kotlinx.coroutines.sync.Semaphore(3)
            val artists = withTimeoutOrNull(4_000) { artistCandidates.map { artist -> async {
                if (!artist.thumbnail.isNullOrBlank()) artist else artistPortraits[artist.id] ?: run {
                    portraits.acquire()
                    try {
                        YouTube.artist(artist.id).getOrNull()?.artist
                            ?.also { if (artistPortraits.size > 128) artistPortraits.clear(); artistPortraits[artist.id] = it }
                    } finally { portraits.release() }
                }
            } }.awaitAll().filterNotNull() }.orEmpty()
            val albums = detail?.albums.orEmpty().ifEmpty { tracks.mapNotNull { song -> song.album?.let { album ->
                AlbumItem(album.id, "", title = album.name, artists = song.artists, thumbnail = song.thumbnail)
            } }.distinctBy { it.id } }.take(12)
            val artistAlbums = artists.associate { artist -> artist.id to albums.filter { album ->
                album.artists.orEmpty().any { it.id == artist.id }
            } }.filterValues { it.isNotEmpty() }
            if (artists.isNotEmpty()) shelves += OnlineShelf(if (spanish) "Artistas de esta mezcla" else "Artists in this mix", artists,
                if (spanish) "Porque escuchaste ${source.title}" else "Because you listened to ${source.title}",
                layout = OnlineShelfLayout.ARTISTS, artistAlbums = artistAlbums)
            if (albums.isNotEmpty()) shelves += OnlineShelf(if (spanish) "Álbumes para descubrir" else "Albums to explore", albums)
        }
        if (recent.isNotEmpty()) shelves += OnlineShelf(if (spanish) "Historial" else "History", recent,
            layout = OnlineShelfLayout.TRACKS)
        shelves += page?.sections.orEmpty().map { it.asOnlineShelf() }
        charts.await()?.sections.orEmpty().take(3).forEach { shelves += OnlineShelf(it.title, it.items) }
        releases.await()?.takeIf { it.isNotEmpty() }?.let {
            shelves += OnlineShelf(if (spanish) "Novedades" else "New releases", it)
        }
        val unique = shelves.filter { it.items.isNotEmpty() }.distinctBy { it.title to it.items.first().id }
        currentCoroutineContext().ensureActive()
        if (unique.isEmpty() && result.isFailure) result.getOrThrow()
        catalog(OnlinePage("Home", artistCollections(unique), page?.continuation))
        }
    }
    suspend fun discovery(): OnlinePage = withContext(Dispatchers.IO) {
        ready()
        supervisorScope {
            val releases = async { withTimeoutOrNull(8_000) { YouTube.explore().getOrNull()?.newReleaseAlbums } }
            val charts = async { withTimeoutOrNull(8_000) { YouTube.getChartsPage().getOrNull() } }
            val shelves = charts.await()?.sections.orEmpty().take(5).map { OnlineShelf(it.title, it.items.take(20)) }.toMutableList()
            releases.await()?.takeIf { it.isNotEmpty() }?.let { shelves.add(0, OnlineShelf(
                if(Locale.getDefault().language == "es") "Nuevos lanzamientos" else "New releases", it.take(20))) }
            if(shelves.none { it.items.isNotEmpty() }) {
                val fallback = YouTube.home().getOrThrow()
                shelves += fallback.sections.take(5).map { OnlineShelf(it.title, it.items.take(20), it.label) }
            }
            catalog(OnlinePage("Search", shelves))
        }
    }
    suspend fun roulette(seed: Song?, visible: List<SongItem>): List<Song> = withContext(Dispatchers.IO) {
        ready()
        supervisorScope {
            val saved = if(_account.value.connected) knownSongs.values.filter { it.id in _likedIds.value }
                else _recentSongs.value + guestFavorites()
            val currentSeed = seed?.takeIf { it.contentUri.startsWith("akira-youtube:") }?.let {
                knownSongs[it.id.removePrefix("youtube:")] ?: SongItem(it.id.removePrefix("youtube:"), it.title,
                    listOf(Artist(it.artist, null)), duration = it.durationSeconds, thumbnail = it.albumArtUri.orEmpty())
            }
            val seeds = (listOfNotNull(currentSeed) + (saved + visible).distinctBy { it.id }.shuffled())
                .distinctBy { it.id }.take(2)
            val radios = seeds.map { song -> async {
                withTimeoutOrNull(7_000) { YouTube.next(WatchEndpoint(videoId = song.id, playlistId = "RDAMVM${song.id}")).getOrNull()?.items }.orEmpty()
            } }
            val home = async { withTimeoutOrNull(7_000) { YouTube.home().getOrNull() } }
            val charts = async { withTimeoutOrNull(7_000) { YouTube.getChartsPage().getOrNull() } }
            val collections = async {
                val items = home.await()?.sections.orEmpty().flatMap { it.items } + charts.await()?.sections.orEmpty().flatMap { it.items }
                items.filter { it is PlaylistItem || it is AlbumItem }.distinctBy { it.id }.shuffled().take(2).map { item -> async {
                    withTimeoutOrNull(6_000) {
                        when(item) {
                            is PlaylistItem -> YouTube.playlist(item.id).getOrNull()?.songs
                            is AlbumItem -> YouTube.album(item.browseId).getOrNull()?.songs
                            else -> emptyList()
                        }
                    }.orEmpty()
                } }.awaitAll().flatten()
            }
            val albums = async {
                val releases = withTimeoutOrNull(6_000) { YouTube.explore().getOrNull()?.newReleaseAlbums }.orEmpty()
                releases.shuffled().take(2).map { album -> async {
                    withTimeoutOrNull(6_000) { YouTube.album(album.browseId).getOrNull()?.songs }.orEmpty()
                } }.awaitAll().flatten()
            }
            val recommendations = home.await()?.sections.orEmpty().flatMap { it.items }.filterIsInstance<SongItem>()
            val discoveries = charts.await()?.sections.orEmpty().flatMap { it.items }.filterIsInstance<SongItem>() + albums.await()
            val similar = radios.awaitAll().flatten() + recommendations + collections.await()
            currentCoroutineContext().ensureActive()
            if(discoveries.isEmpty() && similar.isEmpty()) throw IOException("Recommendation sources unavailable")
            val familiar = (saved + visible).distinctBy { it.id }
            val recent = synchronized(rouletteWinners) { rouletteWinners.toSet() }
            val pool = discoveryPool(discoveries, similar, familiar, recent, seed?.id?.removePrefix("youtube:"))
                .filter { it.id.matches(Regex("[A-Za-z0-9_-]{11}")) }
            if(pool.size < 2) throw IOException("Not enough recommendations available")
            pool.forEach { knownSongs[it.id] = it }
            pool.map { it.asOnlineSong() }
        }
    }
    suspend fun search(query: String, kind: String = "all"): OnlinePage = withContext(Dispatchers.IO) {
        ready()
        val filter = when (kind) {
            "songs" -> YouTube.SearchFilter.FILTER_SONG
            "albums" -> YouTube.SearchFilter.FILTER_ALBUM
            "artists" -> YouTube.SearchFilter.FILTER_ARTIST
            "playlists" -> YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST
            else -> null
        }
        if (filter == null) {
            val page = YouTube.searchSummary(query).getOrThrow()
            catalog(OnlinePage(query, page.summaries.map { OnlineShelf(it.title, it.items) }))
        } else {
            val result = YouTube.search(query, filter).getOrThrow()
            catalog(OnlinePage(query, listOf(OnlineShelf(query, result.items)), result.continuation))
        }
    }
    suspend fun moreSearch(token: String): OnlinePage = withContext(Dispatchers.IO) {
        ready()
        val result = YouTube.searchContinuation(token).getOrThrow()
        catalog(OnlinePage("", listOf(OnlineShelf("", result.items)), result.continuation))
    }
    suspend fun library(kind: String = "playlists"): OnlinePage = withContext(Dispatchers.IO) {
        initialized.await()
        check(_enabled.value) { "Modo online desactivado" }
        val spanish = Locale.getDefault().language == "es"
        if (!_account.value.connected) return@withContext catalog(OnlinePage("Library", listOf(
            OnlineShelf(if(kind == "recent") { if(spanish) "Escuchadas recientemente" else "Recently played" }
                else { if(spanish) "Tus Me gusta" else "Liked songs" }, if(kind == "recent") _recentSongs.value else guestFavorites()))))
        ready()
        if (kind == "songs") {
            val likes = YouTube.playlist("LM").getOrThrow()
            return@withContext catalog(OnlinePage("Library", listOf(OnlineShelf(
                if (spanish) "Tus Me gusta" else "Liked songs", likes.songs)), likes.songsContinuation))
        }
        val endpoint = when (kind) {
            "albums" -> "FEmusic_liked_albums"
            "artists" -> "FEmusic_library_corpus_track_artists"
            else -> "FEmusic_liked_playlists"
        }
        val result = YouTube.library(endpoint).onFailure {
            if (com.akira.app.BuildConfig.DEBUG) android.util.Log.d("AkiraOnlineLibrary", "kind=$kind error=${it.javaClass.simpleName}")
        }.getOrThrow()
        val likes = PlaylistItem("LM", if (spanish) "Tus Me gusta" else "Liked songs", null, null, null, null, null, null)
        val title = when (kind) {
            "albums" -> if (spanish) "Álbumes guardados" else "Saved albums"
            "artists" -> if (spanish) "Tus artistas" else "Your artists"
            else -> if (spanish) "Tus playlists" else "Your playlists"
        }
        val items = if (kind == "playlists") listOf(likes) + result.items.filterNot { it.id == "LM" } else result.items
        catalog(OnlinePage("Library", listOf(OnlineShelf(title, items)), result.continuation))
    }
    suspend fun moreLibrary(token: String): OnlinePage = withContext(Dispatchers.IO) {
        ready()
        YouTube.libraryContinuation(token).getOrThrow().let {
            OnlinePage("Library", listOf(OnlineShelf("Playlists", it.items)), it.continuation)
        }
    }
    suspend fun artistRadio(artist: ArtistItem, seed: Song?): List<Song> = withContext(Dispatchers.IO) {
        ready()
        val endpoint = artist.radioEndpoint ?: seed?.id?.removePrefix("youtube:")?.let { WatchEndpoint(videoId = it, playlistId = "RDAMVM$it") }
            ?: throw IOException("No artist radio endpoint")
        val songs = YouTube.next(endpoint).getOrThrow().items.distinctBy { it.id }
        songs.forEach { knownSongs[it.id] = it }
        songs.map { it.asOnlineSong() }
    }
    suspend fun open(item: YTItem): OnlinePage = withContext(Dispatchers.IO) {
        ready()
        catalog(when (item) {
            is AlbumItem -> YouTube.album(item.browseId).getOrThrow().let {
                OnlinePage(it.album.title, listOf(OnlineShelf(it.album.title, it.songs)) +
                    listOf(OnlineShelf(if (Locale.getDefault().language == "es") "Otras versiones" else "Other versions", it.otherVersions),
                        OnlineShelf(if (Locale.getDefault().language == "es") "También te puede gustar" else "You may also like", it.releasesForYou)).filter { shelf -> shelf.items.isNotEmpty() },
                    collection = OnlineCollection(it.album.copy(thumbnail = it.album.thumbnail.ifBlank { item.thumbnail }), it.description, it.album.year?.toString(), it.album.artists.orEmpty()))
            }
            is ArtistItem -> YouTube.artist(item.id).getOrThrow().let {
                OnlinePage(it.artist.title, it.sections.map { section -> OnlineShelf(section.title, section.items) },
                    collection = OnlineCollection(it.artist.copy(thumbnail = it.artist.thumbnail?.takeIf(String::isNotBlank) ?: item.thumbnail), it.description, it.monthlyListenerCount ?: it.subscriberCountText))
            }
            is PlaylistItem -> YouTube.playlist(item.id).getOrThrow().let {
                OnlinePage(it.playlist.title, listOf(OnlineShelf(it.playlist.title, it.songs)) + listOfNotNull(it.related?.takeIf { related -> related.isNotEmpty() }
                    ?.let { related -> OnlineShelf(if (Locale.getDefault().language == "es") "Recomendadas" else "Related playlists", related) }),
                    it.songsContinuation, OnlineCollection(it.playlist.copy(thumbnail = it.playlist.thumbnail?.takeIf(String::isNotBlank) ?: item.thumbnail), creators = listOfNotNull(it.playlist.author)))
            }
            else -> OnlinePage(item.title, listOf(OnlineShelf(item.title, listOf(item))))
        })
    }
    suspend fun morePlaylist(token: String): OnlinePage = withContext(Dispatchers.IO) {
        ready()
        YouTube.playlistContinuation(token).getOrThrow().let {
            catalog(OnlinePage("", listOf(OnlineShelf("", it.songs)), it.continuation))
        }
    }
    /** Called only from a Media3 loading thread. Persist virtual URIs, not expiring URLs. */
    suspend fun resolve(videoId: String, refresh: Boolean = false): OnlineStream = withContext(Dispatchers.IO) {
        ready()
        lock.withLock {
            ready()
            val now = android.os.SystemClock.elapsedRealtime()
            if (!refresh) streams[videoId]?.takeIf { it.first > now }?.let { return@withLock it.second }
            streams.remove(videoId)
            val clients = listOf(YouTubeClient.VISIONOS, YouTubeClient.WEB_REMIX, YouTubeClient.IOS, YouTubeClient.ANDROID_VR_1_65_10)
            var denied = false
            for (client in clients) {
                ensureActive()
                ready()
                val timestamp = if (client.useSignatureTimestamp) NewPipeExtractor.getSignatureTimestamp(videoId).getOrNull() else null
                val response = YouTube.player(videoId, client = client, signatureTimestamp = timestamp).getOrNull() ?: continue
                if (response.playabilityStatus.status != "OK") { denied = true; continue }
                val formats = response.streamingData?.adaptiveFormats.orEmpty()
                    .filter { it.mimeType.startsWith("audio/") }.sortedByDescending { it.bitrate }
                val headers = mapOf("User-Agent" to client.userAgent, "Accept" to "*/*",
                    "Origin" to if (client == YouTubeClient.WEB_REMIX) "https://music.youtube.com" else "https://www.youtube.com",
                    "Referer" to if (client == YouTubeClient.WEB_REMIX) "https://music.youtube.com/" else "https://www.youtube.com/")
                for (format in formats.take(2)) {
                    val url = NewPipeExtractor.getStreamUrl(format, videoId) ?: format.url ?: continue
                    val stream = OnlineStream(url, headers)
                    if (!usable(stream, format.contentLength)) continue
                    ready()
                    streams[videoId] = now + 4 * 60_000L to stream
                    return@withLock stream
                }
            }
            // Same fallback extractor used by Page; no access-restricted sources are retried here.
            if (!denied) {
                val audioItags = setOf(140, 141, 139, 249, 250, 251, 171, 172)
                val url = NewPipeExtractor.newPipePlayer(videoId).lastOrNull { it.first in audioItags }?.second
                val stream = url?.let { OnlineStream(it, mapOf("User-Agent" to YouTubeClient.USER_AGENT_WEB)) }
                if (stream != null && usable(stream, null)) {
                    ready()
                    streams[videoId] = now + 4 * 60_000L to stream
                    return@withLock stream
                }
            }
            throw IOException("YouTube Music no entregó una fuente reproducible para esta canción")
        }
    }
    /** Check signed URL and a distant byte, avoiding sources that stop after their first chunk. */
    private fun usable(stream: OnlineStream, length: Long?): Boolean = runCatching {
        val uri = Uri.parse(stream.url)
        if (uri.scheme != "https" || !uri.host.orEmpty().endsWith(".googlevideo.com")) return@runCatching false
        fun range(byte: Long): Boolean {
            val request = okhttp3.Request.Builder().url(stream.url)
            stream.headers.forEach { (name, value) -> request.header(name, value) }
            request.header("Range", "bytes=$byte-$byte")
            return streamClient.newCall(request.build()).execute().use { response ->
                response.code == 206 && response.header("Content-Range").orEmpty().startsWith("bytes $byte-$byte/") &&
                    (response.body?.byteStream()?.read() ?: -1) >= 0
            }
        }
        range(0) && (length == null || length <= 1 || range(minOf(length - 1, 1_048_576)))
    }.getOrDefault(false)
    companion object {
        @Volatile private var instance: OnlineRepository? = null
        fun get(context: Context): OnlineRepository = instance ?: synchronized(this) {
            instance ?: OnlineRepository(context.applicationContext).also { instance = it }
        }
    }
}

fun SongItem.asOnlineSong() = Song(
    id = "youtube:$id", title = title, artist = artists.joinToString(", ") { it.name },
    album = album?.name.orEmpty(), durationMs = (duration ?: 0) * 1000L,
    contentUri = "akira-youtube://$id", albumArtUri = thumbnail, format = "YouTube Music"
)

fun YTItem.onlineSubtitle(): String = when (this) {
    is SongItem -> artists.joinToString(", ") { it.name }
    is AlbumItem -> artists.orEmpty().joinToString(", ") { it.name }
    is PlaylistItem -> listOfNotNull(author?.name, songCountText).joinToString(" · ")
    is ArtistItem -> ""
}

internal fun cleanOnlineTitle(title: String): String = title.replace(
    Regex("(?i)\\s*[\\[(](?:official(?: music| vertical)? (?:video|audio|visualizer)|lyric(?:s)?(?: video)?)[\\])]"), "").trim()
private fun onlineIdentity(text: String): String = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
