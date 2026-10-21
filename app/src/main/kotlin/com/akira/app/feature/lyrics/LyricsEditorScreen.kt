package com.akira.app.feature.lyrics

import com.akira.app.core.artwork.*
import com.akira.app.feature.library.*
import com.akira.app.feature.player.*
import com.akira.app.feature.queue.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.lyrics.*
import com.akira.app.core.model.Song
import com.akira.app.core.i18n.LocalStrings
import com.akira.app.core.i18n.AkiraUiText
import com.akira.app.feature.settings.AkiraStudioScreen
import com.akira.app.core.design.util.rememberAkiraHaptics

@Composable
internal fun LyricsEditorScreen(song: Song, lyrics: LoadedLyrics, positionMs: Long?, onDismiss: () -> Unit,
    saving: Boolean, onSave: (LoadedLyrics) -> Unit) {
    val strings = LocalStrings.current
    val haptics = rememberAkiraHaptics()
    val initialText = remember(song.id, lyrics) { lyrics.sidecarText() }
    var value by rememberSaveable(song.id, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initialText)) }
    var invalid by remember { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable(song.id) { mutableStateOf(false) }
    val requestDismiss = {
        when {
            saving -> Unit
            hasUnsavedLyricsChanges(initialText, value.text) -> confirmDiscard = true
            else -> onDismiss()
        }
    }
    AkiraStudioScreen(strings.ui(AkiraUiText.EditLyrics), requestDismiss) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier.size(62.dp).clip(RoundedCornerShape(13.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AkiraIcons.Album, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(25.dp))
                        if (!song.albumArtUri.isNullOrBlank()) {
                            AsyncImage(
                                model = song.albumArtUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            song.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            song.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Text(strings.ui(AkiraUiText.LyricsEditorHint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value, { value = it; invalid = false }, modifier = Modifier.fillMaxWidth().weight(1f),
                label = { Text(strings.ui(AkiraUiText.LyricsText)) }, isError = invalid, enabled = !saving,
                shape = RoundedCornerShape(16.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 23.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = .55f)
                ))
            if (invalid) Text(strings.ui(AkiraUiText.LyricsEditInvalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(enabled = positionMs != null && !saving, onClick = {
                    haptics.tick()
                    val ms = positionMs?.coerceAtLeast(0) ?: return@TextButton
                    val stamp = "[%02d:%02d.%03d]".format(java.util.Locale.ROOT, ms / 60000, ms / 1000 % 60, ms % 1000)
                    val start = value.selection.min; val end = value.selection.max
                    value = TextFieldValue(value.text.replaceRange(start, end, stamp), TextRange(start + stamp.length))
                }) { Text(strings.ui(AkiraUiText.InsertCurrentTime)) }
                Button(enabled = !saving, onClick = {
                    val parsed = runCatching { parseEditedLyrics(value.text) }.getOrNull()
                    if (parsed == null) invalid = true else { haptics.confirm(); onSave(parsed) }
                }, modifier = Modifier.weight(1f)) { Text(strings.save) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirmDiscard) {
        com.akira.app.core.design.components.AkiraConfirmationDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(strings.ui(AkiraUiText.UnsavedLyricsTitle)) },
            text = { Text(strings.ui(AkiraUiText.UnsavedLyricsMessage)) },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(strings.ui(AkiraUiText.KeepEditing))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    haptics.click()
                    confirmDiscard = false
                    onDismiss()
                }) {
                    Text(strings.ui(AkiraUiText.DiscardChanges), color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}

internal fun hasUnsavedLyricsChanges(initial: String, current: String): Boolean = initial != current
