package com.akira.app.feature.library

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.akira.app.core.design.theme.AkiraFontFamily

internal class LibraryChromeState(initialCategory: Boolean = false) {
    var category by mutableStateOf(initialCategory)
    var title by mutableStateOf("")
    var collapse by mutableFloatStateOf(0f)
    var scrim by mutableFloatStateOf(0f)
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
    val categoryProgress by animateFloatAsState(if (state.category) 1f else 0f,
        if (reduced) snap() else tween(300, easing = PageCollapseEasing), label = "libraryChromeScene")
    val gutter = AkiraNavigationMetrics.Gutter
    val rowTop = AkiraNavigationMetrics.HeaderTop
    val target = AkiraNavigationMetrics.Target
    val end by animateDpAsState(if (state.category) gutter else gutter + AkiraNavigationMetrics.AvatarSurface + AkiraNavigationMetrics.GroupGap,
        if (reduced) snap() else tween(300, easing = PageCollapseEasing), label = "libraryChromeTravel")
    val color = MaterialTheme.colorScheme.background
    val fallback = rememberCanvasBackdrop { drawRect(color) }
    val backdrop = state.backdrop ?: fallback
    val inset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalLibraryChrome provides state) { content() }
        CompositionLocalProvider(LocalPageHeaderBackdrop provides backdrop, LocalAppBackdrop provides backdrop) {
            Box(Modifier.fillMaxWidth().height(inset + 102.dp)
                .background(Brush.verticalGradient(0f to color.copy(alpha = .98f * state.scrim),
                    .52f to color.copy(alpha = .88f * state.scrim), 1f to Color.Transparent))) {
                if (categoryProgress > .001f) Box(Modifier.padding(top = inset + rowTop,
                    start = gutter - (target - AkiraNavigationMetrics.BackSurface) / 2)
                    .graphicsLayer { alpha = categoryProgress }) {
                    PageBackButton({ state.onBack() })
                }
                Box(Modifier.padding(top = inset + rowTop, start = 80.dp, end = 148.dp).fillMaxWidth().height(target),
                    contentAlignment = Alignment.Center) {
                    Text(state.title, Modifier.graphicsLayer { alpha = state.collapse * categoryProgress },
                        fontFamily = AkiraFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.align(Alignment.TopEnd).padding(top = inset + rowTop, end = end)) {
                    LibraryActionCapsule(state.icon, state.description, { state.onFirst() }, { state.onMore() })
                }
                if (categoryProgress < .999f) Box(Modifier.align(Alignment.TopEnd).padding(top = inset + rowTop,
                    end = gutter - (target - AkiraNavigationMetrics.AvatarSurface) / 2)
                    .graphicsLayer { alpha = 1f - categoryProgress; translationX = 48.dp.toPx() * categoryProgress }) {
                    AkiraAccountButton({ state.onProfile() }, Modifier.size(target), alwaysShowProfile = true)
                }
            }
        }
    }
}
