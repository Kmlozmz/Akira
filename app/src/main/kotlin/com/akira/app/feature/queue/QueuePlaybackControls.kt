package com.akira.app.feature.queue

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.akira.app.core.design.components.AkiraAnimatedGlyph
import com.akira.app.core.design.components.LocalContentMotionReduced
import com.akira.app.core.design.components.akiraFocusOutline
import com.akira.app.core.design.components.rememberAkiraPressResponse
import com.akira.app.core.design.util.rememberAkiraHaptics

internal enum class QueueRepeatState(val english: String, val spanish: String) {
    Off("Off", "Desactivada"), All("All tracks", "Todas las canciones"), One("One track", "Una canción")
}

internal fun queueRepeatState(mode: Int): QueueRepeatState = when (mode) {
    Player.REPEAT_MODE_ALL -> QueueRepeatState.All
    Player.REPEAT_MODE_ONE -> QueueRepeatState.One
    else -> QueueRepeatState.Off
}

@Composable
internal fun QueueTogglePill(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    stateDescription: String? = null,
) {
    val haptics = rememberAkiraHaptics()
    val reduced = LocalContentMotionReduced.current
    val touch = remember { MutableInteractionSource() }
    val pressed by touch.collectIsPressedAsState()
    val focused by touch.collectIsFocusedAsState()
    val response = rememberAkiraPressResponse(pressed)
    val background by animateColorAsState(
        if (isActive) Color.White else Color.White.copy(alpha = .12f),
        if (reduced) snap() else spring(.85f, 1000f), label = "pillBg")
    val ink by animateColorAsState(
        if (isActive) Color(0xFF14141E) else Color.White,
        if (reduced) snap() else spring(.85f, 1000f), label = "pillContent")
    val activate = { haptics.tick(); onClick() }
    // Repeat cycles three states; a binary toggle would announce the wrong contract.
    val action = if (stateDescription != null) Modifier.semantics {
        this.stateDescription = stateDescription
        role = Role.Button
    }.clickable(interactionSource = touch, indication = null, onClick = activate)
    else Modifier.toggleable(isActive, interactionSource = touch, indication = null,
        role = Role.Switch, onValueChange = { activate() })

    Box(modifier.height(38.dp).then(response.modifier).clip(RoundedCornerShape(19.dp))
        .background(background).akiraFocusOutline(focused)
        .semantics { this.contentDescription = contentDescription }.then(action),
        contentAlignment = Alignment.Center) {
        AkiraAnimatedGlyph(icon, label = null, tint = ink, modifier = Modifier.size(20.dp))
    }
}
