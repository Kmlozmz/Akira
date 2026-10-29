package com.akira.app.core.media

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/** Minimal CrossfadeEngine player interface. Tests use FakeDeck; production delegates to ExoPlayer. Keep Timeline-dependent nextWindowIndex logic in the adapter. */
interface DeckPlayer {
    var volume: Float
    var playbackParameters: androidx.media3.common.PlaybackParameters
        get() = androidx.media3.common.PlaybackParameters.DEFAULT
        set(value) {}
    val mediaItemCount: Int
    val currentMediaItemIndex: Int
    val currentMediaItem: MediaItem?
    fun hasNextMediaItem(): Boolean
    var shuffleModeEnabled: Boolean
    var repeatMode: Int
    val duration: Long
    val currentPosition: Long
    val isPlaying: Boolean
    val playbackState: Int get() = Player.STATE_READY
    /** Production decks use their rendered media position, never a UI timer. */
    val usesPositionClock: Boolean get() = false
    var pauseAtEndOfMediaItems: Boolean
        get() = false
        set(value) {}
    var playWhenReady: Boolean

    /** Next timeline window, or C.INDEX_UNSET. Production delegates to Timeline.getNextWindowIndex; tests supply a fixed value. */
    fun nextWindowIndex(currentIdx: Int): Int

    fun getMediaItemAt(index: Int): MediaItem
    fun setMediaItems(items: List<MediaItem>, startIndex: Int, startPositionMs: Long)
    fun copyPlaybackOrderFrom(source: DeckPlayer) {}
    fun prepare()
    fun play()
    fun pause()
    fun stop()
    fun clearMediaItems()
    fun addListener(listener: Player.Listener)
    fun removeListener(listener: Player.Listener)
}
