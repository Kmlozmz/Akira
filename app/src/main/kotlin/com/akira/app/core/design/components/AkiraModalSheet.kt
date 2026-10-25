package com.akira.app.core.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

val LocalAkiraAccent = staticCompositionLocalOf { Color(0xFFB8C8D4) }

/** Selectors, editors and confirmations share the menu's window and optical surface. */
enum class AkiraPanelStyle { Standard, Compact, Update, AudioOutput, Page, Details }
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AkiraModalSheet(onDismissRequest: () -> Unit, contentColor: Color = MaterialTheme.colorScheme.onSurface,
    title: String? = null,
    style: AkiraPanelStyle = AkiraPanelStyle.Standard,
    footer: (@Composable () -> Unit)? = null,
    containerColor: Color? = null,
    immersive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    if (style == AkiraPanelStyle.Page || style == AkiraPanelStyle.Details) {
        AkiraPageSheet(onDismissRequest, contentColor, title, footer, containerColor,
            style == AkiraPanelStyle.Details, immersive, content)
        return
    }
    AkiraPresentation(when(style) {
        AkiraPanelStyle.AudioOutput->AkiraPresentationKind.AudioOutput
        AkiraPanelStyle.Compact->AkiraPresentationKind.CompactPanel
        AkiraPanelStyle.Update->AkiraPresentationKind.Update
        else->AkiraPresentationKind.Panel
    },
        onDismissRequest, footer=footer) {
        val entry = LocalAkiraPresentationEntry.current
        val host = LocalAkiraPresentationState.current
        val scope = rememberCoroutineScope()
        val threshold = with(LocalDensity.current) { 64.dp.toPx() }
        var settleJob by remember { mutableStateOf<Job?>(null) }
        fun move(delta: Float): Float {
            settleJob?.cancel()
            val previous = entry?.dragOffset ?: 0f
            val next = (previous + delta).coerceAtLeast(0f)
            if (entry != null) entry.dragOffset = next
            return next - previous
        }
        fun settle(velocity: Float = 0f) {
            settleJob?.cancel()
            val offset = entry?.dragOffset ?: 0f
            if (entry != null && host != null && (offset > threshold || (offset > 0f && velocity > 1200f))) {
                entry.releaseVelocity = velocity
                host.closing = entry
            } else settleJob = scope.launch {
                Animatable(offset).animateTo(0f, spring(.88f, 550f), initialVelocity = velocity) {
                    if (entry != null) entry.dragOffset = value.coerceAtLeast(0f)
                }
            }
        }
        val scroll = remember(entry) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                    if (source == NestedScrollSource.UserInput && (entry?.dragOffset ?: 0f) > 0f)
                        Offset(0f, move(available.y)) else Offset.Zero
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                    if (source == NestedScrollSource.UserInput && available.y > 0f)
                        Offset(0f, move(available.y)) else Offset.Zero
                override suspend fun onPreFling(available: Velocity): Velocity {
                    if ((entry?.dragOffset ?: 0f) == 0f) return Velocity.Zero
                    settle(available.y)
                    return available
                }
            }
        }
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column(Modifier.fillMaxWidth().semantics {
                isTraversalGroup = true
                if(title != null) paneTitle = title
            }.then(if(style==AkiraPanelStyle.AudioOutput)Modifier else Modifier.nestedScroll(scroll))
                .padding(top = if(style==AkiraPanelStyle.Update)0.dp else if(style==AkiraPanelStyle.AudioOutput)14.dp else 16.dp,
                    bottom = if(style==AkiraPanelStyle.Update)0.dp else if(style==AkiraPanelStyle.AudioOutput)16.dp else 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, content = content)
        }
    }
}
