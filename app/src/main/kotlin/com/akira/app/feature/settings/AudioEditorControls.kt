package com.akira.app.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.app.core.design.components.AkiraModalSheet
import com.akira.app.core.design.theme.noRippleClickable
import com.akira.app.core.i18n.LocalStrings

internal data class AudioPresetOption(val id: String, val title: String, val detail: String)

@Composable
internal fun AudioPresetPicker(options: List<AudioPresetOption>, selected: String, compact: Boolean=false, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val source=com.akira.app.core.design.components.rememberAkiraMenuSource(hideOnOpen=false,trailingControl=true,icon=Icons.Rounded.KeyboardArrowDown)
    val index = options.indexOfFirst { it.id == selected }
    val current = options.getOrNull(index)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onSelect(options[(if (index < 0) 0 else index + options.size - 1) % options.size].id) }) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.PreviousPreset), tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
        }
        Column(Modifier.weight(1f).then(source.modifier).noRippleClickable { source.open { open = true } }.padding(vertical = if(compact)4.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AnimatedContent(current, label = "audio_preset_name") { displayed ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(displayed?.title ?: com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Custom), fontSize = if(compact)20.sp else 27.sp,
                            fontWeight = FontWeight.SemiBold, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .6f), modifier = Modifier.size(20.dp))
                    }
                    if(!compact) Text(displayed?.detail ?: com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.YourSoundSettings),
                        style = MaterialTheme.typography.bodyMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
                }
            }
        }
        IconButton(onClick = { onSelect(options[(index + 1).coerceAtLeast(0) % options.size].id) }) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.NextPreset), tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
        }
    }
    if (open) AkiraModalSheet(onDismissRequest = { open = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState(), flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()).padding(horizontal = 24.dp).selectableGroup()) {
            Text(com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Presets), style = MaterialTheme.typography.titleLarge, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp))
            options.forEachIndexed { rowIndex, option ->
                if (rowIndex > 0) HorizontalDivider(color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .09f))
                Row(Modifier.fillMaxWidth().heightIn(min = 58.dp)
                    .selectable(option.id == selected, role = Role.RadioButton, onClick = { onSelect(option.id); open = false })
                    .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(option.title, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        Text(option.detail, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .6f), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (option.id == selected) Icon(Icons.Rounded.Check, null, tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioSlider(value: Float, onValueChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>,
    label: String, modifier: Modifier = Modifier, steps: Int = 0, onFinished: () -> Unit = {}, enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface) {
    com.akira.app.feature.player.AkiraPlayerSlider(value,onValueChange,label,modifier.heightIn(min=48.dp),
        valueRange=range,onFinished=onFinished,enabled=enabled,steps=steps,tint=tint,
        restingThickness=7.dp,animatedThumb=true)
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExpressiveAudioSlider(value: Float, onValueChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>,
    label: String, modifier: Modifier = Modifier, steps: Int = 0, onFinished: () -> Unit = {}, enabled: Boolean = true) {
    com.akira.app.feature.player.AkiraPlayerSlider(value,onValueChange,label,modifier.heightIn(min=48.dp),
        valueRange=range,onFinished=onFinished,tint=MaterialTheme.colorScheme.onSurface,
        restingThickness=8.dp,enabled=enabled,steps=steps,animatedThumb=true)
}

@Composable
internal fun AudioValueControl(label: String, stored: Float, range: ClosedFloatingPointRange<Float>, step: Float,
    format: (Float) -> String, onCommit: (Float) -> Unit, inputScale: Float = 1f, enabled: Boolean = true) {
    var value by remember { mutableFloatStateOf(stored) }
    var dragging by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var lastCommit by remember { mutableLongStateOf(0L) }
    val commit by rememberUpdatedState(onCommit)
    val source=com.akira.app.core.design.components.rememberAkiraMenuSource(icon=null)
    LaunchedEffect(stored) { if (!dragging) value = stored }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(62.dp), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .75f), fontSize = 13.sp)
        AudioSlider(value, {
            value=(kotlin.math.round(it/step)*step).coerceIn(range.start,range.endInclusive)
            dragging=true
            val now=android.os.SystemClock.uptimeMillis()
            if(now-lastCommit>=60L) {lastCommit=now;commit(value)}
        }, range, label, Modifier.weight(1f), onFinished = {
            dragging = false
            val result = (kotlin.math.round(value / step) * step).coerceIn(range.start, range.endInclusive)
            value = result; commit(result)
        }, enabled = enabled)
        TextButton(onClick = { source.open { editing = true } }, enabled = enabled, modifier = Modifier.then(source.modifier).width(72.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(format(value), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f), fontSize = 12.sp)
        }
    }
    if (editing) AudioNumberEditor(label, value * inputScale,
        range.start * inputScale..range.endInclusive * inputScale, step * inputScale,
        onDismiss = { editing = false }, onConfirm = {
            value = (it / inputScale).coerceIn(range.start, range.endInclusive)
            onCommit(value); editing = false
        })
}

@Composable
internal fun AudioNumberEditor(title: String, value: Float, range: ClosedFloatingPointRange<Float>, step: Float,
    onDismiss: () -> Unit, onConfirm: (Float) -> Unit) {
    val strings = LocalStrings.current
    fun numberText(number: Float) = java.lang.String.format(java.util.Locale.ROOT, "%.2f", number)
        .trimEnd('0').trimEnd('.')
    var input by remember { mutableStateOf(numberText(value)) }
    val number = input.replace(',', '.').toFloatOrNull()
    val valid = number != null && number.isFinite() && number in range
    AkiraModalSheet(onDismissRequest = onDismiss, title=title) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
            TextField(input, { input = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(12.dp), isError = !valid,
                trailingIcon = if (range.start < 0f) ({
                    TextButton(onClick = { input = if (input.startsWith('-')) input.drop(1) else "-$input" }) {
                        Text("±", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                    }
                }) else null,
                colors = TextFieldDefaults.colors(focusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .06f),
                    unfocusedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .06f), errorContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .06f),
                    focusedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, unfocusedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent))
            Text(com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.SS2, numberText(range.start), numberText(range.endInclusive)) +
                com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Step) + " ${numberText(step)}", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .6f),
                style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(strings.cancel, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
                }
                Button(enabled = valid, onClick = {
                    onConfirm((kotlin.math.round(number!! / step) * step).coerceIn(range.start, range.endInclusive))
                }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, contentColor = androidx.compose.material3.MaterialTheme.colorScheme.surface)) {
                    Text(strings.ok)
                }
            }
        }
    }
}
