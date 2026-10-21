// SPDX-License-Identifier: GPL-3.0-only
// Motion from Page bb28993 MainActivity/NavHost and SubSettingsScaffold.
// Akira adapters: semantic light colors, shared scroll state, native local routes.
package com.akira.app.core.design.navigation

import com.akira.app.core.design.components.*
import com.akira.app.core.design.motion.*
import com.akira.app.core.design.rendering.*

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.akira.app.R
import com.akira.app.core.design.theme.AkiraFontFamily
import com.akira.app.core.design.util.rememberAkiraHaptics
import com.akira.app.core.design.glass.LocalLiquidGlassEnabled
import com.akira.app.core.design.glass.liquidGlass
import com.akira.app.core.design.backdrop.Backdrop
import com.akira.app.core.design.backdrop.backdrops.rememberCanvasBackdrop

// A header samples its own page body, never a recording containing the header.
val LocalPageHeaderBackdrop = staticCompositionLocalOf<Backdrop?> { null }

val LocalPageScrollState = staticCompositionLocalOf<ScrollState?> { null }
val PageCollapseEasing = CubicBezierEasing(.57f, .53f, 0f, 1f)
internal val PagePushEasing = CubicBezierEasing(.22f,.7f,.18f,1f)

fun pageNavigationTransform(forward: Boolean, reduced: Boolean = false): ContentTransform {
    if (reduced) return EnterTransition.None togetherWith ExitTransition.None
    // A page pushes the previous page away. Neither page dissolves in place.
    val timing=tween<IntOffset>(300,easing=PagePushEasing)
    return (slideInHorizontally(timing) { if (forward) it else -it / 3 } togetherWith
        slideOutHorizontally(timing) { if (forward) -it / 3 else it }).apply {
            targetContentZIndex=if(forward)1f else -1f
        }
}

/** Scroll-linked progress: no latch or timed catch-up after the finger stops. */
private val HeaderCollapseDistance = 68.dp

@Composable fun rememberPageCollapse(scrollState: ScrollState): State<Float> {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val travel = with(density) { HeaderCollapseDistance.toPx() }
    return remember(scrollState, travel) { derivedStateOf {
        (scrollState.value / travel).coerceIn(0f, 1f)
    } }
}

@Composable fun rememberPageCollapse(listState: LazyListState): State<Float> {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val travel = with(density) { HeaderCollapseDistance.toPx() }
    return remember(listState, travel) { derivedStateOf {
        if (listState.firstVisibleItemIndex > 0) 1f
        else (listState.firstVisibleItemScrollOffset / travel).coerceIn(0f, 1f)
    } }
}

@Composable fun PageBackButton(onBack: () -> Unit, modifier: Modifier = Modifier,
    collapseProgress: Float = 1f) {
    val haptics = rememberAkiraHaptics()
    val pageColor = MaterialTheme.colorScheme.background
    val fallback = rememberCanvasBackdrop { drawRect(pageColor) }
    val backdrop = LocalPageHeaderBackdrop.current ?: fallback
    val glass = LocalLiquidGlassEnabled.current
    Box(modifier.size(40.dp).clip(CircleShape)
        .clickable { haptics.light(); onBack() }, contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize()
            .then(Modifier.liquidGlass(CircleShape, backdropOverride = backdrop)))
        Icon(painterResource(R.drawable.ic_navigation_back), localizedText("Back", "Atrás"),
            Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}

/** Shared title travel and scale, with sharp glyphs throughout the collapse. */
@Composable fun PagePageHeader(title: String, onBack: (() -> Unit)?, progress: Float = 0f,
    modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null, topInset: Dp = 0.dp) {
    var titleHeight by remember { mutableIntStateOf(0) }
    val reduced = LocalContentMotionReduced.current
    val p = if (reduced) if (progress > .5f) 1f else 0f else progress.coerceIn(0f, 1f)
    val background = MaterialTheme.colorScheme.background
    // The page owns a continuous scrim, never a solid rectangle over scrolled rows.
    // Apply it before the content inset so the fade spans the full viewport width.
    Box(modifier.fillMaxWidth().height(118.dp+topInset).background(Brush.verticalGradient(
        0f to background.copy(alpha=.97f*p),
        .48f to background.copy(alpha=.82f*p),
        .78f to background.copy(alpha=.32f*p),
        1f to Color.Transparent)).padding(top=topInset).padding(horizontal = 20.dp)) {
        if (onBack != null) PageBackButton(onBack, Modifier.padding(top = 16.dp), collapseProgress = p)
        trailing?.let { Box(Modifier.align(Alignment.TopEnd).padding(top=12.dp)) { it() } }
        Text(title, fontFamily = AkiraFontFamily, fontSize = 32.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(end = if(trailing != null) 52.dp else 0.dp)
                .onSizeChanged { titleHeight = it.height }.graphicsLayer {
                    transformOrigin = TransformOrigin(0f,0f)
                    val scale = 1f + (.62f - 1f) * p
                    scaleX = scale; scaleY = scale
                    val collapsedTop = 36.dp.toPx() - titleHeight * .62f / 2f
                    translationX = (if(onBack == null) 0.dp else 54.dp).toPx() * p
                    translationY = if(onBack == null) 16.dp.toPx() else 68.dp.toPx() + (collapsedTop - 68.dp.toPx()) * p
                    // Header titles retain their scale/travel without optical blur.
                })
    }
}

/** Page's tab-owned /8 navigation, seekable by the Android back gesture. */
@Composable fun <T> PageNavigationHost(target: T, backTarget: T, backEnabled: Boolean,
    onBack: () -> Unit, forward: (T,T) -> Boolean, modifier: Modifier = Modifier,
    onProgress: (T,T,Float) -> Unit = {_,_,_->},
    navigationTransform: ((T,T,Boolean) -> ContentTransform)? = null,
    content: @Composable AnimatedContentScope.(T) -> Unit) {
    val state = remember { SeekableTransitionState(target) }
    val currentBack by rememberUpdatedState(onBack)
    val currentTarget by rememberUpdatedState(target)
    val reduced = LocalContentMotionReduced.current
    LaunchedEffect(target) { state.animateTo(target) }
    PredictiveBackHandler(enabled = backEnabled) { events ->
        try {
            events.collect { state.seekTo(it.progress.coerceIn(0f,1f),backTarget) }
            currentBack()
            withFrameNanos { }
            withFrameNanos { }
            // An editor may ask about unsaved changes instead of accepting back.
            if(currentTarget == target) state.animateTo(target)
        } catch (cancel: CancellationException) {
            withContext(NonCancellable) { state.animateTo(target) }
        }
    }
    val transition = rememberTransition(state,label="pageNavigation")
    val from=state.currentState;val to=state.targetState;val fraction=state.fraction
    SideEffect {onProgress(from,to,fraction)}
    transition.AnimatedContent(transitionSpec={(navigationTransform?.invoke(initialState,targetState,reduced)
        ?: pageNavigationTransform(forward(initialState,targetState),reduced)).using(null)},
        modifier=modifier, content=content)
}
