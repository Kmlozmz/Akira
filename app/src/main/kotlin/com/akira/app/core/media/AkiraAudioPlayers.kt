package com.akira.app.core.media

import android.content.Context
import android.util.Log
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.akira.app.core.logging.AkiraLog

/** Shared decoder policy for both decks. ON keeps platform decoders first;
 * optional extensions must actually be bundled before they can be selected.
 * Keep PCM processing available for playback speed and skip-silence.
 */
@UnstableApi
internal fun audioRenderers(context: Context) = DefaultRenderersFactory(context)
    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
    .setEnableDecoderFallback(true)

@UnstableApi
internal class AudioDiagnostics(private val deck: String) : AnalyticsListener {
    private fun record(message: String) {
        Log.i("AkiraAudio", "deck=$deck $message")
        AkiraLog.i("Audio", "deck=$deck $message")
    }
    override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime,
        decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
        record("decoder=$decoderName init=${initializationDurationMs}ms")
    }
    override fun onAudioInputFormatChanged(eventTime: AnalyticsListener.EventTime,
        format: Format, decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?) {
        record("format=${format.sampleMimeType} rate=${format.sampleRate} channels=${format.channelCount} pcm=${format.pcmEncoding}")
    }
    override fun onAudioUnderrun(eventTime: AnalyticsListener.EventTime, bufferSize: Int,
        bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
        record("UNDERRUN buffer=$bufferSize bytes/${bufferSizeMs}ms lastFeed=${elapsedSinceLastFeedMs}ms")
    }
    override fun onAudioSinkError(eventTime: AnalyticsListener.EventTime, audioSinkError: Exception) {
        record("sink error=${audioSinkError.javaClass.simpleName}")
    }
}
