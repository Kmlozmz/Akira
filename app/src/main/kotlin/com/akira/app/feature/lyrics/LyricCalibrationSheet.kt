package com.akira.app.feature.lyrics

import com.akira.app.core.artwork.*
import com.akira.app.feature.library.*
import com.akira.app.feature.player.*
import com.akira.app.feature.queue.*

import com.akira.app.core.design.components.AkiraModalSheet

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.akira.app.core.i18n.AkiraUiText
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.app.core.design.theme.AkiraFontFamily
import com.akira.app.core.design.util.rememberAkiraHaptics
import com.akira.app.core.i18n.LocalStrings
import com.akira.app.core.lyrics.LyricLine
import java.util.Locale

private const val MIN_OFFSET_MS = -10_000L
private const val MAX_OFFSET_MS = 10_000L

/**
 * Hoja modal para calibración fina del desfase de letras sincronizadas.
 *
 * Permite ajustes rápidos (±0,5s, ±1s) y finos (±50ms) entre -10s y +10s.
 * Muestra vista previa en tiempo real del verso que cambiaría de posición.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricCalibrationSheet(
    initialOffsetMs: Long,
    currentPositionMs: Long,
    lyricLines: List<LyricLine>,
    onDismiss: () -> Unit,
    onOffsetChanged: (Long) -> Unit = {},
    onSaveOffset: (Long) -> Unit
) {
    val strings = LocalStrings.current
    val haptics = rememberAkiraHaptics()
    val colors = MaterialTheme.colorScheme


    var currentOffset by remember(initialOffsetMs) { mutableLongStateOf(initialOffsetMs) }

    fun adjust(deltaMs: Long) {
        haptics.tick()
        currentOffset = (currentOffset + deltaMs).coerceIn(MIN_OFFSET_MS, MAX_OFFSET_MS)
        onOffsetChanged(currentOffset)
    }

    AkiraModalSheet(
        onDismissRequest = onDismiss,


    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState(), flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling())
                .padding(horizontal = 22.dp, vertical = 6.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Título
            Text(
                text = strings.calibrateSyncTitle,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = AkiraFontFamily,
                color = colors.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = strings.ui(AkiraUiText.CalibrationDirectionHint),
                fontSize = 13.sp,
                fontFamily = AkiraFontFamily,
                color = colors.onSurface.copy(alpha = 0.55f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Indicador numérico principal del desfase
            val formattedOffset = strings.ui(AkiraUiText.OffsetDisplay, currentOffset / 1000.0, currentOffset)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceContainerHigh)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formattedOffset,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AkiraFontFamily,
                    color = colors.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Vista previa en tiempo real del verso activo resultante
            val effectiveTime = currentPositionMs + currentOffset
            val previewLine = remember(effectiveTime, lyricLines) {
                if (lyricLines.isEmpty()) ""
                else {
                    val match = lyricLines.lastOrNull { it.timeMs <= effectiveTime }
                    match?.text ?: lyricLines.first().text
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.primary.copy(alpha = .10f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column {
                    Text(
                        text = strings.realtimePreview,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = AkiraFontFamily,
                        color = colors.onSurface.copy(alpha = 0.45f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = previewLine.ifBlank { "—" },
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AkiraFontFamily,
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    lyricLines.firstOrNull { it.timeMs > effectiveTime }?.let { next ->
                        Spacer(Modifier.height(10.dp))
                        Text(strings.ui(AkiraUiText.LyricsNextLine), color = colors.onSurfaceVariant, fontSize = 11.sp)
                        Text(next.text, color = colors.onSurface.copy(alpha = .45f), fontFamily = AkiraFontFamily,
                            fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Controles rápidos: -1.0s, -0.5s, +0.5s, +1.0s
            Text(
                text = strings.quickAdjustment,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = AkiraFontFamily,
                color = colors.onSurface.copy(alpha = 0.50f),
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CalibrationButton(strings.ui(AkiraUiText.OffsetSeconds, -1.0), modifier = Modifier.weight(1f)) { adjust(-1000L) }
                CalibrationButton(strings.ui(AkiraUiText.OffsetSeconds, -0.5), modifier = Modifier.weight(1f)) { adjust(-500L) }
                CalibrationButton(strings.ui(AkiraUiText.OffsetSeconds, 0.5), modifier = Modifier.weight(1f)) { adjust(500L) }
                CalibrationButton(strings.ui(AkiraUiText.OffsetSeconds, 1.0), modifier = Modifier.weight(1f)) { adjust(1000L) }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Controles finos: -50ms, +50ms
            Text(
                text = strings.fineAdjustment,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = AkiraFontFamily,
                color = colors.onSurface.copy(alpha = 0.50f),
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CalibrationButton(strings.ui(AkiraUiText.OffsetMilliseconds, -50), isFine = true, modifier = Modifier.weight(1f)) { adjust(-50L) }
                CalibrationButton(strings.ui(AkiraUiText.OffsetMilliseconds, 50), isFine = true, modifier = Modifier.weight(1f)) { adjust(50L) }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Botones inferiores: Restablecer a 0 y Guardar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceContainerHigh)
                        .clickable {
                            haptics.tick()
                            currentOffset = 0L
                            onOffsetChanged(0L)
                        }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.resetToZero,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = AkiraFontFamily,
                        color = colors.onSurface.copy(alpha = 0.85f)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.primary)
                        .clickable {
                            haptics.confirm()
                            onSaveOffset(currentOffset)
                            onDismiss()
                        }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.save,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AkiraFontFamily,
                        color = colors.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun CalibrationButton(
    label: String,
    isFine: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFine) colors.onSurface.copy(alpha = 0.14f) else colors.surfaceContainerHigh)
            .clickable { onClick() }
            .height(48.dp)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = if (isFine) 14.sp else 13.5.sp,
            fontWeight = if (isFine) FontWeight.Bold else FontWeight.SemiBold,
            fontFamily = AkiraFontFamily,
            color = colors.onSurface
        )
    }
}
