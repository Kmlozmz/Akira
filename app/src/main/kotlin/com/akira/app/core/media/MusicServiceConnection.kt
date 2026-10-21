package com.akira.app.core.media

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import androidx.core.content.ContextCompat
import com.akira.app.core.media.MediaItemMapper.toMediaItem
import com.akira.app.core.media.MediaItemMapper.toSong
import com.akira.app.core.media.MediaItemMapper.queueIdentity
import com.akira.app.core.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.akira.app.core.online.OnlineRepository
import com.akira.app.core.online.SourceQueueStore
import com.akira.app.core.widget.WidgetQueueSnapshot
import com.akira.app.core.widget.WidgetPlaybackStore

@UnstableApi
class MusicServiceConnection(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
        private set

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _playbackError = MutableStateFlow(false)
    val playbackError = _playbackError.asStateFlow()
    private var sourceMode: Boolean? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _shuffleMode = MutableStateFlow(false)
    val shuffleMode: StateFlow<Boolean> = _shuffleMode.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<Song>>(emptyList())
    val currentQueue: StateFlow<List<Song>> = _currentQueue.asStateFlow()

    private val _currentMediaItemIndex = MutableStateFlow(0)
    val currentMediaItemIndex: StateFlow<Int> = _currentMediaItemIndex.asStateFlow()

    private var progressJob: Job? = null
    private var queueIndexes: List<Int> = emptyList()
    private val temporarySpeedOwners=mutableSetOf<Any>()
    private var speedBeforeHold: androidx.media3.common.PlaybackParameters?=null

    /** Overlapping fingers share one temporary rate; the final release restores
     * the original speed and pitch, even if the active deck changed meanwhile. */
    fun beginTemporaryDoubleSpeed(): () -> Unit {
        val controller=mediaController ?: return {}
        if(!controller.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH))return {}
        if(temporarySpeedOwners.isEmpty())speedBeforeHold=controller.playbackParameters
        val owner=Any()
        temporarySpeedOwners.add(owner)
        controller.playbackParameters=controller.playbackParameters.withSpeed(2f)
        return {
            if(temporarySpeedOwners.remove(owner) && temporarySpeedOwners.isEmpty()) {
                speedBeforeHold?.let {mediaController?.playbackParameters=it}
                speedBeforeHold=null
            }
        }
    }
    private var pendingCommand: (() -> Unit)? = null
    // Anti-rebote: un doble-tap rápido no debe ciclar el modo dos veces
    private var lastShuffleToggleMs = 0L
    private var lastRepeatToggleMs = 0L

    init {
        connect()
    }

    private fun connect() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, MusicLibraryService::class.java)
        )

        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                if (controllerFuture !== future) return@addListener
                try {
                    val controller = future.get()
                    if (controller != null) {
                        mediaController = controller
                        setupPlayerListener(controller)
                        _isConnected.value = true
                        syncState(controller)
                        pendingCommand?.invoke()
                        pendingCommand = null
                    }
                } catch (_: Exception) {
                    _isConnected.value = false
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private fun setupPlayerListener(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // If player is transitioning / buffering while playWhenReady is still true,
                // do not flicker the play/pause button to paused state in UI!
                if (!isPlaying && player.playWhenReady && player.playbackState != Player.STATE_ENDED) {
                    return
                }
                _isPlaying.value = isPlaying
                if (isPlaying) startProgressTracker() else stopProgressTracker()
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                syncQueue(player)
            }

            override fun onPlaylistMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                syncQueue(player)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _playbackError.value = false
                _currentSong.value = mediaItem?.toSong()
                _durationMs.value = player.duration.coerceAtLeast(0L)
                _currentPositionMs.value = player.currentPosition.coerceAtLeast(0L)
                // Timeline events own the queue snapshot. Skipping a song does
                // not require decoding thousands of metadata bundles again.
                _currentMediaItemIndex.value = queueIndexes.indexOf(player.currentMediaItemIndex).coerceAtLeast(0)
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                // Occurrences can share a mediaId. A seek still changes the
                // queue cursor even when the controller coalesces item events.
                _currentSong.value = player.currentMediaItem?.toSong()
                _currentMediaItemIndex.value = queueIndexes.indexOf(player.currentMediaItemIndex).coerceAtLeast(0)
                _currentPositionMs.value = player.currentPosition.coerceAtLeast(0L)
                _durationMs.value = player.duration.coerceAtLeast(0L)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _isLoading.value = false
                _isPlaying.value = false
                _playbackError.value = true
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isLoading.value = playbackState == Player.STATE_BUFFERING
                _durationMs.value = player.duration.coerceAtLeast(0L)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _shuffleMode.value = shuffleModeEnabled
                syncQueue(player)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = repeatMode
            }
        })
    }

    private fun syncState(player: Player) {
        _isLoading.value = player.playbackState == Player.STATE_BUFFERING
        _isPlaying.value = player.isPlaying
        _currentSong.value = player.currentMediaItem?.toSong()
        _durationMs.value = player.duration.coerceAtLeast(0L)
        _currentPositionMs.value = player.currentPosition.coerceAtLeast(0L)
        _shuffleMode.value = player.shuffleModeEnabled
        _repeatMode.value = player.repeatMode
        syncQueue(player)
        if (player.isPlaying) startProgressTracker()
    }

    private fun syncQueue(player: Player) {
        val count = player.mediaItemCount
        val items = ArrayList<Song>(count)
        val physical=(0 until count).map {player.getMediaItemAt(it)}
        val metadata=player.playlistMetadata.extras
        val serviceOrder=if(metadata?.containsKey(QueueEditCommand.SERVICE_SHUFFLE)==true &&
            metadata.getBoolean(QueueEditCommand.SERVICE_SHUFFLE)==player.shuffleModeEnabled)
            metadata.getStringArrayList(QueueEditCommand.SERVICE_ORDER) else null
        val indexes = queueIndexesFromServiceOrder(physical.map {it.queueIdentity()},serviceOrder) {playbackQueueIndexes(player)}
        queueIndexes = indexes
        for (i in indexes) items.add(physical[i].toSong())
        _currentQueue.value = items
        _currentMediaItemIndex.value = indexes.indexOf(player.currentMediaItemIndex).coerceAtLeast(0)
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                mediaController?.let { controller ->
                    _currentPositionMs.value = controller.currentPosition.coerceAtLeast(0L)
                    _durationMs.value = controller.duration.coerceAtLeast(0L)
                }
                delay(250)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun playSong(song: Song, queue: List<Song> = listOf(song)) {
        _playbackError.value = false
        val controller = mediaController
        if (controller == null) {
            pendingCommand = { playSong(song, queue) }
            return
        }
        val mediaItems = queue.map { it.toMediaItem() }
        val startIndex = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)

        controller.setMediaItems(mediaItems, startIndex, /* startPositionMs = */ 0L)
        controller.prepare()
        controller.play()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.playerError != null) { _playbackError.value = false; controller.prepare() }
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun pause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        controller.seekTo(positionMs)
        _currentPositionMs.value = positionMs
    }

    fun skipToNext() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
        }
    }

    fun skipToPrevious() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3000L) {
            controller.seekTo(0L)
        } else if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
        }
    }

    /** Restore the actual session, not just the cover shown by the activity.
     * Recheck after IO: a widget or a user selection may already have populated
     * the controller. Never pause or replace that newer session.
     */
    internal suspend fun restorePausedSession(fallbackQueue: List<Song> = emptyList(),
        fallbackSongId: String? = null, fallbackPositionMs: Long = 0L) {
        val controller = mediaController ?: return
        if (controller.mediaItemCount > 0) return
        val saved = kotlinx.coroutines.withContext(Dispatchers.IO) {
            val online = OnlineRepository.get(context).enabled.value
            WidgetPlaybackStore.read(context)?.takeIf { it.songs.all { song -> song.contentUri.startsWith("akira-youtube:") == online } }
        }
        if (mediaController !== controller || controller.mediaItemCount > 0) return
        val online = OnlineRepository.get(context).enabled.value
        if (saved != null && saved.songs.any { it.contentUri.startsWith("akira-youtube:") != online }) return
        val songs = saved?.songs ?: fallbackQueue.filter { it.contentUri.startsWith("akira-youtube:") == online }
        if (songs.isEmpty()) return
        val index = saved?.safeIndex ?: songs.indexOfFirst { it.id == fallbackSongId }.coerceAtLeast(0)
        controller.setMediaItems(songs.map { it.toMediaItem(it.queueEntryId) }, index,
            saved?.safePosition ?: fallbackPositionMs.coerceAtLeast(0L))
        if (saved != null) {
            controller.shuffleModeEnabled = saved.shuffle
            controller.repeatMode = saved.repeat
            restorePlaybackOrder(saved.order)
        }
        controller.prepare()
    }

    /** Switch queues paused, preserving each source without mixing MediaStore and YouTube. */
    internal suspend fun selectSourceMode(online: Boolean, restore: Boolean = true) {
        val controller = mediaController ?: return
        val existing = (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it).toSong() }
        val existingMode = existing.firstOrNull()?.contentUri?.startsWith("akira-youtube:")
        val changed = sourceMode != null && sourceMode != online
        sourceMode = online
        if (existingMode == online) return
        val snapshot = existing.takeIf { it.isNotEmpty() }?.let {
            WidgetQueueSnapshot(it, controller.currentMediaItemIndex, controller.currentPosition,
                controller.shuffleModeEnabled, controller.repeatMode,
                playbackQueueIndexes(controller).map { index -> it[index].queueEntryId })
        }
        // Clear immediately, before disk IO. Never resume the old source from the dock.
        controller.pause()
        controller.stop()
        controller.clearMediaItems()
        syncState(controller)
        _playbackError.value = false
        val saved = withContext(Dispatchers.IO) {
            snapshot?.let { SourceQueueStore.write(context, it) }
            if (!restore && !changed) null else {
                SourceQueueStore.read(context, online)
                    ?: WidgetPlaybackStore.read(context)?.takeIf { it.songs.all { song -> song.contentUri.startsWith("akira-youtube:") == online } }
            }
        }
        if (saved == null && (changed || snapshot != null) && controller.mediaItemCount == 0 && sourceMode == online) {
            com.akira.app.core.widget.AkiraWidgets.publish(context, null, false,
                com.akira.app.core.datastore.AppUiSnapshot.read(context) ?: com.akira.app.core.datastore.UserPreferences(), false)
        }
        if (saved == null || mediaController !== controller || controller.mediaItemCount > 0 ||
            sourceMode != online || OnlineRepository.get(context).enabled.value != online) return
        controller.setMediaItems(saved.songs.map { it.toMediaItem(it.queueEntryId) }, saved.safeIndex, saved.safePosition)
        controller.shuffleModeEnabled = saved.shuffle
        controller.repeatMode = saved.repeat
        restorePlaybackOrder(saved.order)
        controller.prepare()
    }

    fun toggleShuffle() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastShuffleToggleMs < 350L) return
        lastShuffleToggleMs = now
        val controller = mediaController ?: return
        // Sin escritura optimista: la única fuente de verdad es el callback
        // onShuffleModeEnabledChanged. El write local + callback async era una
        // fuente de parpadeo (true -> false -> true) si el player re-emitía.
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
    }

    // MusicLibraryService persists repeat changes received through the player callback.
    fun toggleRepeat() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastRepeatToggleMs < 350L) return
        lastRepeatToggleMs = now
        val controller = mediaController ?: return
        val nextMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        // Sin escritura optimista: manda el callback onRepeatModeChanged.
        controller.repeatMode = nextMode
    }

    private fun editQueue(args: android.os.Bundle) {
        val controller = mediaController ?: return
        val future = controller.sendCustomCommand(
            androidx.media3.session.SessionCommand(QueueEditCommand.ACTION, android.os.Bundle.EMPTY), args)
        future.addListener({
            runCatching { future.get() }.onSuccess { result ->
                if (result.resultCode == androidx.media3.session.SessionResult.RESULT_SUCCESS) syncQueue(controller)
                else com.akira.app.core.logging.AkiraLog.w("Queue", "Queue operation rejected: ${result.resultCode}")
            }.onFailure { com.akira.app.core.logging.AkiraLog.e("Queue", "Queue command failed", it) }
        }, ContextCompat.getMainExecutor(context))
    }

    fun restorePlaybackOrder(order: List<String>) {
        editQueue(android.os.Bundle().apply {
            putString(QueueEditCommand.OP, "ORDER"); putStringArrayList(QueueEditCommand.ORDER, ArrayList(order))
        })
    }

    private fun insert(items: List<MediaItem>, mode: String, target: String? = null) {
        editQueue(android.os.Bundle().apply {
            putString(QueueEditCommand.OP, "INSERT"); putString(QueueEditCommand.MODE, mode)
            putString(QueueEditCommand.TARGET, target)
            putParcelableArrayList(QueueEditCommand.ITEMS, ArrayList(items.map { it.toBundle() }))
        })
    }

    fun playNext(song: Song): String? {
        if (mediaController == null) return null
        val item = song.toMediaItem()
        insert(listOf(item), "NEXT")
        return item.toSong().queueEntryId
    }

    /** The Home reel starts its local result in the same queue transaction.
     * Waiting for the service avoids skipping before INSERT has actually run. */
    fun playHomeSelection(song: Song) {
        val item = song.toMediaItem()
        editQueue(android.os.Bundle().apply {
            putString(QueueEditCommand.OP, "INSERT")
            putString(QueueEditCommand.MODE, "NEXT")
            putBoolean("start_added_item", true)
            putParcelableArrayList(QueueEditCommand.ITEMS, arrayListOf(item.toBundle()))
        })
    }

    fun addToQueue(song: Song): String? {
        if (mediaController == null) return null
        val item = song.toMediaItem()
        insert(listOf(item), "END")
        return item.toSong().queueEntryId
    }

    fun undoQueueInsertion(entryId: String) {
        removeQueueEntry(entryId)
    }

    fun removeQueueEntry(entryId: String) {
        editQueue(android.os.Bundle().apply { putString(QueueEditCommand.OP,"REMOVE"); putString(QueueEditCommand.ENTRY,entryId) })
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val from = _currentQueue.value.getOrNull(fromIndex)?.queueEntryId ?: return
        val to = _currentQueue.value.getOrNull(toIndex)?.queueEntryId ?: return
        editQueue(android.os.Bundle().apply {
            putString(QueueEditCommand.OP,"MOVE"); putString(QueueEditCommand.ENTRY,from); putString(QueueEditCommand.TARGET,to)
        })
    }

    fun removeQueueItem(index: Int) {
        _currentQueue.value.getOrNull(index)?.queueEntryId?.let(::removeQueueEntry)
    }

    fun removeSongFromQueue(songId: String) {
        val index = _currentQueue.value.indexOfFirst { it.id == songId }
        if (index >= 0) removeQueueItem(index)
    }

    fun clearQueue() = editQueue(android.os.Bundle().apply { putString(QueueEditCommand.OP,"CLEAR") })

    fun restoreQueue(songs: List<Song>, startIndex: Int) {
        if (startIndex !in songs.indices) return
        // Append missing occurrences without replacing the playing deck. ORDER
        // then restores history as well as upcoming songs, also in shuffle mode.
        // The session processes these bounded commands in submission order.
        songs.chunked(40).forEach { chunk -> insert(chunk.map { it.toMediaItem(it.queueEntryId) },"END") }
        restorePlaybackOrder(songs.map { it.queueEntryId })
    }

    fun insertQueueItem(index: Int, song: Song) {
        val target = _currentQueue.value.getOrNull(index)?.queueEntryId
        insert(listOf(song.toMediaItem(song.queueEntryId)), if (target == null) "END" else "BEFORE", target)
    }

    fun skipToQueueIndex(index: Int) {
        val controller = mediaController ?: return
        val physical = queueIndexes.getOrNull(index) ?: return
        controller.seekToDefaultPosition(physical)
        controller.play()
    }

    fun startSleepTimer(durationMs: Long) {
        val args = android.os.Bundle().apply {
            putLong(SleepTimerBus.EXTRA_DURATION_MS, durationMs)
        }
        mediaController?.sendCustomCommand(
            androidx.media3.session.SessionCommand(SleepTimerBus.COMMAND_START_TIMER, android.os.Bundle.EMPTY),
            args
        )
        SleepTimerBus.start(durationMs)
    }

    fun cancelSleepTimer() {
        mediaController?.sendCustomCommand(
            androidx.media3.session.SessionCommand(SleepTimerBus.COMMAND_CANCEL_TIMER, android.os.Bundle.EMPTY),
            android.os.Bundle.EMPTY
        )
        SleepTimerBus.cancel()
    }

    fun release() {
        speedBeforeHold?.let {mediaController?.playbackParameters=it}
        temporarySpeedOwners.clear();speedBeforeHold=null
        _isLoading.value = false
        stopProgressTracker()
        pendingCommand = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
        _isConnected.value = false
    }
}
