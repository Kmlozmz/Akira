package com.akira.app.feature.settings

import kotlinx.coroutines.flow.first
import com.akira.app.core.audio.EqOutputProfile

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akira.app.core.audio.OutputProfiles
import com.akira.app.core.audio.OutputRoutes
import com.akira.app.core.datastore.UserPreferences
import com.akira.app.core.datastore.UserPreferencesRepository
import com.akira.app.core.design.components.AkiraSegmentedControl
import com.akira.app.core.design.components.AkiraSwitch
import com.akira.app.core.design.util.AkiraHaptics
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
internal fun EqualizerScreen(prefs: UserPreferences, haptics: AkiraHaptics, onBack: () -> Unit,
    onEnabled: (Boolean) -> Unit, onPreset: (EqPresetDef) -> Unit, onBand: (Int, Float) -> Unit,
    onBass: (Float) -> Unit, onSpace: (Float) -> Unit, onPreamp: (Float) -> Unit, onReset: () -> Unit) {
    val strings = com.akira.app.core.i18n.LocalStrings.current
    var editingProfile by remember { mutableStateOf<EqOutputProfile?>(null) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var showProfiles by rememberSaveable { mutableStateOf(false) }
    val output by OutputRoutes.current.collectAsStateWithLifecycle()
    val fxSupport by com.akira.app.core.audio.AudioFxAvailability.current.collectAsStateWithLifecycle()
    val assigned = OutputProfiles.assignments(prefs.eqAssignmentsJson)
    val profile = OutputProfiles.decode(prefs.eqProfilesJson).firstOrNull { it.id == assigned[output.encoded] }
    val context = LocalContext.current
    val repo = remember(context) { UserPreferencesRepository(context) }
    val scope = rememberCoroutineScope()
    val matching = EqPresets.firstOrNull { preset ->
        preset.gains.indices.all { kotlin.math.abs(preset.gains[it] - prefs.eqBandGains.getOrElse(it) { 0f }) < .001f } &&
            kotlin.math.abs(preset.bass - prefs.bassBoost) < .001f && kotlin.math.abs(preset.space - prefs.virtualizer) < .001f
    }
    val options = EqPresets.map { preset ->
        val (title, detail) = when(preset.name) {
            "Equilibrado" -> com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Flat) to com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.OriginalSound)
            "Graves" -> com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Bass) to com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.StrongerBass)
            "Voces" -> com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Vocals) to com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.ClearerVocals)
            "Dinámico" -> com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Dynamic) to com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.BoostedBassAndTreble)
            "Electrónica" -> com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Electronic) to com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.BeatsAndDetail)
            else -> com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.RockPreset) to com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.GuitarsAndRhythm)
        }
        AudioPresetOption(preset.name, title, detail)
    }
    editingProfile?.let { original ->
        EqProfileEditor(original, onDismiss = { editingProfile = null }, onSave = { updated ->
            scope.launch {
                val latest = repo.userPreferences.first()
                val profiles = OutputProfiles.decode(latest.eqProfilesJson)
                repo.setOutputProfiles(OutputProfiles.encode(profiles.map { if (it.id == original.id) updated else it }), latest.eqAssignmentsJson)
                editingProfile = null
            }
        })
    }
    val enableLabel = com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.EnableEqualizer)
    val requestBack: () -> Unit = {
        when {
            editingProfile != null -> editingProfile = null
            showProfiles -> showProfiles = false
            else -> onBack()
        }
    }
    AkiraStudioScreen(com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Equalizer), requestBack,
        trailing = { AkiraSwitch(prefs.eqEnabled, onCheckedChange = onEnabled, enabled = fxSupport.equalizer != false,
            modifier = Modifier.semantics { contentDescription = enableLabel }) }) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(com.akira.app.core.design.navigation.LocalPageScrollState.current ?: rememberScrollState(), flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()).padding(top=118.dp).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AudioPresetPicker(options, matching?.name.orEmpty()) { id ->
                EqPresets.firstOrNull { it.name == id }?.let { haptics.tick(); onPreset(it) }
            }
            EqResponseGraph(prefs.eqBandGains, androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
            AkiraSegmentedControl(listOf("basic" to com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Presets), "advanced" to com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Advanced)),
                if (advanced) "advanced" else "basic", { advanced = it == "advanced" }, androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer)
            if (profile != null) {
                Text(strings.activeOutputProfile.format(profile.name),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .8f), style = MaterialTheme.typography.bodyMedium)
                AudioActionButtons(strings.editProfile, strings.useGlobalSettings,
                    onReset = { editingProfile = profile }, onAction = { scope.launch {
                        val latest = repo.userPreferences.first()
                        val mappings = OutputProfiles.assignments(latest.eqAssignmentsJson)
                        repo.setOutputProfiles(latest.eqProfilesJson, JSONObject(mappings - output.encoded).toString())
                    } })
            }
            if (advanced) {
                Column {
                    repeat(5) { index ->
                        AudioValueControl(com.akira.app.core.i18n.LocalStrings.current.eqBandLabel(index), prefs.eqBandGains.getOrElse(index) { 0f },
                            -12f..12f, .5f, { strings.ui(com.akira.app.core.i18n.AkiraUiText.GainFormat, it) }, { onBand(index, it) }, enabled = fxSupport.equalizer != false)
                    }
                }
                HorizontalDivider(color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
                Text(com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.LevelAndEffects), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                AudioValueControl(com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Preamp), (20f * log10(prefs.eqPreamp.coerceIn(.25f, 1f))).coerceIn(-12f, 0f),
                    -12f..0f, .5f, { strings.ui(com.akira.app.core.i18n.AkiraUiText.GainFormat, it) }, { onPreamp(10f.pow(it / 20f)) })
                AudioValueControl(com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Bass), prefs.bassBoost, 0f..1f, .01f,
                    { "${(it * 100).roundToInt()}%" }, onBass, inputScale = 100f, enabled = fxSupport.bass != false)
                AudioValueControl(com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Space2), prefs.virtualizer, 0f..1f, .01f,
                    { "${(it * 100).roundToInt()}%" }, onSpace, inputScale = 100f, enabled = fxSupport.space != false)
                TextButton(onClick = { onPreamp(10f.pow(-(prefs.eqBandGains.maxOrNull() ?: 0f).coerceAtLeast(0f) / 20f).coerceAtLeast(.25f)) }) {
                    Text(com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.AutoSetHeadroom), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                }
            } else {
                Text(com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.UseTheArrowsToSwitchPresetsOr),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), style = MaterialTheme.typography.bodyMedium)
            }
            val unavailable = listOfNotNull(
                if (fxSupport.equalizer == false) com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Equalizer) else null,
                if (fxSupport.bass == false) com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Bass) else null,
                if (fxSupport.space == false) com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Space2) else null)
            if (unavailable.isNotEmpty()) Text(unavailable.joinToString(", ") + " · " +
                com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.NotSupportedOnThisOutput),
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .65f), style = MaterialTheme.typography.bodyMedium)
            Text(com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.StartWithFlatWhenBoostingBandsUse),
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .65f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            AudioActionButtons(com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Reset), com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.OutputProfiles), onReset, { showProfiles = true })
            if (showProfiles) OutputProfilesSheet { showProfiles = false }
        }
    }
}

@Composable
private fun EqResponseGraph(gains: List<Float>, accent: Color) {
    val displayedGains = (0..4).map { index ->
        animateFloatAsState(gains.getOrElse(index) { 0f }, tween(220), label = "eq_band_$index").value
    }
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
    val gainLabels = listOf("+12", "+6", "0", "−6", "−12").map { measurer.measure(it, labelStyle) }
    val frequencyLabels = (0..4).map { measurer.measure(com.akira.app.core.i18n.LocalStrings.current.eqBandLabel(it), labelStyle) }
    val description = com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.GainCurveByFrequency) + ": " +
        (0..4).map { index ->
            val label = com.akira.app.core.i18n.LocalStrings.current.eqBandLabel(index)
            com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.GainDetailFormat, label, gains.getOrElse(index) { 0f })
        }.joinToString(", ")
    val plotInk = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxWidth().height(196.dp).semantics { contentDescription = description }) {
        val left = 36.dp.toPx()
        val right = size.width - 20.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = size.height - 32.dp.toPx()
        val width = (right - left).coerceAtLeast(1f)
        val height = bottom - top
        val zero = top + height / 2f
        gainLabels.forEachIndexed { index, label ->
            val y = top + height * index / 4f
            drawLine(plotInk.copy(alpha = if (index == 2) .3f else .1f), Offset(left, y), Offset(right, y), 1.dp.toPx())
            drawText(label, topLeft = Offset(left - label.size.width - 8.dp.toPx(), y - label.size.height / 2f))
        }
        val points = (0..4).map { index ->
            val x = left + width * index / 4f
            val y = zero - displayedGains[index].coerceIn(-12f, 12f) / 12f * height / 2f
            drawLine(plotInk.copy(alpha = .08f), Offset(x, top), Offset(x, bottom))
            val label = frequencyLabels[index]
            drawText(label, topLeft = Offset((x - label.size.width / 2f).coerceIn(0f,
                (size.width - label.size.width).coerceAtLeast(0f)), bottom + 10.dp.toPx()))
            Offset(x, y)
        }
        val curve = Path().apply {
            points.forEachIndexed { index, point ->
                if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
            }
        }
        val fill = Path().apply {
            addPath(curve)
            lineTo(right, zero)
            lineTo(left, zero)
            close()
        }
        drawPath(fill, accent.copy(alpha = .08f))
        drawPath(curve, accent, style = Stroke(2.dp.toPx()))
        points.forEach { point ->
            drawCircle(accent.copy(alpha = .18f), 7.dp.toPx(), point)
            drawCircle(accent, 3.5.dp.toPx(), point)
        }
    }
}
