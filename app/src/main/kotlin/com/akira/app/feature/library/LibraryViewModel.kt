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

/** Qué propiedad de la canción define el orden. */
enum class SortField { TITLE, ARTIST, ALBUM, DATE_ADDED, DURATION }

/**
 * Orden de la lista. Cada valor es (campo, dirección):
 *  - TITLE / ARTIST / ALBUM: ASC = A→Z, DESC = Z→A.
 *  - DATE_ADDED: ASC = las más antiguas primero, DESC = las más recientes primero
 *    (fecha en la que el archivo llegó al dispositivo, MediaStore.DATE_ADDED).
 *  - DURATION: ASC = más cortas primero, DESC = más largas primero.
 */
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

        /** Dirección con la que arranca cada criterio al elegirlo por primera vez. */
        fun defaultFor(field: SortField): SortOrder = when (field) {
            SortField.DATE_ADDED -> DATE_ADDED_DESC // lo último que llegó, arriba
            else -> of(field, true)
        }
    }
}

data class LibraryUiState(
    val songs: List<Song> = emptyList(),
    val initialized: Boolean = false,
    val loadFailed: Boolean = false
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val scanner = MediaStoreScanner(application)
    private val favoritesDao = AppDatabase.getInstance(application).favoritesDao()
    private val cache = LibraryCache(application)
    private val prefsRepo = UserPreferencesRepository(application)

    val currentSortOrder = MutableStateFlow(SortOrder.TITLE_ASC)
    val searchQuery = MutableStateFlow("")
    val currentCategory = MutableStateFlow("Tracks")

    // Emite desde un MutableStateFlow propio: primero la caché (instantánea, para
    // que la biblioteca NUNCA aparezca vacía al abrir), después el escaneo real,
    // y luego sólo cuando el ContentObserver detecte cambios en el almacenamiento.
    private val scannedSongs = MutableStateFlow<List<Song>?>(null)
    private val scanFailed = MutableStateFlow(false)

    init {
        // Restaura el orden y la pestaña inicial guardados.
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

    // Filtros de biblioteca definidos en Ajustes / onboarding. Antes se guardaban
    // pero nadie los leía, así que "Duración mínima" no tenía ningún efecto.
    private val libraryFilter = prefsRepo.userPreferences
        .map { Triple(it.minDurationSeconds, it.excludedFolderPaths, it.whitelistedSongIds) }
        .distinctUntilChanged()

    private val librarySnapshot = combine(scannedSongs, favoritesFlow, libraryFilter, scanFailed) { raw, favorites, filter, failed ->
        if (raw == null) LibraryUiState(loadFailed = failed) else {
            val favIds = favorites.map { it.songId }.toSet()
            LibraryUiState(raw.filter { com.akira.app.core.data.passesLibraryFilters(it, filter.first, filter.second, filter.third) }
                .map { it.copy(isFavorite = it.id in favIds) }, initialized = true)
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

/**
 * Ordena respetando el idioma del dispositivo (acentos y mayúsculas no alteran el
 * orden alfabético: "Álbum" va junto a "Album") e ignorando símbolos iniciales
 * ("$uelta" se ordena como "uelta"). Cada criterio desempata con campos
 * secundarios para que el resultado sea estable y predecible.
 */
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
        // DATE_ADDED con respaldo en DATE_MODIFIED. En este dispositivo 10 de
        // 1074 filas venían con date_added = NULL en MediaStore (el escáner lo
        // deja en 0), y con el criterio puro esas 10 se agrupaban al principio
        // como si fueran las más antiguas. dateModifiedSec sí está siempre.
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
