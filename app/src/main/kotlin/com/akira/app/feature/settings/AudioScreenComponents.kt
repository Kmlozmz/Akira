package com.akira.app.feature.settings

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.brand.AkiraBrand
import com.akira.app.core.design.components.rememberAkiraMenuSource
import kotlin.math.round
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import com.akira.app.core.i18n.LocalStrings
import com.akira.app.core.i18n.AkiraUiText

internal fun Modifier.audioControlsEnabled(enabled: Boolean, description: String): Modifier = if(enabled)this else
    alpha(.38f).focusProperties {canFocus=false}.clearAndSetSemantics {
        disabled();contentDescription=description
    }.pointerInput(Unit) {
        awaitPointerEventScope {
            while(true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach {it.consume()}
        }
    }

@Composable internal fun audioAccent(): Color =
    if (MaterialTheme.colorScheme.background.luminance() > .5f) Color(0xFFC91432) else AkiraBrand.accent

@Composable internal fun AudioPreviewSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .045f)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}

@Composable internal fun AudioPresetRail(options: List<AudioPresetOption>, selected: String,
    onSelect: (String) -> Unit) {
    val state = rememberLazyListState()
    val custom = LocalStrings.current.ui(AkiraUiText.Custom)
    val known = options.any {it.id==selected}
    val displayed = if(known)options else listOf(AudioPresetOption("CUSTOM",custom,""))+options
    val selectedId = if(known)selected else "CUSTOM"
    val index = displayed.indexOfFirst { it.id == selectedId }
    LaunchedEffect(index) { if (index >= 0) state.animateScrollToItem(index) }
    LazyRow(state = state, modifier = Modifier.fillMaxWidth().selectableGroup(),
        contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(displayed, key = { it.id }) { option ->
            val active = option.id == selectedId
            Surface(shape = RoundedCornerShape(14.dp),
                color = if (active) audioAccent().copy(alpha = .13f) else MaterialTheme.colorScheme.onSurface.copy(alpha = .045f)) {
                Box(Modifier.selectable(active, role = Role.RadioButton, onClick = { if(option.id!="CUSTOM")onSelect(option.id) })
                    .heightIn(min = 48.dp).padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text(option.title, style = MaterialTheme.typography.labelLarge,
                        color = if (active) audioAccent() else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
    }
}

/** The numeric field and slider edit the same draft; writes during a drag are bounded. */
@Composable internal fun AudioParameter(label: String, stored: Float, range: ClosedFloatingPointRange<Float>,
    step: Float, format: (Float) -> String, onCommit: (Float) -> Unit, enabled: Boolean = true,
    inputScale: Float = 1f, inline: Boolean = false) {
    var value by remember { mutableFloatStateOf(stored) }
    var dragging by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var lastCommit by remember { mutableLongStateOf(0) }
    val commit by rememberUpdatedState(onCommit)
    val source = rememberAkiraMenuSource(icon = null)
    LaunchedEffect(stored) { if (!dragging) value = stored }
    fun change(next: Float) {
        value = (round(next / step) * step).coerceIn(range.start, range.endInclusive)
        dragging = true
        val now = SystemClock.uptimeMillis()
        if (now - lastCommit >= 60) { lastCommit = now; commit(value) }
    }
    val number: @Composable () -> Unit = {
        TextButton({ source.open { editing = true } }, enabled = enabled,
            modifier = source.modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(format(value), color = if (enabled) audioAccent() else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge)
        }
    }
    if(inline) Row(Modifier.fillMaxWidth().heightIn(min=52.dp),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label,Modifier.width(58.dp),style=MaterialTheme.typography.bodyMedium)
        AudioSlider(value,::change,range,label,Modifier.weight(1f),enabled=enabled,tint=audioAccent(),
            onFinished={dragging=false;commit(value)})
        Box(Modifier.widthIn(min=64.dp),contentAlignment=Alignment.CenterEnd) {number()}
    } else {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .45f))
            number()
        }
        AudioSlider(value, ::change, range, label, Modifier.fillMaxWidth(), enabled = enabled,
            tint = audioAccent(), onFinished = { dragging = false; commit(value) })
    }
    }
    if (editing) AudioNumberEditor(label, value * inputScale,
        range.start * inputScale..range.endInclusive * inputScale, step * inputScale,
        { editing = false }, { value = (it / inputScale).coerceIn(range.start, range.endInclusive); commit(value); editing = false })
}
