package com.akira.app.feature.settings

import kotlinx.coroutines.flow.first
import com.akira.app.core.audio.EqOutputProfile
import com.akira.app.core.audio.effectiveEqualizer

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
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.foundation.shape.RoundedCornerShape
import com.akira.app.core.design.components.localizedText
import com.akira.app.core.design.components.rememberAkiraMenuSource
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
    val output by OutputRoutes.current.collectAsStateWithLifecycle()
    val support by com.akira.app.core.audio.AudioFxAvailability.current.collectAsStateWithLifecycle()
    val effective = remember(prefs, output) { prefs.effectiveEqualizer(output) }
    val configuration = effective.configuration
    var showProfiles by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf("bands") }
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 520
    val fixedHeader = remember(compact) { mutableFloatStateOf(if(compact)1f else 0f) }
    val matching = EqPresets.firstOrNull { preset ->
        preset.gains.indices.all { kotlin.math.abs(preset.gains[it]-configuration.bands[it])<.001f } &&
            kotlin.math.abs(preset.bass-configuration.bass)<.001f && kotlin.math.abs(preset.space-configuration.space)<.001f
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

    val enableLabel = strings.ui(com.akira.app.core.i18n.AkiraUiText.EnableEqualizer)
    val profileSource = rememberAkiraMenuSource(icon = Icons.Rounded.Headphones)
    AkiraStudioScreen(strings.ui(com.akira.app.core.i18n.AkiraUiText.Equalizer),
        onBack={if(showProfiles)showProfiles=false else onBack()}, headerProgress=fixedHeader,
        trailing={AkiraSwitch(prefs.eqEnabled,onEnabled,modifier=Modifier.semantics {contentDescription=enableLabel})}) {
        Column(Modifier.fillMaxSize().padding(top=if(compact)72.dp else 118.dp)
            .audioControlsEnabled(prefs.eqEnabled,localizedText("Enable the equalizer to adjust the sound","Activa el ecualizador para ajustar el sonido"))) {
            Column(Modifier.fillMaxWidth().padding(horizontal=24.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                AudioPreviewSurface {
                    if(!compact) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(options.firstOrNull {it.id==matching?.name}?.title ?: strings.ui(com.akira.app.core.i18n.AkiraUiText.Custom),
                            Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                        Text(if(prefs.eqEnabled)localizedText("On","Activado") else localizedText("Off","Apagado"),
                            style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    EqResponseGraph(configuration.bands,audioAccent(),Modifier.fillMaxWidth().height(if(compact)80.dp else 140.dp))
                }
                TextButton(onClick={profileSource.open {showProfiles=true}},modifier=profileSource.modifier.fillMaxWidth(),
                    contentPadding=PaddingValues(horizontal=4.dp,vertical=8.dp)) {
                    Icon(Icons.Rounded.Headphones,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text(if(effective.profile!=null)strings.activeOutputProfile.format(effective.profile.name)
                        else strings.library.globalProfile,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,
                        color=MaterialTheme.colorScheme.onSurface,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Icon(Icons.Rounded.KeyboardArrowRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AkiraSegmentedControl(listOf("bands" to com.akira.app.core.design.components.localizedText("Bands","Bandas"),
                    "effects" to com.akira.app.core.design.components.localizedText("Effects","Efectos")),tab,{tab=it})
            }
            Column(Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(com.akira.app.core.design.navigation.LocalPageScrollState.current ?: rememberScrollState())
                .padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                AudioPresetRail(options,matching?.name.orEmpty()) {id->
                    EqPresets.firstOrNull {it.name==id}?.let {haptics.tick();onPreset(it)}
                }
                Column(Modifier.fillMaxWidth().padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(if(tab=="bands")localizedText("Frequency bands","Bandas de frecuencia") else localizedText("Sound effects","Efectos de sonido"),
                        Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                    TextButton(onReset) {Text(strings.library.reset,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                }
                if(!prefs.eqEnabled) Text(com.akira.app.core.design.components.localizedText(
                    "The equalizer is off. Your changes are saved for when you enable it.",
                    "El ecualizador está apagado. Los cambios se guardan para cuando lo actives."),
                    style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                key(output.encoded,effective.profile?.id,tab) {
                    if(tab=="bands") {
                        configuration.bands.forEachIndexed {index,gain->
                            AudioParameter(strings.eqBandLabel(index),gain,-12f..12f,.5f,
                                {strings.ui(com.akira.app.core.i18n.AkiraUiText.GainFormat,it)},{onBand(index,it)},inline=true)
                        }
                    } else {
                        AudioParameter(strings.ui(com.akira.app.core.i18n.AkiraUiText.Preamp),
                            (20f*log10(configuration.preamp)).coerceIn(-12f,0f),-12f..0f,.5f,
                            {strings.ui(com.akira.app.core.i18n.AkiraUiText.GainFormat,it)},{onPreamp(10f.pow(it/20f))})
                        AudioParameter(strings.library.bass,configuration.bass,0f..1f,.01f,
                            {"${(it*100).roundToInt()}%"},onBass,inputScale=100f,enabled=support.bass!=false)
                        AudioParameter(strings.library.space,configuration.space,0f..1f,.01f,
                            {"${(it*100).roundToInt()}%"},onSpace,inputScale=100f,enabled=support.space!=false)
                        TextButton({onPreamp(10f.pow(-configuration.bands.max().coerceAtLeast(0f)/20f).coerceAtLeast(.25f))}) {
                            Text(strings.ui(com.akira.app.core.i18n.AkiraUiText.AutoSetHeadroom),color=MaterialTheme.colorScheme.onSurface)
                        }
                        Text(strings.ui(com.akira.app.core.i18n.AkiraUiText.StartWithFlatWhenBoostingBandsUse),
                            style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val problem=when {
                    support.equalizer==false -> com.akira.app.core.design.components.localizedText("Equalization is unavailable on this output.","La ecualización no está disponible en esta salida.")
                    prefs.eqEnabled && support.controlled==false -> com.akira.app.core.design.components.localizedText("Another app controls this audio effect.","Otra aplicación controla este efecto de audio.")
                    prefs.eqEnabled && support.applied==false -> com.akira.app.core.design.components.localizedText("Turn the equalizer off and on to retry the audio effect.","Apaga y enciende el ecualizador para reintentar el efecto.")
                    else -> null
                }
                if(problem!=null)Text(problem,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if(showProfiles)OutputProfilesSheet {showProfiles=false}
    }
}

@Composable
internal fun EqResponseGraph(gains: List<Float>, accent: Color, modifier: Modifier) {
    val displayedGains = (0..4).map { index ->
        animateFloatAsState(gains.getOrElse(index) { 0f }, tween(140), label = "eq_band_$index")
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
    Canvas(modifier.semantics { contentDescription = description }) {
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
            val y = zero - displayedGains[index].value.coerceIn(-12f, 12f) / 12f * height / 2f
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
