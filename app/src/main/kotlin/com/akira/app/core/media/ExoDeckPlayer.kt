package com.akira.app.core.media

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/** Adapt ExoPlayer to DeckPlayer, including the Timeline query needed to resolve nextWindowIndex. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ExoDeckPlayer(private val player: ExoPlayer) : DeckPlayer {
    override var playbackParameters: androidx.media3.common.PlaybackParameters
        get()=player.playbackParameters
        set(value) {player.playbackParameters=value}

    override var volume: Float
        get() = player.volume
        set(value) { player.volume = value }

    override val mediaItemCount: Int get() = player.mediaItemCount
    override val currentMediaItemIndex: Int get() = player.currentMediaItemIndex
    override val currentMediaItem: MediaItem? get() = player.currentMediaItem
    override fun hasNextMediaItem(): Boolean = player.hasNextMediaItem()

    override var shuffleModeEnabled: Boolean
        get() = player.shuffleModeEnabled
        set(value) { player.shuffleModeEnabled = value }

    override var repeatMode: Int
        get() = player.repeatMode
        set(value) { player.repeatMode = value }

    override val duration: Long get() = player.duration
    override val currentPosition: Long get() = player.currentPosition
    override val isPlaying: Boolean get() = player.isPlaying
    override val playbackState: Int get() = player.playbackState
    override val usesPositionClock: Boolean get() = true
    override var pauseAtEndOfMediaItems: Boolean
        get() = player.pauseAtEndOfMediaItems
        set(value) { player.pauseAtEndOfMediaItems = value }

    override var playWhenReady: Boolean
        get() = player.playWhenReady
        set(value) { player.playWhenReady = value }

    override fun nextWindowIndex(currentIdx: Int): Int {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return C.INDEX_UNSET
        return timeline.getNextWindowIndex(
            currentIdx,
            player.repeatMode,
            player.shuffleModeEnabled
        )
    }

    override fun getMediaItemAt(index: Int): MediaItem = player.getMediaItemAt(index)

    override fun setMediaItems(items: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        player.setMediaItems(items, startIndex, startPositionMs)
    }

    override fun copyPlaybackOrderFrom(source: DeckPlayer) {
        if (source is ExoDeckPlayer) {
            player.playbackParameters=source.player.playbackParameters
            player.skipSilenceEnabled=source.player.skipSilenceEnabled
            player.shuffleModeEnabled = source.shuffleModeEnabled
            player.repeatMode = source.repeatMode
            player.setShuffleOrder(androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(
                playbackQueueIndexes(source.player).toIntArray(), System.nanoTime()))
        }
    }

    override fun prepare() = player.prepare()
    override fun play() = player.play()
    override fun pause() = player.pause()
    override fun stop() = player.stop()
    override fun clearMediaItems() = player.clearMediaItems()
    override fun addListener(listener: Player.Listener) = player.addListener(listener)
    override fun removeListener(listener: Player.Listener) = player.removeListener(listener)

    /** Expose ExoPlayer for operations outside the transition engine. */
    fun unwrap(): ExoPlayer = player
}
