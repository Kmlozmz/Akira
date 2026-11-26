package com.akira.app.feature.player

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerFluidBackdropTest {
    @Test
    fun beatEnvelopeRespondsToImpactsAndDecaysWhenAudioStops() {
        val tracker = BeatEnvelopeTracker()
        val t0 = 1_000_000_000L
        tracker.onFrame(frameBass = .8f, frameHighs = .5f, frameKick = .9f, frameAccent = .6f, nowNanos = t0)
        val peak = tracker.step(dtSeconds = .032f, nowNanos = t0 + 32_000_000L)
        assertTrue(peak.bass > .3f)
        assertTrue(peak.kick > .5f)
        assertTrue(peak.highs > .2f)
        assertTrue(peak.accent > .3f)
        assertTrue(peak.phaseBoost > 0f)

        var decayed = peak
        var clock = t0 + 32_000_000L
        repeat(90) {
            clock += 32_000_000L
            decayed = tracker.step(dtSeconds = .032f, nowNanos = clock)
        }
        assertEquals(0f, decayed.bass, .001f)
        assertEquals(0f, decayed.highs, .001f)
        assertEquals(0f, decayed.kick, .001f)
        assertEquals(0f, decayed.accent, .001f)
    }

    @Test
    fun liveArtworkSamplerExtractsRegionsAndTracksFrameMotionShift() {
        val sampler = LiveArtworkSampler(gridSize = 12)
        val frameA = IntArray(12 * 12) { idx ->
            val x = idx % 12
            val y = idx / 12
            when {
                x in 3 until 9 && y in 3 until 9 -> 0xFFFA243C.toInt()
                x < 6 && y < 6 -> 0xFF1E88E5.toInt()
                else -> 0xFF181820.toInt()
            }
        }
        val first = sampler.analyze(frameA)
        assertNotNull(first)
        assertEquals(5, first!!.colors.size)
        assertEquals(0f, first.motionEnergy, 0f)
        assertTrue(first.colors[0].red > .8f)
        assertTrue(first.colors[1].blue > .7f)

        val still = sampler.analyze(frameA.copyOf())
        assertEquals(0f, still!!.motionEnergy, 0f)

        val frameB = frameA.copyOf()
        for (y in 7 until 12) {
            for (x in 7 until 12) {
                frameB[y * 12 + x] = 0xFFFFD54F.toInt()
            }
        }
        val moved = sampler.analyze(frameB)!!
        assertTrue(moved.motionEnergy > .2f)
        assertTrue(moved.shiftX > .3f)
        assertTrue(moved.shiftY > .3f)
        assertTrue(moved.colors[4] != Color.Black && moved.colors[4].red > .75f)
    }

    @Test
    fun fluidRichColorCapsOverlyBrightPastelsWithoutCrushingDarkHues() {
        val bright = fluidRichColor(Color(0xFFF6EFE6))
        assertTrue(maxOf(bright.red, bright.green, bright.blue) <= .68f)

        val crimson = fluidRichColor(Color(0xFFFA243C))
        assertTrue(crimson.red > crimson.blue * 2f)
    }
}
