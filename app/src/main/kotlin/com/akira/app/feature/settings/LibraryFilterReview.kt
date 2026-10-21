package com.akira.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.akira.app.core.data.belongsToFolder
import com.akira.app.core.data.isShortTrack
import com.akira.app.core.data.passesLibraryFilters
import com.akira.app.core.datastore.*
import com.akira.app.core.design.components.AkiraSegmentedControl
import com.akira.app.core.design.components.localizedText
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.design.util.rememberAkiraHaptics
import com.akira.app.core.model.Song
import kotlinx.coroutines.launch

/** Batch exceptions explain which rule hid each file before changing library visibility. */
@Composable internal fun LibraryFilterReview(songs: List<Song>, prefs: UserPreferences,
    repo: UserPreferencesRepository, modifier: Modifier = Modifier, listState: LazyListState = rememberLazyListState(),
    fontFamily: FontFamily = com.akira.app.core.design.theme.AkiraFontFamily, onDone: (() -> Unit)? = null) {
    var showRestored by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var saving by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    val haptics = rememberAkiraHaptics()
    val focus = LocalFocusManager.current
    val excluded = remember(songs, prefs.minDurationSeconds, prefs.excludedFolderPaths, prefs.whitelistedSongIds) {
        songs.filter { it.id !in prefs.whitelistedSongIds && !passesLibraryFilters(it, prefs.minDurationSeconds, prefs.excludedFolderPaths, emptySet()) }
    }
    val restored = remember(songs, prefs.whitelistedSongIds) { songs.filter { it.id in prefs.whitelistedSongIds } }
    val candidates = if(showRestored) restored else excluded
    val visible = remember(candidates, query) { candidates.filter {
        query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) || it.path.substringAfterLast('/').contains(query, true)
    } }
    val visibleIds = remember(visible) { visible.map { it.id }.toSet() }
    val allVisibleSelected = visibleIds.isNotEmpty() && selected.containsAll(visibleIds)
    fun apply() {
        if(saving || selected.isEmpty()) return
        val ids = selected.toSet()
        val keep = !showRestored
        saving = true
        haptics.confirm()
        scope.launch {
            try { repo.setSongsWhitelisted(ids, keep); selected = emptyList(); result = true }
            catch(cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch(_: Exception) { result = false }
            finally { saving = false }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AkiraSegmentedControl(listOf("excluded" to localizedText("Excluded", "Excluidas"), "restored" to localizedText("Restored", "Recuperadas")),
            if(showRestored) "restored" else "excluded", {
                if(!saving) { showRestored = it == "restored"; selected = emptyList(); query = ""; result = null }
            }, MaterialTheme.colorScheme.surfaceContainerHigh, fontFamily = fontFamily)
        Text(if(showRestored) localizedText("These songs are included despite your filters. Select songs to apply the filters again.",
            "Estas canciones se incluyen aunque coincidan con un filtro. Selecciónalas para volver a aplicarles los filtros.")
            else localizedText("These files are hidden by your filters. Select any songs you want to include.",
                "Estos archivos quedan fuera por tus filtros. Selecciona las canciones que quieras incluir."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextField(query, { query = it }, Modifier.fillMaxWidth(), enabled = !saving,
            placeholder = { Text(localizedText("Search songs or artists", "Buscar canciones o artistas"), style = MaterialTheme.typography.bodyMedium) },
            singleLine = true, shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent),
            trailingIcon = { if(query.isNotEmpty()) IconButton({ query = "" }) { Icon(AkiraIcons.Close, localizedText("Clear search", "Borrar búsqueda")) } })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if(selected.isEmpty()) localizedText("${visible.size} files", "${visible.size} archivos")
                else localizedText("${selected.size} selected", "${selected.size} seleccionadas"),
                Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            TextButton({
                haptics.click()
                selected = if(allVisibleSelected) selected.filterNot { it in visibleIds } else (selected + visibleIds).distinct()
            }, enabled = !saving && visible.isNotEmpty()) {
                Text(if(allVisibleSelected) localizedText("Deselect", "Desmarcar") else localizedText("Select all", "Seleccionar todo"))
            }
        }
        if(result != null) Text(if(result == true) localizedText("Library updated.", "Biblioteca actualizada.")
            else localizedText("Couldn't save. Try again.", "No se pudo guardar. Reintenta."),
            style = MaterialTheme.typography.bodySmall,
            color = if(result == true) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if(visible.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(when {
                query.isNotBlank() -> localizedText("No matching files.", "No hay coincidencias.")
                showRestored -> localizedText("No songs restored yet.", "Todavía no has recuperado ninguna canción.")
                else -> localizedText("No files excluded by these filters.", "Ningún archivo queda fuera por estos filtros.")
            }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(visible, key = { _, song -> song.id }) { index, song ->
                val chosen = song.id in selected
                Row(Modifier.fillMaxWidth().clip(settingsSegmentShape(index, visible.size))
                    .background(if(chosen) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .toggleable(chosen, enabled = !saving, role = Role.Checkbox, onValueChange = {
                        haptics.click(); selected = if(it) selected + song.id else selected - song.id
                    }).heightIn(min = 84.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
                        Icon(AkiraIcons.Album, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        coil.compose.AsyncImage(song.albumArtUri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        val duration = "${song.durationSeconds / 60}:${(song.durationSeconds % 60).toString().padStart(2, '0')}"
                        Text("${song.artist} · $duration", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(filterReason(song, prefs), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Checkbox(chosen, null, Modifier.clearAndSetSemantics {})
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if(onDone != null) TextButton({ if(!saving) onDone() }, Modifier.heightIn(min = 52.dp), enabled = !saving) {
                Text(localizedText("Close", "Cerrar"))
            }
            Button(::apply, Modifier.weight(1f).heightIn(min = 52.dp), enabled = !saving && selected.isNotEmpty(), shape = RoundedCornerShape(18.dp)) {
                Text(if(saving) localizedText("Saving…", "Guardando…") else if(showRestored)
                    localizedText("Apply filters (${selected.size})", "Aplicar filtros (${selected.size})")
                    else localizedText("Include (${selected.size})", "Incluir (${selected.size})"))
            }
        }
    }
}

@Composable private fun filterReason(song: Song, prefs: UserPreferences): String {
    val reasons = mutableListOf<String>()
    if(isShortTrack(song.durationMs, prefs.minDurationSeconds)) reasons += localizedText("Under ${prefs.minDurationSeconds} s", "Menos de ${prefs.minDurationSeconds} s")
    prefs.excludedFolderPaths.filter { belongsToFolder(song.path, it) }.forEach {
        val folder = it.replace('\\', '/').trimEnd('/').substringAfterLast('/')
        reasons += localizedText("Folder: $folder", "Carpeta: $folder")
    }
    return reasons.joinToString(" · ").ifBlank { localizedText("Added as an exception", "Añadida como excepción") }
}
