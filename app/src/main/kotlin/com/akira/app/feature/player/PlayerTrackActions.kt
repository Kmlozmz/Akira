package com.akira.app.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.components.LocalContentMotionReduced
import com.akira.app.core.design.components.rememberAkiraMenuSource
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.design.util.rememberAkiraHaptics
import com.akira.app.core.i18n.LocalStrings

@Composable
internal fun PlayerTrackActions(isFavorite: Boolean, ink: Color, referencePortrait: Boolean,
    onFavorite: () -> Unit, onMore: () -> Unit) {
    val strings = LocalStrings.current
    val glyphInk = if (referencePortrait) Color.White else ink
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (referencePortrait) 0.dp else 2.dp)) {
        PlayerTrackActionButton(strings.favorite, glyphInk, isFavorite, false,
            if (isFavorite) 24.dp else 22.dp, onFavorite)
        PlayerTrackActionButton(strings.options, glyphInk, false, true,
            if (referencePortrait) 24.dp else 22.dp, onMore)
    }
}

/** The optical menu inherits the complete flat face, including its glyph. */
@Composable
private fun PlayerTrackActionButton(label: String, ink: Color, favorite: Boolean,
    menu: Boolean, glyphSize: Dp, onClick: () -> Unit) {
    val faceSize = 30.dp
    val source = rememberAkiraMenuSource(hasOwnSurface = true)
    source.visualInsetPx = with(androidx.compose.ui.platform.LocalDensity.current) { 9.dp.toPx() }
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val reduced = LocalContentMotionReduced.current
    val scale by animateFloatAsState(if (pressed && !reduced) .94f else 1f,
        spring(dampingRatio = .85f, stiffness = 1100f), label = "playerActionContact")
    val activation = remember { Animatable(1f) }
    var wasFavorite by remember { mutableStateOf(favorite) }
    LaunchedEffect(favorite, reduced) {
        val newlySelected = favorite && !wasFavorite
        wasFavorite = favorite
        if (reduced || !newlySelected) activation.snapTo(1f)
        else {
            activation.animateTo(1.13f, tween(85))
            activation.animateTo(1f, spring(dampingRatio = .7f, stiffness = 560f))
        }
    }
    val haptics = rememberAkiraHaptics()
    SideEffect {
        source.flatSourceSurface = true
        source.surfaceContent = { PlayerActionFace(ink, favorite, menu, faceSize, glyphSize) }
    }
    Box(Modifier.then(if (menu) source.modifier else Modifier).size(48.dp)
        .semantics { if (!menu) selected = favorite }
        .clickable(interactionSource = interactions, indication = null, role = Role.Button,
            onClick = { if (menu) source.open(onClick) else { haptics.light(); onClick() } }),
        contentAlignment = Alignment.Center) {
        Box(Modifier.graphicsLayer { scaleX = scale * activation.value; scaleY = scaleX }) {
            PlayerActionFace(ink, favorite, menu, faceSize, glyphSize, label)
        }
    }
}

@Composable
private fun PlayerActionFace(ink: Color, favorite: Boolean, menu: Boolean,
    faceSize: Dp, glyphSize: Dp, label: String? = null) {
    if (favorite && !menu) {
        Box(Modifier.size(faceSize).drawWithCache {
            // Same filled-star geometry as AkiraIcons.StarFilled, cut out of
            // the circle so the live artwork remains visible through it.
            val face = Path().apply {
                fillType = PathFillType.EvenOdd
                addOval(Rect(0f, 0f, size.width, size.height))
                val unit = glyphSize.toPx() / 24f
                val dx = (size.width - glyphSize.toPx()) / 2f
                val dy = (size.height - glyphSize.toPx()) / 2f
                moveTo(dx + 12f * unit, dy + 3.2f * unit)
                lineTo(dx + 14.7f * unit, dy + 8.7f * unit)
                lineTo(dx + 20.8f * unit, dy + 9.6f * unit)
                lineTo(dx + 16.4f * unit, dy + 13.9f * unit)
                lineTo(dx + 17.4f * unit, dy + 20f * unit)
                lineTo(dx + 12f * unit, dy + 17.2f * unit)
                lineTo(dx + 6.6f * unit, dy + 20f * unit)
                lineTo(dx + 7.6f * unit, dy + 13.9f * unit)
                lineTo(dx + 3.2f * unit, dy + 9.6f * unit)
                lineTo(dx + 9.3f * unit, dy + 8.7f * unit)
                close()
            }
            onDrawBehind { drawPath(face, ink) }
        }.semantics { if (label != null) contentDescription = label })
    } else {
        Box(Modifier.size(faceSize).background(Color.White.copy(alpha = .13f), CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(if (menu) AkiraIcons.MoreHoriz else AkiraIcons.Star,
                label, Modifier.size(glyphSize), tint = ink.copy(alpha = .94f))
        }
    }
}
