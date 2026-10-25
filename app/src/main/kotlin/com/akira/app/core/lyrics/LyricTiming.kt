package com.akira.app.core.lyrics

internal object LyricTiming {
    fun clampOffsetMs(offsetMs: Long): Long = offsetMs.coerceIn(-10_000L, 10_000L)

    fun adjustOffsetMs(offsetMs: Long, deltaMs: Long): Long =
        clampOffsetMs(saturatedAdd(clampOffsetMs(offsetMs), deltaMs))

    fun effectivePositionMs(positionMs: Long, offsetMs: Long): Long =
        saturatedAdd(positionMs, offsetMs).coerceAtLeast(0L)

    fun seekPositionMs(lyricTimeMs: Long, offsetMs: Long): Long = when {
        offsetMs < 0 && lyricTimeMs > Long.MAX_VALUE + offsetMs -> Long.MAX_VALUE
        offsetMs > 0 && lyricTimeMs < Long.MIN_VALUE + offsetMs -> 0L
        else -> (lyricTimeMs - offsetMs).coerceAtLeast(0L)
    }

    fun activeLineIndex(lines: List<LyricLine>, positionMs: Long): Int =
        lines.indexOfLast { it.timeMs <= positionMs }

    private fun saturatedAdd(value: Long, delta: Long): Long = when {
        delta > 0 && value > Long.MAX_VALUE - delta -> Long.MAX_VALUE
        delta < 0 && value < Long.MIN_VALUE - delta -> Long.MIN_VALUE
        else -> value + delta
    }
}
