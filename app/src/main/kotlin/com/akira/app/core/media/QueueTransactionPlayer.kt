package com.akira.app.core.media

import androidx.media3.common.*

/** Expose only committed playlist state to MediaSession (including its legacy
 * system queue). Commands and the current AudioTrack keep their original owner.
 * Hundreds of shuffled removals must not send hundreds of full Binder queues.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class QueueTransactionPlayer(player: Player) : ForwardingPlayer(player) {
    private var depth=0
    // ForwardingPlayer creates equal (not identical) listener wrappers when
    // removing. Identity keys would leak the old MediaSession listener and let
    // the outgoing deck overwrite the new deck's state with IDLE/empty queue.
    private val listeners=LinkedHashMap<Player.Listener,PendingListener>()

    fun begin() { depth++ }
    fun end() {
        check(depth>0)
        if(--depth==0)listeners.values.toList().forEach { it.flush() }
    }

    override fun addListener(listener: Player.Listener) {
        if(listeners.containsKey(listener))return
        val pending=PendingListener(listener)
        listeners[listener]=pending
        super.addListener(pending)
    }
    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)?.let { super.removeListener(it) }
    }

    private inner class PendingListener(val target: Player.Listener) : Player.Listener {
        // Kotlin delegation does not forward Java default interface methods.
        // Forward each callback explicitly or MediaSession retains IDLE/paused
        // while the underlying AudioTrack is already playing.
        override fun onTracksChanged(value: Tracks) = target.onTracksChanged(value)
        override fun onMediaMetadataChanged(value: MediaMetadata) = target.onMediaMetadataChanged(value)
        override fun onPlaylistMetadataChanged(value: MediaMetadata) = target.onPlaylistMetadataChanged(value)
        override fun onIsLoadingChanged(value: Boolean) = target.onIsLoadingChanged(value)
        override fun onTrackSelectionParametersChanged(value: TrackSelectionParameters) = target.onTrackSelectionParametersChanged(value)
        override fun onPlaybackStateChanged(value: Int) = target.onPlaybackStateChanged(value)
        override fun onPlayWhenReadyChanged(value: Boolean, reason: Int) = target.onPlayWhenReadyChanged(value,reason)
        override fun onPlaybackSuppressionReasonChanged(value: Int) = target.onPlaybackSuppressionReasonChanged(value)
        override fun onIsPlayingChanged(value: Boolean) = target.onIsPlayingChanged(value)
        override fun onRepeatModeChanged(value: Int) = target.onRepeatModeChanged(value)
        override fun onShuffleModeEnabledChanged(value: Boolean) = target.onShuffleModeEnabledChanged(value)
        override fun onPlayerError(value: PlaybackException) = target.onPlayerError(value)
        override fun onPlayerErrorChanged(value: PlaybackException?) = target.onPlayerErrorChanged(value)
        override fun onPlaybackParametersChanged(value: PlaybackParameters) = target.onPlaybackParametersChanged(value)
        override fun onSeekBackIncrementChanged(value: Long) = target.onSeekBackIncrementChanged(value)
        override fun onSeekForwardIncrementChanged(value: Long) = target.onSeekForwardIncrementChanged(value)
        override fun onMaxSeekToPreviousPositionChanged(value: Long) = target.onMaxSeekToPreviousPositionChanged(value)
        override fun onVideoSizeChanged(value: VideoSize) = target.onVideoSizeChanged(value)
        override fun onSurfaceSizeChanged(width: Int,height: Int) = target.onSurfaceSizeChanged(width,height)
        override fun onRenderedFirstFrame() = target.onRenderedFirstFrame()
        override fun onAudioSessionIdChanged(value: Int) = target.onAudioSessionIdChanged(value)
        override fun onAudioAttributesChanged(value: AudioAttributes) = target.onAudioAttributesChanged(value)
        override fun onVolumeChanged(value: Float) = target.onVolumeChanged(value)
        override fun onSkipSilenceEnabledChanged(value: Boolean) = target.onSkipSilenceEnabledChanged(value)
        override fun onCues(value: androidx.media3.common.text.CueGroup) = target.onCues(value)
        override fun onMetadata(value: Metadata) = target.onMetadata(value)
        override fun onDeviceInfoChanged(value: DeviceInfo) = target.onDeviceInfoChanged(value)
        override fun onDeviceVolumeChanged(value: Int,muted: Boolean) = target.onDeviceVolumeChanged(value,muted)
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onLoadingChanged(value: Boolean) = target.onLoadingChanged(value)
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onPlayerStateChanged(ready: Boolean,state: Int) = target.onPlayerStateChanged(ready,state)
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onPositionDiscontinuity(reason: Int) { if(depth==0)target.onPositionDiscontinuity(reason) }
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCues(value: List<androidx.media3.common.text.Cue>) = target.onCues(value)
        private var timeline: Pair<Timeline,Int>?=null
        private var item: Pair<MediaItem?,Int>?=null
        private var position: Triple<Player.PositionInfo,Player.PositionInfo,Int>?=null
        private var commands: Player.Commands?=null
        private val flags=LinkedHashSet<Int>()

        override fun onTimelineChanged(value: Timeline,reason: Int) {
            if(depth>0)timeline=value to reason else target.onTimelineChanged(value,reason)
        }
        override fun onMediaItemTransition(value: MediaItem?,reason: Int) {
            if(depth>0)item=value to reason else target.onMediaItemTransition(value,reason)
        }
        override fun onPositionDiscontinuity(old: Player.PositionInfo,new: Player.PositionInfo,reason: Int) {
            if(depth>0)position=Triple(position?.first ?: old,new,reason)
            else target.onPositionDiscontinuity(old,new,reason)
        }
        override fun onAvailableCommandsChanged(value: Player.Commands) {
            if(depth>0)commands=value else target.onAvailableCommandsChanged(value)
        }
        override fun onEvents(player: Player,events: Player.Events) {
            if(depth>0)for(i in 0 until events.size())flags.add(events.get(i))
            else target.onEvents(this@QueueTransactionPlayer,events)
        }
        fun flush() {
            val t=timeline;val m=item;val p=position;val c=commands
            val events=if(flags.isEmpty())null else Player.Events(FlagSet.Builder().addAll(*flags.toIntArray()).build())
            timeline=null;item=null;position=null;commands=null;flags.clear()
            t?.let { target.onTimelineChanged(it.first,it.second) }
            m?.let { target.onMediaItemTransition(it.first,it.second) }
            p?.let { target.onPositionDiscontinuity(it.first,it.second,it.third) }
            c?.let { target.onAvailableCommandsChanged(it) }
            events?.let { target.onEvents(this@QueueTransactionPlayer,it) }
        }
    }
}
