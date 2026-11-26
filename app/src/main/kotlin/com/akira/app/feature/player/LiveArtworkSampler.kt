package com.akira.app.feature.player

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

@Immutable
internal data class LiveArtworkSample(
    val colors: List<Color>,
    val motionEnergy: Float,
    val shiftX: Float,
    val shiftY: Float,
)

/** Extracts 5 live region colours and frame-to-frame motion centroid from a small pixel grid. */
internal class LiveArtworkSampler(private val gridSize: Int = 12) {
    private val previous = IntArray(gridSize * gridSize)
    private var hasPrevious = false

    fun reset() {
        hasPrevious = false
        previous.fill(0)
    }

    fun analyze(pixels: IntArray): LiveArtworkSample? {
        val count = gridSize * gridSize
        if (pixels.size < count) return null

        val half = gridSize / 2
        // 0: center, 1: top-left, 2: top-right, 3: bottom-left, 4: bottom-right
        val red = LongArray(5)
        val green = LongArray(5)
        val blue = LongArray(5)
        val samples = IntArray(5)

        var totalDiff = 0f
        var weightedX = 0f
        var weightedY = 0f
        var diffSum = 0f

        for (y in 0 until gridSize) {
            for (x in 0 until gridSize) {
                val idx = y * gridSize + x
                val px = pixels[idx]
                val r = (px ushr 16) and 255
                val g = (px ushr 8) and 255
                val b = px and 255

                val q = when {
                    x in (gridSize / 4) until (gridSize * 3 / 4) && y in (gridSize / 4) until (gridSize * 3 / 4) -> 0
                    y < half && x < half -> 1
                    y < half -> 2
                    x < half -> 3
                    else -> 4
                }
                red[q] += r
                green[q] += g
                blue[q] += b
                samples[q]++

                if (hasPrevious) {
                    val prev = previous[idx]
                    val pr = (prev ushr 16) and 255
                    val pg = (prev ushr 8) and 255
                    val pb = prev and 255
                    val delta = (abs(r - pr) + abs(g - pg) + abs(b - pb)) / 765f
                    totalDiff += delta
                    if (delta > 0.04f) {
                        val nx = (x.toFloat() / (gridSize - 1).coerceAtLeast(1)) * 2f - 1f
                        val ny = (y.toFloat() / (gridSize - 1).coerceAtLeast(1)) * 2f - 1f
                        weightedX += nx * delta
                        weightedY += ny * delta
                        diffSum += delta
                    }
                }
                previous[idx] = px
            }
        }
        hasPrevious = true

        val fallback = Color(
            (red.sum() / count).toInt().coerceIn(0, 255),
            (green.sum() / count).toInt().coerceIn(0, 255),
            (blue.sum() / count).toInt().coerceIn(0, 255),
        )
        val colors = (0..4).map { i ->
            val n = samples[i]
            if (n == 0) fallback else Color(
                (red[i] / n).toInt().coerceIn(0, 255),
                (green[i] / n).toInt().coerceIn(0, 255),
                (blue[i] / n).toInt().coerceIn(0, 255),
            )
        }
        val motionEnergy = ((totalDiff / count) * 6.5f).coerceIn(0f, 1f)
        val shiftX = if (diffSum > 0.01f) (weightedX / diffSum).coerceIn(-1f, 1f) else 0f
        val shiftY = if (diffSum > 0.01f) (weightedY / diffSum).coerceIn(-1f, 1f) else 0f
        return LiveArtworkSample(colors, motionEnergy, shiftX, shiftY)
    }
}
