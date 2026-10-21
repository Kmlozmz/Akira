// SPDX-License-Identifier: GPL-3.0-only
package com.akira.app.feature.player.dock

import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import com.akira.app.core.design.util.AkiraHaptics
import com.akira.app.core.design.util.rememberAkiraHaptics
internal val GlassSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 320f)
internal enum class Haptic { Tick, Select, Pause, Resume, SkipNext, SkipPrevious }
internal class DockHaptics(private val feedback: AkiraHaptics) {
    fun play(kind: Haptic) = when(kind) {
        Haptic.Tick -> feedback.tick()
        Haptic.Select -> feedback.click()
        else -> feedback.transport()
    }
}
@Composable internal fun rememberHaptics(): DockHaptics {
    val feedback = rememberAkiraHaptics()
    return remember(feedback) { DockHaptics(feedback) }
}
