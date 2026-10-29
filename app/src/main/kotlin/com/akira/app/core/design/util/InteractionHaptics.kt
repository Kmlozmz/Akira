package com.akira.app.core.design.util

import android.os.SystemClock
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.akira.app.core.design.motion.InteractionPreferences
import kotlin.math.roundToInt

internal class PreferenceHaptics(private val delegate: HapticFeedback) : HapticFeedback {
    private var lastAt = Long.MIN_VALUE
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        if (!InteractionPreferences.hapticsEnabled) return
        val now = SystemClock.uptimeMillis()
        if (lastAt != Long.MIN_VALUE && now - lastAt < 90L) return
        lastAt = now
        delegate.performHapticFeedback(hapticFeedbackType)
    }
}

/** Synchronizing playback state never emits a tick. Only new user detents do. */
internal class SliderHapticGate(private val start: Float, private val end: Float, private val intervals: Int,
    private val minimumIntervalMs: Long = 40L) {
    private var detent: Int? = null
    private var lastAt: Long? = null
    private fun position(value: Float): Int = if (end <= start || !value.isFinite()) 0
        else (((value.coerceIn(start, end) - start) / (end - start)) * intervals.coerceAtLeast(1)).roundToInt()
    fun sync(value: Float) { detent = position(value) }
    fun changed(value: Float, now: Long): Boolean {
        val next = position(value)
        val old = detent
        detent = next
        if (old == null || old == next || !value.isFinite() || end <= start) return false
        if (lastAt?.let { now - it < minimumIntervalMs } == true) return false
        lastAt = now
        return true
    }
}
