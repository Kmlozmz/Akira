package com.akira.app.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.app.core.design.util.AkiraHaptics
import com.akira.app.core.i18n.LocalStrings
import com.akira.app.core.media.CrossfadeCurve
import com.akira.app.core.media.CrossfadeDirection
import com.akira.app.core.media.CrossfadeShape
import com.akira.app.core.media.CurveSpec
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.conflate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrossfadeCurveScreen(
    activePresetId: String,
    outgoingSpec: CurveSpec,
    incomingSpec: CurveSpec,
    crossfadeSeconds: Int,
    haptics: AkiraHaptics,
    onDismiss: () -> Unit,
    onSaveCurveConfig: (presetId: String, outSpec: CurveSpec, inSpec: CurveSpec) -> Unit,
    onTestCrossfade: () -> Unit,
    onDurationChange: (Int) -> Unit,
    lastCrossfadeSeconds: Int = 8
) {
    val strings = LocalStrings.current
    val curveColors = audioCurvePalette(MaterialTheme.colorScheme.background)
    var selectedPreset by remember { mutableStateOf(activePresetId) }
    var currentOutSpec by remember { mutableStateOf(outgoingSpec) }
    var currentInSpec by remember { mutableStateOf(incomingSpec) }

    val saveLatest by rememberUpdatedState(onSaveCurveConfig)
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(selectedPreset, currentOutSpec, currentInSpec) }
            .drop(1).conflate().collect { (preset, outgoing, incoming) ->
                saveLatest(preset, outgoing, incoming)
                kotlinx.coroutines.delay(70)
            }
    }
    var preview by remember { mutableFloatStateOf(.5f) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var lastDuration by rememberSaveable { mutableIntStateOf(crossfadeSeconds.takeIf { it > 0 } ?: lastCrossfadeSeconds) }
    LaunchedEffect(crossfadeSeconds) { if (crossfadeSeconds > 0) lastDuration = crossfadeSeconds }
    fun updateSpecs(newOut: CurveSpec, newIn: CurveSpec, newPreset: String? = null) {
        currentOutSpec = newOut
        currentInSpec = newIn
        val calculatedPreset = newPreset ?: CrossfadeCurve.detectPreset(newOut, newIn)
        selectedPreset = CrossfadeCurve.canonicalPresetId(calculatedPreset)

    }

    val options = listOf(
        AudioPresetOption(CrossfadeCurve.PRESET_CONSTANT_POWER, com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Balanced), com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.EvenVolume)),
        AudioPresetOption(CrossfadeCurve.PRESET_SMOOTH, com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Smooth), com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.GentleEntrance)),
        AudioPresetOption(CrossfadeCurve.PRESET_LINEAR, com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Direct), com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.LinearBlend)),
        AudioPresetOption(CrossfadeCurve.PRESET_FAST_START_SLOW_END, com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Energetic), com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.FastInGradualOut))
    )
    AkiraStudioScreen(strings.transitionsTitle, onBack = {
        onSaveCurveConfig(selectedPreset, currentOutSpec, currentInSpec)
        onDismiss()
    }) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(com.akira.app.core.design.navigation.LocalPageScrollState.current ?: rememberScrollState(), flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()).padding(top=118.dp).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            com.akira.app.core.design.components.AkiraSegmentedControl(
                listOf("GAPLESS" to strings.transitionGapless, "CROSSFADE" to strings.transitionCrossfade),
                if (crossfadeSeconds == 0) "GAPLESS" else "CROSSFADE", { mode ->
                    haptics.tick()
                    onDurationChange(com.akira.app.core.media.TransitionMode.durationFor(mode, lastDuration))
                }, androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer)
            if (crossfadeSeconds == 0) Text(strings.transitionGaplessDescription,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .7f), style = MaterialTheme.typography.bodyLarge)
            else {
            AudioPresetPicker(options, selectedPreset) { id ->
                CrossfadeCurve.PRESETS[id]?.let { haptics.tick(); updateSpecs(it.outgoingSpec, it.incomingSpec, id) }
            }
            CrossfadeCurveChart(currentOutSpec, currentInSpec, preview, advanced,
                Modifier.fillMaxWidth().height(220.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(strings.outgoingCurve, color = curveColors.outgoing, style = MaterialTheme.typography.bodyMedium)
                Text(strings.incomingCurve, color = curveColors.incoming, style = MaterialTheme.typography.bodyMedium)
            }
            var duration by remember(crossfadeSeconds) { mutableFloatStateOf(crossfadeSeconds.toFloat()) }
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Duration), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
                    Text(com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.SS3, duration.roundToInt()), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                }
                AudioSlider(duration, { duration = it }, 1f..30f, com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.CrossfadeDuration),
                    Modifier.fillMaxWidth(), steps = 28, onFinished = { haptics.tick(); onDurationChange(duration.roundToInt()) })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(strings.secondsFormat.format(1), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), style = MaterialTheme.typography.bodyMedium)
                    Text(com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Value30S), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), style = MaterialTheme.typography.bodyMedium)
                }
            }
            com.akira.app.core.design.components.AkiraSegmentedControl(
                listOf("basic" to com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Presets), "advanced" to com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Advanced)),
                if (advanced) "advanced" else "basic", { advanced = it == "advanced" }, androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer)
            if (advanced) {
                Column {
                    AudioSlider(preview, { preview = it }, 0f..1f, com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.MixPosition), Modifier.fillMaxWidth())
                    val outGain = CrossfadeCurve.gain(CrossfadeDirection.OUTGOING, currentOutSpec, preview)
                    val inGain = CrossfadeCurve.gain(CrossfadeDirection.INCOMING, currentInSpec, preview)
                    Text(com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Value2fSS0fS0f).format(preview * crossfadeSeconds,
                        strings.outgoingCurve, outGain * 100, strings.incomingCurve, inGain * 100),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .7f), style = MaterialTheme.typography.bodyMedium)
                }
                if (CrossfadeCurve.hasPowerAnomaly(currentOutSpec, currentInSpec)) {
                    Text(strings.powerAnomalyWarning, color = curveColors.power, style = MaterialTheme.typography.bodyMedium)
                }
                Text(strings.outgoingCurve, color = curveColors.outgoing, style = MaterialTheme.typography.titleMedium)
                CurveControls(currentOutSpec, CrossfadeDirection.OUTGOING, crossfadeSeconds, haptics) { updateSpecs(it, currentInSpec) }
                Text(strings.incomingCurve, color = curveColors.incoming, style = MaterialTheme.typography.titleMedium)
                CurveControls(currentInSpec, CrossfadeDirection.INCOMING, crossfadeSeconds, haptics) { updateSpecs(currentOutSpec, it) }
            }
            Text(com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.BalancedKeepsPowerSteadyTry38),
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .65f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            }
        }
        if (crossfadeSeconds > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            AudioActionButtons(strings.library.reset, strings.testCrossfade, onReset = {
                CrossfadeCurve.PRESETS[CrossfadeCurve.PRESET_CONSTANT_POWER]?.let { updateSpecs(it.outgoingSpec, it.incomingSpec, it.id) }
            }, onAction = {
                onSaveCurveConfig(selectedPreset, currentOutSpec, currentInSpec); onTestCrossfade()
            }, actionEnabled = crossfadeSeconds > 0)
        }
    }
}

@Composable
private fun CurveControls(
    spec: CurveSpec,
    direction: CrossfadeDirection,
    seconds: Int,
    haptics: AkiraHaptics,
    onSpecChange: (CurveSpec) -> Unit
) {
    val strings = LocalStrings.current
    var choosingShape by remember { mutableStateOf(false) }
    val source=com.akira.app.core.design.components.rememberAkiraMenuSource(icon=Icons.Rounded.KeyboardArrowDown)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        val shapes = listOf(
            CrossfadeShape.LINEAR to strings.shapeLinear,
            CrossfadeShape.CONSTANT_POWER to strings.shapeConstantPower,
            CrossfadeShape.SMOOTH to strings.shapeSmooth,
            CrossfadeShape.FAST_START to strings.shapeFastStart,
            CrossfadeShape.SLOW_START to strings.shapeSlowStart
        )
        val selectedShape = if (spec.shape == CrossfadeShape.CURRENT) CrossfadeShape.CONSTANT_POWER else spec.shape
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(strings.curveShape, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .7f), modifier = Modifier.weight(1f))
            TextButton(onClick = { source.open { choosingShape = true } },modifier=Modifier.then(source.modifier)) {
                Text(shapes.firstOrNull { it.first == selectedShape }?.second.orEmpty(), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
            }
        }
        if (choosingShape) com.akira.app.core.design.components.AkiraModalSheet(onDismissRequest = { choosingShape = false }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState(), flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()).padding(horizontal = 24.dp)) {
                Text(strings.curveShape, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp))
                shapes.forEach { (shape, name) ->
                    TextButton(onClick = { haptics.tick(); onSpecChange(spec.copy(shape = shape)); choosingShape = false },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics {
                            selected = shape == selectedShape; role = Role.RadioButton
                        }, contentPadding = PaddingValues(0.dp)) {
                        Text(name, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                        if (shape == selectedShape) Icon(Icons.Rounded.Check, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Curvatura (0% a 100% en pasos de 5%)
        val curvaturePercent = (spec.curvature * 100f).roundToInt()
        val curvatureSteps = remember { (0..100 step 1).toList() }
        PrecisionPercentControl(
            label = strings.curveCurvature,
            steps = curvatureSteps,
            value = curvaturePercent,
            onValueChange = { percent: Int ->
                onSpecChange(spec.copy(curvature = percent / 100f))
            },
            haptics = haptics,
            valueLabel = { "$it%" }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Rango de mezcla (Inicio y Fin con separación mínima de 10%)
        val startPercent = (spec.startRange * 100f).roundToInt()
        val endPercent = (spec.endRange * 100f).roundToInt()
        val startSteps = remember { (0..90 step 1).toList() }
        val endSteps = remember { (10..100 step 1).toList() }

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                PrecisionPercentControl(
                    label = strings.curveRangeStart,
                    steps = startSteps,
                    value = startPercent,
                    onValueChange = { newStart: Int ->
                        val startF = newStart / 100f
                        val endF = maxOf(spec.endRange, startF + 0.10f).coerceIn(0.1f, 1.0f)
                        onSpecChange(spec.copy(startRange = startF, endRange = endF))
                    },
                    haptics = haptics,
                    valueLabel = { "%d%% · %.2f s".format(it, it * seconds / 100f) }
                )
            }
            Box(modifier = Modifier.fillMaxWidth()) {
                PrecisionPercentControl(
                    label = strings.curveRangeEnd,
                    steps = endSteps,
                    value = endPercent,
                    onValueChange = { newEnd: Int ->
                        val endF = newEnd / 100f
                        val startF = minOf(spec.startRange, endF - 0.10f).coerceIn(0.0f, 0.9f)
                        onSpecChange(spec.copy(startRange = startF, endRange = endF))
                    },
                    haptics = haptics,
                    valueLabel = { "%d%% · %.2f s".format(it, it * seconds / 100f) }
                )
            }
        }
    }
}

@Composable
private fun CrossfadeCurveChart(
    outgoingSpec: CurveSpec,
    incomingSpec: CurveSpec,
    preview: Float,
    showPower: Boolean,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    val plotInk = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    val curveColors = audioCurvePalette(MaterialTheme.colorScheme.background)
    Canvas(
        modifier = modifier
            .padding(14.dp)
            .semantics { contentDescription = strings.graphAccessibilityDesc }
    ) {
        val width = size.width
        val height = size.height

        // Ejes y cuadrícula base (0%, 25%, 50%, 75%, 100%)
        val gridColor = plotInk.copy(alpha = 0.08f)
        for (i in 0..4) {
            val y = height * (i / 4f)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
            val x = width * (i / 4f)
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1f
            )
        }

        // Línea punteada de 1.0 (nivel nominal de potencia)
        val yNominal = height * 0.5f // Si mapeamos potencia de 0.0 a 2.0, 1.0 está en el centro
        if (showPower) drawLine(
            color = plotInk.copy(alpha = 0.18f),
            start = Offset(0f, yNominal),
            end = Offset(width, yNominal),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
        )

        val samples = 100
        val outPath = Path()
        val inPath = Path()
        val powerPath = Path()

        for (i in 0..samples) {
            val t = i / samples.toFloat()
            val x = t * width

            val outGain = CrossfadeCurve.gain(CrossfadeDirection.OUTGOING, outgoingSpec, t)
            val inGain = CrossfadeCurve.gain(CrossfadeDirection.INCOMING, incomingSpec, t)
            val power = (outGain * outGain) + (inGain * inGain) // 0.0 .. 2.0

            val yOut = height * (1f - outGain)
            val yIn = height * (1f - inGain)
            // Potencia mapeada: 1.0 = height * 0.5f, 2.0 = 0f, 0.0 = height
            val yPower = (height * (1f - (power / 2f))).coerceIn(0f, height)

            if (i == 0) {
                outPath.moveTo(x, yOut)
                inPath.moveTo(x, yIn)
                powerPath.moveTo(x, yPower)
            } else {
                outPath.lineTo(x, yOut)
                inPath.lineTo(x, yIn)
                powerPath.lineTo(x, yPower)
            }
        }

        val markerX = preview * width
        if (showPower) drawLine(plotInk.copy(alpha = .6f), Offset(markerX,0f), Offset(markerX,height), strokeWidth = 1.5f)
        if (showPower) drawCircle(curveColors.outgoing, 5.dp.toPx(), Offset(markerX, height*(1f-CrossfadeCurve.gain(CrossfadeDirection.OUTGOING,outgoingSpec,preview))))
        if (showPower) drawCircle(curveColors.incoming, 5.dp.toPx(), Offset(markerX, height*(1f-CrossfadeCurve.gain(CrossfadeDirection.INCOMING,incomingSpec,preview))))
        // Dibujar curvas
        // 1. Potencia total (línea punteada blanca/amarilla)
        if (showPower) drawPath(
            path = powerPath,
            color = curveColors.power,
            style = Stroke(
                width = 2.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
            )
        )

        // 2. Saliente (Naranja)
        drawPath(
            path = outPath,
            color = curveColors.outgoing,
            style = Stroke(width = 3.5f)
        )

        // 3. Entrante (Azul)
        drawPath(
            path = inPath,
            color = curveColors.incoming,
            style = Stroke(width = 3.5f)
        )
    }
}
