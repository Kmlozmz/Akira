package com.akira.app.feature.player

import com.akira.app.feature.library.*
import com.akira.app.feature.lyrics.*
import com.akira.app.feature.queue.*

import com.akira.app.core.design.components.AkiraModalSheet

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.app.core.design.theme.AkiraFontFamily
import com.akira.app.core.design.util.rememberAkiraHaptics
import com.akira.app.core.i18n.LocalStrings
import com.akira.app.core.media.SleepTimerBus
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SleepTimerSheet(
    onDismiss: () -> Unit,
    onStartTimer: (Long) -> Unit,
    onCancelTimer: () -> Unit
) {
    val strings = LocalStrings.current
    val haptics = rememberAkiraHaptics()


    val remainingMs by SleepTimerBus.remainingMs.collectAsStateWithLifecycle()
    val activeMode by SleepTimerBus.activeMode.collectAsStateWithLifecycle()

    val presetMinutes = if (com.akira.app.BuildConfig.DEBUG) listOf(1, 5, 10, 15, 30, 45, 60, 90)
        else listOf(5, 10, 15, 30, 45, 60, 90)

    AkiraModalSheet(
        onDismissRequest = onDismiss,


    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 6.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bedtime,
                    contentDescription = null,
                    tint = Color(0xFF82B1FF),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = strings.sleepTimer,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AkiraFontFamily,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = strings.sleepTimerDesc,
                fontSize = 13.sp,
                fontFamily = AkiraFontFamily,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Indicador de temporizador activo / cuenta atrás
            if (activeMode != 0L) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1E1E2C))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            val timeText = when (activeMode) {
                                SleepTimerBus.END_OF_SONG -> strings.sleepTimerEndOfSong
                                SleepTimerBus.END_OF_QUEUE -> strings.sleepTimerEndOfQueue
                                else -> {
                                    val totalSec = (remainingMs / 1000L).coerceAtLeast(0L)
                                    val minutes = totalSec / 60L
                                    val seconds = totalSec % 60L
                                    String.format(Locale.US, "%02d:%02d", minutes, seconds)
                                }
                            }
                            Text(
                                text = String.format(strings.sleepTimerActive, timeText),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AkiraFontFamily,
                                color = Color(0xFF82B1FF)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                .clickable {
                                    haptics.tick()
                                    onCancelTimer()
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = null,
                                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = strings.cancel,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = AkiraFontFamily,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
            }

            // Chips de tiempo (5, 10, 15, 30, 45, 60, 90 min)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presetMinutes.forEach { mins ->
                    val isSelected = activeMode == mins * 60_000L
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0xFF82B1FF) else androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            .clickable {
                                haptics.confirm()
                                onStartTimer(mins * 60_000L)
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.SMin, mins),
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = AkiraFontFamily,
                            color = if (isSelected) Color(0xFF14141E) else androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Opciones especiales: Al terminar la canción / Al terminar la cola
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isSongSelected = activeMode == SleepTimerBus.END_OF_SONG
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSongSelected) Color(0xFF82B1FF) else androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        .clickable {
                            haptics.confirm()
                            onStartTimer(SleepTimerBus.END_OF_SONG)
                            onDismiss()
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.sleepTimerEndOfSong,
                        fontSize = 13.sp,
                        fontWeight = if (isSongSelected) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = AkiraFontFamily,
                        color = if (isSongSelected) Color(0xFF14141E) else androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }

                val isQueueSelected = activeMode == SleepTimerBus.END_OF_QUEUE
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isQueueSelected) Color(0xFF82B1FF) else androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        .clickable {
                            haptics.confirm()
                            onStartTimer(SleepTimerBus.END_OF_QUEUE)
                            onDismiss()
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.sleepTimerEndOfQueue,
                        fontSize = 13.sp,
                        fontWeight = if (isQueueSelected) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = AkiraFontFamily,
                        color = if (isQueueSelected) Color(0xFF14141E) else androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
