package com.akira.app.core.design.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.*
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.design.glass.LocalAppBackdrop
import com.akira.app.core.design.glass.LocalLiquidGlassEnabled
import com.akira.app.core.design.backdrop.Backdrop
import com.akira.app.core.design.backdrop.backdrops.rememberLayerBackdrop
import com.akira.app.core.design.backdrop.backdrops.layerBackdrop
import com.akira.app.core.design.components.LocalContentMotionReduced
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.*
import com.akira.app.core.design.motion.presentationBoundsSpec
import com.akira.app.core.design.motion.presentationProgress
import com.akira.app.core.design.motion.presentationContentTransform
import com.akira.app.core.design.glass.GlassContrast
import com.akira.app.core.design.glass.glassContentColor
import com.akira.app.core.design.glass.isGlassSupported
import androidx.compose.ui.graphics.luminance
import com.akira.app.core.design.glass.LocalGlassDeformation

private val RectConverter = TwoWayConverter<Rect, AnimationVector4D>(
    { AnimationVector4D(it.left, it.top, it.right, it.bottom) },
    { Rect(it.v1, it.v2, it.v3, it.v4) }
)

internal fun presentationSourcePresence(progress: Float): Float {
    val fraction = (progress / com.akira.app.core.design.motion.PresentationMotionProfile.SourceHandoffEnd).coerceIn(0f, 1f)
    return fraction * fraction * (3f - 2f * fraction)
}

internal enum class AkiraPresentationKind { Menu, Popover, Panel, CompactPanel, Update, AudioOutput, AudioQuality }
internal class AkiraPresentationEntry(
    val kind: AkiraPresentationKind,
    val anchor: Rect?,
    val source: AkiraMenuSource?,
    val preferAbove: Boolean,
    val content: @Composable () -> Unit,
    val dismiss: () -> Unit,
) {
    var parent: AkiraPresentationEntry? = null
    var dragOffset by mutableFloatStateOf(0f)
    var releaseVelocity = 0f
    var removed by mutableStateOf(false)
    var bounds by mutableStateOf<Rect?>(null)
    var colors by mutableStateOf<ColorScheme?>(null)
    var typography: Typography? = null
    var backdrop: Backdrop? = null
    var glass by mutableStateOf(false)
    var reduced by mutableStateOf(false)
    var footer by mutableStateOf<(@Composable () -> Unit)?>(null)
}

internal class AkiraPresentationState(val anchors: AkiraMenuAnchors?) {
    val entries = mutableStateListOf<AkiraPresentationEntry>()
    var generation by mutableLongStateOf(0L)
    var closing by mutableStateOf<AkiraPresentationEntry?>(null)
    var rootAnchor: Rect? = null
    var rootSource: AkiraMenuSource? = null

    val desiredEntry: AkiraPresentationEntry?
        get() = if (closing != null && closing?.removed == false)
            entries.takeWhile { it !== closing }.lastOrNull { !it.removed }
        else entries.lastOrNull { !it.removed }

    // Direct actions dispose their caller before the return animation finishes.
    val returningEntry: AkiraPresentationEntry?
        get() = closing ?: entries.lastOrNull()

    fun showsBody(entry: AkiraPresentationEntry): Boolean =
        entry === desiredEntry || (desiredEntry == null && entry === returningEntry)

    fun add(entry: AkiraPresentationEntry) {
        entry.parent = entries.lastOrNull { !it.removed }
        if (entries.isEmpty()) {
            generation++
            closing = null
            rootAnchor = entry.anchor
            rootSource = entry.source
            anchors?.hiddenSource = entry.source
        }
        entries.add(entry)
    }

    fun release() {
        entries.removeAll { it.removed }
        if (entries.isEmpty()) {
            anchors?.hiddenSource = null
            anchors?.clear()
            rootSource?.restoreFocus()
            rootSource = null
            rootAnchor = null
            closing = null
        }
    }
}

internal val LocalAkiraPresentationState = staticCompositionLocalOf<AkiraPresentationState?> { null }
internal val LocalAkiraPresentationEntry = staticCompositionLocalOf<AkiraPresentationEntry?> { null }
/** Overlay source supplied by the foreground scene, excluding the overlay
 * itself. Inline controls retain their own local backdrop to avoid recursion. */
internal val LocalAkiraOverlayBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** One window and one continuous surface for a chain of menus, selectors and editors.
 * This host is a sibling of page/backdrop capture, never part of its own sample. */
@Composable
fun AkiraPresentationHost(content: @Composable () -> Unit) {
    val anchors = LocalAkiraMenuAnchors.current
    val state = remember(anchors) { AkiraPresentationState(anchors) }
    val pages = remember(state) { AkiraPageSheetState {
        // Moving from a contextual action to a page replaces the menu. It must
        // not remain suspended, or reopen when the page is dismissed.
        state.entries.toList().forEach { it.removed = true; it.dismiss() }
        state.release()
    } }
    val scene = rememberLayerBackdrop()
    val presenting = state.entries.isNotEmpty() || pages.entries.isNotEmpty()
    val sampleScene = presenting && LocalLiquidGlassEnabled.current && isGlassSupported()
    CompositionLocalProvider(LocalAkiraPresentationState provides state, LocalAkiraPageSheetState provides pages) {
      Box(Modifier.fillMaxSize().drawBehind {
          if (pages.presence > 0f) drawRect(Color.Black)
      }) {
        // Capture the transformed, live scene, excluding every dialog surface.
        Box(Modifier.fillMaxSize()
            .then(if (presenting) Modifier.clearAndSetSemantics {} else Modifier)
            .then(if (sampleScene) Modifier.layerBackdrop(scene) else Modifier)) {
            Box(Modifier.fillMaxSize().pageSheetScene(pages)) { content() }
        }
        if (presenting) AkiraPresentationLayer(state, pages, scene)
      }
    }
}

/** Overlays sample their sibling scene without a second window or captured copy of themselves. */
@Composable
private fun AkiraPresentationLayer(state: AkiraPresentationState, pages: AkiraPageSheetState, scene: Backdrop) {
    androidx.activity.compose.BackHandler {
        if (state.entries.isNotEmpty()) {
            if (state.closing == null) state.closing = state.entries.lastOrNull { !it.removed }
        } else pages.close()
    }
    com.akira.app.core.design.rendering.PreferInteractiveFrameRate()
    Box(Modifier.fillMaxSize()) {
        if (pages.entries.isNotEmpty()) AkiraPageSheetOverlay(pages, scene)
        if (state.entries.isNotEmpty()) AkiraPresentationOverlay(state, scene)
        else AkiraNoticeHost(Modifier.align(Alignment.BottomCenter).navigationBarsPadding(), priority = 3)
    }
}

@Composable
internal fun AkiraPresentation(
    kind: AkiraPresentationKind,
    onDismiss: () -> Unit,
    preferAbove: Boolean = false,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val host = LocalAkiraPresentationState.current
    if (host == null) {
        // Onboarding and previews can use the same component outside LibraryScreen.
        AkiraPresentationHost { AkiraPresentation(kind, onDismiss, preferAbove, footer, content) }
        return
    }
    val latestContent by rememberUpdatedState(content)
    val latestDismiss by rememberUpdatedState(onDismiss)
    val latestFooter by rememberUpdatedState(footer)
    val footerBody: (@Composable () -> Unit)? = remember(footer != null) {
        if(footer != null) { { latestFooter?.invoke() } } else null
    }
    val anchors = LocalAkiraMenuAnchors.current
    val entry = remember {
        AkiraPresentationEntry(kind, anchors?.freshRect(), anchors?.freshSource(), preferAbove,
            { latestContent() }, { latestDismiss() })
    }
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val backdrop = LocalAkiraOverlayBackdrop.current ?: LocalAppBackdrop.current
    val glass = LocalLiquidGlassEnabled.current
    val reduced = LocalContentMotionReduced.current
    // Keep the page/player's own material context without overriding the dialog's
    // LocalView, input connection, focus manager, or window insets.
    SideEffect {
        entry.colors = colors
        entry.typography = typography
        entry.backdrop = backdrop
        entry.glass = glass
        entry.reduced = reduced
        entry.footer = footerBody
    }
    DisposableEffect(entry) {
        host.add(entry)
        onDispose { entry.removed = true }
    }
}

@Composable
private fun AkiraPresentationOverlay(state: AkiraPresentationState, scene: Backdrop) {
    val active = state.entries.isNotEmpty()
    val idleEntry = remember { AkiraPresentationEntry(AkiraPresentationKind.Menu, null, null, false, {}, {}) }
    idleEntry.glass = LocalLiquidGlassEnabled.current
    val top = state.entries.lastOrNull { !it.removed }
    val closing = state.closing
    val desired = state.desiredEntry
    val first = state.entries.firstOrNull() ?: idleEntry
    val density = LocalDensity.current
    val reduced = desired?.reduced ?: first.reduced
    val initial = remember { state.rootAnchor ?: Rect(0f, 0f, 1f, 1f) }
    val geometry = remember { Animatable(initial, RectConverter) }
    val materialization = remember { Animatable(0f) }
    val corner = remember { Animatable(min(initial.width, initial.height) / 2f) }
    val smoothing = remember { Animatable(if (state.rootAnchor == null) 1f else 0f) }
    val destination = desired?.bounds
    val liveOrigin = state.rootSource?.bounds() ?: state.rootAnchor.takeIf { state.rootSource == null }
    val backdropEntry = desired ?: top ?: state.entries.lastOrNull() ?: idleEntry
    val source = state.rootSource
    val progressEntry by rememberUpdatedState(desired ?: closing ?: top ?: first)
    val returning by rememberUpdatedState(desired == null)
    val returnFloor = remember { mutableFloatStateOf(1f) }
    var windowOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    fun sourceProgress(): Float {
        if(first.kind==AkiraPresentationKind.AudioOutput)return materialization.value.coerceIn(0f,1f)
        val origin = state.rootAnchor ?: return 1f
        val target = progressEntry.bounds ?: return 0f
        return presentationProgress(geometry.value,
            origin.translate(-windowOrigin.x, -windowOrigin.y), target)
    }
    fun handoffProgress(): Float {
        val progress = sourceProgress()
        return if (returning) min(progress, returnFloor.floatValue) else progress
    }
    LaunchedEffect(returning, state.generation) {
        returnFloor.floatValue = 1f
        if (returning) snapshotFlow { sourceProgress() }.collect { progress ->
            // A settling spring must not hide the restored control again.
            returnFloor.floatValue = min(returnFloor.floatValue, progress)
        }
    }
    DisposableEffect(source) {
        source?.progressProvider = ::handoffProgress
        source?.returningProvider = { returning }
        onDispose { source?.progressProvider = null; source?.returningProvider = null }
    }
    var preparedGeneration by remember { mutableLongStateOf(-1L) }
    var returnedGeneration by remember { mutableLongStateOf(-1L) }
    var expandedGeneration by remember { mutableLongStateOf(-1L) }
    fun opticalProgress(): Float = if (!returning &&
        (expandedGeneration == state.generation || progressEntry !== first)) 1f else sourceProgress()
    fun close() {
        if (state.closing == null && top != null) state.closing = top
    }

    LaunchedEffect(desired, destination, closing, windowOrigin, reduced, liveOrigin, state.generation) {
        if (desired != null && destination == null) return@LaunchedEffect
        // Disposing the caller clears `closing` in a later composition. That
        // bookkeeping must not run the capsule's return trajectory a second time.
        if(desired==null && returnedGeneration==state.generation) {
            state.release()
            return@LaunchedEffect
        }
        val departingSource=active && preparedGeneration!=state.generation
        if (departingSource) {
            val origin = liveOrigin?.translate(-windowOrigin.x, -windowOrigin.y)
                ?: destination?.let { Rect(it.center.x-1f,it.center.y-1f,it.center.x+1f,it.center.y+1f) }
                ?: Rect(0f,0f,1f,1f)
            geometry.snapTo(origin)
            corner.snapTo(min(origin.width,origin.height)/2f)
            smoothing.snapTo(if(liveOrigin==null)1f else 0f)
            preparedGeneration = state.generation
        }
        val dissolving = desired==null && first.kind==AkiraPresentationKind.AudioOutput
        val end = if(dissolving) geometry.value.translate(0f,with(density){6.dp.toPx()}) else destination ?: liveOrigin?.translate(-windowOrigin.x, -windowOrigin.y)
            ?: geometry.value.let { Rect(it.center.x - 1f, it.center.y - 1f, it.center.x + 1f, it.center.y + 1f) }
        if (state.rootAnchor == null && geometry.value.width <= 1f && destination != null) {
            val center = destination.center
            geometry.snapTo(Rect(center.x - 8f, center.y - 8f, center.x + 8f, center.y + 8f))
        }
        val radius = if (desired != null) with(density) {
            (when(desired.kind) { AkiraPresentationKind.AudioOutput->34.dp; AkiraPresentationKind.Update, AkiraPresentationKind.AudioQuality->32.dp; AkiraPresentationKind.Panel->28.dp; else->22.dp }).toPx()
        } else if(dissolving)corner.value else min(end.width, end.height) / 2f
        val dragged = top?.dragOffset ?: 0f
        val releaseVelocity = top?.releaseVelocity ?: 0f
        if (dragged != 0f) {
            geometry.snapTo(geometry.value.translate(0f, dragged))
            top?.dragOffset = 0f
            top?.releaseVelocity = 0f
        }
        if (reduced) {
            geometry.snapTo(end); corner.snapTo(radius); smoothing.snapTo(if (desired != null) 1f else 0f)
            materialization.snapTo(if(desired!=null)1f else 0f)
        } else coroutineScope {
            val travel = presentationBoundsSpec(desired!=null,
                    liveOrigin!=null && (departingSource || desired==null))
            launch { geometry.animateTo(end, travel,
                initialVelocity = if (dragged != 0f) Rect(0f, releaseVelocity, 0f, releaseVelocity) else geometry.velocity) }
            launch { corner.animateTo(radius, com.akira.app.core.design.motion.AkiraMotion.settle()) }
            launch { smoothing.animateTo(if (desired != null) 1f else 0f, com.akira.app.core.design.motion.AkiraMotion.settle()) }
            launch { materialization.animateTo(if(desired!=null)1f else 0f,
                tween(if(dissolving)180 else 110)) }

        }
        if(desired==null)returnedGeneration=state.generation
        else expandedGeneration=state.generation
        if (closing != null && !closing.removed) closing.dismiss()
        state.release()
    }
    // A dismissal callback updates its caller on the following composition. Keep
    // the returning surface until then, rather than briefly opening it again.
    SideEffect { if (state.closing?.removed == true) state.closing = null }

    val scrim = animateFloatAsState(if (desired != null) .12f else 0f,
        if (reduced) snap() else tween(180), label = "presentationScrim")
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { windowOrigin = it.positionInWindow() }) {
            val window = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
            val layoutDirection = LocalLayoutDirection.current
            val insets = WindowInsets.safeDrawing.union(WindowInsets.ime)
            val margin = with(density) { 16.dp.toPx() }
            val safe = Rect(insets.getLeft(density, layoutDirection).toFloat() + margin,
                insets.getTop(density).toFloat() + margin,
                window.width - insets.getRight(density, layoutDirection) - margin,
                window.height - insets.getBottom(density) - margin)
            Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = scrim.value)) }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    close()
                })
            val shape = remember(corner, smoothing, density) {
                com.akira.app.core.design.motion.PresentationShape({ corner.value }, { smoothing.value }, density)
            }
            val copies = state.entries.toList()
            val returningEntry = state.returningEntry
            val bodies = copies.map { entry ->
                remember(entry, desired, reduced, returningEntry) {
                    @Composable {

                        val visible = animateFloatAsState(if (state.showsBody(entry)) 1f else 0f,
                            if (reduced) snap() else tween(140), label = "presentationContent")
                        Box(Modifier.graphicsLayer {
                            val final = entry.bounds
                            val p=opticalProgress()
                            val pose=presentationContentTransform(geometry.value.size,
                                final?.size ?: geometry.value.size,p)
                            val reveal=((p-.06f)/.34f).coerceIn(0f,1f)
                            transformOrigin=androidx.compose.ui.graphics.TransformOrigin(0f,0f)
                            scaleX=pose.scale;scaleY=pose.scale
                            translationX=pose.offset.x;translationY=pose.offset.y
                            renderEffect = null
                            // The parent fades the returning surface and its contents together.
                            val presence=reveal*reveal*(3f-2f*reveal)
                            alpha = visible.value * presence
                        }
                            .pointerInput(entry === desired) {
                                if (entry !== desired) awaitPointerEventScope {
                                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                }
                            }.then(if (entry !== desired) Modifier.clearAndSetSemantics { } else Modifier)) {
                            CompositionLocalProvider(LocalAkiraPresentationEntry provides entry) { entry.content() }
                        }
                    }
                }
            }
            val sourceGlyph = remember(first, geometry, active, shape, reduced) {
                @Composable {

                    val icon = if(active && source?.hasOwnSurface==true) source.icon else null
                    val body=if(active && source?.hasOwnSurface==true)source.surfaceContent else null
                    if(body!=null) Box(Modifier.graphicsLayer {
                        val origin=state.rootAnchor ?: return@graphicsLayer
                        val p=(sourceProgress()/.45f).coerceIn(0f,1f)
                        alpha=1f-p*p*(3f-2f*p)
                        scaleX=1f; scaleY=1f
                        renderEffect = null
                    }) {body()}
                    else if (icon != null) Icon(icon, null,
                        Modifier.size(22.dp).graphicsLayer {
                            val sourceWidth = state.rootAnchor?.width ?: 1f
                            alpha = ((sourceWidth * 2f - geometry.value.width) / sourceWidth.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        }, tint = MaterialTheme.colorScheme.onSurface)
                    else Spacer(Modifier.size(1.dp))
                }
            }
            MaterialTheme(colorScheme = backdropEntry.colors ?: MaterialTheme.colorScheme,
                typography = backdropEntry.typography ?: MaterialTheme.typography) {
                val scheme=MaterialTheme.colorScheme
                val ink=glassContentColor()
                val lightInk=ink.luminance()>.5f
                val readableScheme=remember(scheme,ink,backdropEntry.glass) {
                    if(backdropEntry.glass && isGlassSupported()) {
                        val primary=GlassContrast.readableAccent(scheme.primary,lightInk)
                        scheme.copy(onSurface=ink,onSurfaceVariant=ink.copy(alpha=GlassContrast.SecondaryAlpha),
                            primary=primary,onPrimary=if(primary.luminance()>.179f)Color.Black else Color.White,
                            error=GlassContrast.readableAccent(scheme.error,lightInk))
                    } else scheme
                }
                MaterialTheme(colorScheme=readableScheme) {
                CompositionLocalProvider(LocalAppBackdrop provides scene,
                    com.akira.app.core.design.glass.LocalGlassReadableSurface provides true,
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                    LocalLiquidGlassEnabled provides backdropEntry.glass,
                    LocalContentMotionReduced provides reduced,
                    androidx.compose.foundation.LocalOverscrollFactory provides null,
                    LocalGlassDeformation provides { 0f }) {
                    val materialRole=if(first.kind==AkiraPresentationKind.AudioOutput)AkiraMaterialRole.AudioOutput else AkiraMaterialRole.Panel
                    SubcomposeLayout(Modifier.graphicsLayer {
                        // A bare glyph or a member of a shared capsule has no
                        // independent circular material to return to.
                        alpha = if(!active)0f else if(first.kind==AkiraPresentationKind.AudioOutput)materialization.value.coerceIn(0f,1f)
                            else if(source!=null && (!source.hasOwnSurface || returning))
                                presentationSourcePresence(handoffProgress()) else 1f
                    }.offset {
                        val rect = geometry.value.translate(0f, top?.dragOffset ?: 0f)
                        IntOffset(rect.left.roundToInt(), rect.top.roundToInt())
                    }
                        .akiraMaterial(shape, materialRole).graphicsLayer {
                            this.shape = shape
                            renderEffect = null
                            clip = true
                        }) { constraints ->
                        val rect = geometry.value.translate(0f, top?.dragOffset ?: 0f)
                        val placeables = copies.mapIndexed { index, entry ->
                            val output = entry.kind == AkiraPresentationKind.AudioOutput
                            val availableWidth = when {
                                entry.kind == AkiraPresentationKind.AudioQuality -> min(safe.width, window.width * .82f)
                                output -> safe.width - with(density) { 16.dp.toPx() }
                                else -> safe.width
                            }
                            val preferredWidth = with(density) {
                                when (entry.kind) {
                                    AkiraPresentationKind.Popover -> 320.dp
                                    AkiraPresentationKind.Menu -> 260.dp
                                    AkiraPresentationKind.AudioOutput -> 340.dp
                                    AkiraPresentationKind.AudioQuality -> 328.dp
                                    AkiraPresentationKind.Update -> 380.dp
                                    AkiraPresentationKind.CompactPanel -> 360.dp
                                    else -> 560.dp
                                }.toPx()
                            }
                            val maxWidth = min(preferredWidth, availableWidth).roundToInt().coerceAtLeast(1)
                            val maxHeight = (safe.height - if(output)with(density){64.dp.toPx()} else 0f).roundToInt().coerceAtLeast(1)
                            val child = subcompose(entry, bodies[index]).single()
                                .measure(Constraints(minWidth = maxWidth, maxWidth = maxWidth, maxHeight = maxHeight))
                            val position = if (entry.kind == AkiraPresentationKind.Menu || entry.kind == AkiraPresentationKind.Popover) {
                                // Size and place every menu as a direct opening
                                // from the original button. A tall parent's top
                                // edge would leave a short submenu far above it.
                                var origin = entry
                                while(origin.parent?.kind == AkiraPresentationKind.Menu) origin = origin.parent!!
                                val anchor = origin.anchor ?: origin.source?.bounds()
                                val a = anchor?.translate(-windowOrigin.x - safe.left, -windowOrigin.y - safe.top)
                                anchoredMenuPosition(a, Size(safe.width, safe.height), Size(child.width.toFloat(), child.height.toFloat()),
                                    0f, with(density) { 8.dp.toPx() }, origin.preferAbove, layoutDirection,
                                    attachedSurface=origin.source?.hasOwnSurface==true) + safe.topLeft
                            } else if(output) androidx.compose.ui.geometry.Offset(safe.center.x-child.width/2f,
                                safe.bottom-with(density){64.dp.toPx()}-child.height)
                            else androidx.compose.ui.geometry.Offset(safe.center.x - child.width / 2f, safe.center.y - child.height / 2f)
                            entry.bounds = Rect(position, Size(child.width.toFloat(), child.height.toFloat()))
                            child
                        }
                        val glyph = subcompose("sourceGlyph", sourceGlyph).single().measure(Constraints())
                        val w = rect.width.roundToInt().coerceIn(1, constraints.maxWidth)
                        val h = rect.height.roundToInt().coerceIn(1, constraints.maxHeight)
                        layout(w, h) {
                            placeables.forEach { child ->
                                // Hidden parents stay composed so their selectors and
                                // editors can return without losing state or focus.
                                child.place(0, 0)
                            }
                            glyph.place((w - glyph.width) / 2, (h - glyph.height) / 2)
                        }
                    }
                    (desired ?: top)?.let { entry -> entry.footer?.let { footerContent ->
                        Box(Modifier.align(Alignment.BottomCenter)
                            .padding(bottom=with(density){(window.height-safe.bottom).toDp()})
                            .widthIn(max=320.dp).graphicsLayer {
                                val fraction=(geometry.value.width / (entry.bounds?.width ?: 1f)).coerceIn(0f,1f)
                                alpha=fraction*fraction*(if(entry.kind==AkiraPresentationKind.AudioOutput)materialization.value.coerceIn(0f,1f) else 1f)
                                scaleX=.8f+.2f*fraction;scaleY=scaleX
                            }.clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                            .akiraMaterial(androidx.compose.foundation.shape.RoundedCornerShape(50),materialRole)) {
                            footerContent()
                        }
                    } }
                }
            }
            }
            if (closing != null) Box(Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null) { })
            if(active) AkiraNoticeHost(Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),priority=3)
        }
}
