package com.akira.app.feature.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.akira.app.core.datastore.UserPreferences
import com.akira.app.core.design.brand.AkiraBrand
import com.akira.app.core.design.components.localizedText
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.model.Song

/** A listening invitation built from the collection the user chose to keep. */
@Composable internal fun SetupReadyCollection(songs: List<Song>, prefs: UserPreferences) {
    val covers = remember(songs) { songs.filter { !it.albumArtUri.isNullOrBlank() }.distinctBy { it.albumArtUri }.take(4) }
    val albums = remember(songs) { songs.map { if(it.albumId != 0L) it.albumId.toString() else "${it.album}/${it.artist}" }.distinct().size }
    val artists = remember(songs) { songs.map { it.artist.trim().lowercase(java.util.Locale.ROOT) }.filter { it.isNotBlank() }.distinct().size }
    SetupEntrance {
        Box(Modifier.fillMaxWidth().height(248.dp).clip(RoundedCornerShape(30.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
            if(covers.isNotEmpty()) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                covers.chunked(2).forEach { column ->
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        column.forEach { song ->
                            AsyncImage(song.albumArtUri, null, Modifier.fillMaxWidth().weight(1f), contentScale = ContentScale.Crop)
                        }
                    }
                }
            } else SetupEclipse(Modifier.size(120.dp))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                listOf(Color.Black.copy(alpha=.02f), Color.Black.copy(alpha=.12f), Color.Black.copy(alpha=.84f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(localizedText("YOUR COLLECTION", "TU COLECCIÓN"), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha=.8f))
                Text(if(songs.isEmpty()) localizedText("No songs added", "No se han añadido canciones")
                    else localizedText("${songs.size} songs", "${songs.size} canciones"),
                    style = MaterialTheme.typography.headlineSmall, color = Color.White)
            }
            Box(Modifier.align(Alignment.TopEnd).padding(16.dp).size(42.dp)
                .background(AkiraBrand.accent, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                SetupEclipse(Modifier.size(26.dp), Color.White)
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        ReadyCount(albums, localizedText("albums", "álbumes"), Modifier.weight(1f))
        ReadyCount(artists, localizedText("artists", "artistas"), Modifier.weight(1f))
    }
    SetupCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AkiraIcons.Check, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(localizedText("Settings saved", "Ajustes guardados"), style = MaterialTheme.typography.titleMedium)
                val theme = when(prefs.themeMode) {
                    "LIGHT" -> localizedText("Light", "Claro")
                    "DARK", "AMOLED" -> localizedText("Dark", "Oscuro")
                    else -> localizedText("System appearance", "Tema del sistema")
                }
                val sound = if(prefs.crossfadeSeconds > 0) "Crossfade · ${prefs.crossfadeSeconds} s"
                    else localizedText("Crossfade off", "Crossfade desactivado")
                Text("$theme · $sound", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Text(localizedText("Music stored on this phone is available offline.", "La música de este teléfono está disponible sin conexión."),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun ReadyCount(count: Int, label: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(count.toString(), style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
