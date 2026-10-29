package com.akira.app.feature.settings

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akira.app.BuildConfig
import com.akira.app.core.datastore.UserPreferences
import com.akira.app.core.datastore.UserPreferencesRepository
import com.akira.app.core.audio.EqualizerConfiguration
import com.akira.app.core.audio.OutputRoutes
import com.akira.app.core.design.components.LocalAkiraOverlayBackdrop
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.design.motion.rememberAkiraBouncingFling
import com.akira.app.core.design.util.rememberAkiraHaptics
import com.akira.app.core.i18n.LocalStrings
import com.akira.app.core.i18n.AkiraUiText
import com.akira.app.core.logging.AkiraLog
import com.akira.app.core.media.CrossfadeShape
import com.akira.app.core.media.CurveSpec
import com.akira.app.core.model.Song
import com.akira.app.core.design.glass.LocalAppBackdrop
import com.akira.app.core.design.backdrop.backdrops.layerBackdrop
import com.akira.app.core.design.backdrop.backdrops.rememberLayerBackdrop
import com.akira.app.core.design.components.*
import com.akira.app.core.design.motion.*
import com.akira.app.core.design.navigation.*
import com.akira.app.core.design.rendering.*
import com.akira.app.feature.home.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(prefsRepo: UserPreferencesRepository, onBack: () -> Unit, modifier: Modifier = Modifier,
    onTestCrossfade: () -> Unit = {}, previewSongs: List<Song> = emptyList(), previewSong: Song? = null,
    bottomContentPadding: Dp = 0.dp, startWithSound: Boolean = false) {
    val prefs by prefsRepo.userPreferences.collectAsStateWithLifecycle(initialValue = null)
    var section by rememberSaveable(startWithSound) {
        mutableStateOf<SettingsSection?>(if (startWithSound) SettingsSection.SOUND else null)
    }
    var editor by rememberSaveable { mutableStateOf<SettingsEditor?>(null) }
    val parentNavigation = LocalStudioNavigation.current
    val navigation = parentNavigation ?: remember { StudioNavigationState() }
    val scope = rememberCoroutineScope()
    val strings = LocalStrings.current
    val haptics = rememberAkiraHaptics()
    val list = rememberLazyListState()
    val backdrop = rememberLayerBackdrop()
    val pageColor = MaterialTheme.colorScheme.background
    val controlsBackdrop = com.akira.app.core.design.backdrop.backdrops.rememberCanvasBackdrop { drawRect(pageColor) }
    val collapse by rememberPageCollapse(list)
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    CompositionLocalProvider(LocalStudioNavigation provides navigation, LocalAppBackdrop provides controlsBackdrop,
        LocalAkiraOverlayBackdrop provides backdrop, LocalPageHeaderBackdrop provides backdrop) {
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .semantics { paneTitle = strings.settingsTitle; isTraversalGroup = true }) {
            if (prefs == null) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.onSurface)
                Text(strings.ui(AkiraUiText.SettingsLoading), style = MaterialTheme.typography.bodyMedium)
            } else LazyColumn(state = list,
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().align(Alignment.TopCenter)
                    .then(if (com.akira.app.core.design.glass.LocalLiquidGlassEnabled.current && com.akira.app.core.design.glass.isGlassSupported()) Modifier.layerBackdrop(backdrop) else Modifier),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = status + 126.dp,
                    bottom = maxOf(bottomContentPadding, bottom + 32.dp)),
                verticalArrangement = Arrangement.spacedBy(2.dp), flingBehavior = rememberAkiraBouncingFling()) {
                item("online-mode") { Box(Modifier.padding(bottom = 18.dp)) {
                    com.akira.app.feature.online.OnlineModeControl()
                } }
                itemsIndexed(SettingsSection.entries, key = { _,category -> category.name }) { index,category ->
                    Box(Modifier.clip(settingsSegmentShape(index,SettingsSection.entries.size))) {
                        SettingsRow(category.icon(), strings.ui(category.title), strings.ui(category.summary),
                            { section = category }, Modifier.testTag("settings-section-${category.name}"),
                            leading = if (category == SettingsSection.LIBRARY) ({
                                Icon(androidx.compose.ui.res.painterResource(com.akira.app.R.drawable.ic_navigation_library_filled), null,
                                    Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .85f))
                            }) else null)
                    }
                }
            }
            PagePageHeader(strings.settingsTitle, onBack, collapse,
                Modifier.widthIn(max = 720.dp).align(Alignment.TopCenter), topInset = status)
        }
        prefs?.let { current ->
            // Back can arrive before the native selector window gains focus.
            val closeSection = { if (editor != null) editor = null else section = null }
            section?.let { category ->
                if (category == SettingsSection.APPEARANCE) AppearanceScreen(current, prefsRepo, previewSongs, previewSong, closeSection)
                else AkiraStudioScreen(strings.ui(category.title), closeSection) {
                    Column(Modifier.fillMaxWidth().verticalScroll(LocalPageScrollState.current ?: rememberScrollState(),
                        flingBehavior = rememberAkiraBouncingFling()).padding(top = 118.dp)
                        .padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if(category!=SettingsSection.SOUND) Text(strings.ui(category.summary), Modifier.padding(bottom = 8.dp),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        when (category) {
                            SettingsSection.PLAYBACK -> {
                                SettingsGroupTitle(strings.ui(AkiraUiText.SettingsListening))
                                SettingsGroup {
                                    SettingsToggleRow(Icons.Rounded.History, strings.autoResume, strings.autoResumeDesc, current.autoResume) {
                                        scope.launch { prefsRepo.setAutoResume(it) }
                                    }
                                    SettingsToggleRow(Icons.Rounded.FastForward, strings.skipSilence, strings.skipSilenceDesc, current.skipSilence) {
                                        scope.launch { prefsRepo.setSkipSilence(it) }
                                    }
                                }
                                SettingsGroupTitle(strings.ui(AkiraUiText.SettingsHeadphones))
                                SettingsGroup {
                                    SettingsToggleRow(AkiraIcons.Airplay, strings.headphoneResume, strings.headphoneResumeDesc, current.bluetoothResume) {
                                        scope.launch { prefsRepo.setBluetoothResume(it, current.bluetoothWindowMinutes) }
                                    }
                                    SettingsRow(AkiraIcons.SleepTimer, strings.ui(AkiraUiText.ReconnectTimeLimit), "${current.bluetoothWindowMinutes} min",
                                        { editor = SettingsEditor.BLUETOOTH_WINDOW }, enabled = current.bluetoothResume, opensSelector = true)
                                }
                            }
                            SettingsSection.SOUND -> SettingsGroup {
                                SettingsRow(Icons.Rounded.Waves, strings.transitionCrossfade,
                                    if(current.crossfadeSeconds==0)localizedText("Off · tracks play without overlap","Desactivado · sin solapamiento")
                                    else strings.secondsFormat.format(current.crossfadeSeconds),
                                    {editor=SettingsEditor.TRANSITIONS})
                                SettingsRow(AkiraIcons.Airplay, strings.library.normalization, normalizationLabel(current.normalizationMode),
                                    { editor = SettingsEditor.NORMALIZATION }, opensSelector = true)
                                SettingsRow(Icons.Rounded.Equalizer, strings.ui(AkiraUiText.Equalizer),
                                    strings.ui(AkiraUiText.Value5BandsPresetsHeadroomProfiles), { editor = SettingsEditor.EQUALIZER })
                                SettingsRow(AkiraIcons.Airplay, strings.library.profiles, com.akira.app.core.design.components.localizedText("Automatic equalizer for speakers and headphones","Ecualización automática para altavoz y auriculares"),
                                    { editor = SettingsEditor.OUTPUT_PROFILES }, opensSelector = true)
                            }
                            SettingsSection.LYRICS -> {
                                SettingsGroup {
                                    SettingsRow(AkiraIcons.Lyrics, strings.ui(AkiraUiText.LyricsSourcesTitle),
                                        if (!current.onlineLyrics) strings.ui(AkiraUiText.LyricsLocalSummary) else
                                            strings.ui(AkiraUiText.LyricsAutoSummary),
                                        { editor = SettingsEditor.LYRICS_SOURCE }, opensSelector = true)
                                }
                                Text(strings.ui(AkiraUiText.SettingsLocalLyricsHint), Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            SettingsSection.LIBRARY -> SettingsGroup {
                                SettingsToggleRow(Icons.Rounded.FolderOff, strings.ui(AkiraUiText.SetupHideWhatsApp),
                                    strings.ui(AkiraUiText.SetupHideWhatsAppDetail), "WhatsApp" in current.excludedFolderPaths,
                                    { scope.launch { prefsRepo.setFolderExcluded("WhatsApp", it) } })
                                SettingsToggleRow(Icons.Rounded.NotificationsOff, strings.ui(AkiraUiText.SetupHideRingtones),
                                    strings.ui(AkiraUiText.SetupHideRingtonesDetail), "Ringtones" in current.excludedFolderPaths,
                                    { scope.launch { prefsRepo.setFolderExcluded("Ringtones", it) } })
                                SettingsRow(Icons.Rounded.Timer, strings.ui(AkiraUiText.MinimumDuration),
                                    if (current.minDurationSeconds == 0) strings.ui(AkiraUiText.ShowAllTracks) else
                                        strings.ui(AkiraUiText.HideTracksShorterThanSS, current.minDurationSeconds),
                                    { editor = SettingsEditor.MINIMUM_DURATION }, opensSelector = true)
                                SettingsRow(Icons.Rounded.FilterList, strings.ui(AkiraUiText.SetupReviewFilters),
                                    strings.ui(AkiraUiText.SetupExceptionsSummary), { editor = SettingsEditor.LIBRARY_EXCEPTIONS })
                                SettingsRow(Icons.Rounded.Home, strings.ui(AkiraUiText.DefaultTab), tabLabel(current.defaultTab),
                                    { editor = SettingsEditor.DEFAULT_TAB }, opensSelector = true)
                            }
                            SettingsSection.APPLICATION -> {
                                SettingsGroup {
                                    SettingsRow(Icons.Rounded.Language, strings.languageTitle, languageLabel(current.language),
                                        { editor = SettingsEditor.LANGUAGE }, opensSelector = true)
                                    SettingsRow(Icons.Rounded.SystemUpdateAlt, localizedText("Updates", "Actualizaciones"),
                                        "Akira ${BuildConfig.VERSION_NAME}", { editor = SettingsEditor.UPDATES })
                                }
                                SettingsGroupTitle(strings.sectionDiagnostics)
                                DiagnosticsSettings()
                                Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(strings.ui(AkiraUiText.SettingsVersion), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            SettingsSection.APPEARANCE -> Unit
                        }
                        SettingsSelector(editor, current, prefsRepo) { editor = null }
                    }
                }
            }
            // Full-screen editors register here: their caller stays composed when
            // StudioNavigationHost covers/disposes the category body.
            when (editor) {
                SettingsEditor.UPDATES -> UpdatesScreen(onBack = { editor = null })
                SettingsEditor.LIBRARY_EXCEPTIONS -> LibraryExceptionsScreen(current, prefsRepo) { editor = null }
                SettingsEditor.EQUALIZER -> EqualizerScreen(current, haptics, { editor = null },
                    { scope.launch { prefsRepo.setEqEnabled(it) } },
                    { preset -> scope.launch { prefsRepo.editActiveEqualizer(OutputRoutes.current.value, preset.name) {
                        it.copy(bands = preset.gains, bass = preset.bass, space = preset.space)
                    } } },
                    { index, value -> scope.launch {
                        prefsRepo.editActiveEqualizer(OutputRoutes.current.value) { config ->
                            config.copy(bands = config.bands.toMutableList().also { it[index] = value })
                        }
                    } },
                    { value -> scope.launch { prefsRepo.editActiveEqualizer(OutputRoutes.current.value) { it.copy(bass = value) } } },
                    { value -> scope.launch { prefsRepo.editActiveEqualizer(OutputRoutes.current.value) { it.copy(space = value) } } },
                    { value -> scope.launch { prefsRepo.editActiveEqualizer(OutputRoutes.current.value) { it.copy(preamp = value) } } },
                    { scope.launch { prefsRepo.editActiveEqualizer(OutputRoutes.current.value, "Equilibrado") { EqualizerConfiguration() } } })
                SettingsEditor.TRANSITIONS -> {
                    val outgoing = CurveSpec(runCatching { CrossfadeShape.valueOf(current.crossfadeOutShape) }.getOrDefault(CrossfadeShape.CURRENT),
                        current.crossfadeOutCurvature, current.crossfadeOutStart, current.crossfadeOutEnd)
                    val incoming = CurveSpec(runCatching { CrossfadeShape.valueOf(current.crossfadeInShape) }.getOrDefault(CrossfadeShape.CURRENT),
                        current.crossfadeInCurvature, current.crossfadeInStart, current.crossfadeInEnd)
                    CrossfadeCurveScreen(current.crossfadePreset, outgoing, incoming, current.crossfadeSeconds, haptics,
                        { editor = null }, { preset, out, into -> scope.launch {
                            prefsRepo.setCrossfadeCurveConfig(preset, out.shape.name, out.curvature, out.startRange, out.endRange,
                                into.shape.name, into.curvature, into.startRange, into.endRange)
                        } }, onTestCrossfade, { scope.launch { prefsRepo.setCrossfadeSeconds(it) } }, current.lastCrossfadeSeconds)
                }
                else -> Unit
            }
        }
        if (parentNavigation == null) StudioNavigationHost(navigation)
    }
}

private fun SettingsSection.icon(): ImageVector = when (this) {
    SettingsSection.PLAYBACK -> AkiraIcons.Play
    SettingsSection.SOUND -> Icons.Rounded.Equalizer
    SettingsSection.APPEARANCE -> AkiraIcons.Appearance
    SettingsSection.LYRICS -> AkiraIcons.Lyrics
    SettingsSection.LIBRARY -> AkiraIcons.Queue // Painter override shares the dock's Library glyph.
    SettingsSection.APPLICATION -> Icons.Rounded.Settings
}

@Composable internal fun normalizationLabel(mode: String): String = LocalStrings.current.library.let {
    when (mode) { "TRACK" -> it.track; "ALBUM" -> it.album; else -> it.off }
}
@Composable private fun languageLabel(language: String): String = LocalStrings.current.let {
    when (language) { "es" -> it.langEs; "en" -> it.langEn; else -> it.langSystem }
}
@Composable private fun tabLabel(tab: String): String = LocalStrings.current.let {
    when (tab) { "ALBUMS" -> it.tabAlbums; "ARTISTS" -> it.tabArtists; else -> it.tabTracks }
}

@Composable
private fun SettingsSelector(editor: SettingsEditor?, prefs: UserPreferences, repo: UserPreferencesRepository, close: () -> Unit) {
    val scope = rememberCoroutineScope()
    val strings = LocalStrings.current
    fun select(write: suspend () -> Unit) { scope.launch { write() }; close() }
    when (editor) {
        SettingsEditor.LYRICS_SOURCE -> LyricsSourcesSheet(prefs.onlineLyrics, prefs.lyricsProvider, close) { online, provider ->
            select { repo.setLyricsSource(online, provider) }
        }
        SettingsEditor.OUTPUT_PROFILES -> OutputProfilesSheet(close)
        SettingsEditor.BLUETOOTH_WINDOW -> IntervalPicker(strings.ui(AkiraUiText.ReconnectTimeLimit),
            strings.ui(AkiraUiText.ResumeOnlyWhenReconnectingWithinThisTime), prefs.bluetoothWindowMinutes.toString(),
            (listOf(1, 5, 10, 20, 30, 60) + prefs.bluetoothWindowMinutes).distinct().sorted().map { it.toString() to "$it min" }, close) {
                select { repo.setBluetoothResume(prefs.bluetoothResume, it.toInt()) }
            }
        SettingsEditor.NORMALIZATION -> SettingsChoiceSheet(strings.library.normalization, strings.ui(AkiraUiText.SettingsNormalizationHint),
            prefs.normalizationMode, listOf("OFF", "TRACK", "ALBUM").map { it to normalizationLabel(it) }, close) {
                select { repo.setNormalizationMode(it) }
            }
        SettingsEditor.LANGUAGE -> SettingsChoiceSheet(strings.languageTitle, strings.languageDesc, prefs.language,
            listOf("SYSTEM", "es", "en").map { it to languageLabel(it) }, close) { select { repo.setLanguage(it) } }
        SettingsEditor.MINIMUM_DURATION -> SettingsChoiceSheet(strings.ui(AkiraUiText.MinimumDuration),
            strings.ui(AkiraUiText.SettingsMinimumDurationHint), prefs.minDurationSeconds.toString(),
            (listOf(0, 15, 30, 60) + prefs.minDurationSeconds).distinct().sorted().map {
                it.toString() to if (it == 0) strings.ui(AkiraUiText.All) else strings.secondsFormat.format(it)
            }, close) { select { repo.setMinDurationSeconds(it.toInt()) } }
        SettingsEditor.DEFAULT_TAB -> SettingsChoiceSheet(strings.ui(AkiraUiText.DefaultTab), strings.ui(AkiraUiText.WhenOpeningTheLibrary),
            prefs.defaultTab, listOf("TRACKS", "ALBUMS", "ARTISTS").map { it to tabLabel(it) }, close) {
                select { repo.setDefaultTab(it) }
            }
        else -> Unit
    }
}

@Composable
private fun DiagnosticsSettings() {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var includeTitles by rememberSaveable { mutableStateOf(false) }
    SettingsGroup {
        SettingsToggleRow(Icons.Rounded.History, strings.includeTrackTitles, strings.includeTrackTitlesDesc, includeTitles) { includeTitles = it }
        Text(strings.logsPrivacyNotice, Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsRow(AkiraIcons.Share, strings.exportLogs, strings.exportLogsDesc, {
            scope.launch {
                try {
                    val file = withContext(Dispatchers.IO) { AkiraLog.exportToFile(context, includeTitles) }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, strings.exportLogs)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData = ClipData.newUri(context.contentResolver, strings.exportLogs, uri)
                    }
                    context.startActivity(Intent.createChooser(intent, strings.exportLogs))
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { com.akira.app.core.design.components.AkiraNotices.show(strings.library.error) }
            }
        })
    }
}
