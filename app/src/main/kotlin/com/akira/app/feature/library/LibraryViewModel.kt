package com.akira.app.feature.library

import com.akira.app.core.artwork.*
import com.akira.app.feature.lyrics.*
import com.akira.app.feature.player.*
import com.akira.app.feature.queue.*

import com.akira.app.core.data.library.LibraryCache

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akira.app.core.data.MediaStoreScanner
import com.akira.app.core.database.AppDatabase
import com.akira.app.core.database.model.FavoriteSongEntity
import com.akira.app.core.datastore.UserPreferencesRepository
import com.akira.app.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.CollationKey
import java.text.Collator

/** Song property used for sorting. */
enum class SortField { TITLE, ARTIST, ALBUM, DATE_ADDED, DURATION }

/** Sort by field and direction: text fields use A-Z or Z-A; DATE_ADDED uses MediaStore arrival time; DURATION uses shortest or longest first. */
enum class SortOrder(val field: SortField, val ascending: Boolean) {
    TITLE_ASC(SortField.TITLE, true),
    TITLE_DESC(SortField.TITLE, false),
    ARTIST_ASC(SortField.ARTIST, true),
    ARTIST_DESC(SortField.ARTIST, false),
    ALBUM_ASC(SortField.ALBUM, true),
    ALBUM_DESC(SortField.ALBUM, false),
    DATE_ADDED_DESC(SortField.DATE_ADDED, false),
    DATE_ADDED_ASC(SortField.DATE_ADDED, true),
    DURATION_DESC(SortField.DURATION, false),
    DURATION_ASC(SortField.DURATION, true);

    companion object {
        fun of(field: SortField, ascending: Boolean): SortOrder =
            entries.first { it.field == field && it.ascending == ascending }

        fun fromName(name: String?): SortOrder =
            entries.firstOrNull { it.name == name } ?: TITLE_ASC

        /** Initial direction for a newly selected sort criterion. */
        fun defaultFor(field: SortField): SortOrder = when (field) {
            SortField.DATE_ADDED -> DATE_ADDED_DESC // Newest additions first.
            else -> of(field, true)
        }
    }
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val scanner = MediaStoreScanner(application)
    private val favoritesDao = AppDatabase.getInstance(application).favoritesDao()
    private val cache = LibraryCache(application)
    private val prefsRepo = UserPreferencesRepository(application)

    val currentSortOrder = MutableStateFlow(SortOrder.TITLE_ASC)
    val searchQuery = MutableStateFlow("")
    val currentCategory = MutableStateFlow("Tracks")

    // Cached songs remain visible until MediaStore supplies a successful scan.
    private val scannedSongs = MutableStateFlow<List<Song>?>(null)
    private val scanFailed = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val prefs = prefsRepo.userPreferences.first()
            currentSortOrder.value = SortOrder.fromName(prefs.sortOrder)
            currentCategory.value = when (prefs.defaultTab) {
                "ALBUMS" -> "Albums"
                "ARTISTS" -> "Artists"
                else -> "Tracks"
            }
        }
        viewModelScope.launch {
            val cached = cache.read()
            if (cached.isNotEmpty()) scannedSongs.value = cached
            scanner.observeMediaStoreChanges().collect { refreshFromMediaStore() }
        }
    }

    private suspend fun refreshFromMediaStore() {
        try {
            val scanned = scanner.scanSongsInFolder("all", throwOnFailure = true)
            // A successful empty scan means the files were removed. Only a failed
            // scan retains the previous snapshot.
            scanFailed.value = false
            scannedSongs.value = scanned
            cache.write(scanned)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            scanFailed.value = true
            com.akira.app.core.logging.AkiraLog.w("Library", "Keeping previous library after failed scan", error)
        }
    }

    private val favoritesFlow = favoritesDao.observeFavorites()

    private val libraryFilter = prefsRepo.userPreferences
        .map { Triple(it.minDurationSeconds, it.excludedFolderPaths, it.whitelistedSongIds) }
        .distinctUntilChanged()

    private val librarySnapshot = combine(scannedSongs, favoritesFlow, libraryFilter, scanFailed) { raw, favorites, filter, failed ->
        librarySnapshot(raw, favorites.map { it.songId }.toSet(), failed) {
            com.akira.app.core.data.passesLibraryFilters(it, filter.first, filter.second, filter.third)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, LibraryUiState())

    val allSongs: StateFlow<List<Song>> = librarySnapshot.map { it.songs }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Loading and tracks travel together, so a delayed filter/sort cannot expose a false empty state.
    val uiState: StateFlow<LibraryUiState> = combine(librarySnapshot, currentSortOrder, searchQuery) { snapshot, order, query ->
        val filtered = if (query.isBlank()) snapshot.songs else snapshot.songs.filter {
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
        }
        snapshot.copy(songs = sortSongs(filtered, order))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, LibraryUiState())

    val songs: StateFlow<List<Song>> = uiState.map { it.songs }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun retryLoad() {
        viewModelScope.launch { scanFailed.value = false; refreshFromMediaStore() }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
                favoritesDao.toggleFavorite(
                    FavoriteSongEntity(
                        songId = song.id,
                        title = song.title,
                        artist = song.artist,
                        album = song.album,
                        durationSeconds = song.durationSeconds,
                        albumId = song.albumId,
                        addedAtMillis = System.currentTimeMillis()
                    )
                )
        }
    }

    fun setSortOrder(order: SortOrder) {
        currentSortOrder.value = order
        viewModelScope.launch { prefsRepo.setSortOrder(order.name) }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setCategory(category: String) {
        currentCategory.value = category
    }
}

/** Use device-locale collation to ignore case and accents, skip leading symbols, and use secondary fields to resolve ties consistently. */
internal fun sortSongs(songs: List<Song>, order: SortOrder): List<Song> {
    if (songs.size < 2) return songs

    val collator = Collator.getInstance().apply { strength = Collator.SECONDARY }
    fun key(text: String): CollationKey =
        collator.getCollationKey(text.trimStart { !it.isLetterOrDigit() }.ifEmpty { text })

    class Keyed(val song: Song, val title: CollationKey, val artist: CollationKey, val album: CollationKey)

    val keyed = songs.map { Keyed(it, key(it.title), key(it.artist), key(it.album)) }

    val comparator: Comparator<Keyed> = when (order.field) {
        SortField.TITLE -> compareBy<Keyed> { it.title }.thenBy { it.artist }
        SortField.ARTIST -> compareBy<Keyed> { it.artist }
            .thenBy { it.album }
            .thenBy { it.song.trackNumber }
            .thenBy { it.title }
        SortField.ALBUM -> compareBy<Keyed> { it.album }
            .thenBy { it.artist }
            .thenBy { it.song.trackNumber }
            .thenBy { it.title }
        // MediaStore may omit DATE_ADDED; use DATE_MODIFIED as the fallback.
        SortField.DATE_ADDED -> compareBy<Keyed> {
            if (it.song.dateAddedSec != 0L) it.song.dateAddedSec else it.song.dateModifiedSec
        }
            .thenBy { it.song.dateModifiedSec }
            .thenBy { it.title }
        SortField.DURATION -> compareBy<Keyed> { it.song.durationMs }.thenBy { it.title }
    }

    return keyed
        .sortedWith(if (order.ascending) comparator else comparator.reversed())
        .map { it.song }
}
