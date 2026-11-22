package com.akira.app.core.media

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackLightAnalyzerTest {
    @Test
    fun followsBassAttacksAndIgnoresSteadyTonesAsImpacts() {
        val rate = 6000
        val detector = PlaybackLightAnalyzer(rate)
        val times = buildList {
            repeat(rate * 3) { i ->
                val timeUs = i * 1_000_000L / rate
                val phase = timeUs % 500_000L
                val amplitude = if (timeUs >= 500_000L && phase < 90_000L) .72f else .025f
                val sample = amplitude * sin(2 * PI * 64 * i / rate).toFloat()
                if ((detector.sample(sample)?.kick ?: 0f) > 0f) add(timeUs)
            }
        }
        assertEquals(5, times.size)
        times.forEachIndexed { index, time ->
            assertTrue(time in ((index + 1) * 500_000L)..((index + 1) * 500_000L + 50_000L))
        }

        val constant = PlaybackLightAnalyzer(rate)
        repeat(rate) { i ->
            val signal = .4f * sin(2 * PI * 64 * i / rate).toFloat()
            constant.sample(signal)?.let { frame ->
                assertEquals(0f, frame.kick, 0f)
                assertEquals(0f, frame.accent, 0f)
                assertTrue(frame.bass > frame.highs)
            }
        }
    }

    @Test
    fun resolvesAnUpperBandAccentOverAConstantBassLine() {
        val rate = 6000
        val detector = PlaybackLightAnalyzer(rate)
        val frames = buildList {
            repeat(rate) { i ->
                val percussion = if (i in 3000..3299) .42f * sin(2 * PI * 1190 * i / rate).toFloat() else 0f
                val bass = .10f * sin(2 * PI * 60 * i / rate).toFloat()
                detector.sample(bass + percussion)?.let(::add)
            }
        }
        val accent = frames[25]
        assertTrue(accent.accent > .4f)
        assertEquals(0f, accent.kick, 0f)
        assertTrue(accent.highs > frames[20].highs)
        assertTrue(frames[36].highs < accent.highs)
    }
}
