package com.akira.app.feature.setup

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import com.akira.app.core.design.brand.AkiraBrandFont
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.akira.app.core.design.backdrop.backdrops.*
import com.akira.app.core.design.brand.AkiraBrand
import com.akira.app.core.design.glass.*
import com.akira.app.core.design.navigation.*
import com.akira.app.feature.home.components.*
import kotlinx.coroutines.isActive
import kotlin.math.sin

/** Stops ambient work when the activity is not visible; reduced motion has a static pose. */
@Composable internal fun onboardingPhase(duration: Int = 16_000): State<Float> {
    val reduced = setupReducedMotion()
    val owner = LocalLifecycleOwner.current
    val phase = remember { Animatable(0f) }
    LaunchedEffect(owner, reduced, duration) {
        if (reduced) phase.snapTo(0f)
        else owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                val remaining = ((6.283185f - phase.value) / 6.283185f * duration).toInt().coerceAtLeast(1)
                phase.animateTo(6.283185f, tween(remaining, easing = LinearEasing))
                phase.snapTo(0f)
            }
        }
    }
    return phase.asState()
}

/** No progress indicator: each scene has its own visual rhythm and a stable navigation frame. */
@Composable internal fun OnboardingFrame(
    title: String, description: String, onBack: (() -> Unit)?,
    headerAction: (@Composable () -> Unit)? = null,
    hero: (@Composable () -> Unit)? = null,
    scrollable: Boolean = true,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlassStyleEnabled.current
    val ambient = if (glass) onboardingPhase(18_000) else remember { mutableFloatStateOf(0f) }
    val source = rememberLayerBackdrop()
    CompositionLocalProvider(LocalAppBackdrop provides source, LocalPageHeaderBackdrop provides source,
        LocalContentColor provides colors.onBackground) {
        Box(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding().imePadding()
            .semantics { paneTitle = title; isTraversalGroup = true }) {
            Canvas(Modifier.matchParentSize().layerBackdrop(source)) {
                drawRect(colors.background)
                if (glass) {
                    val glow = Offset(size.width * (.8f + .08f * sin(ambient.value)), size.height * .15f)
                    drawCircle(Brush.radialGradient(listOf(AkiraBrand.accent.copy(alpha = .07f), Color.Transparent), glow, size.width * .85f),
                        size.width * .85f, glow)
                }
            }
            Column(Modifier.widthIn(max = 560.dp).fillMaxSize().align(Alignment.TopCenter)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        PageBackButton(onBack)
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(buildAnnotatedString {
                        append("Akira")
                        withStyle(SpanStyle(color = AkiraBrand.accent)) { append(".") }
                    }, fontFamily = AkiraBrandFont, fontWeight = FontWeight.Bold, fontSize = 24.sp,
                        letterSpacing = (-.7).sp, color = colors.onBackground)
                    Spacer(Modifier.weight(1f))
                    headerAction?.invoke()
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val minimum = (maxHeight - 44.dp).coerceAtLeast(0.dp)
                    Column(Modifier.fillMaxWidth().then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                        .padding(horizontal = 28.dp).padding(top = 18.dp, bottom = 26.dp)) {
                        Column(Modifier.fillMaxWidth().then(if (scrollable) Modifier.heightIn(min = minimum) else Modifier.fillMaxSize()),
                            verticalArrangement = if (hero != null) Arrangement.Center else Arrangement.spacedBy(24.dp)) {
                            hero?.invoke()
                            Column(Modifier.fillMaxWidth().padding(top = if (hero != null) 18.dp else 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(title, style = if (hero != null) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineLarge,
                                    modifier = Modifier.semantics { heading() })
                                if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                            }
                            content()
                        }
                    }
                }
                if (footer != null) Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 10.dp, bottom = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), content = footer)
            }
        }
    }
}
