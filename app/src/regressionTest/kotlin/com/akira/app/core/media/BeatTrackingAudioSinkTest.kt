package com.akira.app.core.media

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

@UnstableApi
@OptIn(ExperimentalCoroutinesApi::class)
class BeatTrackingAudioSinkTest {
    @Test
    fun reportsDecodedAccentWhenTheAudioClockReachesIt() = runTest {
        val frames = mutableListOf<PlaybackLightFrame>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            PlaybackBeatLights.frames.collect { frames.add(it) }
        }
        var clockUs = 1_000_000L
        var forwardedFrames = 0
        val delegate = Proxy.newProxyInstance(AudioSink::class.java.classLoader, arrayOf(AudioSink::class.java)) { _, method, args ->
            when (method.name) {
                "getCurrentPositionUs" -> clockUs
                "handleBuffer" -> {
                    val buffer = args[0] as ByteBuffer
                    assertEquals(2, buffer.position())
                    forwardedFrames += buffer.remaining() / 2
                    buffer.position(buffer.limit())
                    true
                }
                else -> null
            }
        } as AudioSink
        val tracker = BeatTrackingAudioSink(delegate, AudioDeck.A)
        val rate = 3000
        tracker.configure(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setSampleRate(rate).setChannelCount(1).setPcmEncoding(C.ENCODING_PCM_16BIT).build(), 0, null)

        repeat(24) { window ->
            val pcm = ByteBuffer.allocate(2 + 75 * 2).order(ByteOrder.LITTLE_ENDIAN)
            pcm.position(2)
            val amplitude = if (window in 18..20) .75 else .025
            repeat(75) { offset ->
                val i = window * 75 + offset
                pcm.putShort((32767 * amplitude * sin(2 * PI * 65 * i / rate)).toInt().toShort())
            }
            pcm.position(2)
            assertTrue(tracker.handleBuffer(pcm, 1_000_000L + window * 25_000L, 1))
        }
        assertEquals(1800, forwardedFrames)
        assertEquals(1_000_000L, tracker.getCurrentPositionUs(false))
        assertTrue(frames.isEmpty())
        clockUs = 1_475_000L
        assertEquals(clockUs, tracker.getCurrentPositionUs(false))
        assertEquals(1, frames.size)
        assertTrue(frames.single().kick > 0f)
        assertTrue(frames.single().bass > frames.single().highs)
    }
}
