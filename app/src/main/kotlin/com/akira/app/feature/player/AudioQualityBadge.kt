package com.akira.app.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.app.core.design.theme.AkiraFontFamily
import com.akira.app.core.design.theme.DarkColorScheme
import com.akira.app.core.data.readAudioQualityInfo
import com.akira.app.core.design.components.AkiraPresentation
import com.akira.app.core.design.components.AkiraPresentationKind
import com.akira.app.core.design.components.localizedText
import com.akira.app.core.design.components.rememberAkiraMenuSource
import com.akira.app.core.design.util.rememberAkiraHaptics
import com.akira.app.core.i18n.LocalStrings
import com.akira.app.core.media.AudioQuality
import com.akira.app.core.media.AudioQualityInfo
import com.akira.app.core.model.Song

private val LosslessSymbol: ImageVector by lazy {
    ImageVector.Builder("LosslessWave", 40.dp, 26.dp, 40f, 26f).apply {
        path(stroke = SolidColor(Color.White), strokeLineWidth = 2.1f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            for (offset in listOf(0f, 7f, 14f)) {
                moveTo(2f + offset, 14f)
                curveTo(3f + offset, 6f, 5f + offset, 3f, 8f + offset, 3f)
                curveTo(13f + offset, 3f, 13f + offset, 23f, 19f + offset, 23f)
                curveTo(22f + offset, 23f, 24f + offset, 21f, 25f + offset, 14f)
            }
        }
    }.build()
}

@Composable
internal fun AudioQualityBadge(song: Song, initialInfo: AudioQualityInfo, onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val info by produceState(initialInfo, initialInfo, song.contentUri, song.path, song.dateModifiedSec) {
        value = initialInfo
        value = readAudioQualityInfo(context, song, initialInfo)
    }
    val label = if (info.quality == AudioQuality.DOLBY_ATMOS) "Dolby Atmos" else LocalStrings.current.lossless
    val symbol = if (info.quality == AudioQuality.DOLBY_ATMOS) Icons.Rounded.SurroundSound else LosslessSymbol
    val source = rememberAkiraMenuSource(icon = null, hideOnOpen = false, hasOwnSurface = true)
    val haptics = rememberAkiraHaptics()
    var showDetails by remember(song.contentUri) { mutableStateOf(false) }
    Row(modifier.then(source.modifier).clip(RoundedCornerShape(4.dp))
        .background(Color.White.copy(alpha = .10f))
        .clickable(role = Role.Button) {
            haptics.light()
            source.open { showDetails = true }
        }.padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(symbol, contentDescription = null, modifier = Modifier.size(16.dp, 12.dp),
            tint = Color.White.copy(alpha = .65f))
        Text(label, color = Color.White.copy(alpha = .65f), fontFamily = AkiraFontFamily,
            fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (showDetails) MaterialTheme(colorScheme = DarkColorScheme) {
        AkiraPresentation(AkiraPresentationKind.AudioQuality, onDismiss = { showDetails = false }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .semantics { paneTitle = label }.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp))
                Icon(symbol, contentDescription = null, modifier = Modifier.size(64.dp, 40.dp),
                    tint = Color.White.copy(alpha = .8f))
                Spacer(Modifier.height(44.dp))
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                    Text(label, color = Color.White, fontFamily = AkiraFontFamily,
                        fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(info.description.ifBlank { localizedText("Audio details unavailable", "Datos de audio no disponibles") },
                        color = Color.White.copy(alpha = .8f), fontFamily = AkiraFontFamily,
                        fontSize = 15.sp, lineHeight = 20.sp)
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = { showDetails = false; onOpenSettings() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = .13f), contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(localizedText("Audio Quality Settings", "Ajustes de calidad de audio"),
                        fontFamily = AkiraFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { showDetails = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                    Text("OK", fontFamily = AkiraFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
