package com.akira.app.core.media

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/**
 * Superficie mínima del reproductor que necesita [CrossfadeEngine].
 *
 * Existe para poder probar el motor sin un ExoPlayer real: un `FakeDeck`
 * implementa esta interfaz con valores fijos, mientras que en producción un
 * adaptador la implementa delegando en ExoPlayer. La única operación que se
 * simplifica respecto a ExoPlayer es [nextWindowIndex]: el motor original
 * consultaba `currentTimeline` (un objeto Media3 imposible de falsificar sin
 * construir ventanas reales), así que esa consulta vive ahora en el adaptador.
 */
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

    /**
     * Siguiente ventana según la línea de tiempo, o `C.INDEX_UNSET` si no hay.
     * En producción delega en `Timeline.getNextWindowIndex`; en tests devuelve
     * un valor fijo.
     */
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
