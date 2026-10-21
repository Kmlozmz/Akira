package com.akira.app.feature.setup

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akira.app.core.datastore.*
import com.akira.app.core.design.brand.AkiraBrand
import com.akira.app.core.design.components.*
import com.akira.app.core.design.glass.*
import com.akira.app.core.design.backdrop.backdrops.*
import com.akira.app.core.design.navigation.*
import com.akira.app.core.design.rendering.*
import com.akira.app.feature.home.components.*

/** Persistent navigation and actions frame a scrollable, independently sampled content plane. */
@Composable
internal fun SetupLayout(title: String, subtitle: String, onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier, footer: (@Composable ColumnScope.() -> Unit)? = null,
    scrollable: Boolean = true, step: Int = 0, caption: String? = null,
    pagePresence: () -> Float = { 1f },
    content: @Composable ColumnScope.() -> Unit) = SetupBrandTheme {
    val context = LocalContext.current
    val repo = remember(context) { UserPreferencesRepository(context) }
    val initial = remember(context) { AppUiSnapshot.read(context) ?: UserPreferences() }
    val prefs by repo.userPreferences.collectAsStateWithLifecycle(initial)
    val opacity by rememberUpdatedState(prefs.liquidGlassOpacity)
    val diffusion by rememberUpdatedState(prefs.liquidGlassDiffusion)
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(80.dp) }
    val phase = setupAmbientPhase(16000)
    val currentPresence by rememberUpdatedState(pagePresence)
    val backdrop = rememberLayerBackdrop()
    val scroll = key(step) { rememberScrollState() }
    CompositionLocalProvider(LocalContentColor provides colors.onBackground,
        LocalAppBackdrop provides backdrop, LocalPageHeaderBackdrop provides backdrop,
        LocalLiquidGlassEnabled provides prefs.liquidGlassEnabled,
        LocalLiquidGlassOpacity provides remember { { GlassOpacityPreview.value ?: opacity } },
        LocalLiquidGlassDiffusion provides remember { { GlassDiffusionPreview.value ?: diffusion } },
        LocalDockDiffusionEnabled provides prefs.dockBackgroundDiffusion) {
        Box(modifier.fillMaxSize().background(colors.background).safeDrawingPadding()
            .semantics { paneTitle = title; isTraversalGroup = true }) {
            Canvas(Modifier.matchParentSize().layerBackdrop(backdrop).clipToBounds()) {
                drawRect(colors.background)
                val center = Offset(size.width * (.85f + .12f * kotlin.math.sin(phase.value)), size.height * .12f)
                drawCircle(Brush.radialGradient(listOf(AkiraBrand.accent.copy(alpha = .065f), Color.Transparent),
                    center, size.width * 1.1f), size.width * 1.1f, center)
            }
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.widthIn(max = 620.dp).fillMaxSize().align(Alignment.TopCenter)
                        .graphicsLayer {
                            alpha = pagePresence()
                            translationY = (1f - pagePresence()) * 8.dp.toPx()
                        }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (currentPresence() < .999f) event.changes.forEach { it.consume() }
                                }
                            }
                        }
                        .then(if (scrollable) Modifier.verticalScroll(scroll) else Modifier)
                        .padding(start = 24.dp, end = 24.dp, top = (headerHeight + 40.dp).coerceAtLeast(124.dp), bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(title, style = MaterialTheme.typography.headlineLarge,
                                modifier = Modifier.semantics { heading() })
                            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                        }
                        content()
                        Spacer(Modifier.height(8.dp))
                    }
                    // Cover the text plane before drawing navigation; the final stops soften the boundary.
                    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(headerHeight + 24.dp)
                        .background(Brush.verticalGradient(0f to colors.background,
                            (headerHeight / (headerHeight + 24.dp)) to colors.background, 1f to Color.Transparent))
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                            }
                        })
                    Column(Modifier.widthIn(max = 620.dp).fillMaxWidth().align(Alignment.TopCenter)
                        .onSizeChanged { headerHeight = with(density) { it.height.toDp() } }
                        .padding(horizontal = 24.dp).padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (onBack != null) {
                                CompositionLocalProvider(LocalPageHeaderBackdrop provides backdrop) {
                                    PageBackButton(onBack, Modifier.size(44.dp).shadow(8.dp, CircleShape, clip = false,
                                        ambientColor = Color.Black.copy(alpha = .18f), spotColor = Color.Black.copy(alpha = .22f)))
                                }
                                Spacer(Modifier.width(14.dp))
                            } else {
                                SetupEclipse(Modifier.size(28.dp), colors.primary)
                                Spacer(Modifier.width(10.dp))
                            }
                            Text("Akira", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.weight(1f))
                            Text("${(step + 1).coerceIn(1, 8).toString().padStart(2, '0')} / 08",
                                style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                        }
                        Row(Modifier.fillMaxWidth().semantics {
                            progressBarRangeInfo = ProgressBarRangeInfo((step + 1).toFloat(), 1f..8f, 6)
                        }, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            repeat(8) { index -> Box(Modifier.weight(1f).height(3.dp)
                                .background(if (index <= step) colors.primary else colors.onSurface.copy(alpha = .10f), CircleShape)) }
                        }
                    }
                    if (footer != null) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(24.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, colors.background))))
                }
                if (footer != null) Column(Modifier.widthIn(max = 620.dp).fillMaxWidth().background(colors.background)
                    .padding(horizontal = 24.dp).padding(top = 10.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    footer()
                    if (caption != null) Text(caption,
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

@Composable internal fun SetupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(28.dp))
        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

@Composable internal fun SetupAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val response = rememberAkiraPressResponse(pressed)
    val haptics = com.akira.app.core.design.util.rememberAkiraHaptics()
    Button({ haptics.click(); onClick() }, modifier = Modifier.fillMaxWidth().then(response.modifier).heightIn(min = 58.dp),
        enabled = enabled, interactionSource = interaction, shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AkiraBrand.accent, contentColor = Color.White,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold))
        Spacer(Modifier.width(12.dp))
        Icon(com.akira.app.core.design.icon.AkiraIcons.ChevronRight, null, Modifier.size(22.dp))
    }
}

@Composable internal fun SetupSecondaryAction(label: String, onClick: () -> Unit) {
    val haptics = com.akira.app.core.design.util.rememberAkiraHaptics()
    FilledTonalButton({ haptics.click(); onClick() }, Modifier.fillMaxWidth().heightIn(min = 50.dp),
        shape = RoundedCornerShape(18.dp)) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

@Composable internal fun SetupEclipse(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier) { scale(size.minDimension / 128f, pivot = Offset.Zero) { drawPath(AkiraBrand.symbol, color) } }
}

@Composable internal fun setupReducedMotion(): Boolean = LocalContentMotionReduced.current || isReduceMotionActive(LocalContext.current)

@Composable internal fun SetupEntrance(delay: Int = 0, content: @Composable () -> Unit) {
    val reduced = setupReducedMotion()
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) { if (reduced) progress.snapTo(1f) else {
        kotlinx.coroutines.delay(delay.toLong()); progress.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
    } }
    Box(Modifier.fillMaxWidth().graphicsLayer { alpha = progress.value; translationY = (1f - progress.value) * 12.dp.toPx() }) { content() }
}

@Composable internal fun setupAmbientPhase(durationMillis: Int = 14000): State<Float> {
    if (setupReducedMotion()) return remember { mutableFloatStateOf(0f) }
    return rememberInfiniteTransition(label = "setupAmbient").animateFloat(0f, 6.283185f,
        infiniteRepeatable(tween(durationMillis, easing = LinearEasing)), label = "setupPhase")
}
