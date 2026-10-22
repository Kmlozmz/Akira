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
import androidx.compose.ui.window.*
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.design.glass.LocalAppBackdrop
import com.akira.app.core.design.glass.LocalLiquidGlassEnabled
import com.akira.app.core.design.glass.glassAccentColor
import com.akira.app.core.design.glass.glassContentColor
import com.akira.app.core.design.glass.isGlassSupported
import com.akira.app.core.design.backdrop.Backdrop
import com.akira.app.core.design.backdrop.backdrops.rememberLayerBackdrop
import com.akira.app.core.design.backdrop.backdrops.layerBackdrop
import com.akira.app.core.design.components.LocalContentMotionReduced
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.*
import com.akira.app.core.design.motion.ElasticGlassShape
import com.akira.app.core.design.motion.ElasticContentWarp
import com.akira.app.core.design.motion.ElasticBoundsSpec
import com.akira.app.core.design.motion.MenuBoundsSpec
import com.akira.app.core.design.glass.LocalGlassDeformation

private val RectConverter = TwoWayConverter<Rect, AnimationVector4D>(
    { AnimationVector4D(it.left, it.top, it.right, it.bottom) },
    { Rect(it.v1, it.v2, it.v3, it.v4) }
)

internal enum class AkiraPresentationKind { Menu, Panel, CompactPanel, Update, AudioOutput }
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

/** One window and one optical surface for a chain of menus, selectors and editors.
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
    CompositionLocalProvider(LocalAkiraPresentationState provides state, LocalAkiraPageSheetState provides pages) {
      Box(Modifier.fillMaxSize().drawBehind {
          if (pages.presence > 0f) drawRect(Color.Black)
      }) {
        // Capture the transformed, live scene, excluding every dialog surface.
        Box(Modifier.fillMaxSize().then(if (pages.entries.isNotEmpty()) Modifier.layerBackdrop(scene) else Modifier)) {
            Box(Modifier.fillMaxSize().pageSheetScene(pages)) { content() }
        }
        // A hidden Dialog still owns a Compose text-input/window session on
        // some IMEs. Dispose it after the return animation, so Search belongs
        // exclusively to the activity again. Keep it for nested presentations.
        if (state.entries.isNotEmpty() || pages.entries.isNotEmpty()) AkiraPresentationWindow(state, pages, scene)
      }
    }
}

/** Reuse one native window while a presentation chain is open. */
@Composable
private fun AkiraPresentationWindow(state: AkiraPresentationState, pages: AkiraPageSheetState, scene: Backdrop) {
    val active = state.entries.isNotEmpty() || pages.entries.isNotEmpty()
    Dialog(onDismissRequest = {
        if (state.entries.isNotEmpty()) {
            if (state.closing == null) state.closing = state.entries.lastOrNull { !it.removed }
        } else pages.close()
    }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
        dismissOnClickOutside=true)) {
        val view = LocalView.current
        val flags = android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.setDimAmount(0f)
            // Compose owns the complete surface morph. The platform Dialog
            // fade otherwise retains an old buffer over the restored capsule.
            window?.setWindowAnimations(0)
            if (active) window?.clearFlags(flags) else window?.addFlags(flags)
            view.importantForAccessibility = if (active) android.view.View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                else android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        val frame=remember { mutableLongStateOf(0L) }
        val redraw=remember { { frame.longValue } }
        LaunchedEffect(Unit) {
            while(true)withFrameNanos { frame.longValue=it }
        }
        // Invalidate draw only, without recomposing menus or pausing lyrics.
        CompositionLocalProvider(com.akira.app.core.design.glass.LocalGlassRedrawSignal provides redraw) {
            Box(Modifier.fillMaxSize()) {
                if (pages.entries.isNotEmpty()) AkiraPageSheetOverlay(pages, scene)
                if (state.entries.isNotEmpty()) AkiraPresentationOverlay(state)
                else AkiraNoticeHost(Modifier.align(Alignment.BottomCenter).navigationBarsPadding(), priority = 3)
            }
        }
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
private fun AkiraPresentationOverlay(state: AkiraPresentationState) {
    val active = state.entries.isNotEmpty()
    val idleEntry = remember { AkiraPresentationEntry(AkiraPresentationKind.Menu, null, null, false, {}, {}) }
    idleEntry.glass = LocalLiquidGlassEnabled.current
    val top = state.entries.lastOrNull { !it.removed }
    val closing = state.closing
    val desired = if (closing != null && !closing.removed)
        state.entries.takeWhile { it !== closing }.lastOrNull { !it.removed } else top
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
    fun sourceProgress(): Float {
        if(first.kind==AkiraPresentationKind.AudioOutput)return materialization.value.coerceIn(0f,1f)
        val origin = state.rootAnchor ?: return 1f
        // Nested presentations can have a different resting size. Comparing
        // them with the first menu held strain above zero indefinitely.
        val target = progressEntry.bounds ?: return 0f
        // Area also advances when a wide capsule first becomes a round bubble:
        // its width can stay constant while its height and contents inflate.
        val from=sqrt((origin.width*origin.height).coerceAtLeast(1f))
        val to=sqrt((target.width*target.height).coerceAtLeast(1f))
        val current=sqrt((geometry.value.width*geometry.value.height).coerceAtLeast(1f))
        return if(abs(to-from)<1f)1f else ((current-from)/(to-from)).coerceIn(0f,1f)
    }
    DisposableEffect(source) {
        source?.progressProvider = ::sourceProgress
        onDispose { source?.progressProvider = null }
    }
    var preparedGeneration by remember { mutableLongStateOf(-1L) }
    var returnedGeneration by remember { mutableLongStateOf(-1L) }
    var windowOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
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
        val departingSource = active && preparedGeneration != state.generation
        if (active && preparedGeneration != state.generation) {
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
            (when(desired.kind) { AkiraPresentationKind.AudioOutput->34.dp; AkiraPresentationKind.Update->32.dp; AkiraPresentationKind.Panel->28.dp; else->22.dp }).toPx()
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
            // Akira tuning, not claimed as private Apple system constants. Animatable
            // retargets from its current value AND velocity when Back interrupts.
            val travel = if(first.kind==AkiraPresentationKind.Menu && liveOrigin!=null && (departingSource || desired==null))
                MenuBoundsSpec(desired!=null,density.density) else ElasticBoundsSpec(desired!=null,first.kind==AkiraPresentationKind.AudioOutput)
            launch { geometry.animateTo(end, travel,
                initialVelocity = if (dragged != 0f) Rect(0f, releaseVelocity, 0f, releaseVelocity) else geometry.velocity) }
            launch { corner.animateTo(radius, spring(.82f, 850f)) }
            launch { smoothing.animateTo(if (desired != null) 1f else 0f, spring(.86f, 850f)) }
            launch { materialization.animateTo(if(desired!=null)1f else 0f,
                tween(if(dissolving)180 else 110)) }
        }
        if(desired==null)returnedGeneration=state.generation
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
            val strain: () -> androidx.compose.ui.geometry.Offset = {
                val p=sourceProgress()
                val target=progressEntry.bounds
                val origin=state.rootAnchor?.translate(-windowOrigin.x,-windowOrigin.y)
                if(reduced || first.kind==AkiraPresentationKind.AudioOutput || target==null || origin==null)
                    androidx.compose.ui.geometry.Offset.Zero
                else {
                    val span=(target.center-origin.center).getDistance().coerceAtLeast(1f)
                    val direction=(origin.center-target.center)/span
                    direction*(4f*p*(1f-p))
                }
            }
            val shape = remember(corner, smoothing, density, first, reduced) {
                ElasticGlassShape({
                    val p=sourceProgress()
                    val t=((p-.45f)/.55f).coerceIn(0f,1f)
                    val settle=t*t*(3f-2f*t)
                    val round=min(geometry.value.width,geometry.value.height)/2f
                    round+(corner.value-round)*settle
                }, { smoothing.value }, strain, density)
            }
            val copies = state.entries.toList()
            val bodies = copies.map { entry ->
                remember(entry, desired, reduced) {
                    @Composable {
                        val contentWarp=remember { ElasticContentWarp() }
                        // The final body returns into its source with the surface.
                        // Fading it away in 140 ms left an empty shrinking panel.
                        val visible = animateFloatAsState(if (entry === desired || (desired == null && entry === closing)) 1f else 0f,
                            if (reduced) snap() else tween(140), label = "presentationContent")
                        Box(Modifier.graphicsLayer {
                            val final = entry.bounds
                            val sx = if(final==null)0f else geometry.value.width/final.width.coerceAtLeast(1f)
                            val sy = if(final==null)0f else geometry.value.height/final.height.coerceAtLeast(1f)
                            val reveal = ((min(sx,sy) - .10f) / .80f).coerceIn(0f, 1f)
                            // The contents transform with the surface, instead of
                            // revealing a cropped, already full-sized menu.
                            transformOrigin=androidx.compose.ui.graphics.TransformOrigin(0f,0f)
                            scaleX=sx.coerceAtLeast(.001f)
                            scaleY=sy.coerceAtLeast(.001f)
                            renderEffect=if(reduced || final==null)null else contentWarp.effect(final.size,shape.strain())
                            alpha = visible.value * reveal * reveal * (3f - 2f * reveal)
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
                    val warp=remember {ElasticContentWarp()}
                    val icon = if(active && source?.hasOwnSurface==true) source.icon else null
                    val body=if(active && source?.hasOwnSurface==true)source.surfaceContent else null
                    if(body!=null) Box(Modifier.graphicsLayer {
                        val origin=state.rootAnchor ?: return@graphicsLayer
                        val p=(sourceProgress()/.45f).coerceIn(0f,1f)
                        alpha=1f-p*p*(3f-2f*p)
                        scaleX=geometry.value.width/origin.width.coerceAtLeast(1f)
                        scaleY=scaleX
                        translationY=-(geometry.value.height-origin.height)*.5f*(1f-p)
                        renderEffect=if(reduced)null else warp.effect(origin.size,shape.strain())
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
                val ink = glassContentColor()
                val accent = glassAccentColor()
                val colors = if (backdropEntry.glass && isGlassSupported()) MaterialTheme.colorScheme.copy(
                    onSurface = ink, onSurfaceVariant = ink.copy(alpha = .7f), primary = accent,
                    onPrimary = if (ink == Color.Black) Color.White else Color.Black,
                ) else MaterialTheme.colorScheme
                MaterialTheme(colorScheme = colors) {
                CompositionLocalProvider(LocalAppBackdrop provides (backdropEntry.backdrop ?: LocalAppBackdrop.current),
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                    LocalLiquidGlassEnabled provides backdropEntry.glass,
                    LocalContentMotionReduced provides reduced,
                    androidx.compose.foundation.LocalOverscrollFactory provides null,
                    LocalGlassDeformation provides { shape.strain().getDistance().coerceIn(0f,1f) }) {
                    val materialRole=if(first.kind==AkiraPresentationKind.AudioOutput)AkiraMaterialRole.AudioOutput else AkiraMaterialRole.Panel
                    SubcomposeLayout(Modifier.graphicsLayer {
                        // A bare glyph or a member of a shared capsule has no
                        // independent circular material to return to.
                        alpha = if(!active)0f else if(first.kind==AkiraPresentationKind.AudioOutput)materialization.value.coerceIn(0f,1f) else if(source!=null && !source.hasOwnSurface)
                            (sourceProgress()/.16f).coerceIn(0f,1f) else 1f
                    }.offset {
                        val rect = geometry.value.translate(0f, top?.dragOffset ?: 0f)
                        IntOffset(rect.left.roundToInt(), rect.top.roundToInt())
                    }
                        .akiraMaterial(shape, materialRole).clip(shape)) { constraints ->
                        val rect = geometry.value.translate(0f, top?.dragOffset ?: 0f)
                        val placeables = copies.mapIndexed { index, entry ->
                            val output = entry.kind == AkiraPresentationKind.AudioOutput
                            val maxWidth = min(with(density) { (if (entry.kind == AkiraPresentationKind.Menu) 260.dp else if(output)340.dp else if(entry.kind==AkiraPresentationKind.Update)380.dp else if(entry.kind==AkiraPresentationKind.CompactPanel)360.dp else 560.dp).toPx() }, safe.width - if(output)with(density){16.dp.toPx()} else 0f).roundToInt().coerceAtLeast(1)
                            val maxHeight = (safe.height - if(output)with(density){64.dp.toPx()} else 0f).roundToInt().coerceAtLeast(1)
                            val child = subcompose(entry, bodies[index]).single()
                                .measure(Constraints(minWidth = maxWidth, maxWidth = maxWidth, maxHeight = maxHeight))
                            val position = if (entry.kind == AkiraPresentationKind.Menu) {
                                // Size and place every menu as a direct opening
                                // from the original button. A tall parent's top
                                // edge would leave a short submenu far above it.
                                var origin = entry
                                while(origin.parent?.kind == AkiraPresentationKind.Menu) origin = origin.parent!!
                                val anchor = origin.source?.bounds() ?: origin.anchor
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
