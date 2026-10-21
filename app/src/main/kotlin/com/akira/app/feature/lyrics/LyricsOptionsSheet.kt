package com.akira.app.feature.lyrics

import com.akira.app.core.artwork.*
import com.akira.app.feature.library.*
import com.akira.app.feature.player.*
import com.akira.app.feature.queue.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.components.AkiraModalSheet
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.i18n.LocalStrings
import com.akira.app.core.i18n.AkiraUiText

@Composable
internal fun LyricsOptionsSheet(hasLyrics: Boolean, synced: Boolean, saved: Boolean, saving: Boolean,
    onDismiss: () -> Unit, onSave: () -> Unit, onExport: () -> Unit, onEdit: () -> Unit,
    onRemoveSaved: () -> Unit, onSources: () -> Unit, onCalibrate: () -> Unit, onSongOptions: () -> Unit) {
    val strings = LocalStrings.current
    AkiraModalSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState(), flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()).padding(24.dp)) {
            Text(strings.ui(AkiraUiText.LyricsOptions), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            if (saved) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(AkiraIcons.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(strings.ui(AkiraUiText.LyricsSavedOffline), Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onRemoveSaved) { Text(strings.ui(AkiraUiText.RemoveSavedLyrics)) }
                }
            } else LyricsOption(AkiraIcons.Download, strings.ui(AkiraUiText.SaveLyricsOffline), strings.ui(AkiraUiText.SaveLyricsHint), hasLyrics && !saving, onSave)
            LyricsOption(AkiraIcons.Edit, strings.ui(if (hasLyrics) AkiraUiText.EditLyrics else AkiraUiText.AddLyrics), strings.ui(AkiraUiText.EditLyricsHint), true, onEdit)
            LyricsOption(AkiraIcons.Share, strings.ui(AkiraUiText.ExportLyricsFile), strings.ui(AkiraUiText.ExportLyricsHint), hasLyrics, onExport)
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
            LyricsOption(AkiraIcons.Lyrics, strings.ui(AkiraUiText.LyricsSourcesTitle), strings.ui(AkiraUiText.SourceActionHint), true, onSources)
            LyricsOption(AkiraIcons.SleepTimer, strings.calibrateSync, strings.ui(AkiraUiText.CalibrationActionHint), synced, onCalibrate)
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
            LyricsOption(AkiraIcons.MoreHoriz, strings.trackOptions, "", true, onSongOptions)
        }
    }
}

@Composable
private fun LyricsOption(icon: ImageVector, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) .85f else .3f))
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .4f))
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .4f))
            }
        }
    }
}
