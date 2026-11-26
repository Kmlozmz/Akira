// SPDX-License-Identifier: GPL-3.0-only
// Source attribution: THIRD_PARTY_NOTICES.md.
// FloatingTabBar/Echo and backdrop retain their original headers and licenses.
package com.akira.app.feature.home

import com.akira.app.feature.player.dock.*

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.app.R
import com.akira.app.core.model.Song
import com.akira.app.core.design.components.localizedText
import com.akira.app.core.design.glass.*
import com.akira.app.feature.player.dock.navigation.*
import com.akira.app.feature.search.LocalSearchSession
import com.akira.app.feature.search.SearchDock
import com.akira.app.feature.player.rememberAmbientPhase
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class AkiraDockAnchor(val mini: Rect, val artwork: Rect, val corner: Dp)

@Stable
class AkiraDockState(val scroll: FloatingTabBarScrollConnection) {
    var heightPx by mutableIntStateOf(0)
    var miniBounds by mutableStateOf<Rect?>(null)
    var artBounds by mutableStateOf<Rect?>(null)
    var artCorner by mutableStateOf(8.dp)
    var transitionAnchor by mutableStateOf<AkiraDockAnchor?>(null)
        private set
    // Keep the two AnimatedContent branches separate. They coexist during a
    // fold; whichever happens to lay out last is not necessarily on screen.
    private val miniCoordinates = arrayOfNulls<LayoutCoordinates>(2)
    private val artCoordinates = arrayOfNulls<LayoutCoordinates>(2)
    private val corners = arrayOf(8.dp, 6.dp)
    fun reportMini(inline: Boolean, coords: LayoutCoordinates) { miniCoordinates[if (inline) 1 else 0] = coords }
    fun reportArtwork(inline: Boolean, coords: LayoutCoordinates, corner: Dp) {
        val index = if (inline) 1 else 0
        artCoordinates[index] = coords; corners[index] = corner
    }
    fun capture() {
        val index = if (scroll.isInline) 1 else 0
        val miniCoords = miniCoordinates[index]?.takeIf { it.isAttached }
        val artCoords = artCoordinates[index]?.takeIf { it.isAttached }
        if (miniCoords == null || artCoords == null) {
            if(com.akira.app.BuildConfig.DEBUG) android.util.Log.d("AkiraDock", "capture pending rows=${index} mini=${miniCoords != null} art=${artCoords != null}")
            return
        }
        val mini = miniCoords.boundsInRoot()
        val art = artCoords.boundsInRoot()
        if (mini.width <= 0f || mini.height <= 0f || art.width <= 0f) return
        miniBounds = mini; artBounds = art; artCorner = corners[index]
        transitionAnchor = AkiraDockAnchor(mini, art, corners[index])
        if(com.akira.app.BuildConfig.DEBUG) android.util.Log.d("AkiraDock", "capture rows=${if(index==1)1 else 2} mini=$mini artwork=$art")
    }
}

@Composable fun rememberAkiraDockState(): AkiraDockState {
    val scroll = rememberFloatingTabBarScrollConnection()
    return remember(scroll) { AkiraDockState(scroll) }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomeNavigation(
    selected: HomeDestination,
    onSelect: (HomeDestination) -> Unit,
    state: AkiraDockState,
    song: Song?,
    isPlaying: Boolean,
    isLoading: Boolean = false,
    artworkVisible: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onHoldStart: (() -> (() -> Unit))? = null,
    onExpand: () -> Unit,
    pull: MiniPlayerPull?,
    modifier: Modifier = Modifier,
    playerProgress: () -> Float = { 0f },
    searchEnabled: Boolean = true,
    classicInset: Dp = 0.dp,
    playbackFraction: () -> Float = { 0f },
    trackAccent: Color? = null,
    loadingState: State<Boolean>? = null,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val reduced = com.akira.app.core.design.components.LocalContentMotionReduced.current
    val classic = !LocalGlassStyleEnabled.current
    val ink = com.akira.app.core.design.glass.rememberGlassInk()
    val contentColor = if (classic) MaterialTheme.colorScheme.onBackground else ink.color
    val classicAccent = trackAccent?.let { classicTonalAccent(it, darkSurface = contentColor.luminance() > .5f) }
    val selectedContentColor = if (classic && classicAccent != null) classicAccent
        else Color(com.akira.app.core.design.system.AkiraDockSpec.accentArgb)
    val haptics = rememberHaptics()
    val currentSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selected)
    val group = remember { listOf(HomeDestination.HOME, HomeDestination.LIBRARY, HomeDestination.SETTINGS) }
    val labels = listOf(localizedText("Home", "Inicio"), localizedText("Library", "Biblioteca"), localizedText("Settings", "Ajustes"), localizedText("Search", "Buscar"))
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 0.sp,
        fontFamily = com.akira.app.core.design.theme.CurrentAkiraFontFamily,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
    val surface: @Composable () -> Modifier = {
        if (classic) Modifier else Modifier.liquidGlass(rememberDockGlassShape())
    }
    val search = LocalSearchSession.current
    val searchActive = selected == HomeDestination.SEARCH && searchEnabled && search != null
    val searchProgress by animateFloatAsState(if (searchActive) 1f else 0f,
        if (reduced) snap() else spring(dampingRatio = .9f, stiffness = 700f), label = "dockSearch")
    val editingProgress by animateFloatAsState(if (searchActive && search?.editing == true) 1f else 0f,
        if (reduced) snap() else spring(dampingRatio = 1f, stiffness = 800f), label = "dockSearchEditing")
    val searchFraction = searchProgress.coerceIn(0f, 1f)
    val editingFraction = editingProgress.coerceIn(0f, 1f)
    val searchHeight = dockMiniHeightDp(density.fontScale, inline = true).dp
    val navigationHeight = (47f+14f*density.fontScale).dp * (1f-searchFraction) + searchHeight * searchFraction
    val accessory: @Composable (Modifier, Float) -> Unit = { accessoryModifier, compactFraction ->
        val inline = state.scroll.isInline
        song?.let { current ->
            GlassNowPlaying(song = current, compactFraction = compactFraction, isPlaying = isPlaying,
                isLoading = loadingState?.value ?: isLoading,
                classicAccent = classicAccent.takeIf { classic },
                contentColor = contentColor, onPlayPause = onPlayPause, onNext = onNext,
                onPrevious = onPrevious, onExpand = onExpand, controlsLocked = false, onBlockedControl = {},
                onHoldStart = onHoldStart,
                artworkVisible = artworkVisible,
                onMiniPositioned = { state.reportMini(inline, it) },
                onArtPositioned = { coords, corner -> state.reportArtwork(inline, coords, corner) },
                pull = pull, drawGlass = !classic, modifier = accessoryModifier.graphicsLayer {
                    // Keep the soft shadow outside the capsule while it fades.
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    alpha = dockMiniReveal(playerProgress()) * (1f-editingFraction)
                    translationY = if (reduced) 0f else with(density) { dockMiniReturnOffsetDp(playerProgress()).dp.toPx() }
                })
        }
    }
    val bar: @Composable (Modifier) -> Unit = { barModifier -> FloatingTabBar(
        selectedTabKey = selected,
        navigationModifier = (if (classic) Modifier else ink.modifier).graphicsLayer {
            compositingStrategy = CompositingStrategy.ModulateAlpha
            alpha = dockNavigationReveal(playerProgress())
        },
        scrollConnection = state.scroll,
        modifier = barModifier.fillMaxWidth(),
        tabBarContentModifier = surface,
        liveAccessory = if (song == null) null else accessory,
        replacement = if (search != null && (searchActive || searchFraction > .001f)) DockNavigationReplacement(
            searchFraction, searchHeight, 1f-editingFraction, 1f-editingFraction) { slot ->
            SearchDock(search, onHome = { currentSelect(HomeDestination.HOME) }, onSubmit = { search.submit++ },
                modifier = slot, reveal = searchFraction, editingProgress = editingFraction,
                heightOverride = navigationHeight, drawHomeSurface = false, active = searchActive)
        } else null,
        colors = FloatingTabBarDefaults.colors(backgroundColor = Color.Transparent,
            accessoryBackgroundColor = Color.Transparent,
            indicatorColor = if (classic) (classicAccent ?: MaterialTheme.colorScheme.primary).copy(alpha = .20f)
                else glassIndicatorColor().copy(alpha = .5f)),
        elevations = FloatingTabBarDefaults.elevations(inlineElevation = 0.dp, expandedElevation = 0.dp),
        sizes = FloatingTabBarDefaults.sizes(
            tabBarContentPadding = if (classic) PaddingValues(horizontal = 4.dp, vertical = 2.dp) else PaddingValues(4.dp),
            tabExpandedContentPadding = if (classic) PaddingValues(vertical = 4.dp) else PaddingValues(vertical = 5.5.dp),
            componentSpacing = if (classic) 2.dp else 10.dp),
        iconOnlyIndicator = classic,
        contentKey = remember(classic, contentColor, selectedContentColor, labels) {
            listOf(classic, contentColor, selectedContentColor, labels)
        },
    ) {
        group.forEachIndexed { i, destination ->
            tab(key = destination, icon = {
                val selected = currentSelected == destination
                val tint = if (selected) selectedContentColor else contentColor.copy(alpha = if (classic) .74f else .65f)
                val semantics = (if (classic) Modifier.size(22.dp) else Modifier.size(25.dp, 28.dp))
                    .semantics { role = Role.Tab; this.selected = selected }
                if (destination == HomeDestination.SETTINGS) Icon(Icons.Rounded.Settings, labels[i], tint = tint, modifier = semantics)
                else Icon(painterResource(if (destination == HomeDestination.HOME) R.drawable.ic_navigation_home_filled else R.drawable.ic_navigation_library_filled), labels[i], tint = tint, modifier = semantics)
            }, title = {
                val labelTint = if (currentSelected == destination) selectedContentColor
                    else contentColor.copy(alpha = if (classic) .74f else .65f)
                Text(labels[i], style = labelStyle,
                    color = labelTint,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = if (classic) 3.dp else 1.dp))
            }, onClick = { if (currentSelected != destination) haptics.play(Haptic.Select); currentSelect(destination) }, indication = null)
        }
        standaloneTab(key = HomeDestination.SEARCH, icon = {
            val selected = currentSelected == HomeDestination.SEARCH
            Icon(painterResource(R.drawable.ic_navigation_search_outline), labels[3],
                tint = if (selected) selectedContentColor else contentColor.copy(alpha = if (classic) .74f else .65f),
                modifier = (if (classic) Modifier.size(22.dp) else Modifier.size(25.dp, 28.dp))
                    .semantics { role = Role.Tab; this.selected = selected })
        }, title = {
            val labelTint = if (currentSelected == HomeDestination.SEARCH) selectedContentColor
                else contentColor.copy(alpha = if (classic) .74f else .65f)
            Text(labels[3], style = labelStyle,
                color = labelTint,
                maxLines = 1, modifier = Modifier.padding(top = if (classic) 3.dp else 1.dp))
        }, onClick = { haptics.play(Haptic.Select); currentSelect(HomeDestination.SEARCH) }, indication = null)
    }
    }
    if (classic) {
        Box(modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
            val progress = classicAccent ?: MaterialTheme.colorScheme.primary
            val track = progress.copy(alpha = .18f)
            Box(Modifier.matchParentSize().graphicsLayer { alpha = dockNavigationReveal(playerProgress()) }
                .classicDockSurface(trackAccent))
            if (song != null && !searchActive) {
                val visible by remember(playerProgress) {
                    derivedStateOf { dockNavigationReveal(playerProgress()) > .001f }
                }
                val waveHeight = animateFloatAsState(if (isPlaying && !reduced) 1f else 0f,
                    if (reduced) snap() else tween(145), label = "classicProgressWave")
                val moving by remember(visible, isPlaying) {
                    derivedStateOf { visible && (isPlaying || waveHeight.value > .001f) }
                }
                val phase = rememberAmbientPhase(moving, radiansPerSecond = 3.3f)
                Box(Modifier.align(androidx.compose.ui.Alignment.TopCenter).fillMaxWidth().height(11.dp)
                    .graphicsLayer { alpha = dockNavigationReveal(playerProgress()) }
                    .classicPlaybackProgress(track, progress, playbackFraction, { phase.value }, { waveHeight.value }))
            }
            bar(Modifier.widthIn(max = 560.dp).padding(start = 4.dp, end = 4.dp,
                top = if (song != null && !searchActive) 10.dp else 0.dp, bottom = classicInset))
        }
    } else bar(modifier)
}

/** Keeps Classic dock accents chromatic rather than double-washing toward white. */
private fun classicTonalAccent(color: Color, darkSurface: Boolean): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return if (hsv[1] > .08f) {
        if (darkSurface) Color.hsv(hsv[0], hsv[1].coerceIn(.40f, .72f), .94f)
        else Color.hsv(hsv[0], hsv[1].coerceIn(.45f, .85f), .48f)
    } else {
        if (darkSurface) lerp(color, Color.White, .40f) else lerp(color, Color.Black, .36f)
    }
}

private fun Modifier.classicPlaybackProgress(
    track: Color, progress: Color, fraction: () -> Float, phase: () -> Float, waveHeight: () -> Float,
) = drawWithCache {
    val start = 16.dp.toPx()
    val length = (size.width - 2f * start).coerceAtLeast(0f)
    val center = size.height * .5f
    val maxAmplitude = 2.4.dp.toPx()
    val wavelength = 32.dp.toPx()
    val frequency = (2f * PI.toFloat()) / wavelength
    val step = wavelength * .25f
    val strokeWidth = 3.5.dp.toPx()
    val trackStrokeWidth = 3.0.dp.toPx()
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    val wave = Path()
    onDrawBehind {
        val played = length * fraction().coerceIn(0f, 1f)
        val remainingStart = if (played > 0f) start + played + 5.5.dp.toPx() else start
        if (start + length > remainingStart) {
            drawLine(track, Offset(remainingStart, center), Offset(start + length, center),
                trackStrokeWidth, StrokeCap.Round)
            drawCircle(progress.copy(alpha = .75f), 1.3.dp.toPx(), Offset(start + length, center))
        }
        if (played <= 0f) return@onDrawBehind
        val amplitude = maxAmplitude * waveHeight()
        if (amplitude <= .01f) {
            drawLine(progress, Offset(start, center), Offset(start + played, center), strokeWidth, StrokeCap.Round)
            return@onDrawBehind
        }
        val shift = phase()
        var x = 0f
        var y = center + amplitude * sin(shift)
        var slope = amplitude * frequency * cos(shift)
        wave.rewind()
        wave.moveTo(start, y)
        while (x < played) {
            val next = (x + step).coerceAtMost(played)
            val angle = next * frequency + shift
            val nextY = center + amplitude * sin(angle)
            val nextSlope = amplitude * frequency * cos(angle)
            val handle = (next - x) / 3f
            wave.cubicTo(start + x + handle, y + slope * handle,
                start + next - handle, nextY - nextSlope * handle, start + next, nextY)
            x = next
            y = nextY
            slope = nextSlope
        }
        drawPath(wave, progress, style = stroke)
    }
}
