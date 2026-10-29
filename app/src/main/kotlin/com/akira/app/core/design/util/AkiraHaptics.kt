package com.akira.app.core.design.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** Use Android semantic haptic constants when available and older equivalents otherwise. View.performHapticFeedback respects the user's touch-feedback setting. */
class AkiraHaptics(private val view: View) {
    companion object { private var lastSegmentAt = Long.MIN_VALUE }

    private fun perform(constant: Int) {
        if (!com.akira.app.core.design.motion.InteractionPreferences.hapticsEnabled) return
        try {
            view.performHapticFeedback(constant)
        } catch (_: Exception) {
            try { view.performHapticFeedback(constant) } catch (_: Exception) {}
        }
    }

    /** Discrete option or tab selection. */
    fun tick() = perform(
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK
        else HapticFeedbackConstants.CLOCK_TICK
    )

    /** Light repeated ticks while dragging a slider. */
    fun softTick() {
        val now = android.os.SystemClock.uptimeMillis()
        if (lastSegmentAt != Long.MIN_VALUE && now - lastSegmentAt < 40L) return
        lastSegmentAt = now
        perform(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
            else HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Interruptores. */
    fun toggle(on: Boolean) = perform(
        when {
            Build.VERSION.SDK_INT >= 34 ->
                if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
            Build.VERSION.SDK_INT >= 30 -> HapticFeedbackConstants.CONFIRM
            else -> HapticFeedbackConstants.KEYBOARD_TAP
        }
    )

    /** Completed reset or apply action. */
    fun confirm() = perform(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.KEYBOARD_TAP
    )

    /** Light button or back feedback. */
    fun light() = perform(HapticFeedbackConstants.KEYBOARD_TAP)

    /** Standard interaction click. */
    fun click() = perform(HapticFeedbackConstants.KEYBOARD_TAP)

    /** A single firm detent for committed transport actions, not a long buzz. */
    fun transport() = perform(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONTEXT_CLICK
        else HapticFeedbackConstants.KEYBOARD_TAP
    )

    /** Toggle feedback without a state argument. */
    fun toggle() = toggle(true)

    /** Long-press emphasis. */
    fun heavy() = perform(HapticFeedbackConstants.LONG_PRESS)
}

@Composable
fun rememberAkiraHaptics(): AkiraHaptics {
    val view = LocalView.current
    return remember(view) { AkiraHaptics(view) }
}
