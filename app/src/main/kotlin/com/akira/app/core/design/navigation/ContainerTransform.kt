// SPDX-License-Identifier: GPL-3.0-only
// Page bb28993, original container transform; renamed package.
// ContainerTransform.kt

package com.akira.app.core.design.navigation

import com.akira.app.core.design.components.*
import com.akira.app.core.design.motion.*
import com.akira.app.core.design.rendering.*

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/** every Home cover card is drawn with this radius, so the open transition starts from it */
// ContentCardCornerRadius is provided by LocalHomeSupport.kt

/** Collection bounds and the underlying scene finish together without a long spring tail. */
const val ContainerOpenMillis = 300
const val ContainerCloseMillis = ContainerOpenMillis
val ContainerCloseEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Shared bounds own the destination's travel. Only the underlying scene recedes. */
fun collectionContainerTransform(opening: Boolean, reduced: Boolean): androidx.compose.animation.ContentTransform {
    if(reduced)return (androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None)
    return if(opening) (androidx.compose.animation.EnterTransition.None togetherWith
        (androidx.compose.animation.scaleOut(tween(ContainerOpenMillis,easing=ContainerCloseEasing),targetScale=.96f)+fadeOut(tween(ContainerOpenMillis),targetAlpha=.5f)))
    else ((androidx.compose.animation.scaleIn(tween(ContainerCloseMillis,easing=ContainerCloseEasing),initialScale=.96f)+fadeIn(tween(ContainerCloseMillis),initialAlpha=.5f)) togetherWith androidx.compose.animation.ExitTransition.None)
}

// both sides of a match animate their own bounds, so they must pick the same spec: shrinking
// means closing
@OptIn(ExperimentalSharedTransitionApi::class)
private val ContainerBoundsTransform = BoundsTransform { initial, target ->
    if (target.width < initial.width) {
        tween(ContainerCloseMillis, easing = ContainerCloseEasing)
    } else {
        tween(ContainerOpenMillis, easing = ContainerCloseEasing)
    }
}

/**
 * what each tapped card looked like (width and corner radius, by key) plus the width an opened
 * screen settles at, so both sides of the transition clip to exactly the same outline.
 * only touched on the main thread.
 */
private object ContainerTransformRegistry {
    val sourceWidths = HashMap<String, Float>()
    val sourceRadii = HashMap<String, Dp>()
    val sourceCovers = HashMap<String, String>()
    var targetWidth = 0f
}

/**
 * the cover the tapped card was showing. the opened screen draws it from its very first frame,
 * instead of growing an empty canvas while its own data is still loading.
 */
fun containerTransformCover(key: String?): String? =
    key?.let { ContainerTransformRegistry.sourceCovers[it] }

/**
 * the corner radius is worked out from the live bounds instead of its own animation clock, so it
 * can never drift from the bounds, and a predictive back drag scrubs both at once.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
private class MorphingCornerClip(
    private val key: String,
    private val startRadius: Dp,
    private val endRadiusPx: () -> Float,
) : SharedTransitionScope.OverlayClip {
    private val path = Path()

    override fun getClipPath(
        state: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path {
        val from = ContainerTransformRegistry.sourceWidths[key]
        val to = ContainerTransformRegistry.targetWidth
        val t = if (from != null && to > from + 1f) {
            ((bounds.width - from) / (to - from)).coerceIn(0f, 1f)
        } else {
            1f
        }
        val start = ContainerTransformRegistry.sourceRadii[key] ?: startRadius
        val radius = lerp(with(density) { start.toPx() }, endRadiusPx(), t)
        path.rewind()
        path.addRoundRect(RoundRect(bounds, CornerRadius(radius)))
        return path
    }
}

@Composable
private fun rememberMorphingCornerClip(key: String, startRadius: Dp): MorphingCornerClip {
    val view = LocalView.current
    return remember(key, startRadius, view) {
        MorphingCornerClip(key, startRadius) { screenCornerRadiusPx(view) }
    }
}

private fun screenCornerRadiusPx(view: View): Float =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) roundedCornerRadiusPx(view) else 0f

@RequiresApi(Build.VERSION_CODES.S)
private fun roundedCornerRadiusPx(view: View): Float =
    view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: 0f

/** the tapped side: a cover card on Home (or anywhere a playlist can be opened from) */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.containerTransformSource(
    key: String?,
    cornerRadius: Dp = ContentCardCornerRadius,
    coverUrl: String? = null,
): Modifier {
    if (key == null || LocalContentMotionReduced.current) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalNavAnimatedContentScope.current ?: return this
    val clip = rememberMorphingCornerClip(key, cornerRadius)

    return with(sharedScope) {
        this@containerTransformSource
            .onPlaced {
                ContainerTransformRegistry.sourceWidths[key] = it.size.width.toFloat()
                ContainerTransformRegistry.sourceRadii[key] = cornerRadius
                if (coverUrl != null) ContainerTransformRegistry.sourceCovers[key] = coverUrl
            }
            .sharedBounds(
                sharedContentState = rememberSharedContentState(key),
                animatedVisibilityScope = visibilityScope,
                // the opened screen is drawn above the card, so the card only has to be gone
                // under it on the way out and back in under it before it fades off
                enter = fadeIn(tween(160, delayMillis = 60)),
                exit = fadeOut(tween(160, delayMillis = 40)),
                boundsTransform = ContainerBoundsTransform,
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopCenter),
                clipInOverlayDuringTransition = clip,
            )
    }
}

/** the opened side: the root of the screen that grows out of the card */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.containerTransformTarget(
    key: String?,
    sourceCornerRadius: Dp = ContentCardCornerRadius,
): Modifier {
    if (key == null || LocalContentMotionReduced.current) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalNavAnimatedContentScope.current ?: return this
    val clip = rememberMorphingCornerClip(key, sourceCornerRadius)

    return with(sharedScope) {
        this@containerTransformTarget
            .onSizeChanged { ContainerTransformRegistry.targetWidth = it.width.toFloat() }
            .sharedBounds(
                sharedContentState = rememberSharedContentState(key),
                animatedVisibilityScope = visibilityScope,
                // on the way back it stays solid for the first stretch of the shrink, so it doesn't
                // ghost over Home while it's still nearly full screen, and is gone by 220ms when
                // the card is almost home and its own cover takes over
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(140, delayMillis = 80)),
                boundsTransform = ContainerBoundsTransform,
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopCenter),
                zIndexInOverlay = 1f,
                clipInOverlayDuringTransition = clip,
            )
    }
}
