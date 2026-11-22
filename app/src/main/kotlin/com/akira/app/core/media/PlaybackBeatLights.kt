package com.akira.app.core.media

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.sqrt

internal enum class AudioDeck { A, B }

internal data class PlaybackLightFrame(
    val bass: Float,
    val highs: Float,
    val kick: Float,
    val accent: Float,
)

internal object PlaybackBeatLights {
    private val events = MutableSharedFlow<PlaybackLightFrame>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val frames = events.asSharedFlow()
    @Volatile var activeDeck = AudioDeck.A
    private val lastImpactNanos = AtomicLong()
    private val lastSweepNanos = AtomicLong()

    fun observes(deck: AudioDeck) = activeDeck == deck && events.subscriptionCount.value > 0

    fun publish(deck: AudioDeck, frame: PlaybackLightFrame) {
        if (!observes(deck)) return
        if (frame.kick == 0f && frame.accent == 0f) {
            events.tryEmit(frame)
            return
        }
        val now = System.nanoTime()
        val impact = frame.kick > 0f && canTrigger(lastImpactNanos, now, 340_000_000L)
        val sweep = frame.accent > 0f && canTrigger(lastSweepNanos, now, 170_000_000L)
        events.tryEmit(frame.copy(
            kick = if (impact) frame.kick else 0f,
            accent = if (sweep) frame.accent else 0f,
        ))
    }

    private fun canTrigger(clock: AtomicLong, now: Long, spacing: Long): Boolean {
        val previous = clock.get()
        return (previous == 0L || now - previous >= spacing) && clock.compareAndSet(previous, now)
    }
}

/** Two band envelopes and their transient strength. The continuous levels
 * drive the light between impacts, even on tracks with very steady bass. */
internal class PlaybackLightAnalyzer(private val sampleRate: Int) {
    private val windowFrames = (sampleRate / 50).coerceAtLeast(1)
    private val bassFilter = 905f / (sampleRate + 905f)
    private val midFilter = 3_100f / (sampleRate + 3_100f)
    private val bassBand = Band()
    private val highBand = Band()
    private var low = 0f
    private var mid = 0f
    private var samples = 0
    private var windows = 0

    fun sample(value: Float): PlaybackLightFrame? {
        low += bassFilter * (value - low)
        mid += midFilter * (value - mid)
        bassBand.accumulate(low)
        highBand.accumulate(value - mid)
        if (++samples < windowFrames) return null

        val lowLevel = bassBand.finish(samples)
        val highLevel = highBand.finish(samples)
        val warmed = windows >= 5
        val kick = if (warmed && lowLevel > highLevel * .70f) bassBand.attack(lowLevel) else 0f
        val accent = if (warmed && highLevel > lowLevel * .36f) highBand.attack(highLevel) else 0f
        val frame = PlaybackLightFrame(bassBand.normalize(lowLevel), highBand.normalize(highLevel), kick, accent)
        bassBand.update(lowLevel, warmed)
        highBand.update(highLevel, warmed)
        samples = 0
        if (!warmed) windows++
        return frame
    }

    private class Band {
        private var power = 0f
        private var average = 0f
        private var previous = 0f
        private var flux = 0f

        fun accumulate(value: Float) { power += value * value }

        fun finish(samples: Int): Float {
            val level = sqrt(power / samples)
            power = 0f
            return level
        }

        fun attack(level: Float): Float {
            val rise = level - previous
            val threshold = maxOf(.005f, average * .13f, flux * 2.1f)
            if (level < .012f || rise <= threshold || level <= average * 1.09f + .004f) return 0f
            return (.28f + (rise - threshold) / (average + .065f)).coerceIn(0f, 1f)
        }

        fun normalize(level: Float): Float =
            (level / (.085f + average * 1.55f)).coerceIn(0f, 1f)

        fun update(level: Float, warmed: Boolean) {
            flux += ((level - previous).coerceAtLeast(0f) - flux) * .16f
            average += (level - average) * if (warmed) .09f else .38f
            previous = level
        }
    }
}

/** Reads a sparse sample of decoded PCM, leaving the buffer and all routing
 * intact. Pulses are delivered as the sink's playback clock reaches them. */
@UnstableApi
internal class BeatTrackingAudioSink(sink: AudioSink, private val deck: AudioDeck) : ForwardingAudioSink(sink) {
    private data class TimedLight(val positionUs: Long, val frame: PlaybackLightFrame)

    private val pending = ArrayDeque<TimedLight>()
    private var format: Format? = null
    private var detector: PlaybackLightAnalyzer? = null
    private var retryBuffer: ByteBuffer? = null
    private var lastTimestampUs = C.TIME_UNSET
    private var observing = false

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
        format = inputFormat.takeIf { it.sampleMimeType == MimeTypes.AUDIO_RAW }
        clearAnalysis()
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        val enabled = PlaybackBeatLights.observes(deck)
        if (enabled != observing) {
            clearAnalysis()
            observing = enabled
        }
        if (enabled && buffer !== retryBuffer && presentationTimeUs != C.TIME_UNSET) {
            if (lastTimestampUs != C.TIME_UNSET && abs(presentationTimeUs - lastTimestampUs) > 250_000L) {
                clearAnalysis()
            }
            format?.let { scan(buffer, it, presentationTimeUs) }
            lastTimestampUs = presentationTimeUs
        }
        retryBuffer = buffer
        val consumed = super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        if (consumed) retryBuffer = null
        return consumed
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val position = super.getCurrentPositionUs(sourceEnded)
        if (PlaybackBeatLights.observes(deck) && position != AudioSink.CURRENT_POSITION_NOT_SET) {
            var current: PlaybackLightFrame? = null
            var kick = 0f
            var accent = 0f
            while (pending.firstOrNull()?.let { it.positionUs <= position } == true) {
                val light = pending.removeFirst()
                if (position - light.positionUs >= 100_000L) continue
                current = light.frame
                kick = maxOf(kick, light.frame.kick)
                accent = maxOf(accent, light.frame.accent)
            }
            current?.let { PlaybackBeatLights.publish(deck, it.copy(kick = kick, accent = accent)) }
        }
        return position
    }

    override fun flush() {
        clearAnalysis()
        super.flush()
    }

    override fun handleDiscontinuity() {
        clearAnalysis()
        super.handleDiscontinuity()
    }

    override fun reset() {
        clearAnalysis()
        super.reset()
    }

    private fun clearAnalysis() {
        pending.clear()
        detector = null
        retryBuffer = null
        lastTimestampUs = C.TIME_UNSET
    }

    private fun scan(buffer: ByteBuffer, format: Format, startUs: Long) {
        val sampleBytes = when (format.pcmEncoding) {
            C.ENCODING_PCM_8BIT -> 1
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
            else -> return
        }
        val rate = format.sampleRate
        val channels = format.channelCount
        if (rate <= 0 || channels <= 0) return
        val size = channels * sampleBytes
        val stride = (rate / 6000).coerceAtLeast(1)
        val analysis = detector ?: PlaybackLightAnalyzer(rate / stride).also { detector = it }
        val start = buffer.position()
        val frames = buffer.remaining() / size
        for (frame in 0 until frames step stride) {
            val index = start + frame * size
            val left = pcmSample(buffer, index, format.pcmEncoding)
            val mono = if (channels == 1) left else (left + pcmSample(buffer, index + sampleBytes, format.pcmEncoding)) * .5f
            val positionUs = startUs + frame * 1_000_000L / rate
            val frame = analysis.sample(mono) ?: continue
            if (pending.size == 256) pending.removeFirst()
            pending.addLast(TimedLight(positionUs, frame))
        }
    }

    private fun pcmSample(buffer: ByteBuffer, index: Int, encoding: Int): Float = when (encoding) {
        C.ENCODING_PCM_8BIT -> ((buffer[index].toInt() and 255) - 128) / 128f
        C.ENCODING_PCM_16BIT -> buffer.getShort(index) / 32768f
        C.ENCODING_PCM_24BIT -> {
            val packed = (buffer[index].toInt() and 255) or ((buffer[index + 1].toInt() and 255) shl 8) or
                (buffer[index + 2].toInt() shl 16)
            packed / 8388608f
        }
        C.ENCODING_PCM_32BIT -> buffer.getInt(index) / 2147483648f
        else -> buffer.getFloat(index).takeIf(Float::isFinite)?.coerceIn(-1f, 1f) ?: 0f
    }
}
