package com.akira.app.core.media

/** Service-looper state. Explicit transport commands revoke automatic resume,
 * even when Player emits no event because it is already paused. */
internal class TransientFocusResume {
    var pending: Boolean = false
        private set

    fun lost(willResume: Boolean, wasPlaying: Boolean) {
        pending = willResume && (wasPlaying || pending)
    }

    fun cancel() { pending = false }

    fun consumeGain(): Boolean = pending.also { pending = false }
}
