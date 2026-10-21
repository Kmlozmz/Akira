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

internal class LibrarySetupViewModel(application: Application) : AndroidViewModel(application) {
    private val scanner = MediaStoreScanner(application)
    private val prefs = UserPreferencesRepository(application)
    private val rawSongs=kotlinx.coroutines.flow.MutableStateFlow<List<com.akira.app.core.model.Song>>(emptyList())
    val allScannedSongs=rawSongs.asStateFlow()
    private var hasScanned=false
    init {viewModelScope.launch {
        val cached=LibraryCache(application).read()
        if(!hasScanned)rawSongs.value=cached
    }}
    val controller = SetupController(viewModelScope, scan = { progress ->
        val songs = scanner.scanSongsInFolder("all", onProgress = { count, title ->
            withContext(Dispatchers.Main.immediate) { progress(count, title) }
        }, throwOnFailure = true)
        hasScanned=true
        rawSongs.value=songs
        LibraryCache(application).write(songs)
        val filters = prefs.userPreferences.first()
        withContext(Dispatchers.Default) {
            songs.filter { passesLibraryFilters(it, filters.minDurationSeconds, filters.excludedFolderPaths, filters.whitelistedSongIds) }
        }
    }, complete = {
        if (!hasSetupPermissions(application)) throw SecurityException("Setup permissions unavailable")
        // Completing setup never rewrites a user's sound, appearance or playback settings.
        prefs.setOnboardingCompleted(true)
    })
}
