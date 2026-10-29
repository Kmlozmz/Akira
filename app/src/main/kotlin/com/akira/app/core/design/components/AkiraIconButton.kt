package com.akira.app.core.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.util.rememberAkiraHaptics
import com.akira.app.core.design.glass.lightweightLiquidGlass

/**
 * Shared circular action with a 48 dp touch target and configurable surface size.
 * Press feedback scales the control; [popOnTrue] adds selection feedback.
 */
@Composable
fun AkiraIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
    glyphSize: Dp = 22.dp,
    showBackground: Boolean = true,
    popOnTrue: Boolean = false,
    surfaceSize: Dp = 40.dp,
    glassSurface: Boolean = true,
    menuSourceOverride: AkiraMenuSource? = null,
    glyphOverride: ImageVector? = null
) {
    val defaultSource=rememberAkiraMenuSource(hasOwnSurface=showBackground)
    val menuSource=menuSourceOverride ?: defaultSource
    val isMore=icon==com.akira.app.core.design.icon.AkiraIcons.MoreHoriz
    menuSource.visualInsetPx=with(androidx.compose.ui.platform.LocalDensity.current){if(showBackground)((48.dp-surfaceSize)/2).toPx() else 0f}
    val haptics = rememberAkiraHaptics()
    val neutral = tint == Color.White || tint == androidx.compose.material3.MaterialTheme.colorScheme.onSurface || tint == Color.Black
    val ink = if (showBackground && glassSurface && neutral) com.akira.app.core.design.glass.rememberGlassInk(tint) else null
    val glyphTint = ink?.color ?: tint
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val response=rememberAkiraPressResponse(pressed)
    androidx.compose.runtime.DisposableEffect(menuSource,response,isMore) {
        if(isMore && menuSourceOverride==null) { menuSource.contactProvider=response.amount;menuSource.contactStrain=response.strain }
        onDispose { if(isMore && menuSourceOverride==null) { menuSource.contactProvider=null;menuSource.contactStrain=null } }
    }
    val reduced=com.akira.app.core.design.components.LocalContentMotionReduced.current
    val inheritedDeformation=com.akira.app.core.design.glass.LocalGlassDeformation.current
    val density=androidx.compose.ui.platform.LocalDensity.current
    val surfaceShape=remember(response,density,surfaceSize) {
        com.akira.app.core.design.motion.ElasticGlassShape(
            { with(density){surfaceSize.toPx()/2f} }, { 0f }, response.strain,density)
    }
    val pop = remember { Animatable(0f) }
    var lastPop by remember { mutableStateOf(popOnTrue) }
    LaunchedEffect(popOnTrue,reduced) {
        val shouldPop = popOnTrue && !lastPop
        lastPop = popOnTrue
        if (shouldPop) haptics.toggle()
        if (shouldPop && !reduced) {
            pop.snapTo(0.10f)
            pop.animateTo(0f, spring(dampingRatio = 0.66f, stiffness = 1000f))
        } else {
            pop.snapTo(0f)
        }
    }

    Box(
        modifier = modifier.then(ink?.modifier ?: Modifier)
            .then(if(isMore && menuSourceOverride==null)menuSource.modifier else Modifier)
            .size(48.dp)
            .then(response.modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = { if(isMore){haptics.light();menuSource.open(onClick)}else onClick() }
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.akira.app.core.design.glass.LocalGlassDeformation provides {
                (inheritedDeformation()+response.amount()*.35f).coerceIn(0f,1f)
            }) {
        if (showBackground) {
            Box(
                modifier = Modifier
                    .size(surfaceSize)
                    .then(if(glassSurface) Modifier.lightweightLiquidGlass(surfaceShape, tint.copy(alpha = 0.14f))
                        else Modifier.background(tint.copy(alpha = .10f), surfaceShape))
            )
        }
        AkiraAnimatedGlyph(
            icon = glyphOverride ?: icon,
            label = contentDescription ?: if(isMore)com.akira.app.core.i18n.akiraStrings().options else null,
            tint = glyphTint,
            modifier = Modifier.size(glyphSize).graphicsLayer { scaleX=1f+pop.value;scaleY=scaleX }
        )
        }
    }
}
