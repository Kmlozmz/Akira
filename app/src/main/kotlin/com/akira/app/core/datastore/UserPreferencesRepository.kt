package com.akira.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.akira.app.core.media.CrossfadeCurve
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_music_preferences")

data class UserPreferences(
    val shuffle: Boolean = false,
    val repeatMode: Int = 0, // 0 = OFF, 1 = ALL, 2 = ONE
    val lastPlayedSongId: String? = null,
    val lastPositionMs: Long = 0L,
    val equalizerPreset: String = "BALANCED",
    val bassBoost: Float = 0.5f,
    val virtualizer: Float = 0.5f,
    // Band gains use dB (-12..+12); preamp is a linear multiplier.
    val eqEnabled: Boolean = false,
    val eqBandGains: List<Float> = List(5) { 0f },
    val eqPreamp: Float = 1.0f,
    val dynamicColor: Boolean = true,
    val isOnboardingCompleted: Boolean = false,
    val minDurationSeconds: Int = 30,
    val cornerRadiusDp: Int = 22,
    val navBarStyle: String = "FLOATING_PILL", // "FLOATING_PILL" o "DOCKED_BAR"
    val libraryHeaderStyle: String = "COMPACT_PILL", // "COMPACT_PILL" o "TAB_ROW"
    val liquidGlassEnabled: Boolean = false,
    val liquidGlassOpacity: Float = .25f,
    val liquidGlassDiffusion: Float = 8f, // Background scattering, dp; independent of tint.
    val dockBackgroundDiffusion: Boolean = false,
    val fullscreenPlayerArtwork: Boolean = false,
    val animatedPlayerArtwork: Boolean = true,
    val favoriteArtists: Set<String> = emptySet(),
    val themeMode: String = "SYSTEM", // "SYSTEM", "DARK", "AMOLED"
    val appearancePreset: String = "MIDNIGHT",
    val accentColor: String = "PRESET",
    val spaceMode: Boolean = false,
    val spaceStyle: String = "SPACE",
    val spaceEnabledThemes: Set<String> = emptySet(),
    val backgroundMotion: Boolean = true,
    val backgroundIntensity: Float = .7f,
    val excludedFolderPaths: Set<String> = emptySet(),
    val whitelistedSongIds: Set<String> = emptySet(),
    // Retained for stored preferences; crossfadeSeconds determines the transition mode.
    val gaplessPlayback: Boolean = true,
    val autoResume: Boolean = true,
    val skipSilence: Boolean = false,
    val lastCrossfadeSeconds: Int = 8,
    val crossfadeSeconds: Int = 0,
    val hiResAudio: Boolean = true,
    val defaultTab: String = "TRACKS",
    val sortOrder: String = "TITLE_ASC",
    val language: String = "SYSTEM",
    val autoplay: Boolean = false,
    val normalizationMode: String = "OFF",
    val eqProfilesJson: String = "[]",
    val eqAssignmentsJson: String = "{}",
    val bluetoothResume: Boolean = false,
    val bluetoothWindowMinutes: Int = 10,
    val onlineLyrics: Boolean = true,
    val lyricsProvider: String = "AUTO",
    val recentAutoplayIds: List<String> = emptyList(),
    val crossfadePreset: String = "ACTUAL",
    val crossfadeOutShape: String = "CURRENT",
    val crossfadeOutCurvature: Float = 1.0f,
    val crossfadeOutStart: Float = 0.0f,
    val crossfadeOutEnd: Float = 1.0f,
    val crossfadeInShape: String = "CURRENT",
    val crossfadeInCurvature: Float = 1.0f,
    val crossfadeInStart: Float = 0.0f,
    val crossfadeInEnd: Float = 1.0f
)

class UserPreferencesRepository(private val context: Context) {

    companion object {
        const val EQ_BAND_COUNT = 5
        val EQ_BAND_FREQUENCIES = intArrayOf(60, 250, 1000, 4000, 12000)
        const val EQ_MIN_DB = -12f
        const val EQ_MAX_DB = 12f
    }

    private object Keys {
        val SHUFFLE = booleanPreferencesKey("pref_shuffle")
        val REPEAT_MODE = intPreferencesKey("pref_repeat_mode")
        val LAST_SONG_ID = stringPreferencesKey("pref_last_song_id")
        val LAST_POSITION_MS = longPreferencesKey("pref_last_position_ms")
        val EQUALIZER_PRESET = stringPreferencesKey("pref_eq_preset")
        val BASS_BOOST = floatPreferencesKey("pref_bass_boost")
        val VIRTUALIZER = floatPreferencesKey("pref_virtualizer")
        val EQ_ENABLED = booleanPreferencesKey("pref_eq_enabled")
        // Se guardan en 5 claves separadas en vez de un string serializado:
        // DataStore Preferences no soporta listas de forma nativa y meterlas
        // en un Set obligaría a codificarlas como texto.
        fun eqBand(index: Int) = floatPreferencesKey("pref_eq_band_$index")
        val EQ_PREAMP = floatPreferencesKey("pref_eq_preamp")
        val DYNAMIC_COLOR = booleanPreferencesKey("pref_dynamic_color")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("pref_onboarding_completed")
        val MIN_DURATION_SECONDS = intPreferencesKey("pref_min_duration_seconds")
        val CORNER_RADIUS_DP = intPreferencesKey("pref_corner_radius_dp")
        val NAV_BAR_STYLE = stringPreferencesKey("pref_nav_bar_style")
        val LIBRARY_HEADER_STYLE = stringPreferencesKey("pref_library_header_style")
        val LIQUID_GLASS = booleanPreferencesKey("pref_liquid_glass_enabled")
        val LIQUID_GLASS_OPACITY = floatPreferencesKey("pref_liquid_glass_opacity")
        val LIQUID_GLASS_DIFFUSION = floatPreferencesKey("pref_liquid_glass_diffusion")
        val DOCK_BACKGROUND_DIFFUSION = booleanPreferencesKey("pref_dock_background_diffusion")
        val FULLSCREEN_PLAYER_ARTWORK = booleanPreferencesKey("pref_fullscreen_player_artwork")
        val ANIMATED_PLAYER_ARTWORK = booleanPreferencesKey("pref_animated_player_artwork")
        val FAVORITE_ARTISTS = stringSetPreferencesKey("pref_favorite_artists")
        val THEME_MODE = stringPreferencesKey("pref_theme_mode")
        val APPEARANCE_PRESET = stringPreferencesKey("pref_appearance_preset")
        val ACCENT_COLOR = stringPreferencesKey("pref_accent_color")
        val SPACE_THEMES = stringSetPreferencesKey("pref_space_themes")
        val SPACE_STYLE = stringPreferencesKey("pref_space_style")
        val BACKGROUND_MOTION = booleanPreferencesKey("pref_background_motion")
        val BACKGROUND_INTENSITY = floatPreferencesKey("pref_background_intensity")
        val EXCLUDED_FOLDER_PATHS = stringSetPreferencesKey("pref_excluded_folder_paths")
        val WHITELISTED_SONG_IDS = stringSetPreferencesKey("pref_whitelisted_song_ids")
        val GAPLESS_PLAYBACK = booleanPreferencesKey("pref_gapless_playback")
        val AUTO_RESUME = booleanPreferencesKey("pref_auto_resume")
        val SKIP_SILENCE = booleanPreferencesKey("pref_skip_silence")
        val LAST_CROSSFADE_SECONDS = intPreferencesKey("pref_last_crossfade_seconds")
        val CROSSFADE_SECONDS = intPreferencesKey("pref_crossfade_seconds")
        val HI_RES_AUDIO = booleanPreferencesKey("pref_hi_res_audio")
        val DEFAULT_TAB = stringPreferencesKey("pref_default_tab")
        val SORT_ORDER = stringPreferencesKey("pref_sort_order")
        val LANGUAGE = stringPreferencesKey("pref_language")
        val AUTOPLAY = booleanPreferencesKey("pref_autoplay")
        val NORMALIZATION_MODE = stringPreferencesKey("pref_normalization_mode")
        val EQ_PROFILES = stringPreferencesKey("pref_eq_profiles")
        val EQ_ASSIGNMENTS = stringPreferencesKey("pref_eq_assignments")
        val BT_RESUME = booleanPreferencesKey("pref_bt_resume")
        val BT_WINDOW = intPreferencesKey("pref_bt_window")
        val ONLINE_LYRICS = booleanPreferencesKey("pref_online_lyrics")
        val LYRICS_PROVIDER = stringPreferencesKey("pref_lyrics_provider")
        val RECENT_AUTOPLAY_IDS = stringPreferencesKey("pref_recent_autoplay_ids")
        val CROSSFADE_PRESET = stringPreferencesKey("pref_crossfade_preset")
        val CROSSFADE_OUT_SHAPE = stringPreferencesKey("pref_crossfade_out_shape")
        val CROSSFADE_OUT_CURVATURE = floatPreferencesKey("pref_crossfade_out_curvature")
        val CROSSFADE_OUT_START = floatPreferencesKey("pref_crossfade_out_start")
        val CROSSFADE_OUT_END = floatPreferencesKey("pref_crossfade_out_end")
        val CROSSFADE_IN_SHAPE = stringPreferencesKey("pref_crossfade_in_shape")
        val CROSSFADE_IN_CURVATURE = floatPreferencesKey("pref_crossfade_in_curvature")
        val CROSSFADE_IN_START = floatPreferencesKey("pref_crossfade_in_start")
        val CROSSFADE_IN_END = floatPreferencesKey("pref_crossfade_in_end")
    }

    val userPreferences: Flow<UserPreferences> = context.dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { prefs ->
        val storedAppearance = prefs[Keys.APPEARANCE_PRESET] ?: if (prefs[Keys.THEME_MODE] == "DARK") "GRAPHITE" else "MIDNIGHT"
        val baseAppearance = com.akira.app.core.model.baseAppearanceId(storedAppearance)
        val spaceThemes = prefs[Keys.SPACE_THEMES] ?: if (com.akira.app.core.model.AppearancePreset.fromId(storedAppearance).space) setOf(baseAppearance) else emptySet()
        UserPreferences(
            shuffle = prefs[Keys.SHUFFLE] ?: false,
            repeatMode = prefs[Keys.REPEAT_MODE] ?: 0,
            lastPlayedSongId = prefs[Keys.LAST_SONG_ID],
            lastPositionMs = prefs[Keys.LAST_POSITION_MS] ?: 0L,
            equalizerPreset = prefs[Keys.EQUALIZER_PRESET] ?: "BALANCED",
            bassBoost = prefs[Keys.BASS_BOOST] ?: 0.5f,
            virtualizer = prefs[Keys.VIRTUALIZER] ?: 0.5f,
            eqEnabled = prefs[Keys.EQ_ENABLED] ?: false,
            eqBandGains = List(EQ_BAND_COUNT) { prefs[Keys.eqBand(it)] ?: 0f },
            eqPreamp = prefs[Keys.EQ_PREAMP] ?: 1.0f,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            isOnboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            minDurationSeconds = prefs[Keys.MIN_DURATION_SECONDS] ?: 30,
            cornerRadiusDp = prefs[Keys.CORNER_RADIUS_DP] ?: 22,
            navBarStyle = prefs[Keys.NAV_BAR_STYLE] ?: "FLOATING_PILL",
            libraryHeaderStyle = prefs[Keys.LIBRARY_HEADER_STYLE] ?: "COMPACT_PILL",
            liquidGlassEnabled = prefs[Keys.LIQUID_GLASS] ?: false,
            liquidGlassDiffusion = (prefs[Keys.LIQUID_GLASS_DIFFUSION] ?: 8f).takeIf { it.isFinite() }?.coerceIn(0f,24f) ?: 8f,
            dockBackgroundDiffusion = prefs[Keys.DOCK_BACKGROUND_DIFFUSION] ?: false,
            liquidGlassOpacity = (prefs[Keys.LIQUID_GLASS_OPACITY] ?: .25f).takeIf { it.isFinite() }?.coerceIn(0f,1f) ?: .25f,
            fullscreenPlayerArtwork = prefs[Keys.FULLSCREEN_PLAYER_ARTWORK] ?: false,
            animatedPlayerArtwork = prefs[Keys.ANIMATED_PLAYER_ARTWORK] ?: true,
            favoriteArtists = prefs[Keys.FAVORITE_ARTISTS] ?: emptySet(),
            themeMode = prefs[Keys.THEME_MODE] ?: "SYSTEM",
            appearancePreset = baseAppearance,
            spaceMode = baseAppearance in spaceThemes,
            spaceEnabledThemes = spaceThemes,
            spaceStyle = prefs[Keys.SPACE_STYLE] ?: storedAppearance.takeIf { com.akira.app.core.model.AppearancePreset.fromId(it).space } ?: "SPACE",
            accentColor = prefs[Keys.ACCENT_COLOR] ?: "PRESET",
            backgroundMotion = prefs[Keys.BACKGROUND_MOTION] ?: true,
            backgroundIntensity = (prefs[Keys.BACKGROUND_INTENSITY] ?: .7f).takeIf { it.isFinite() }?.coerceIn(.2f, 1f) ?: .7f,
            excludedFolderPaths = prefs[Keys.EXCLUDED_FOLDER_PATHS] ?: emptySet(),
            whitelistedSongIds = prefs[Keys.WHITELISTED_SONG_IDS] ?: emptySet(),
            gaplessPlayback = prefs[Keys.GAPLESS_PLAYBACK] ?: true,
            autoResume = prefs[Keys.AUTO_RESUME] ?: true,
            skipSilence = prefs[Keys.SKIP_SILENCE] ?: false,
            lastCrossfadeSeconds = (prefs[Keys.LAST_CROSSFADE_SECONDS] ?: prefs[Keys.CROSSFADE_SECONDS]?.takeIf { it > 0 } ?: 8).coerceIn(1, 30),
            crossfadeSeconds = prefs[Keys.CROSSFADE_SECONDS] ?: 0,
            hiResAudio = prefs[Keys.HI_RES_AUDIO] ?: true,
            defaultTab = prefs[Keys.DEFAULT_TAB] ?: "TRACKS",
            sortOrder = prefs[Keys.SORT_ORDER] ?: "TITLE_ASC",
            language = prefs[Keys.LANGUAGE] ?: "SYSTEM",
            autoplay = prefs[Keys.AUTOPLAY] ?: false,
            normalizationMode = prefs[Keys.NORMALIZATION_MODE] ?: "OFF",
            eqProfilesJson = prefs[Keys.EQ_PROFILES] ?: "[]",
            eqAssignmentsJson = prefs[Keys.EQ_ASSIGNMENTS] ?: "{}",
            bluetoothResume = prefs[Keys.BT_RESUME] ?: false,
            bluetoothWindowMinutes = (prefs[Keys.BT_WINDOW] ?: 10).coerceIn(1,60),
            onlineLyrics = prefs[Keys.ONLINE_LYRICS] ?: true,
            // Global searches compare every source; provider overrides belong to a song.
            lyricsProvider = com.akira.app.core.lyrics.LyricsProvider.AUTO.name,
            recentAutoplayIds = runCatching { org.json.JSONArray(prefs[Keys.RECENT_AUTOPLAY_IDS] ?: "[]").let { array -> List(array.length()) { array.getString(it) } } }.getOrDefault(emptyList()),
            crossfadePreset = CrossfadeCurve.canonicalPresetId(prefs[Keys.CROSSFADE_PRESET] ?: "ACTUAL"),
            crossfadeOutShape = prefs[Keys.CROSSFADE_OUT_SHAPE] ?: "CURRENT",
            crossfadeOutCurvature = prefs[Keys.CROSSFADE_OUT_CURVATURE] ?: 1.0f,
            crossfadeOutStart = prefs[Keys.CROSSFADE_OUT_START] ?: 0.0f,
            crossfadeOutEnd = prefs[Keys.CROSSFADE_OUT_END] ?: 1.0f,
            crossfadeInShape = prefs[Keys.CROSSFADE_IN_SHAPE] ?: "CURRENT",
            crossfadeInCurvature = prefs[Keys.CROSSFADE_IN_CURVATURE] ?: 1.0f,
            crossfadeInStart = prefs[Keys.CROSSFADE_IN_START] ?: 0.0f,
            crossfadeInEnd = prefs[Keys.CROSSFADE_IN_END] ?: 1.0f
        )
    }

    suspend fun setCrossfadeCurveConfig(
        preset: String,
        outShape: String,
        outCurvature: Float,
        outStart: Float,
        outEnd: Float,
        inShape: String,
        inCurvature: Float,
        inStart: Float,
        inEnd: Float
    ) {
        context.dataStore.edit {
            it[Keys.CROSSFADE_PRESET] = preset
            it[Keys.CROSSFADE_OUT_SHAPE] = outShape
            it[Keys.CROSSFADE_OUT_CURVATURE] = outCurvature
            it[Keys.CROSSFADE_OUT_START] = outStart
            it[Keys.CROSSFADE_OUT_END] = outEnd
            it[Keys.CROSSFADE_IN_SHAPE] = inShape
            it[Keys.CROSSFADE_IN_CURVATURE] = inCurvature
            it[Keys.CROSSFADE_IN_START] = inStart
            it[Keys.CROSSFADE_IN_END] = inEnd
        }
    }

    suspend fun setAutoplay(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTOPLAY] = enabled }
    }

    suspend fun setNormalizationMode(mode: String) {
        require(mode in listOf("OFF", "TRACK", "ALBUM"))
        context.dataStore.edit { it[Keys.NORMALIZATION_MODE] = mode }
    }

    suspend fun setOutputProfiles(profiles: String, assignments: String) {
        context.dataStore.edit { it[Keys.EQ_PROFILES] = profiles; it[Keys.EQ_ASSIGNMENTS] = assignments }
    }

    suspend fun setBluetoothResume(enabled: Boolean, minutes: Int) {
        context.dataStore.edit { it[Keys.BT_RESUME] = enabled; it[Keys.BT_WINDOW] = minutes.coerceIn(1,60) }
    }

    suspend fun setLyricsSource(enabled: Boolean, provider: String) {
        context.dataStore.edit {
            it[Keys.ONLINE_LYRICS] = enabled
            it[Keys.LYRICS_PROVIDER] = com.akira.app.core.lyrics.LyricsProvider.AUTO.name
        }
    }

    suspend fun setOnlineLyrics(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ONLINE_LYRICS] = enabled }
    }

    suspend fun saveRecentAutoplayIds(ids: List<String>) {
        context.dataStore.edit { it[Keys.RECENT_AUTOPLAY_IDS] = org.json.JSONArray(ids.takeLast(50)).toString() }
    }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language }
    }

    suspend fun setShuffle(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHUFFLE] = enabled }
    }

    suspend fun setRepeatMode(mode: Int) {
        context.dataStore.edit { it[Keys.REPEAT_MODE] = mode }
    }

    suspend fun savePlaybackState(songId: String, positionMs: Long) {
        context.dataStore.edit {
            it[Keys.LAST_SONG_ID] = songId
            it[Keys.LAST_POSITION_MS] = positionMs
        }
    }

    suspend fun setEqualizer(preset: String, bassBoost: Float, virtualizer: Float) {
        context.dataStore.edit {
            it[Keys.EQUALIZER_PRESET] = preset
            it[Keys.BASS_BOOST] = bassBoost
            it[Keys.VIRTUALIZER] = virtualizer
        }
    }

    /** Preset changes are one coherent update for both audio decks. */
    suspend fun setEqConfiguration(preset: String, gains: List<Float>, bass: Float, space: Float) {
        require(gains.size == EQ_BAND_COUNT && gains.all { it.isFinite() })
        context.dataStore.edit { values ->
            values[Keys.EQUALIZER_PRESET] = preset
            gains.forEachIndexed { index, gain -> values[Keys.eqBand(index)] = gain.coerceIn(EQ_MIN_DB, EQ_MAX_DB) }
            values[Keys.BASS_BOOST] = bass.coerceIn(0f, 1f)
            values[Keys.VIRTUALIZER] = space.coerceIn(0f, 1f)
        }
    }

    suspend fun setEqEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EQ_ENABLED] = enabled }
    }

    suspend fun setEqBandGain(index: Int, gainDb: Float) {
        if (index !in 0 until EQ_BAND_COUNT) return
        val clamped = gainDb.coerceIn(EQ_MIN_DB, EQ_MAX_DB)
        context.dataStore.edit { it[Keys.eqBand(index)] = clamped }
    }

    suspend fun setEqBandGains(gainsDb: List<Float>) {
        context.dataStore.edit {
            gainsDb.take(EQ_BAND_COUNT).forEachIndexed { i, g ->
                it[Keys.eqBand(i)] = g.coerceIn(EQ_MIN_DB, EQ_MAX_DB)
            }
        }
    }

    suspend fun setEqPreamp(gain: Float) {
        context.dataStore.edit { it[Keys.EQ_PREAMP] = gain.coerceIn(0.25f, 2.0f) }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setMinDurationSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.MIN_DURATION_SECONDS] = seconds }
    }

    suspend fun setCornerRadiusDp(radiusDp: Int) {
        context.dataStore.edit { it[Keys.CORNER_RADIUS_DP] = radiusDp }
    }

    suspend fun setNavBarStyle(style: String) {
        context.dataStore.edit { it[Keys.NAV_BAR_STYLE] = style }
    }

    suspend fun setDefaultTab(tab: String) {
        context.dataStore.edit { it[Keys.DEFAULT_TAB] = tab }
    }

    suspend fun setSortOrder(order: String) {
        context.dataStore.edit { it[Keys.SORT_ORDER] = order }
    }

    suspend fun setHiResAudio(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HI_RES_AUDIO] = enabled }
    }

    suspend fun setLibraryHeaderStyle(style: String) {
        context.dataStore.edit { it[Keys.LIBRARY_HEADER_STYLE] = style }
    }

    suspend fun setLiquidGlassEnabled(enabled: Boolean) { context.dataStore.edit { it[Keys.LIQUID_GLASS] = enabled } }
    suspend fun setLiquidGlassOpacity(value: Float) {
        context.dataStore.edit { it[Keys.LIQUID_GLASS_OPACITY] = value.takeIf { it.isFinite() }?.coerceIn(0f,1f) ?: .25f }
    }
    suspend fun setLiquidGlassDiffusion(value: Float) {
        context.dataStore.edit { it[Keys.LIQUID_GLASS_DIFFUSION] = value.takeIf { it.isFinite() }?.coerceIn(0f,24f) ?: 8f }
    }
    suspend fun setDockBackgroundDiffusion(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DOCK_BACKGROUND_DIFFUSION] = enabled }
    }
    suspend fun setFullscreenPlayerArtwork(enabled: Boolean) { context.dataStore.edit { it[Keys.FULLSCREEN_PLAYER_ARTWORK] = enabled } }
    suspend fun setAnimatedPlayerArtwork(enabled: Boolean) { context.dataStore.edit { it[Keys.ANIMATED_PLAYER_ARTWORK] = enabled } }
    suspend fun setArtistFavorite(name: String, enabled: Boolean) { context.dataStore.edit {
        val key=name.trim().lowercase(java.util.Locale.ROOT)
        val current=it[Keys.FAVORITE_ARTISTS] ?: emptySet()
        it[Keys.FAVORITE_ARTISTS]=if(enabled)current+key else current-key
    } }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun setAccentColor(value: String) {
        if (value == "PRESET" || Regex("#[0-9a-fA-F]{6}").matches(value))
            context.dataStore.edit { it[Keys.ACCENT_COLOR] = value.uppercase() }
    }

    suspend fun setAppearancePreset(id: String) {
        if (com.akira.app.core.model.AppearancePreset.entries.none { it.id == id }) return
        context.dataStore.edit {
            val previous = it[Keys.APPEARANCE_PRESET] ?: "MIDNIGHT"
            if (it[Keys.SPACE_THEMES] == null && com.akira.app.core.model.AppearancePreset.fromId(previous).space) {
                it[Keys.SPACE_THEMES] = setOf(com.akira.app.core.model.baseAppearanceId(previous))
                if (it[Keys.SPACE_STYLE] == null) it[Keys.SPACE_STYLE] = previous
            }
            it[Keys.APPEARANCE_PRESET] = id
            // Display mode is independent of the palette.
        }
    }
    suspend fun setSpaceMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val stored = prefs[Keys.APPEARANCE_PRESET] ?: "MIDNIGHT"
            val base = com.akira.app.core.model.baseAppearanceId(stored)
            val themes = (prefs[Keys.SPACE_THEMES] ?: if (com.akira.app.core.model.AppearancePreset.fromId(stored).space) setOf(base) else emptySet()).toMutableSet()
            if (enabled) themes.add(base) else themes.remove(base)
            prefs[Keys.SPACE_THEMES] = themes
        }
    }
    suspend fun setSpaceStyle(id: String) {
        if (com.akira.app.core.model.AppearancePreset.fromId(id).space) context.dataStore.edit { it[Keys.SPACE_STYLE] = id }
    }
    suspend fun setBackgroundMotion(enabled: Boolean) { context.dataStore.edit { it[Keys.BACKGROUND_MOTION] = enabled } }
    suspend fun setBackgroundIntensity(value: Float) {
        if (value.isFinite()) context.dataStore.edit { it[Keys.BACKGROUND_INTENSITY] = value.coerceIn(.2f, 1f) }
    }

    suspend fun setExcludedFolderPaths(paths: Set<String>) {
        context.dataStore.edit { it[Keys.EXCLUDED_FOLDER_PATHS] = paths }
    }

    suspend fun setFolderExcluded(path: String, excluded: Boolean) {
        context.dataStore.edit { prefs ->
            val paths = prefs[Keys.EXCLUDED_FOLDER_PATHS] ?: emptySet()
            prefs[Keys.EXCLUDED_FOLDER_PATHS] = if (excluded) paths + path else paths - path
        }
    }

    suspend fun setWhitelistedSongIds(ids: Set<String>) {
        context.dataStore.edit { it[Keys.WHITELISTED_SONG_IDS] = ids }
    }

    suspend fun setSongWhitelisted(id: String, allowed: Boolean) {
        context.dataStore.edit { prefs ->
            val ids = prefs[Keys.WHITELISTED_SONG_IDS] ?: emptySet()
            prefs[Keys.WHITELISTED_SONG_IDS] = if (allowed) ids + id else ids - id
        }
    }

    suspend fun setSongsWhitelisted(ids: Set<String>, allowed: Boolean) {
        context.dataStore.edit { prefs ->
            val existing=prefs[Keys.WHITELISTED_SONG_IDS] ?: emptySet()
            prefs[Keys.WHITELISTED_SONG_IDS]=if(allowed)existing+ids else existing-ids
        }
    }

    suspend fun setSkipSilence(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SKIP_SILENCE] = enabled }
    }

    suspend fun setAutoResume(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_RESUME] = enabled }
    }

    suspend fun setCrossfadeSeconds(value: Int) {
        context.dataStore.edit {
            val seconds = value.coerceIn(0, 30)
            it[Keys.CROSSFADE_SECONDS] = seconds
            if (seconds > 0) it[Keys.LAST_CROSSFADE_SECONDS] = seconds
        }
    }

    suspend fun setListeningExperience(
        gaplessPlayback: Boolean,
        autoResume: Boolean,
        crossfadeSeconds: Int,
        skipSilence: Boolean = false
    ) {
        context.dataStore.edit {
            it[Keys.GAPLESS_PLAYBACK] = gaplessPlayback
            it[Keys.AUTO_RESUME] = autoResume
            val seconds = crossfadeSeconds.coerceIn(0, 30)
            it[Keys.CROSSFADE_SECONDS] = seconds
            if (seconds > 0) it[Keys.LAST_CROSSFADE_SECONDS] = seconds
            it[Keys.SKIP_SILENCE] = skipSilence
        }
    }
}
