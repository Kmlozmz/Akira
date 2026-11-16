package com.akira.app.feature.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.akira.app.core.data.MediaStoreScanner
import com.akira.app.core.data.library.LibraryCache
import com.akira.app.core.data.passesLibraryFilters
import com.akira.app.core.datastore.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

internal class LibrarySetupViewModel(application: Application) : AndroidViewModel(application) {
    private val scanner = MediaStoreScanner(application)
    private val prefs = UserPreferencesRepository(application)
    private val rawSongs=kotlinx.coroutines.flow.MutableStateFlow<List<com.akira.app.core.model.Song>>(emptyList())
    val allScannedSongs=rawSongs.asStateFlow()
    val writes = SetupPreferenceWrites(viewModelScope)
    private val output = kotlinx.coroutines.flow.MutableStateFlow<List<com.akira.app.core.model.Song>>(emptyList())
    val completedSongs = output.asStateFlow()
    val visibleSongs = combine(rawSongs, prefs.userPreferences) { songs, filters ->
        songs.filter { passesLibraryFilters(it, filters.minDurationSeconds, filters.excludedFolderPaths, filters.whitelistedSongIds) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private var hasScanned=false
    init {viewModelScope.launch {
        val cached=LibraryCache(application).read()
        if(!hasScanned)rawSongs.value=cached
    }}
    val controller = SetupController(viewModelScope, scan = { progress ->
        val songs = scanner.scanSongsInFolder("all", onProgress = { count, title ->
            withContext(Dispatchers.Main.immediate) { progress(count, title) }
        }, throwOnFailure = true)
        currentCoroutineContext().ensureActive()
        hasScanned=true
        rawSongs.value=songs
        LibraryCache(application).write(songs)
        val filters = prefs.userPreferences.first()
        withContext(Dispatchers.Default) {
            songs.filter { passesLibraryFilters(it, filters.minDurationSeconds, filters.excludedFolderPaths, filters.whitelistedSongIds) }
        }
    }, complete = { result ->
        if (!hasSetupPermissions(application)) throw SecurityException("Setup permissions unavailable")
        check(writes.pending.value == 0 && writes.failures.value.isEmpty()) { "Preferences are not saved" }
        val filters = prefs.userPreferences.first()
        output.value = if (result.librarySkipped) emptyList() else withContext(Dispatchers.Default) {
            rawSongs.value.filter { passesLibraryFilters(it, filters.minDurationSeconds, filters.excludedFolderPaths, filters.whitelistedSongIds) }
        }
        // Completing setup never rewrites a user's sound, appearance or playback settings.
        prefs.setOnboardingCompleted(true)
    })
}
