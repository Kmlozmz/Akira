package com.akira.app.core.design.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.akira.app.core.design.backdrop.Backdrop
import com.akira.app.core.design.glass.LocalAppBackdrop
import kotlin.math.roundToInt

internal val LocalNavigationBackLayer = staticCompositionLocalOf<NavigationBackLayer?> { null }
internal val LocalNavigationBackPriority = staticCompositionLocalOf { 0 }
private val LocalNavigationBackScene = staticCompositionLocalOf<BackScene?> { null }

internal class NavigationBackLayer {
    internal val entries = mutableStateListOf<BackEntry>()
}

internal class BackScene(private val outer: BackScene?, active: Boolean) {
    var active by mutableStateOf(active)
    val depth: Int = (outer?.depth ?: 0) + 1
    val visible: Boolean get() = active && (outer?.visible != false)
    var coordinates: LayoutCoordinates? = null
}

internal class BackEntry(
    val scene: BackScene,
    val priority: Int,
    val content: State<@Composable () -> Unit>,
    colors: ColorScheme,
    backdrop: Backdrop,
) {
    var colors by mutableStateOf(colors)
    var backdrop by mutableStateOf(backdrop)
    var placement by mutableStateOf<IntOffset?>(null)
    var size by mutableStateOf(IntSize.Zero)
        private set
    private var anchor: LayoutCoordinates? = null

    fun positionAt(coordinates: LayoutCoordinates) {
        anchor = coordinates
        size = coordinates.size
        updatePlacement()
    }

    fun updatePlacement() {
        val source = anchor?.takeIf { it.isAttached } ?: return
        val page = scene.coordinates?.takeIf { it.isAttached } ?: return
        // Coordinates within the destination stay stable while its outer layer slides or scales.
        val local = page.localPositionOf(source, Offset.Zero)
        if (local.x.isFinite() && local.y.isFinite()) placement = IntOffset(local.x.roundToInt(), local.y.roundToInt())
    }
}

/** Marks who owns Back without making the control a child of the page transition. */
@Composable
internal fun NavigationBackScene(active: Boolean, content: @Composable () -> Unit) {
    val layer = LocalNavigationBackLayer.current
    if (layer == null) {
        content()
        return
    }
    val parent = LocalNavigationBackScene.current
    val scene = remember(parent) { BackScene(parent, active) }
    SideEffect { scene.active = active }
    Box(Modifier.fillMaxSize().onGloballyPositioned {
        scene.coordinates = it
        layer.entries.forEach { entry -> if (entry.scene === scene) entry.updatePlacement() }
    }) {
        CompositionLocalProvider(LocalNavigationBackScene provides scene, content = content)
    }
}

@Composable
internal fun NavigationBackSlot(placeholder: @Composable () -> Unit, content: @Composable () -> Unit) {
    val layer = LocalNavigationBackLayer.current
    val scene = LocalNavigationBackScene.current
    if (layer == null || scene == null) {
        content()
        return
    }
    val priority = LocalNavigationBackPriority.current
    val render = rememberUpdatedState(content)
    val colors = MaterialTheme.colorScheme
    val backdrop = LocalPageHeaderBackdrop.current ?: LocalAppBackdrop.current
    val entry = remember(scene, priority) { BackEntry(scene, priority, render, colors, backdrop) }
    SideEffect { entry.colors = colors; entry.backdrop = backdrop }
    DisposableEffect(layer, entry) {
        layer.entries.add(entry)
        onDispose { layer.entries.remove(entry) }
    }
    Box(Modifier.onGloballyPositioned(entry::positionAt)) { placeholder() }
}

@Composable
internal fun NavigationBackOverlay(layer: NavigationBackLayer) {
    val selected = layer.entries.asSequence().filter { it.scene.visible && it.placement != null }
        .maxWithOrNull(compareBy<BackEntry> { it.priority }.thenBy { it.scene.depth }) ?: return
    val density = LocalDensity.current
    val width = with(density) { selected.size.width.toDp() }
    val height = with(density) { selected.size.height.toDp() }
    MaterialTheme(colorScheme = selected.colors) {
        CompositionLocalProvider(LocalPageHeaderBackdrop provides selected.backdrop) {
            Box(Modifier.absoluteOffset { selected.placement ?: IntOffset.Zero }.size(width, height)) {
                selected.content.value()
            }
        }
    }
}
