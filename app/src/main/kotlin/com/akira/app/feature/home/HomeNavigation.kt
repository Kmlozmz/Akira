// SPDX-License-Identifier: GPL-3.0-only
// Source attribution: THIRD_PARTY_NOTICES.md.
// FloatingTabBar/Echo and backdrop retain their original headers and licenses.
package com.akira.app.feature.home

import com.akira.app.feature.player.dock.*

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
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
    isLoading: Boolean,
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
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val reduced = com.akira.app.core.design.components.LocalContentMotionReduced.current
    val ink = com.akira.app.core.design.glass.rememberGlassInk()
    val contentColor = ink.color
    val selectedContentColor = if(contentColor == Color.Black) Color(0xFFFF2D55) else Color(0xFFFF375F)
    val haptics = rememberHaptics()
    val currentSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selected)
    val group = remember { listOf(HomeDestination.HOME, HomeDestination.LIBRARY, HomeDestination.SETTINGS) }
    val labels = listOf(localizedText("Home", "Inicio"), localizedText("Library", "Biblioteca"), localizedText("Settings", "Ajustes"), localizedText("Search", "Buscar"))
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 0.sp,
        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
    val surface: @Composable () -> Modifier = {
        Modifier.liquidGlass(rememberDockGlassShape())
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
            GlassNowPlaying(song = current, compactFraction = compactFraction, isPlaying = isPlaying, isLoading = isLoading,
                contentColor = contentColor, onPlayPause = onPlayPause, onNext = onNext,
                onPrevious = onPrevious, onExpand = onExpand, controlsLocked = false, onBlockedControl = {},
                onHoldStart = onHoldStart,
                artworkVisible = artworkVisible,
                onMiniPositioned = { state.reportMini(inline, it) },
                onArtPositioned = { coords, corner -> state.reportArtwork(inline, coords, corner) },
                pull = pull, modifier = accessoryModifier.graphicsLayer {
                    alpha = dockMiniReveal(playerProgress()) * (1f-editingFraction)
                    translationY = if (reduced) 0f else with(density) { dockMiniReturnOffsetDp(playerProgress()).dp.toPx() }
                })
        }
    }
    FloatingTabBar(
        selectedTabKey = selected,
        navigationModifier = Modifier.then(ink.modifier).graphicsLayer { alpha = dockNavigationReveal(playerProgress()) },
        scrollConnection = state.scroll,
        modifier = modifier.fillMaxWidth(),
        tabBarContentModifier = surface,
        liveAccessory = if (song == null) null else accessory,
        replacement = if (search != null && (searchActive || searchFraction > .001f)) DockNavigationReplacement(
            searchFraction, searchHeight, 1f-editingFraction, 1f-editingFraction) { slot ->
            SearchDock(search, onHome = { currentSelect(HomeDestination.HOME) }, onSubmit = { search.submit++ },
                modifier = slot, reveal = searchFraction, editingProgress = editingFraction,
                heightOverride = navigationHeight, drawHomeSurface = false, active = searchActive)
        } else null,
        colors = FloatingTabBarDefaults.colors(backgroundColor = Color.Transparent,
            accessoryBackgroundColor = Color.Transparent, indicatorColor = glassIndicatorColor().copy(alpha = .5f)),
        elevations = FloatingTabBarDefaults.elevations(inlineElevation = 0.dp, expandedElevation = 0.dp),
        sizes = FloatingTabBarDefaults.sizes(tabBarContentPadding = PaddingValues(4.dp),
            tabExpandedContentPadding = PaddingValues(vertical = 5.5.dp), componentSpacing = 10.dp),
        contentKey = remember(contentColor, selectedContentColor, labels) { listOf(contentColor, selectedContentColor, labels) },
    ) {
        group.forEachIndexed { i, destination ->
            tab(key = destination, icon = {
                val tint = if (currentSelected == destination) selectedContentColor else contentColor.copy(alpha = .65f)
                val semantics = Modifier.size(25.dp,28.dp).semantics { role = Role.Tab; this.selected = currentSelected == destination }
                if (destination == HomeDestination.SETTINGS) Icon(Icons.Rounded.Settings, labels[i], tint = tint, modifier = semantics)
                else Icon(painterResource(if (destination == HomeDestination.HOME) R.drawable.ic_navigation_home_filled else R.drawable.ic_navigation_library_filled), labels[i], tint = tint, modifier = semantics)
            }, title = {
                Text(labels[i], style = labelStyle,
                    color = if (currentSelected == destination) selectedContentColor else contentColor.copy(alpha = .65f),
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp))
            }, onClick = { if (currentSelected != destination) haptics.play(Haptic.Select); currentSelect(destination) })
        }
        standaloneTab(key = HomeDestination.SEARCH, icon = {
            Icon(painterResource(R.drawable.ic_navigation_search_outline), labels[3], tint = if(currentSelected == HomeDestination.SEARCH) selectedContentColor else contentColor.copy(alpha = .65f),
                modifier = Modifier.size(25.dp,28.dp).semantics { role = Role.Tab; this.selected = currentSelected == HomeDestination.SEARCH })
        }, title = {
            Text(labels[3], style = labelStyle,
                color = if (currentSelected == HomeDestination.SEARCH) selectedContentColor else contentColor.copy(alpha = .65f),
                maxLines = 1, modifier = Modifier.padding(top = 1.dp))
        }, onClick = { haptics.play(Haptic.Select); currentSelect(HomeDestination.SEARCH) })
    }
}
