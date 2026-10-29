package com.akira.app.core.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*

/** Shared switch with one state-change haptic and a 48 dp touch target. */
@Composable
fun AkiraSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    haptic: ((Boolean) -> Unit)? = null
) {
    val haptics = com.akira.app.core.design.util.rememberAkiraHaptics()
    val currentChecked by androidx.compose.runtime.rememberUpdatedState(checked)
    val currentChange by androidx.compose.runtime.rememberUpdatedState(onCheckedChange)
    fun change(value: Boolean) {
        if (!enabled || value == currentChecked) return
        if (haptic != null) haptic(value) else haptics.toggle(value)
        currentChange(value)
    }
    if (!com.akira.app.core.design.glass.LocalLiquidGlassEnabled.current || !com.akira.app.core.design.glass.isGlassSupported() || LocalContentMotionReduced.current) {
        Switch(checked = checked, onCheckedChange = ::change, modifier = modifier, enabled = enabled,
            thumbContent = { Icon(if (checked) Icons.Rounded.Check else Icons.Rounded.Close, null, Modifier.size(16.dp)) },
            colors = SwitchDefaults.colors(
                checkedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                uncheckedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest,
                checkedBorderColor = Color.Transparent, uncheckedBorderColor = Color.Transparent))
        return
    }
    com.akira.app.core.design.controls.components.LiquidToggle(
        selected = { currentChecked }, onSelect = ::change,
        backdrop = com.akira.app.core.design.glass.LocalAppBackdrop.current,
        modifier = modifier.size(64.dp, 48.dp)
            .graphicsLayer { alpha = if (enabled) 1f else .38f }
            .then(if (enabled) Modifier else Modifier.pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() } }
            })
            .semantics { if (!enabled) disabled() }
            .onKeyEvent { event ->
                if (enabled && event.type == KeyEventType.KeyUp &&
                    (event.key == Key.Spacebar || event.key == Key.Enter || event.key == Key.DirectionCenter)) {
                    change(!currentChecked); true
                } else false
            }.focusable(enabled)
    )
}
