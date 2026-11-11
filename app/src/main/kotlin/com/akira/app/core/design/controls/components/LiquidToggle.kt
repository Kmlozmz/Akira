/* Derived from ClearPDF, Copyright 2026 Chethan616, MIT License.
 * Akira adaptations: namespace, integration and accessibility. See assets/licenses/glass-controls/NOTICE. */
package com.akira.app.core.design.controls.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import com.akira.app.core.design.controls.utils.DampedDragAnimation
import com.akira.app.core.design.backdrop.Backdrop
import com.akira.app.core.design.backdrop.backdrops.layerBackdrop
import com.akira.app.core.design.backdrop.backdrops.rememberBackdrop
import com.akira.app.core.design.backdrop.backdrops.rememberCombinedBackdrop
import com.akira.app.core.design.backdrop.backdrops.rememberLayerBackdrop
import com.akira.app.core.design.backdrop.drawBackdrop
import com.akira.app.core.design.backdrop.effects.blur
import com.akira.app.core.design.backdrop.effects.lens
import com.akira.app.core.design.backdrop.highlight.Highlight
import com.akira.app.core.design.backdrop.shadow.InnerShadow
import com.akira.app.core.design.backdrop.shadow.Shadow
import androidx.compose.foundation.shape.CircleShape as Capsule
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LiquidToggle(
    selected: () -> Boolean,
    onSelect: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val sharedMaterial = com.akira.app.core.design.glass.LocalSharedGlassMaterial.current
    val glassOpacity = com.akira.app.core.design.glass.LocalLiquidGlassOpacity.current
    val glassDiffusion = com.akira.app.core.design.glass.LocalLiquidGlassDiffusion.current
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(context) { MotionSettings.load(context) }
    val isDarkMode = (MaterialTheme.colorScheme.surface.luminance() < .5f)
    val isLightTheme = !isDarkMode
    val accentColor = MaterialTheme.colorScheme.primary
    val trackColor =
        if (isLightTheme) Color(0xFF787878).copy(0.2f)
        else Color(0xFF787880).copy(0.36f)

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { (if (sharedMaterial != null) 22f else 20f).dp.toPx() }
    val animationScope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (selected()) 1f else 0f) }
    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStarted = {},
            onDragStopped = {
                if (didDrag) {
                    fraction = if (targetValue >= 0.5f) 1f else 0f
                    onSelect(fraction == 1f)
                    didDrag = false
                } else {
                    fraction = if (selected()) 0f else 1f
                    onSelect(fraction == 1f)
                }
            },
            onDrag = { _, dragAmount ->
                if (!didDrag) {
                    didDrag = dragAmount.x != 0f
                }
                val delta = dragAmount.x / dragWidth
                fraction =
                    if (isLtr) (fraction + delta).fastCoerceIn(0f, 1f)
                    else (fraction - delta).fastCoerceIn(0f, 1f)
            }
        )
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }
            .collectLatest { fraction ->
                dampedDragAnimation.updateValue(fraction)
            }
    }
    LaunchedEffect(selected) {
        snapshotFlow { selected() }
            .collectLatest { isSelected ->
                val target = if (isSelected) 1f else 0f
                if (target != fraction) {
                    fraction = target
                    dampedDragAnimation.animateToValue(target)
                }
            }
    }

    val trackBackdrop = rememberLayerBackdrop()

    // Squash-and-stretch of the thumb along its travel (LiquidTracker technique).
    val stretch = rememberLiquidStretchState(stretchFactor = 0.4f, minScale = 0.85f, maxScale = 1.2f)
    LaunchedEffect(dampedDragAnimation, stretch) {
        snapshotFlow { dampedDragAnimation.value }
            .collect { v -> stretch.onPositionSample(v * dragWidth) }
    }

    // The whole switch is the gesture surface: DampedDragAnimation reports a tap through
    // onDragStopped (didDrag == false), so no extra `clickable` - stacking one here made a tap
    // fire onSelect twice (toggle on, then straight back off).
    Box(
        modifier
            .then(dampedDragAnimation.modifier)
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(selected())
                onClick {
                    onSelect(!selected())
                    true
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(Capsule)
                .drawBehind {
                    val fraction = dampedDragAnimation.value
                    drawRect(lerp(trackColor, accentColor, fraction))
                }
                .size(if (sharedMaterial != null) 63.dp else 64.dp, 28.dp)
        )

        Box(
            Modifier
                .graphicsLayer {
                    val fraction = dampedDragAnimation.value
                    val padding = 2f.dp.toPx()
                    translationX =
                        if (isLtr) lerp(padding, padding + dragWidth, fraction)
                        else lerp(-padding, -(padding + dragWidth), fraction)
                }
                .liquidStretch(stretch)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            val scaleX = lerp(2f / 3f, 0.75f, progress)
                            val scaleY = lerp(0f, 0.75f, progress)
                            if(sharedMaterial!=null)drawBackdrop()
                            else scale(scaleX, scaleY) { drawBackdrop() }
                        }
                    ),
                    shape = { Capsule },
                    effects = {
                        if (sharedMaterial != null) {
                            sharedMaterial.switchMaterial?.apply(this,
                                dampedDragAnimation.pressProgress.coerceIn(0f,1f))
                        } else {
                            val progress = dampedDragAnimation.pressProgress
                            // At rest the thumb is an opaque white capsule (see onDrawSurface), so a blur
                            // of what is under it would be invisible work. Only refract once pressed.
                            if (progress > 0f) blur(8f.dp.toPx() * (1f - progress) * GlassSettings.style.blur)
                            lens(
                                5f.dp.toPx() * progress * GlassSettings.style.refractionHeight,
                                10f.dp.toPx() * progress * GlassSettings.style.refractionAmount,
                                chromaticAberration = GlassSettings.style.chromaticAberration, depthEffect = GlassSettings.style.depthEffect
                            )
                        }
                    },
                    highlight = {
                        if (sharedMaterial != null) null else {
                            val progress = dampedDragAnimation.pressProgress
                            // Alpha 0 at rest: skip it outright, so an idle thumb never builds the
                            // highlight shader at all.
                            if (progress <= 0f) null else Highlight.Ambient.copy(
                                width = Highlight.Ambient.width / 1.5f,
                                blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                                alpha = progress
                            )
                        }
                    },
                    shadow = null,
                    innerShadow = {
                        if (sharedMaterial != null) null else {
                            val progress = dampedDragAnimation.pressProgress
                            if (progress <= 0f) null else InnerShadow(
                                radius = 4f.dp * progress,
                                alpha = progress
                            )
                        }
                    },
                    // The backdrop is fully covered by the opaque white surface at rest; don't even
                    // record it then. A scrolling list re-samples every visible glass node per frame,
                    // and a page of idle toggles was paying for blurs nobody could see.
                    onDrawBackdrop = { drawBackdrop ->
                        if (dampedDragAnimation.pressProgress > 0f) drawBackdrop()
                    },
                    layerBlock = {
                        if(sharedMaterial!=null && !GlassSettings.reducedMotion) {
                            val p=dampedDragAnimation.pressProgress
                            scaleX=1f+21f/37f*p;scaleY=1f+14.333f/24f*p
                        } else {
                            scaleX=dampedDragAnimation.scaleX;scaleY=dampedDragAnimation.scaleY
                        }
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = if (sharedMaterial != null &&
                            (sharedMaterial.failed || sharedMaterial.switchMaterial?.failed!=false || android.os.Build.VERSION.SDK_INT < 33)) 1f else 1f - progress))
                    }
                )
                .size(if (sharedMaterial != null) 37.dp else 40.dp, 24.dp)
        )
    }
}
