package com.akira.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.akira.app.core.design.components.*
import com.akira.app.core.design.motion.*
import com.akira.app.core.design.navigation.*
import com.akira.app.core.design.rendering.*
import com.akira.app.feature.home.components.*
import com.akira.app.core.design.backdrop.backdrops.rememberLayerBackdrop
import com.akira.app.core.design.backdrop.backdrops.layerBackdrop

/** Subpages stay in the same Android window so enter, pop and predictive back
 * use Page's actual Compose transition, including an interrupted return. */
internal class StudioEntry(val id: String, val depth: Int, val scroll: androidx.compose.foundation.ScrollState) {
    var render by mutableStateOf<@Composable () -> Unit>({})
    var onBack: () -> Unit = {}
}
@Stable internal class StudioNavigationState {
    val entries = mutableStateListOf<StudioEntry>()
    val active: StudioEntry? get() = entries.lastOrNull()
    var rootReveal by mutableFloatStateOf(0f)
}
internal val LocalStudioNavigation = staticCompositionLocalOf<StudioNavigationState?> { null }

@Composable internal fun StudioNavigationHost(state: StudioNavigationState) {
    val active = state.active
    PageNavigationHost(target=active,backTarget=state.entries.dropLast(1).lastOrNull(),
        backEnabled=active!=null,onBack={active?.onBack?.invoke()},
        forward={from,to->(to?.depth ?: -1)>(from?.depth ?: -1)},modifier=Modifier.fillMaxSize(),
        onProgress={from,to,fraction -> state.rootReveal=when {
            from==null && to!=null -> PagePushEasing.transform(fraction)
            from!=null && to==null -> 1f-PagePushEasing.transform(fraction)
            to!=null -> 1f
            else -> 0f
        }}) { entry ->
        if(entry!=null) entry.render()
    }
}

@Composable internal fun AkiraStudioScreen(title: String, onBack: () -> Unit,
    trailing: @Composable (() -> Unit)? = null, headerProgress: State<Float>? = null,
    content: @Composable ColumnScope.() -> Unit) {
    val navigator = LocalStudioNavigation.current
    // The registration stays composed while another studio page covers this
    // one. Own scroll here, so returning from an editor restores its position.
    val scroll = rememberScrollState()
    val render: @Composable () -> Unit = {
        val scrollCollapse by rememberPageCollapse(scroll)
        val collapse = headerProgress?.value ?: scrollCollapse
        val headerBackdrop = rememberLayerBackdrop()
        val glass = com.akira.app.core.design.glass.isGlassSupported()
        CompositionLocalProvider(LocalPageHeaderBackdrop provides headerBackdrop,
            androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground,
            com.akira.app.core.design.glass.LocalAppBackdrop provides headerBackdrop,
            com.akira.app.core.design.components.LocalAkiraOverlayBackdrop provides headerBackdrop) {
        Box(Modifier.fillMaxSize().semantics { paneTitle=title; isTraversalGroup=true }
            .background(MaterialTheme.colorScheme.background).pointerInput(Unit) { detectTapGestures {} }.systemBarsPadding()) {
            Column(Modifier.widthIn(max=720.dp).fillMaxSize().align(Alignment.TopCenter)
                .then(if (glass) Modifier.layerBackdrop(headerBackdrop) else Modifier)
                .background(MaterialTheme.colorScheme.background)) {
                CompositionLocalProvider(LocalPageScrollState provides scroll) { content() }
            }
            PagePageHeader(title,onBack,collapse,Modifier.widthIn(max=720.dp).align(Alignment.TopCenter),trailing=trailing)
            com.akira.app.core.design.components.AkiraNoticeHost(Modifier.align(Alignment.BottomCenter).padding(bottom=8.dp),priority=2)
        }
        }
    }
    if(navigator==null) {
        Dialog(onDismissRequest=onBack,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) { render() }
    } else {
        val entry=remember(navigator) { StudioEntry(java.util.UUID.randomUUID().toString(),navigator.entries.size,scroll) }
        SideEffect { entry.render=render; entry.onBack=onBack }
        DisposableEffect(navigator,entry) {
            navigator.entries.add(entry)
            onDispose { navigator.entries.remove(entry) }
        }
    }
}
