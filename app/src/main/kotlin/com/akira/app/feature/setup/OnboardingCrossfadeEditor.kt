package com.akira.app.feature.setup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.akira.app.core.datastore.UserPreferences
import com.akira.app.core.design.components.localizedText
import com.akira.app.core.media.*
import com.akira.app.feature.settings.ExpressiveAudioSlider
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** A silent visual demonstration using the engine's configured gain curves. */
@Composable internal fun OnboardingCrossfadeEditor(prefs: UserPreferences, onCommit: (Int) -> Unit) {
    var draft by remember { mutableFloatStateOf(prefs.crossfadeSeconds.coerceIn(1, 30).toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(prefs.crossfadeSeconds) { if (!dragging && prefs.crossfadeSeconds > 0) draft = prefs.crossfadeSeconds.toFloat().coerceIn(1f, 30f) }
    val outgoing = remember(prefs.crossfadeOutShape, prefs.crossfadeOutCurvature, prefs.crossfadeOutStart, prefs.crossfadeOutEnd) {
        runCatching { CurveSpec(CrossfadeShape.valueOf(prefs.crossfadeOutShape), prefs.crossfadeOutCurvature, prefs.crossfadeOutStart, prefs.crossfadeOutEnd) }
            .getOrDefault(CrossfadeCurve.DEFAULT_OUTGOING_SPEC)
    }
    val incoming = remember(prefs.crossfadeInShape, prefs.crossfadeInCurvature, prefs.crossfadeInStart, prefs.crossfadeInEnd) {
        runCatching { CurveSpec(CrossfadeShape.valueOf(prefs.crossfadeInShape), prefs.crossfadeInCurvature, prefs.crossfadeInStart, prefs.crossfadeInEnd) }
            .getOrDefault(CrossfadeCurve.DEFAULT_INCOMING_SPEC)
    }
    val phase = onboardingPhase(6_000)
    val first = MaterialTheme.colorScheme.primary
    val second = MaterialTheme.colorScheme.onSurface
    val seconds = draft.roundToInt()
    val description = localizedText("Visual crossfade preview, $seconds seconds", "Vista previa visual del crossfade, $seconds segundos")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(localizedText("Current song", "Canción actual"), style = MaterialTheme.typography.labelMedium, color = first)
            Text(localizedText("Next song", "Siguiente canción"), style = MaterialTheme.typography.labelMedium, color = second.copy(alpha = .7f))
        }
        Canvas(Modifier.fillMaxWidth().height(112.dp).semantics { contentDescription = description }) {
            val overlap = .08f + (seconds / 30f) * .52f
            val begin = .5f - overlap / 2; val end = .5f + overlap / 2
            drawRoundRect(first.copy(alpha = .07f), Offset(begin * size.width, 0f), Size(overlap * size.width, size.height), CornerRadius(12.dp.toPx()))
            val cursor = phase.value / 6.283185f
            repeat(70) { i ->
                val x = (i + .5f) / 70
                val progress = ((x - begin) / overlap).coerceIn(0f, 1f)
                val gainOut = CrossfadeCurve.gain(CrossfadeDirection.OUTGOING, outgoing, progress)
                val gainIn = CrossfadeCurve.gain(CrossfadeDirection.INCOMING, incoming, progress)
                val wave = .4f + .6f * abs(sin(i * .9f))
                val amplitude = size.height * .19f * wave
                val alpha = if (x <= cursor) 1f else .4f
                val px = x * size.width
                if (gainOut > .01f) drawLine(first.copy(alpha = alpha), Offset(px, size.height * .27f - amplitude * gainOut),
                    Offset(px, size.height * .27f + amplitude * gainOut), 2.dp.toPx(), StrokeCap.Round)
                if (gainIn > .01f) drawLine(second.copy(alpha = alpha * .6f), Offset(px, size.height * .75f - amplitude * gainIn),
                    Offset(px, size.height * .75f + amplitude * gainIn), 2.dp.toPx(), StrokeCap.Round)
            }
            drawLine(second.copy(alpha = .2f), Offset(cursor * size.width, 0f), Offset(cursor * size.width, size.height), 1.dp.toPx())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(localizedText("Duration", "Duración"), style = MaterialTheme.typography.bodyMedium)
            Text("$seconds s", style = MaterialTheme.typography.titleMedium)
        }
        ExpressiveAudioSlider(draft, { draft = it; dragging = true }, 1f..30f,
            localizedText("Crossfade duration", "Duración del crossfade"), steps = 28,
            onFinished = { dragging = false; onCommit(draft.roundToInt()) })
    }
}
