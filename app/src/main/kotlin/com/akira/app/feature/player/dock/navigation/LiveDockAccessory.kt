package com.akira.app.feature.player.dock.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import com.akira.app.core.design.glass.LocalGlassDeformation
import com.akira.app.core.design.components.LocalContentMotionReduced
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.ceil
import kotlin.math.sqrt
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.glass.LocalGlassExport
import com.akira.app.core.design.backdrop.backdrops.LayerBackdrop

internal val LocalDockNavigationGlass=staticCompositionLocalOf<LayerBackdrop?> {null}
internal val LocalDockMorphActive=staticCompositionLocalOf { false }
internal val LocalDockTargetInline=staticCompositionLocalOf { false }
internal val LocalDockSurfaceContact=staticCompositionLocalOf<com.akira.app.core.design.components.AkiraPressResponse?> { null }

/** Physical coordinates throughout; the placement must not mirror them again. */
internal fun dockFoldSelectionTarget(inline: Boolean, width: Int, compactHeight: Dp,
    expandedHeight: Dp, count: Int, selected: Int, padding: PaddingValues,
    spacing: Dp, density: Density, direction: LayoutDirection): Rect = with(density) {
    require(count > 0 && selected in 0 until count)
    val h = expandedHeight.toPx()
    val leftPad = padding.calculateLeftPadding(direction).toPx()
    val rightPad = padding.calculateRightPadding(direction).toPx()
    val cell = (width - 10.dp.toPx() - leftPad - rightPad - spacing.toPx() * (count - 1)) / count
    val rtl = direction == LayoutDirection.Rtl
    val index = if (rtl) count - 1 - selected else selected
    val left = 5.dp.toPx() + leftPad + index * (cell + spacing.toPx())
    val compactWidth = compactHeight.toPx()
    val compactStart = if (selected == count - 1) width - 7.dp.toPx() - compactWidth else 7.dp.toPx()
    val compactLeft = if (rtl) width - compactStart - compactWidth else compactStart
    if (inline) Rect(compactLeft, -compactWidth, compactLeft + compactWidth, 0f)
    else Rect(left, -h + padding.calculateTopPadding().toPx(), left + cell, -padding.calculateBottomPadding().toPx())
}

/** Selection is laid out at its travelling bounds, never stretched from the
 * expanded row's recorded layer. A 50% corner stays circular at every width. */
@Composable
internal fun LiveDockFoldSelection(inline: Boolean, width: Int, compactHeight: Dp,
    expandedHeight: Dp, count: Int, selected: Int, padding: androidx.compose.foundation.layout.PaddingValues,
    spacing: Dp, color: androidx.compose.ui.graphics.Color, moving: Boolean, modifier: Modifier) {
    if(inline || count <= 0 || selected !in 0 until count)return
    val density=androidx.compose.ui.platform.LocalDensity.current
    val direction=androidx.compose.ui.platform.LocalLayoutDirection.current
    val target = dockFoldSelectionTarget(inline, width, compactHeight, expandedHeight,
        count, selected, padding, spacing, density, direction)
    val bounds by animateValueAsState(target,Rect.VectorConverter,
        if(LocalContentMotionReduced.current)snap() else DockBoundsSpec,label="dockFoldSelection")
    Layout(modifier=modifier,content={Box(Modifier
        .graphicsLayer { alpha=if(moving)1f else 0f }
        .background(color, androidx.compose.foundation.shape.RoundedCornerShape(50)))}) { children,constraints ->
        val child=children.single().measure(Constraints.fixed(bounds.width.roundToInt().coerceAtLeast(1),
            bounds.height.roundToInt().coerceAtLeast(1)))
        layout(constraints.maxWidth,constraints.maxHeight) {
            child.place(bounds.left.roundToInt(),constraints.maxHeight+bounds.top.roundToInt())
        }
    }
}

/** A single optical surface survives both row compositions. Its outline is
 * measured at the animated bounds, never scaled/clipped from a recorded row. */
@Composable
internal fun LiveDockNavigationGlass(inline: Boolean, width: Int, compactHeight: Dp,
    expandedHeight: Dp, backdrop: LayerBackdrop, surface: @Composable () -> Modifier,
    modifier: Modifier, replacementProgress: Float = 0f) {
    val density=androidx.compose.ui.platform.LocalDensity.current
    val target=with(density) {
        val left=7.dp.toPx()
        if(inline) Rect(left,-compactHeight.toPx(),left+compactHeight.toPx(),0f)
        else Rect(5.dp.toPx(),-expandedHeight.toPx(),width-5.dp.toPx(),0f)
    }
    val foldBounds by animateValueAsState(target,Rect.VectorConverter,
        if(LocalContentMotionReduced.current)snap() else DockBoundsSpec,label="dockNavigationBounds")
    val searchBounds = with(density) { Rect(0f, -compactHeight.toPx(), compactHeight.toPx(), 0f) }
    val bounds = androidx.compose.ui.geometry.lerp(foldBounds, searchBounds, replacementProgress)
    CompositionLocalProvider(LocalGlassExport provides backdrop) {
        Layout(modifier=modifier,content={Box((LocalDockSurfaceContact.current?.visualModifier ?: Modifier).then(surface()))}) { children,constraints ->
            val child=children.single().measure(Constraints.fixed(bounds.width.roundToInt().coerceAtLeast(1),
                bounds.height.roundToInt().coerceAtLeast(1)))
            layout(constraints.maxWidth,constraints.maxHeight) {
                child.placeRelative(bounds.left.roundToInt(),constraints.maxHeight+bounds.top.roundToInt())
            }
        }
    }
}

/** Both densities retain title + artist, including enlarged system fonts. */
internal fun dockMiniHeightDp(fontScale: Float, inline: Boolean): Float =
    if (inline) maxOf(48f, 30f * fontScale + 12f)
    else maxOf(34f, 30f * fontScale) + 16f

/** Material response follows deformation, not an independent timed flash. */
internal fun dockGlassDeformation(width: Float, travel: Float): Float =
    (abs(width - travel) * 2.4f).coerceIn(0f, 1f)

internal fun dockAccessoryBounds(container: IntSize, compact: IntSize, expanded: IntSize,
    expandedHeight: Int, width: Float, travel: Float, height: Float): Rect {
    val w = (expanded.width + (compact.width - expanded.width) * width).coerceAtLeast(1f)
    val h = (expanded.height + (compact.height - expanded.height) * height).coerceAtLeast(1f)
    val from = expandedHeight - expanded.height / 2f
    val center = from + (compact.height / 2f - from) * travel
    val left = (container.width - w) / 2f
    val top = container.height - center - h / 2f
    return Rect(left, top, left + w, top + h)
}

/** Leading-axis fit to the supplied recording, not Apple's private constants.
 * The other axis follows the leading surface rather than stepping to its end. */
@Stable
internal class DockFoldMotion(inline: Boolean) {
    private val widthState=mutableFloatStateOf(if(inline)1f else 0f)
    private val travelState=mutableFloatStateOf(if(inline)1f else 0f)
    private val heightState=mutableFloatStateOf(if(inline)1f else 0f)
    val width: State<Float> get()=widthState
    val travel: State<Float> get()=travelState
    val height: State<Float> get()=heightState
    var reduced by mutableStateOf(false)
    private var widthVelocity=0f
    private var travelVelocity=0f
    private var heightVelocity=0f

    fun snap(inline: Boolean) {
        val end=if(inline)1f else 0f
        widthState.floatValue=end;travelState.floatValue=end;heightState.floatValue=end
        widthVelocity=0f;travelVelocity=0f;heightVelocity=0f
    }

    fun settled(inline: Boolean): Boolean {
        val end=if(inline)1f else 0f
        return abs(width.value-end)<.001f && abs(travel.value-end)<.001f && abs(height.value-end)<.001f &&
            abs(widthVelocity)<.008f && abs(travelVelocity)<.008f && abs(heightVelocity)<.008f
    }

    /** Both axes keep position and velocity across a reversal. Substeps avoid
     * a frame-rate-dependent spring, including 60/90/120 Hz and dropped frames.
     * Only publish once per frame; no array allocations or idle ticker.
     */
    fun advance(inline: Boolean, elapsedSeconds: Float) {
        val elapsed=elapsedSeconds.coerceIn(0f,.064f)
        val steps=ceil(elapsed/.001f).toInt().coerceAtLeast(1)
        val dt=elapsed/steps
        val end=if(inline)1f else 0f
        var w=width.value;var y=travel.value;var h=height.value
        val widthK=if(inline)320f else DockSpring.WidthStiffness
        val travelK=if(inline)DockSpring.WidthStiffness else DockSpring.TravelStiffness
        val widthDrag=2f*(if(inline)DockSpring.TravelDamping else DockSpring.WidthDamping)*sqrt(widthK)
        val travelDrag=2f*(if(inline)DockSpring.WidthDamping else DockSpring.TravelDamping)*sqrt(travelK)
        val heightK=DockSpring.WidthStiffness
        val heightDrag=2f*DockSpring.HeightDamping*sqrt(heightK)
        repeat(steps) {
            // Width leads collapse; vertical travel leads expansion. The
            // follower starts immediately with growing acceleration, not delay.
            val widthTarget=if(inline)end else y
            val travelTarget=if(inline)w else end
            widthVelocity+=(widthK*(widthTarget-w)-widthDrag*widthVelocity)*dt
            travelVelocity+=(travelK*(travelTarget-y)-travelDrag*travelVelocity)*dt
            heightVelocity+=(heightK*(end-h)-heightDrag*heightVelocity)*dt
            w+=widthVelocity*dt;y+=travelVelocity*dt;h+=heightVelocity*dt
        }
        widthState.floatValue=w;travelState.floatValue=y;heightState.floatValue=h
    }
    fun deformation(): Float = if(reduced)0f else dockGlassDeformation(width.value,travel.value)
    fun strain(): androidx.compose.ui.geometry.Offset {
        if(reduced)return androidx.compose.ui.geometry.Offset.Zero
        val lead=(width.value-travel.value).coerceIn(-.4f,.4f)
        return androidx.compose.ui.geometry.Offset(lead*.55f,-lead*.85f)
    }
}
internal val LocalDockFoldMotion=staticCompositionLocalOf<DockFoldMotion?> { null }

@Composable
internal fun rememberDockFoldMotion(inline: Boolean): DockFoldMotion {
    val reduced = LocalContentMotionReduced.current
    val motion=remember { DockFoldMotion(inline) }
    SideEffect { motion.reduced=reduced }
    LaunchedEffect(motion,inline,reduced) {
        if(reduced) { motion.snap(inline);return@LaunchedEffect }
        var previous=withFrameNanos { it }
        while(!motion.settled(inline)) {
            val frame=withFrameNanos { it }
            motion.advance(inline,(frame-previous)/1_000_000_000f)
            previous=frame
        }
        motion.snap(inline)
    }
    return motion
}

/** The clip, rim and optical material must use the same deforming outline. */
@Composable
internal fun rememberDockGlassShape(): com.akira.app.core.design.motion.ElasticGlassShape {
    val density=androidx.compose.ui.platform.LocalDensity.current
    return remember(density) {
        com.akira.app.core.design.motion.ElasticGlassShape({Float.MAX_VALUE},{0f},
            { androidx.compose.ui.geometry.Offset.Zero },density,preserveCapsuleEnds=true)
    }
}

/**
 * One living surface, never a shared-element texture of the old capsule.
 * Place/measure at the current spring bounds so the backdrop reads the page at
 * this frame's actual position and refracts it using this frame's outline.
 * Coordinates are relative to the bottom: AnimatedContent's changing height
 * must not add a second vertical trajectory to the accessory's own spring.
 */
@Composable
internal fun LiveDockAccessory(
    inline: Boolean,
    compactSize: IntSize,
    expandedSize: IntSize,
    expandedHeight: Int,
    modifier: Modifier,
    content: @Composable (Modifier, Float) -> Unit,
) {
    val motion=LocalDockFoldMotion.current ?: rememberDockFoldMotion(inline)
    val reduced=motion.reduced
    val width=motion.width;val travel=motion.travel;val height=motion.height
    val material: () -> Float = remember(reduced, width, travel) {
        { if (reduced) 0f else dockGlassDeformation(width.value, travel.value) }
    }

    val strain: () -> androidx.compose.ui.geometry.Offset = remember(reduced, width, travel) {
        { if(reduced) androidx.compose.ui.geometry.Offset.Zero else {
            val lead=(width.value-travel.value).coerceIn(-.4f,.4f)
            androidx.compose.ui.geometry.Offset(lead*.55f,-lead*.85f)
        } }
    }
    CompositionLocalProvider(LocalGlassDeformation provides material,
        com.akira.app.core.design.glass.LocalGlassStrain provides strain) {
        Layout(modifier = modifier, content = {
            // A persistent composition also preserves press/gesture state when
            // the user reverses a fold. Only the layout changes.
            content(Modifier, width.value.coerceIn(0f, 1f))
        }) { measurables, constraints ->
            val compact = compactSize.takeUnless { it == IntSize.Zero } ?: expandedSize
            val bounds = dockAccessoryBounds(IntSize(constraints.maxWidth, constraints.maxHeight),
                compact, expandedSize, expandedHeight, width.value, travel.value, height.value)
            val child = measurables.single().measure(Constraints.fixed(
                bounds.width.roundToInt().coerceAtLeast(1), bounds.height.roundToInt().coerceAtLeast(1)))
            layout(constraints.maxWidth, constraints.maxHeight) {
                child.placeRelative(bounds.left.roundToInt(), bounds.top.roundToInt())
            }
        }
    }
}
