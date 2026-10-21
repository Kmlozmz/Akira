package com.akira.app.feature.library

import com.akira.app.core.artwork.*
import com.akira.app.feature.lyrics.*
import com.akira.app.feature.player.*
import com.akira.app.feature.queue.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.akira.app.core.design.components.AkiraModalSheet
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.i18n.LocalStrings
import com.akira.app.core.i18n.StringsSpanish
import com.akira.app.core.model.Song

@Composable
internal fun DeleteSongDialog(song: Song, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val strings = LocalStrings.current
    val es = strings == StringsSpanish
    val danger = Color(0xFFFF9B95)
    AkiraModalSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState(), flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()).padding(horizontal = 24.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(danger.copy(alpha = .12f)),
                    contentAlignment = Alignment.Center) {
                    Icon(AkiraIcons.Trash, null, tint = danger, modifier = Modifier.size(22.dp))
                }
                Text(strings.ui(com.akira.app.core.i18n.AkiraUiText.DeleteSongTitle), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .05f))
                .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .06f)),
                    contentAlignment = Alignment.Center) {
                    Icon(AkiraIcons.Album, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), modifier = Modifier.size(24.dp))
                    AsyncImage(song.albumArtUri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(song.title, style = MaterialTheme.typography.titleMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .7f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(strings.ui(com.akira.app.core.i18n.AkiraUiText.DeleteSongExplanation),
                style = MaterialTheme.typography.bodyMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .85f),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(danger.copy(alpha = .07f))
                    .padding(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .08f), contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)) { Text(strings.cancel) }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB93636), contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)) {
                    Text(strings.continueBtn)
                }
            }
        }
    }
}
