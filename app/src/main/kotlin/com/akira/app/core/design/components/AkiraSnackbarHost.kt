// SPDX-License-Identifier: GPL-3.0-only
// Pill geometry/type/actions adapted from Page bb28993 TopToast.kt.
// Akira retains its bottom hosts, backdrop blur, timed undo and swipe dismissal.
package com.akira.app.core.design.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.app.core.design.theme.AkiraFontFamily
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import coil.compose.AsyncImage
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.platform.LocalAccessibilityManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.WeakHashMap

private data class AkiraNoticeVisuals(
    override val message: String,
    override val actionLabel: String?,
    override val withDismissAction: Boolean,
    override val duration: SnackbarDuration,
    val thumbnail: String?
) : SnackbarVisuals

private val noticeRequests = WeakHashMap<SnackbarHostState, Deferred<SnackbarResult>>()

/** Replacing a notice dismisses its undo action without cancelling the caller's scope. */
suspend fun SnackbarHostState.showAkiraSnackbar(
    message: String,
    actionLabel: String? = null,
    withDismissAction: Boolean = false,
    duration: SnackbarDuration = SnackbarDuration.Short,
    thumbnail: String? = null
): SnackbarResult = coroutineScope {
    val host = this@showAkiraSnackbar
    val request = async(start = CoroutineStart.LAZY) {
        host.showSnackbar(AkiraNoticeVisuals(message, actionLabel, withDismissAction, duration, thumbnail))
    }
    synchronized(noticeRequests) { noticeRequests.put(host, request)?.cancel() }
    try {
        request.await()
    } catch (cancelled: CancellationException) {
        currentCoroutineContext().ensureActive()
        SnackbarResult.Dismissed
    } finally {
        synchronized(noticeRequests) {
            if (noticeRequests[host] === request) noticeRequests.remove(host)
        }
    }
}

/** Passive notices are delivered directly to the most recently opened app window. */
internal class NoticeWindow(val priority: Int, val show: (String) -> Unit)
val LocalAkiraNoticeColors = staticCompositionLocalOf<ColorScheme?> { null }

object AkiraNotices {
    internal val windows = mutableStateListOf<NoticeWindow>()
    private var pending: String? = null
    internal fun activeWindow() = windows.asReversed().maxByOrNull { it.priority }
    fun show(message: String) {
        val window = activeWindow()
        if (window != null) window.show(message) else {
            pending = message
        }
    }
    internal fun attach(window: NoticeWindow) {
        windows.add(window)
        val message = pending
        pending = null
        if (message != null) window.show(message)
    }
}

@Composable
fun AkiraNoticeHost(modifier: Modifier = Modifier, priority: Int = 0, hostState: SnackbarHostState? = null) {
    val localState = remember { SnackbarHostState() }
    val state = hostState ?: localState
    val scope = rememberCoroutineScope()
    val window = remember(priority) { NoticeWindow(priority) { message -> scope.launch { state.showAkiraSnackbar(message) } } }
    val lifecycleState by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    if (!lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return
    DisposableEffect(window) {
        AkiraNotices.attach(window)
        onDispose { AkiraNotices.windows.remove(window) }
    }
    if (AkiraNotices.activeWindow() === window) MaterialTheme(
        colorScheme=LocalAkiraNoticeColors.current ?: MaterialTheme.colorScheme) { AkiraSnackbarHost(state, modifier) }
}

@Composable
fun AkiraSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val current = hostState.currentSnackbarData
    val accessibility = LocalAccessibilityManager.current
    val reduced = com.akira.app.core.design.components.LocalContentMotionReduced.current
    LaunchedEffect(current) {
        if (current != null) {
            val base = when (current.visuals.duration) {
                SnackbarDuration.Short -> 4000L
                SnackbarDuration.Long -> 10000L
                SnackbarDuration.Indefinite -> Long.MAX_VALUE
            }
            val timeout = accessibility?.calculateRecommendedTimeoutMillis(base,
                containsIcons = true, containsText = true, containsControls = current.visuals.actionLabel != null) ?: base
            delay(timeout)
            current.dismiss()
        }
    }
    AnimatedContent(current, modifier = modifier.fillMaxWidth(), contentKey = { it },
        contentAlignment = Alignment.BottomCenter,
        transitionSpec = {
            if (reduced) (EnterTransition.None togetherWith ExitTransition.None).using(null)
            else ((slideInVertically(spring(dampingRatio = .74f, stiffness = Spring.StiffnessMedium)) { it + 40 } +
                fadeIn(tween(160)) + scaleIn(tween(320, easing = CubicBezierEasing(.2f, 0f, 0f, 1f)), initialScale = .92f)) togetherWith
                (slideOutVertically(tween(260, easing = CubicBezierEasing(.2f, 0f, 0f, 1f))) { it + 40 } + fadeOut(tween(200))))
                .using(SizeTransform(clip = false) { _, _ -> tween(1) })
        }, label = "akiraBottomNotice") { data ->
        if (data != null) {
        key(data) {
        val haptics = com.akira.app.core.design.util.rememberAkiraHaptics()
        val dismissLabel = com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.DismissNotice)
        val dismissState = rememberSwipeToDismissBoxState(positionalThreshold = { it * .25f })
        LaunchedEffect(dismissState.currentValue) {
            if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) { haptics.light(); data.dismiss() }
        }
        SwipeToDismissBox(state = dismissState, backgroundContent = {},
            modifier = Modifier.semantics {
                customActions = listOf(androidx.compose.ui.semantics.CustomAccessibilityAction(
                    dismissLabel
                ) { data.dismiss(); true })
            }) {
        Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
        AkiraGlassSurface(Modifier.padding(horizontal = 18.dp, vertical = 8.dp).widthIn(max = 340.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
            shape = RoundedCornerShape(50)) {
            Row(Modifier.heightIn(min = 44.dp).padding(horizontal=14.dp,vertical=2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(if(data.visuals.message.lowercase().contains("favorit"))com.akira.app.core.design.icon.AkiraIcons.StarFilled
                    else Icons.Rounded.Check,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(9.dp))
                Text(data.visuals.message, Modifier.weight(1f,fill=false),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    fontFamily = AkiraFontFamily, fontSize = 14.sp, lineHeight = 18.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                data.visuals.actionLabel?.let { label ->
                    TextButton(onClick = data::performAction,
                        colors = ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(label, fontFamily = AkiraFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Normal, maxLines = 1)
                    }
                }
                if (data.visuals.withDismissAction) {
                    TextButton(onClick = data::dismiss) { Icon(com.akira.app.core.design.icon.AkiraIcons.Close, com.akira.app.core.i18n.LocalStrings.current.cancel, tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp)) }
                }
            }
        }
        }
        }
        }
    }
}
}
