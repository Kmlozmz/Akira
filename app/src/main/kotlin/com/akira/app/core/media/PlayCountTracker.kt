package com.akira.app.core.media

/** Count listened playback progress; seeks and pauses do not add listening time. */
class PlayCountTracker(private val durationMs: Long) {
    private var heardMs = 0L
    private var lastPosition: Long? = null
    private var counted = false
    private var skipped = false
    fun seek() { skipped = true }
    fun sample(positionMs: Long, playing: Boolean, elapsedMs: Long): Boolean {
        val previous = lastPosition
        lastPosition = positionMs
        if (counted || skipped || durationMs <= 0 || !playing || previous == null) return false
        val advance = positionMs - previous
        if (advance in 1..(elapsedMs.coerceAtLeast(0) + 500)) heardMs += advance.coerceAtMost(elapsedMs)
        if (heardMs >= minOf(30_000L, durationMs / 2)) {
            counted = true
            return true
        }
        return false
    }
}
