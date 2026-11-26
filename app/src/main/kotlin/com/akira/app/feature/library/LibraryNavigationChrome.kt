package com.akira.app.feature.library

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.app.core.design.backdrop.Backdrop
import com.akira.app.core.design.backdrop.backdrops.rememberCanvasBackdrop
import com.akira.app.core.design.components.*
import com.akira.app.core.design.glass.LocalAppBackdrop
import com.akira.app.core.design.navigation.*
import com.akira.app.core.design.theme.CurrentAkiraFontFamily as AkiraFontFamily

internal val HiddenLibraryHeader: () -> Float = { 0f }

internal fun Modifier.libraryHeaderScrim(color: Color, progress: () -> Float): Modifier = drawWithCache {
    val gradient = Brush.verticalGradient(
        0f to color.copy(alpha = .98f),
        .52f to color.copy(alpha = .88f),
        1f to Color.Transparent,
    )
    onDrawBehind { drawRect(gradient, alpha = progress()) }
}

internal class LibraryChromeState(initialCategory: Boolean = false) {
    var category by mutableStateOf(initialCategory)
    var title by mutableStateOf("")
    var collapse by mutableStateOf(HiddenLibraryHeader)
    var scrim by mutableStateOf(HiddenLibraryHeader)
    var backdrop by mutableStateOf<Backdrop?>(null)
    var icon by mutableStateOf<ImageVector>(if (initialCategory) LibraryIcons.Filter else LibraryIcons.View)
    var description by mutableStateOf("")
    var onBack: () -> Unit = {}
    var onFirst: () -> Unit = {}
    var onMore: () -> Unit = {}
    var onProfile: () -> Unit = {}
}
internal val LocalLibraryChrome = staticCompositionLocalOf<LibraryChromeState?> { null }
internal val LocalLibraryChromeActive = staticCompositionLocalOf { true }

/** One capsule survives category navigation; only its horizontal position changes. */
@Composable internal fun LibraryChromeHost(content: @Composable () -> Unit) {
    val state = rememberSaveable(saver = Saver<LibraryChromeState, Boolean>(
        save = { it.category }, restore = { LibraryChromeState(it) })) { LibraryChromeState() }
    val reduced = LocalContentMotionReduced.current
    val categoryProgress = animateFloatAsState(if (state.category) 1f else 0f,
        if (reduced) snap() else tween(300, easing = PageCollapseEasing), label = "libraryChromeScene")
    val showBack by remember { derivedStateOf { state.category || categoryProgress.value > .001f } }
    val showProfile by remember { derivedStateOf { !state.category || categoryProgress.value < .999f } }
    val gutter = AkiraNavigationMetrics.Gutter
    val rowTop = AkiraNavigationMetrics.HeaderTop
    val target = AkiraNavigationMetrics.Target
    val end = animateDpAsState(if (state.category) gutter else gutter + AkiraNavigationMetrics.AvatarSurface + AkiraNavigationMetrics.GroupGap,
        if (reduced) snap() else tween(300, easing = PageCollapseEasing), label = "libraryChromeTravel")
    val color = MaterialTheme.colorScheme.background
    val fallback = rememberCanvasBackdrop { drawRect(color) }
    val backdrop = state.backdrop ?: fallback
    val inset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalLibraryChrome provides state) { content() }
        CompositionLocalProvider(LocalPageHeaderBackdrop provides backdrop, LocalAppBackdrop provides backdrop) {
            Box(Modifier.fillMaxWidth().height(inset + 102.dp)
                .libraryHeaderScrim(color) { state.scrim() }) {
                if (showBack) Box(Modifier.padding(top = inset + rowTop,
                    start = gutter - (target - AkiraNavigationMetrics.BackSurface) / 2)
                    .graphicsLayer { alpha = categoryProgress.value }) {
                    DrawBackButton({ state.onBack() }, enabled = state.category)
                }
                Box(Modifier.padding(top = inset + rowTop, start = 80.dp, end = 148.dp).fillMaxWidth().height(target),
                    contentAlignment = Alignment.Center) {
                    Text(state.title, Modifier.graphicsLayer { alpha = state.collapse() * categoryProgress.value },
                        fontFamily = AkiraFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.align(Alignment.TopEnd).padding(top = inset + rowTop, end = gutter)
                    .graphicsLayer { translationX = (gutter - end.value).toPx() }) {
                    LibraryActionCapsule(state.icon, state.description, { state.onFirst() }, { state.onMore() })
                }
                if (showProfile) Box(Modifier.align(Alignment.TopEnd).padding(top = inset + rowTop,
                    end = gutter - (target - AkiraNavigationMetrics.AvatarSurface) / 2)
                    .graphicsLayer { alpha = 1f - categoryProgress.value; translationX = 48.dp.toPx() * categoryProgress.value }) {
                    AkiraAccountButton({ state.onProfile() }, Modifier.size(target), alwaysShowProfile = true)
                }
            }
        }
    }
}
