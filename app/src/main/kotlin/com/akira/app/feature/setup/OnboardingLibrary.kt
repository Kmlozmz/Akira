package com.akira.app.feature.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import com.akira.app.core.data.passesLibraryFilters
import com.akira.app.core.datastore.*
import com.akira.app.core.design.components.*
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.model.Song
import com.akira.app.feature.settings.*

/** Use the same moving segment indicator as the rest of Akira. */
@Composable internal fun OnboardingOptions(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    AkiraSegmentedControl(options, selected, onSelect, MaterialTheme.colorScheme.surfaceContainerHigh)
}

/** A compact library overview replaces the arbitrary sample of two tracks. All counts use visible songs. */
@Composable internal fun OnboardingCollection(songs: List<Song>) {
    val albums = remember(songs) { songs.distinctBy { if (it.albumId != 0L) it.albumId.toString() else "${it.album}/${it.artist}" }.size }
    val artists = remember(songs) { songs.map { it.artist.trim().lowercase(java.util.Locale.ROOT) }.filter { it.isNotBlank() }.distinct().size }
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val count by androidx.compose.animation.core.animateIntAsState(if (appeared) songs.size else 0,
        androidx.compose.animation.core.tween(if (setupReducedMotion()) 0 else 550), label = "libraryCount")
    val actualCount = com.akira.app.feature.library.collectionCount(songs.size)
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) { contentDescription = actualCount },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(count.toString(), style = MaterialTheme.typography.displayMedium, modifier = Modifier.clearAndSetSemantics {})
            Text(localizedText("songs ready", "canciones listas"), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(localizedText("$albums albums", "$albums álbumes"), style = MaterialTheme.typography.titleSmall)
            Text(localizedText("$artists artists", "$artists artistas"), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
}

/** The scan has one legible progress surface; placeholders never pretend to be discovered songs. */
@Composable internal fun OnboardingLibraryStatus(state: SetupState) {
    val scanning = state.status == SetupStatus.SCANNING
    val phase = onboardingPhase(3_000)
    val color = MaterialTheme.colorScheme.onSurface
    SettingsGroup {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (scanning) localizedText("${state.count} files read", "${state.count} archivos leídos")
                    else localizedText("Music on this phone", "Música en este teléfono"), style = MaterialTheme.typography.titleMedium)
                Text(if (scanning && state.title.isNotBlank()) state.title
                    else localizedText("Songs, albums and artists in one library", "Canciones, álbumes y artistas en una biblioteca"),
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp))
            Column(Modifier.fillMaxWidth().clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(2) { index ->
                    Row(Modifier.graphicsLayer {
                        alpha = if (scanning) .6f + .2f * (1 + kotlin.math.sin(phase.value + index)) else .8f
                    }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val fill = color.copy(alpha = .10f)
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)).background(fill))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.fillMaxWidth(if (index == 0) .76f else .6f).height(9.dp).clip(RoundedCornerShape(5.dp)).background(fill))
                            Box(Modifier.fillMaxWidth(.4f).height(7.dp).clip(RoundedCornerShape(5.dp)).background(fill))
                        }
                    }
                }
            }
        }
    }
}

/** All common filters stay on the library page; only individual-file review opens an editor. */
@Composable internal fun OnboardingLibraryFilters(prefs: UserPreferences, songs: List<Song>, repo: UserPreferencesRepository,
    model: LibrarySetupViewModel, scanned: Boolean, onReview: () -> Unit) {
    val excluded = remember(songs, prefs.minDurationSeconds, prefs.excludedFolderPaths, prefs.whitelistedSongIds) {
        songs.count { !passesLibraryFilters(it, prefs.minDurationSeconds, prefs.excludedFolderPaths, prefs.whitelistedSongIds) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroupTitle(localizedText("Library filters", "Filtros de biblioteca"))
        SettingsGroup {
            SettingsToggleRow(Icons.Rounded.FolderOff, localizedText("Hide WhatsApp audio", "Ocultar audios de WhatsApp"),
                "", "WhatsApp" in prefs.excludedFolderPaths) { value -> model.writes.submit("whatsapp") { repo.setFolderExcluded("WhatsApp", value) } }
            SettingsToggleRow(Icons.Rounded.NotificationsOff, localizedText("Hide ringtones", "Ocultar tonos de llamada"),
                "", "Ringtones" in prefs.excludedFolderPaths) { value -> model.writes.submit("ringtones") { repo.setFolderExcluded("Ringtones", value) } }
        }
        SettingsGroup {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(localizedText("Minimum length", "Duración mínima"), style = MaterialTheme.typography.titleSmall)
                AkiraSegmentedControl(listOf("0" to localizedText("All", "Todas"), "15" to "15 s", "30" to "30 s", "60" to "1 min"),
                    prefs.minDurationSeconds.toString(), { value ->
                        model.writes.submit("duration") { repo.setMinDurationSeconds(value.toInt()) }
                    }, MaterialTheme.colorScheme.surfaceContainerHigh)
            }
            if (scanned) SettingsRow(Icons.Rounded.FilterList,
                localizedText("Review excluded audio", "Revisar audio excluido"),
                localizedText("$excluded files hidden by these filters", "$excluded archivos ocultos por estos filtros"), onReview)
        }
    }
}

@Composable internal fun PreferenceWriteError(model: LibrarySetupViewModel) {
    val failures by model.writes.failures.collectAsState()
    val pending by model.writes.pending.collectAsState()
    if (failures.isNotEmpty()) {
        Text(localizedText("A setting couldn't be saved.", "No se pudo guardar un ajuste."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        TextButton({ model.writes.retry() }, enabled = pending == 0) { Text(localizedText("Retry saving", "Reintentar guardado")) }
    }
}

/** The shared file editor is presented in Akira's floating panel, keeping the library visible behind it. */
@Composable internal fun OnboardingExcludedFiles(songs: List<Song>, prefs: UserPreferences, repo: UserPreferencesRepository,
    persist: suspend (Set<String>, Boolean) -> Unit, onClose: () -> Unit) {
    val height = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * .70f).coerceAtMost(600.dp)
    AkiraModalSheet(onDismissRequest = onClose, title = localizedText("Excluded audio", "Audio excluido")) {
        Text(localizedText("Excluded audio", "Audio excluido"), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp))
        LibraryFilterReview(songs, prefs, repo, Modifier.fillMaxWidth().height(height).padding(horizontal = 20.dp),
            onDone = onClose, persistSelection = persist, compact = true)
    }
}
