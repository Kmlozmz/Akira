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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipPath
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
    var closingAll by mutableStateOf(false)
    var rootAnchor: Rect? = null
    var rootSource: AkiraMenuSource? = null
    var foregroundEffect by mutableStateOf<com.akira.app.core.design.motion.FontForegroundEffect?>(null)

    val desiredEntry: AkiraPresentationEntry?
        get() = if (closingAll) null else if (closing != null && closing?.removed == false)
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
            closingAll = false
            closing = null
            rootAnchor = entry.anchor
            rootSource = entry.source
            anchors?.hiddenSource = entry.source
        }
        entries.add(entry)
    }

    fun dismissAll() {
        if (closing != null || closingAll) return
        closing = entries.lastOrNull { !it.removed } ?: entries.lastOrNull() ?: return
        closingAll = true
    }

    fun finishClose() {
        val departing = if (closingAll) entries.toList() else listOfNotNull(closing)
        departing.forEach { entry ->
            if (!entry.removed) {
                entry.removed = true
                entry.dismiss()
            }
        }
        release()
        closing = null
        closingAll = false
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
            closingAll = false
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
    val material=com.akira.app.core.design.glass.LocalSharedGlassMaterial.current
    val shaderContext=androidx.compose.ui.platform.LocalContext.current.applicationContext
    LaunchedEffect(state,material,shaderContext) {
        if(material!=null && android.os.Build.VERSION.SDK_INT>=33 && state.foregroundEffect==null) {
            state.foregroundEffect=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching{com.akira.app.core.design.motion.FontForegroundEffect(shaderContext)}.getOrNull()
            }
        }
    }
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
            if (state.entries.lastOrNull { !it.removed }?.kind == AkiraPresentationKind.Menu) state.dismissAll()
            else if (state.closing == null) state.closing = state.entries.lastOrNull { !it.removed }
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
    val sharedTimeline = com.akira.app.core.design.glass.LocalSharedGlassMaterial.current?.presentationMotion
    var sharedFrame by remember { mutableStateOf<com.akira.app.core.design.motion.SharedMorphFrame?>(null) }
    val sharedClock = remember { Animatable(0f) }
    var fontActor by remember { mutableStateOf<com.akira.app.core.design.motion.FontPresentationActor?>(null) }
    val foregroundEffect=state.foregroundEffect
    fun currentGeometry(): Rect = sharedFrame?.bounds ?: geometry.value
    val materialization = remember { Animatable(0f) }
    val corner = remember { Animatable(min(initial.width, initial.height) / 2f) }
    val smoothing = remember { Animatable(if (state.rootAnchor == null) 1f else 0f) }
    val destination = desired?.bounds
    val liveOrigin = state.rootSource?.bounds() ?: state.rootAnchor
    val backdropEntry = desired ?: top ?: state.entries.lastOrNull() ?: idleEntry
    val motionOrigin = if (sharedTimeline!=null && desired!=null) state.rootAnchor ?: liveOrigin else liveOrigin
    val source = state.rootSource
    val progressEntry by rememberUpdatedState(desired ?: closing ?: top ?: first)
    val returning by rememberUpdatedState(desired == null)
    val returnFloor = remember { mutableFloatStateOf(1f) }
    var windowOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    fun sourceProgress(): Float {
        sharedFrame?.let { return (1f-it.sourceAlpha).coerceIn(0f,1f) }
        if(first.kind==AkiraPresentationKind.AudioOutput)return materialization.value.coerceIn(0f,1f)
        val origin = state.rootAnchor ?: return 1f
        val target = progressEntry.bounds ?: return 0f
        return presentationProgress(currentGeometry(),
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
        state.dismissAll()
    }

    LaunchedEffect(desired, destination, closing, windowOrigin, reduced, motionOrigin, state.generation, sharedTimeline) {
        if (desired != null && destination == null) return@LaunchedEffect
        // Disposing the caller clears `closing` in a later composition. That
        // bookkeeping must not run the capsule's return trajectory a second time.
        if(desired==null && returnedGeneration==state.generation) {
            state.release()
            return@LaunchedEffect
        }
        val departingSource=active && preparedGeneration!=state.generation
        if (departingSource) {
            val origin = motionOrigin?.translate(-windowOrigin.x, -windowOrigin.y)
                ?: destination?.let { Rect(it.center.x-1f,it.center.y-1f,it.center.x+1f,it.center.y+1f) }
                ?: Rect(0f,0f,1f,1f)
            geometry.snapTo(origin)
            corner.snapTo(min(origin.width,origin.height)/2f)
            smoothing.snapTo(if(liveOrigin==null)1f else 0f)
            preparedGeneration = state.generation
        }
        val dissolving = sharedTimeline==null && desired==null && first.kind==AkiraPresentationKind.AudioOutput
        val end = if(dissolving) currentGeometry().translate(0f,with(density){6.dp.toPx()}) else destination ?: liveOrigin?.translate(-windowOrigin.x, -windowOrigin.y)
            ?: currentGeometry().let { Rect(it.center.x - 1f, it.center.y - 1f, it.center.x + 1f, it.center.y + 1f) }
        if (state.rootAnchor == null && currentGeometry().width <= 1f && destination != null) {
            val center = destination.center
            geometry.snapTo(Rect(center.x - 8f, center.y - 8f, center.x + 8f, center.y + 8f))
        }
        val radius = if (desired != null) with(density) {
            (when { sharedTimeline!=null && (desired.kind==AkiraPresentationKind.Menu || desired.kind==AkiraPresentationKind.Popover)->com.akira.app.core.design.system.AkiraMenuSpec.glassRadius.dp; else->when(desired.kind) { AkiraPresentationKind.AudioOutput->34.dp; AkiraPresentationKind.Update, AkiraPresentationKind.AudioQuality->32.dp; AkiraPresentationKind.Panel->28.dp; else->22.dp } }).toPx()
        } else if(dissolving)corner.value else min(end.width, end.height) / 2f
        val dragged = top?.dragOffset ?: 0f
        val releaseVelocity = top?.releaseVelocity ?: 0f
        if (dragged != 0f) {
            geometry.snapTo(currentGeometry().translate(0f, dragged))
            top?.dragOffset = 0f
            top?.releaseVelocity = 0f
        }
        if (reduced) {
            sharedFrame = null
            geometry.snapTo(end); corner.snapTo(radius); smoothing.snapTo(if (desired != null) 1f else 0f)
            materialization.snapTo(if(desired!=null)1f else 0f)
        } else if (sharedTimeline != null) {
            val opening = desired != null
            val root=state.rootAnchor?.translate(-windowOrigin.x,-windowOrigin.y) ?: liveOrigin?.translate(-windowOrigin.x,-windowOrigin.y) ?: end
            fun logical(rect:Rect)=Rect(rect.left/density.density,rect.top/density.density,rect.right/density.density,rect.bottom/density.density)
            if(opening) {
                val previous=fontActor
                if(departingSource || previous==null) {
                    fontActor=com.akira.app.core.design.motion.FontPresentationActor(logical(end),logical(root),density.density,(radius/density.density).toDouble(),source?.transfersForeground==true,source?.hasOwnSurface==true,sourceMaterial=source?.hasOwnSurface==true).also{it.open()}
                } else if(previous.menu!=logical(end)) {
                    // Nested pages retain their current rectangle instead of restarting at the trigger.
                    val from=sharedFrame?.rawBody ?: currentGeometry()
                    val startRadius=sharedFrame?.elements?.firstOrNull()?.radius?.let{abs(it)} ?: corner.value
                    sharedClock.snapTo(0f)
                    sharedClock.animateTo(1f,tween(240,easing=FastOutSlowInEasing)) {
                        val box=androidx.compose.ui.geometry.lerp(from,end,value)
                        sharedFrame=com.akira.app.core.design.motion.SharedMorphFrame(box,listOf(
                            com.akira.app.core.design.motion.MorphElement(box.center,Size(box.width/2,box.height/2),-(startRadius+(radius-startRadius)*value))),
                            0f,1f,1f,0f)
                    }
                    fontActor=com.akira.app.core.design.motion.FontPresentationActor(logical(end),logical(root),density.density,(radius/density.density).toDouble(),source?.transfersForeground==true,source?.hasOwnSurface==true,sourceMaterial=source?.hasOwnSurface==true).also {
                        it.open();repeat(240){_->it.step(1.0/120)}
                    }
                } else if(previous.closing) previous.open()
            } else {
                if(fontActor==null)fontActor=com.akira.app.core.design.motion.FontPresentationActor(logical(currentGeometry()),logical(root),density.density,directReturn=source?.transfersForeground==true,sourceSurface=source?.hasOwnSurface==true,sourceMaterial=source?.hasOwnSurface==true).also {
                    it.open();repeat(240){_->it.step(1.0/120)}
                }
                fontActor?.close()
            }
            val actor=fontActor!!
            sharedFrame=actor.frame()
            var previousTime=withFrameNanos{it}
            var moving=true
            while(moving && !(actor.closing && actor.collapsed)) {
                val now=withFrameNanos{it}
                moving=actor.step((now-previousTime)/1e9)
                previousTime=now
                sharedFrame=actor.frame()
            }
            geometry.snapTo(end);corner.snapTo(radius);smoothing.snapTo(if(opening)1f else 0f)
            materialization.snapTo(if(opening)1f else 0f)
            // Retain the settled optical frame: changing the outline and light
            // recipe here produces a visible rim flash after the spring stops.
            if(!opening) sharedFrame = null
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
        if (closing != null) state.finishClose() else state.release()
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
            val shape = remember(corner, smoothing, density, sharedTimeline) {
                if (sharedTimeline != null) com.akira.app.core.design.motion.SharedPresentationShape({ sharedFrame }, { corner.value }, density)
                else com.akira.app.core.design.motion.PresentationShape({ corner.value }, { smoothing.value }, density)
            }
            val copies = state.entries.toList()
            val returningEntry = state.returningEntry
            val bodies = copies.map { entry ->
                remember(entry, desired, reduced, returningEntry) {
                    @Composable {

                        val visible = animateFloatAsState(if (state.showsBody(entry)) 1f else 0f,
                            if (reduced) snap() else tween(140), label = "presentationContent")
                        Box(Modifier.graphicsLayer {
                            val blur=sharedFrame?.menuBlur ?: 0f
                            renderEffect=com.akira.app.core.design.rendering.BlurEffectCache.getDecal(blur)
                            val p=opticalProgress()
                            val reveal=((p-.06f)/.34f).coerceIn(0f,1f)
                            val presence=sharedFrame?.contentAlpha ?: (reveal*reveal*(3f-2f*reveal))
                            alpha=if(state.showsBody(entry))visible.value*presence else 0f
                        }) {
                        Box(Modifier.graphicsLayer {
                            val final = entry.bounds
                            val p=opticalProgress()
                            val pose=sharedFrame?.contentTransform(final?.size ?: currentGeometry().size) ?: presentationContentTransform(currentGeometry().size,
                                final?.size ?: currentGeometry().size,p)
                            transformOrigin=androidx.compose.ui.graphics.TransformOrigin(0f,0f)
                            scaleX=pose.scale;scaleY=pose.scaleY
                            translationX=pose.offset.x;translationY=pose.offset.y
                        }.drawWithContent {
                            val frame=sharedFrame
                            if(frame?.rawBody!=null) {
                                val contentHeight=frame.contentHeight.coerceAtLeast(.001f)
                                val clipShape=com.akira.app.core.design.components.ContinuousMenuShape(with(density){frame.contentRadius.toDp()},1f)
                                val outline=clipShape.createOutline(Size(size.width,contentHeight),layoutDirection,this)
                                val path=androidx.compose.ui.graphics.Path().apply {
                                    when(outline){is Outline.Generic->addPath(outline.path);is Outline.Rounded->addRoundRect(outline.roundRect);is Outline.Rectangle->addRect(outline.rect)}
                                }
                                clipPath(path){this@drawWithContent.drawContent()}
                            } else drawContent()
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
            }
            val sourceGlyph = remember(first, geometry, active, shape, reduced) {
                @Composable {

                    val icon = if(active && source?.transfersForeground==true) source.icon else null
                    val body=if(active && source?.transfersForeground==true)source.surfaceContent else null
                    if(body!=null) Box(Modifier.graphicsLayer {
                        val origin=state.rootAnchor ?: return@graphicsLayer
                        val p=(sourceProgress()/.45f).coerceIn(0f,1f)
                        alpha=sharedFrame?.sourceAlpha ?: (1f-p*p*(3f-2f*p))
                        scaleX=1f; scaleY=1f
                        val blur=sharedFrame?.seedBlur ?: 0f
                        renderEffect=com.akira.app.core.design.rendering.BlurEffectCache.getDecal(blur)
                    }) {body()}
                    else if (icon != null) Icon(icon, null,
                        Modifier.size(22.dp).graphicsLayer {
                            val sourceWidth = state.rootAnchor?.width ?: 1f
                            alpha = sharedFrame?.sourceAlpha ?: ((sourceWidth * 2f - currentGeometry().width) / sourceWidth.coerceAtLeast(1f)).coerceIn(0f, 1f)
                            val blur=sharedFrame?.seedBlur ?: 0f
                            renderEffect=com.akira.app.core.design.rendering.BlurEffectCache.getDecal(blur)
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
                    com.akira.app.core.design.glass.LocalSharedGlassNature provides
                        (if (first.kind==AkiraPresentationKind.Menu || first.kind==AkiraPresentationKind.Popover)
                            com.akira.app.core.design.glass.SharedGlassNature.Menu else com.akira.app.core.design.glass.SharedGlassNature.Panel),
                    com.akira.app.core.design.glass.LocalGlassReadableSurface provides true,
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                    LocalLiquidGlassEnabled provides backdropEntry.glass,
                    LocalContentMotionReduced provides reduced,
                    androidx.compose.foundation.LocalOverscrollFactory provides null,
                    LocalGlassDeformation provides { 0f }) {
                    val materialRole=if(first.kind==AkiraPresentationKind.AudioOutput)AkiraMaterialRole.AudioOutput else AkiraMaterialRole.Panel
                    SubcomposeLayout(Modifier.wrapContentSize(Alignment.TopStart,unbounded=true).graphicsLayer {
                        compositingStrategy=androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                        // A bare glyph or a member of a shared capsule has no
                        // independent circular material to return to.
                        alpha = if(!active)0f else (sharedFrame?.let { if (backdropEntry.glass) 1f else it.alpha }) ?: if(first.kind==AkiraPresentationKind.AudioOutput)materialization.value.coerceIn(0f,1f)
                            else if(source!=null && (!source.hasOwnSurface || returning))
                                presentationSourcePresence(handoffProgress()) else 1f
                    }.offset {
                        val rect = currentGeometry().translate(0f, top?.dragOffset ?: 0f)
                        IntOffset(rect.left.roundToInt(), rect.top.roundToInt())
                    }
                        .akiraMaterial(shape, materialRole, presence = {
                            if(source?.flatSourceSurface==true)
                                1f-(sharedFrame?.sourceAlpha ?: (1f-sourceProgress())).coerceIn(0f,1f)
                            else 1f
                        }).graphicsLayer {
                            this.shape = shape
                            renderEffect=if(android.os.Build.VERSION.SDK_INT>=33)sharedFrame?.let{foregroundEffect?.bind(it,density.density)}else null
                            clip = sharedFrame==null || foregroundEffect==null
                        }) { constraints ->
                        val rect = currentGeometry().translate(0f, top?.dragOffset ?: 0f)
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
                                    AkiraPresentationKind.Menu -> com.akira.app.core.design.system.AkiraMenuSpec.width.dp
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
                        val w = ceil(rect.width).toInt().coerceIn(1, constraints.maxWidth)
                        val h = ceil(rect.height).toInt().coerceIn(1, constraints.maxHeight)
                        layout(w, h) {
                            placeables.forEach { child ->
                                // Hidden parents stay composed so their selectors and
                                // editors can return without losing state or focus.
                                child.place(0, 0)
                            }
                            val sourceCenter=sharedFrame?.seedCenter ?: state.rootAnchor?.center?.minus(windowOrigin)
                            if(sharedTimeline!=null && sourceCenter!=null) {
                                glyph.place((sourceCenter.x-rect.left-glyph.width/2f).roundToInt(),
                                    (sourceCenter.y-rect.top-glyph.height/2f).roundToInt())
                            } else glyph.place((w-glyph.width)/2,(h-glyph.height)/2)
                        }
                    }
                    (desired ?: top)?.let { entry -> entry.footer?.let { footerContent ->
                        Box(Modifier.align(Alignment.BottomCenter)
                            .padding(bottom=with(density){(window.height-safe.bottom).toDp()})
                            .widthIn(max=320.dp).graphicsLayer {
                                val fraction=(currentGeometry().width / (entry.bounds?.width ?: 1f)).coerceIn(0f,1f)
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
