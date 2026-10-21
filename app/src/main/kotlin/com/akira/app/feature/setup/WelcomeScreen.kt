package com.akira.app.feature.setup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.brand.AkiraBrand
import com.akira.app.core.design.components.localizedText
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.i18n.AkiraUiText
import com.akira.app.core.i18n.LocalStrings
import com.akira.app.core.model.Song
import kotlin.math.sin

@Composable
fun WelcomeScreen(onGetStartedClick: () -> Unit, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    SetupLayout(
        localizedText("Welcome to Akira", "Bienvenido a Akira"),
        localizedText("Add your music, then set up the player.", "Añade tu música y configura el reproductor."),
        modifier = modifier, step = 0,
        caption = localizedText("No account required.", "No necesitas una cuenta."),
        footer = { SetupAction(strings.ui(AkiraUiText.GetStarted), onClick = onGetStartedClick) }
    ) {
        SetupWelcomeHero()
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SetupFeature(AkiraIcons.Album, localizedText("Music on your phone", "Música del teléfono"),
                localizedText("Browse songs, albums and artists, even offline.", "Accede a canciones, álbumes y artistas sin conexión."))
            SetupFeature(SetupAudioIcons.Balanced, localizedText("Player settings", "Ajustes del reproductor"),
                localizedText("Choose the appearance, volume and transitions.", "Elige la apariencia, el volumen y las transiciones."))
        }
    }
}

@Composable private fun SetupFeature(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(23.dp), tint = MaterialTheme.colorScheme.primary) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A periodic sound field surrounds the mark without restarting its position or velocity. */
@Composable internal fun SetupWelcomeHero(songs: List<Song> = emptyList()) {
    if (songs.isNotEmpty()) { SetupCollectionHero(songs); return }
    val phase = setupAmbientPhase(8000)
    SetupEntrance(50) {
        Box(Modifier.fillMaxWidth().height(238.dp).clip(RoundedCornerShape(32.dp)).background(AkiraBrand.accent)
            .clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val baseline = size.height * .64f
                repeat(53) { index ->
                    val x = (index + .5f) / 53f * size.width
                    val envelope = .35f + .65f * kotlin.math.abs(x / size.width - .5f) * 2f
                    val height = (12f + 24f * (.5f + .5f * sin(index * .55f + phase.value * 2f))) * envelope * 1.dp.toPx()
                    drawLine(Color.White.copy(alpha = .22f), Offset(x, baseline - height), Offset(x, baseline + height),
                        3.dp.toPx(), StrokeCap.Round)
                }
            }
            Canvas(Modifier.size(140.dp)) {
                val drift = sin(phase.value) * 4.dp.toPx()
                translate(top = drift) {
                    scale(size.width / 128f, pivot = Offset.Zero) { drawPath(AkiraBrand.symbol, Color.White) }
                }
            }
        }
    }
}

@Composable internal fun SetupCollectionHero(songs: List<Song>) {
    val phase = setupAmbientPhase(10000)
    val covers = remember(songs) { songs.filter { !it.albumArtUri.isNullOrBlank() }.distinctBy { it.albumArtUri }.take(3) }
    Box(Modifier.fillMaxWidth().height(208.dp).clip(RoundedCornerShape(30.dp))
        .background(MaterialTheme.colorScheme.primaryContainer).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Brush.radialGradient(listOf(AkiraBrand.accent.copy(alpha = .16f), Color.Transparent),
                center, size.width * .65f), size.width * .65f, center)
        }
        if (covers.isEmpty()) SetupEclipse(Modifier.size(126.dp))
        else Row(horizontalArrangement = Arrangement.spacedBy((-12).dp), verticalAlignment = Alignment.CenterVertically) {
            covers.forEachIndexed { index, song ->
                coil.compose.AsyncImage(song.albumArtUri, null, Modifier.size(if (index == 1) 132.dp else 112.dp)
                    .graphicsLayer {
                        rotationZ = (index - 1) * 9f
                        translationY = sin(phase.value + index) * 4.dp.toPx()
                    }.clip(RoundedCornerShape(22.dp)), contentScale = ContentScale.Crop)
            }
        }
    }
}
