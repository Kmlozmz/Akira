package com.akira.app.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.components.AkiraModalSheet
import com.akira.app.core.design.components.AkiraSwitch
import com.akira.app.core.design.components.rememberAkiraMenuSource
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.design.util.rememberAkiraHaptics
import com.akira.app.core.design.components.LocalContentMotionReduced

@Composable
internal fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Each row keeps its own hit target and rounded clip. A shared outer clip
    // would fill the gaps and leave the internal corners square.
    Layout(content=content,modifier=modifier.fillMaxWidth()) { children,constraints ->
        val gap=2.dp.roundToPx()
        val rows=children.map {it.measure(constraints.copy(minHeight=0))}
        val width=(rows.maxOfOrNull {it.width} ?: 0).coerceIn(constraints.minWidth,constraints.maxWidth)
        val height=(rows.sumOf {it.height}+gap*(rows.size-1).coerceAtLeast(0)).coerceIn(constraints.minHeight,constraints.maxHeight)
        layout(width,height) {
            var y=0
            rows.forEachIndexed {index,row ->
                row.placeRelativeWithLayer(0,y) {clip=true;shape=settingsSegmentShape(index,rows.size)}
                y+=row.height+gap
            }
        }
    }
}

internal fun settingsSegmentShape(index: Int, count: Int) = RoundedCornerShape(
    topStart=if(index==0)20.dp else 4.dp,topEnd=if(index==0)20.dp else 4.dp,
    bottomStart=if(index==count-1)20.dp else 4.dp,bottomEnd=if(index==count-1)20.dp else 4.dp)

@Composable
internal fun SettingsGroupTitle(title: String) {
    Text(title, Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun SettingsRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, opensSelector: Boolean = false,
    leading: (@Composable () -> Unit)? = null) {
    val haptics = rememberAkiraHaptics()
    val source = rememberAkiraMenuSource(hideOnOpen = false, trailingControl = true, icon = AkiraIcons.ChevronRight)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = LocalContentMotionReduced.current
    val fill by animateColorAsState(if (pressed) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainer, if (reduced) snap() else tween(120), label = "settingsRowContact")
    Row(modifier.fillMaxWidth().heightIn(min = 76.dp).background(fill)
        .then(if (opensSelector) source.modifier else Modifier)
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, enabled = enabled) {
            haptics.light()
            if (opensSelector) source.open(onClick) else onClick()
        }.padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (leading != null) leading() else
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) .85f else .38f))
        SettingsRowText(title, detail, Modifier.weight(1f), enabled)
        Icon(AkiraIcons.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) .7f else .35f))
    }
}

@Composable
internal fun SettingsToggleRow(icon: ImageVector, title: String, detail: String, checked: Boolean,
    onChange: (Boolean) -> Unit) {
    val haptics = rememberAkiraHaptics()
    fun change(value: Boolean) { haptics.toggle(value); onChange(value) }
    Row(Modifier.fillMaxWidth().heightIn(min = 76.dp)
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .toggleable(checked, role = Role.Switch, onValueChange = ::change)
        .padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .85f))
        SettingsRowText(title, detail, Modifier.weight(1f))
        AkiraSwitch(checked, ::change, Modifier.clearAndSetSemantics { }, haptic = {})
    }
}

@Composable
private fun SettingsRowText(title: String, detail: String, modifier: Modifier, enabled: Boolean = true) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f))
        if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .5f))
    }
}

/** Values are committed only by selecting a row; dismissing leaves preferences intact. */
@Composable
internal fun SettingsChoiceSheet(title: String, description: String, selected: String,
    options: List<Pair<String, String>>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val haptics = rememberAkiraHaptics()
    AkiraModalSheet(onDismiss, title = title) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            SettingsGroup(Modifier.selectableGroup()) {
                options.forEach { (id, label) ->
                    val active = id == selected
                    Row(Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (active) .12f else .04f))
                        .selectable(active, role = Role.RadioButton, onClick = { haptics.tick(); onSelect(id) })
                        .heightIn(min = 56.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        if (active) Icon(AkiraIcons.Check, null, Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}
