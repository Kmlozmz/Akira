package com.akira.app.core.audio

import kotlin.math.ln
import kotlin.math.roundToInt

/** Maps the logical Hz/dB curve to every hardware band (milliHz/millibels). */
internal object EqualizerBands {
    fun levels(
        frequenciesHz: IntArray,
        gainsDb: FloatArray,
        centersMilliHz: IntArray,
        minimumMb: Int,
        maximumMb: Int
    ): IntArray {
        val points = frequenciesHz.toList().mapIndexedNotNull { index, frequency ->
            if (frequency <= 0) null else frequency.toDouble() to
                (gainsDb.getOrNull(index)?.takeIf { it.isFinite() } ?: 0f)
        }.distinctBy { it.first }.sortedBy { it.first }
        return IntArray(centersMilliHz.size) { band ->
            val frequency = centersMilliHz[band] / 1000.0
            val gain = when {
                points.isEmpty() || frequency <= 0 -> 0f
                frequency <= points.first().first -> points.first().second
                frequency >= points.last().first -> points.last().second
                else -> {
                    val right = points.indexOfFirst { it.first >= frequency }
                    val (lowHz, lowGain) = points[right - 1]
                    val (highHz, highGain) = points[right]
                    val fraction = (ln(frequency / lowHz) / ln(highHz / lowHz)).toFloat()
                    lowGain + (highGain - lowGain) * fraction
                }
            }
            (gain * 100f).roundToInt().coerceIn(minimumMb, maximumMb)
        }
    }
}
