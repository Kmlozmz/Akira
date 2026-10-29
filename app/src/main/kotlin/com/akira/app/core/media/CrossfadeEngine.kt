package com.akira.app.core.media

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.akira.app.core.logging.AkiraLog
import kotlinx.coroutines.*

private const val TAG = "AkiraPlayback"

/** Mixes two decks, retaining the incoming AudioTrack when ownership changes.
 * Readiness and end guards prevent either deck from starting the incoming track twice. */
class CrossfadeEngine(
    var activePlayer: DeckPlayer,
    var inactivePlayer: DeckPlayer,
    private val scope: CoroutineScope,
    private val onSwapActivePlayer: (DeckPlayer) -> Unit,
    private val onIncomingTrackReady: (MediaItem) -> Unit = {},
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val onCrossfadeCancelled: () -> Unit = {}
) {
    var crossfadeDurationMs: Long = 0L // 0 = Disabled

    var outgoingCurveSpec: CurveSpec = CrossfadeCurve.DEFAULT_OUTGOING_SPEC
    var incomingCurveSpec: CurveSpec = CrossfadeCurve.DEFAULT_INCOMING_SPEC

    private var activeOutgoingSpec: CurveSpec = CrossfadeCurve.DEFAULT_OUTGOING_SPEC
    private var activeIncomingSpec: CurveSpec = CrossfadeCurve.DEFAULT_INCOMING_SPEC

    // External multiplier for normalization, sleep-timer fades and other gain composition.
    var externalVolumeMultiplier: Float = 1.0f
        set(value) {
            field = value.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
            applyDeckVolumes(lastAppliedFadeOut, lastAppliedFadeIn)
        }

    private var lastAppliedFadeOut: Float = 1.0f
    private var lastAppliedFadeIn: Float = 0.0f
    private var lastDebugLogTime: Long = 0L
    private val normalizationFactors = mutableMapOf<DeckPlayer, Float>()
    fun setNormalization(player: DeckPlayer, factor: Float) {
        normalizationFactors[player] = if (factor.isFinite()) factor.coerceIn(0f, 1f) else 1f
        applyDeckVolumes(lastAppliedFadeOut, lastAppliedFadeIn)
    }

    private fun applyDeckVolumes(fadeOutGain: Float, fadeInGain: Float) {
        lastAppliedFadeOut = fadeOutGain.coerceIn(0f, 1f)
        lastAppliedFadeIn = fadeInGain.coerceIn(0f, 1f)
        val finalOut = (lastAppliedFadeOut * externalVolumeMultiplier * (normalizationFactors[activePlayer] ?: 1f)).coerceIn(0f, 1f)
        val finalIn = (lastAppliedFadeIn * externalVolumeMultiplier * (normalizationFactors[inactivePlayer] ?: 1f)).coerceIn(0f, 1f)
        if(kotlin.math.abs(activePlayer.volume-finalOut)>.00001f)activePlayer.volume = finalOut
        if(kotlin.math.abs(inactivePlayer.volume-finalIn)>.00001f)inactivePlayer.volume = finalIn
    }

    private var tickerJob: Job? = null

    // Temporizador de apagado: suprime el crossfade y pausa al final de la pista/cola.
    var pauseAtEndOfItem: Boolean = false
    var pauseAtEndOfQueue: Boolean = false
    var onSleepTimerSongEnded: (() -> Unit)? = null
    var onSleepTimerQueueEnded: (() -> Unit)? = null

    private enum class State { IDLE, PREBUFFERED, CROSSFADING }
    private var state = State.IDLE
    private var crossfadeStartTime = 0L
    private var activeDurationMs = 0L
    private var crossfadePausedAt: Long? = null
    private var pausedCrossfadeDuration = 0L
    private var isHandingOver = false
    private var targetTrackIndex = C.INDEX_UNSET
    private var incomingStartPositionMs=0L
    private var failedIncomingId: String?=null
    private val endGuards=java.util.IdentityHashMap<DeckPlayer,Boolean>()
    val isMixing: Boolean get()=state==State.CROSSFADING

    private fun guardEnd(player: DeckPlayer) {
        if(!endGuards.containsKey(player))endGuards[player]=player.pauseAtEndOfMediaItems
        player.pauseAtEndOfMediaItems=true
    }
    private fun restoreEndGuards() {
        endGuards.forEach { (player,original) -> player.pauseAtEndOfMediaItems=original }
        endGuards.clear()
    }

    private val firstDeck = activePlayer
    private val secondDeck = inactivePlayer
    private var started = false
    private fun deckName(p: DeckPlayer) = if (p === firstDeck) "A" else "B"

    private val deckAListener = createDeckListener(activePlayer)
    private val deckBListener = createDeckListener(inactivePlayer)

    fun start() {
        if (started) return
        started = true
        firstDeck.addListener(deckAListener)
        secondDeck.addListener(deckBListener)

        tickerJob?.cancel()
        tickerJob = scope.launch(mainDispatcher) {
            while (isActive) {
                tick()
                delay(if(crossfadeDurationMs>0L)25L else 250L)
            }
        }
    }

    private fun createDeckListener(player: DeckPlayer) = object : Player.Listener {
        override fun onPlaybackParametersChanged(parameters: androidx.media3.common.PlaybackParameters) {
            if(!isHandingOver && player===activePlayer && state!=State.IDLE &&
                inactivePlayer.playbackParameters!=parameters) inactivePlayer.playbackParameters=parameters
        }
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (isHandingOver) return
            if (player == activePlayer && state != State.IDLE && reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                // The incoming deck holds a snapshot. Reusing it after a queue edit
                // would restore removed items or discard new ones at handover.
                Log.d(TAG, "Queue changed during crossfade; discard incoming snapshot")
                cancelCrossfade()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (isHandingOver) return
            if (player == activePlayer && reason == Player.DISCONTINUITY_REASON_SEEK) {
                cancelCrossfade()
            }
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            if (isHandingOver) return
            if (player == activePlayer) {
                failedIncomingId=null
                if (state == State.CROSSFADING && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    completeHandover()
                    return
                }
                // A late/not-ready prebuffer must never survive a natural
                // transition and later mix the wrong song into the new one.
                if (state != State.IDLE) {
                    cancelCrossfade()
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (isHandingOver) return
            if (player == activePlayer && state != State.CROSSFADING && playbackState == Player.STATE_ENDED) {
                if (pauseAtEndOfItem) {
                    onSleepTimerSongEnded?.invoke()
                } else if (pauseAtEndOfQueue) {
                    onSleepTimerQueueEnded?.invoke()
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            AkiraLog.e(TAG,"deck=${deckName(player)} state=$state decoder/source error=${error.errorCodeName}",error)
            if(player===inactivePlayer && state!=State.IDLE) {
                failedIncomingId=player.currentMediaItem?.mediaId
                cancelCrossfade()
            } else if(player===activePlayer && state!=State.IDLE)cancelCrossfade()
        }

        // Mirror playWhenReady so outgoing buffering does not pause the incoming deck.
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            Log.d(TAG, "deck=${deckName(player)} playWhenReady=$playWhenReady reason=$reason " +
                "active=${player === activePlayer} state=$state handingOver=$isHandingOver")
            if (isHandingOver) return
            if (player === activePlayer && state == State.CROSSFADING) {
                // Reaching the protected end is not a user pause. The incoming
                // track continues its rendered-position envelope to completion.
                if(!playWhenReady && reason==Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM)return
                if (!playWhenReady && crossfadePausedAt == null) {
                    crossfadePausedAt = clock()
                } else if (playWhenReady && crossfadePausedAt != null) {
                    pausedCrossfadeDuration += (clock() - crossfadePausedAt!!).coerceAtLeast(0L)
                    crossfadePausedAt = null
                }
                if (inactivePlayer.playWhenReady != playWhenReady) {
                    inactivePlayer.playWhenReady = playWhenReady
                }
            }
        }
    }

    fun cancelCrossfade() {
        AkiraLog.d(TAG, "Crossfade cancelled -> State.IDLE")
        state = State.IDLE
        restoreEndGuards()
        crossfadePausedAt = null
        pausedCrossfadeDuration = 0L
        isHandingOver = false
        targetTrackIndex = C.INDEX_UNSET
        applyDeckVolumes(1.0f, 0.0f)
        inactivePlayer.playWhenReady=false
        inactivePlayer.stop()
        inactivePlayer.clearMediaItems()
        onCrossfadeCancelled()
    }

    /** At the protected EOF the incoming deck already owns audible playback.
     * Commit it before taking the queue-edit snapshot instead of stopping it. */
    fun prepareQueueEdit() {
        val remaining=activePlayer.duration-activePlayer.currentPosition
        if(isMixing && !activePlayer.playWhenReady && inactivePlayer.playWhenReady && remaining<=80L)
            completeHandover()
        else cancelCrossfade()
    }

    private fun tick() {
        if (crossfadeDurationMs <= 0L) {
            if (state != State.IDLE) cancelCrossfade()
            return
        }

        if (!activePlayer.isPlaying && state != State.CROSSFADING) return

        val duration = activePlayer.duration
        val position = activePlayer.currentPosition
        if (duration == C.TIME_UNSET || duration <= 0L || position < 0L) return

        val remainingMs = duration - position

        when (state) {
            State.IDLE -> {
                // The timer needs the natural track ending without an early crossfade.
                if (pauseAtEndOfItem) return

                if (pauseAtEndOfQueue) {
                    val nextIndex = getNextItemIndex(activePlayer)
                    if (nextIndex == C.INDEX_UNSET || nextIndex >= activePlayer.mediaItemCount) {
                        return
                    }
                }

                val prebufferWindow = crossfadeDurationMs + 3000L
                if (remainingMs in 1..prebufferWindow) {
                    val nextIndex = getNextItemIndex(activePlayer)
                    if (nextIndex != C.INDEX_UNSET && nextIndex < activePlayer.mediaItemCount) {
                        if(activePlayer.getMediaItemAt(nextIndex).mediaId==failedIncomingId)return
                        Log.d(TAG, "Pre-buffering next track on inactive deck (remaining=$remainingMs ms)")
                        targetTrackIndex = nextIndex
                        val items = (0 until activePlayer.mediaItemCount).map { activePlayer.getMediaItemAt(it) }
                        inactivePlayer.playWhenReady=false
                        applyDeckVolumes(1f, 0f)
                        inactivePlayer.setMediaItems(items, nextIndex, 0L)
                        inactivePlayer.copyPlaybackOrderFrom(activePlayer)
                        state = State.PREBUFFERED
                        inactivePlayer.prepare()
                    }
                }
            }

            State.PREBUFFERED -> {
                if (remainingMs > crossfadeDurationMs + 3500L) {
                    cancelCrossfade()
                    return
                }

                if (remainingMs <= crossfadeDurationMs) {
                    if(remainingMs<=80L || inactivePlayer.playbackState!=Player.STATE_READY)return
                    Log.d(TAG, "STARTING DUAL-DECK PING-PONG CROSSFADE! duration=$crossfadeDurationMs ms")
                    crossfadeStartTime = clock()
                    val incomingLimit=inactivePlayer.duration.takeIf { it!=C.TIME_UNSET && it>0L }?.div(2) ?: Long.MAX_VALUE
                    activeDurationMs = minOf(crossfadeDurationMs,remainingMs,incomingLimit).coerceAtLeast(1L)
                    incomingStartPositionMs=inactivePlayer.currentPosition.coerceAtLeast(0L)
                    crossfadePausedAt = null
                    pausedCrossfadeDuration = 0L
                    // Preference changes must not alter a mix already in progress.
                    activeOutgoingSpec = outgoingCurveSpec
                    activeIncomingSpec = incomingCurveSpec
                    if (targetTrackIndex == C.INDEX_UNSET) {
                        targetTrackIndex = getNextItemIndex(activePlayer)
                    }
                    applyDeckVolumes(1.0f, 0.0f)
                    guardEnd(activePlayer)
                    guardEnd(inactivePlayer)
                    state = State.CROSSFADING
                    inactivePlayer.play()
                    AkiraLog.i(TAG, "Crossfade active phase started: duration=${activeDurationMs}ms, outShape=${activeOutgoingSpec.shape.name}, inShape=${activeIncomingSpec.shape.name}")
                    // The UI may show the incoming track before deck handover.
                    inactivePlayer.currentMediaItem?.let { onIncomingTrackReady(it) }
                }
            }

            State.CROSSFADING -> {
                val now = clock()
                if (!activePlayer.isPlaying && !inactivePlayer.isPlaying && crossfadePausedAt == null) {
                    crossfadePausedAt = now
                } else if ((activePlayer.isPlaying || inactivePlayer.isPlaying) &&
                    activePlayer.playWhenReady && inactivePlayer.playWhenReady && crossfadePausedAt != null) {
                    pausedCrossfadeDuration += (now - crossfadePausedAt!!).coerceAtLeast(0L)
                    crossfadePausedAt = null
                }
                val pausedNow = crossfadePausedAt?.let { (now - it).coerceAtLeast(0L) } ?: 0L
                val elapsed = if(inactivePlayer.usesPositionClock)
                    (inactivePlayer.currentPosition-incomingStartPositionMs).coerceAtLeast(0L)
                    else clock() - crossfadeStartTime - pausedCrossfadeDuration - pausedNow
                if (elapsed >= activeDurationMs) {
                    Log.d(TAG, "Crossfade mix complete. Executing Ping-Pong deck swap!")
                    completeHandover()
                } else {
                    val progress = (elapsed.toFloat() / activeDurationMs.toFloat()).coerceIn(0f, 1f)
                    val outGain = CrossfadeCurve.gain(CrossfadeDirection.OUTGOING, activeOutgoingSpec, progress)
                    val inGain = CrossfadeCurve.gain(CrossfadeDirection.INCOMING, activeIncomingSpec, progress)

                    applyDeckVolumes(outGain, inGain)

                    val now = clock()
                    if (now - lastDebugLogTime >= 500L) {
                        lastDebugLogTime = now
                        val expOut = CrossfadeCurve.gain(CrossfadeDirection.OUTGOING, activeOutgoingSpec, progress)
                        val expIn = CrossfadeCurve.gain(CrossfadeDirection.INCOMING, activeIncomingSpec, progress)
                        Log.d(TAG, "CROSSFADE_DEBUG: t=${String.format(java.util.Locale.US, "%.3f", progress)} outReal=${String.format(java.util.Locale.US, "%.3f", activePlayer.volume)} outExp=${String.format(java.util.Locale.US, "%.3f", expOut)} inReal=${String.format(java.util.Locale.US, "%.3f", inactivePlayer.volume)} inExp=${String.format(java.util.Locale.US, "%.3f", expIn)}")
                    }
                }
            }
        }
    }

    private fun completeHandover() {
        if (isHandingOver) return
        isHandingOver = true
        state = State.IDLE

        // Player callbacks and the ticker share the main looper. Complete the
        // swap synchronously so a queued seek/cancel cannot resurrect a stale deck.
        try {
            val outgoing = activePlayer
            val incoming = inactivePlayer

            applyDeckVolumes(0f, 1f)

            // Expose the incoming deck before clearing the outgoing deck to avoid publishing IDLE.
            activePlayer = incoming
            inactivePlayer = outgoing
            restoreEndGuards()
            applyDeckVolumes(1f, 0f)
            onSwapActivePlayer(incoming)

            outgoing.stop()
            outgoing.clearMediaItems()

            Log.d(TAG, "handover done: active=${deckName(incoming)} playWhenReady=${incoming.playWhenReady} " +
                "isPlaying=${incoming.isPlaying}")
            AkiraLog.i(TAG, "Handover completed: new active deck=${deckName(incoming)}")
        } catch (e: Exception) {
            Log.e(TAG, "Handover error: ${e.message}")
            AkiraLog.e(TAG, "Handover error: ${e.message}", e)
            cancelCrossfade()
        } finally {
            isHandingOver = false
            targetTrackIndex = C.INDEX_UNSET
        }
    }

    private fun getNextItemIndex(player: DeckPlayer): Int {
        val currentIdx = player.currentMediaItemIndex
        if (currentIdx == C.INDEX_UNSET) return C.INDEX_UNSET

        // The adapter queries Timeline to preserve shuffle order.
        val nextWindow = player.nextWindowIndex(currentIdx)
        if (nextWindow != C.INDEX_UNSET) return nextWindow

        return when {
            player.hasNextMediaItem() -> currentIdx + 1
            player.repeatMode == Player.REPEAT_MODE_ALL -> 0
            player.repeatMode == Player.REPEAT_MODE_ONE -> currentIdx
            else -> C.INDEX_UNSET
        }
    }

    fun release() {
        restoreEndGuards()
        tickerJob?.cancel()
        tickerJob = null
        firstDeck.removeListener(deckAListener)
        secondDeck.removeListener(deckBListener)
        started = false
        onCrossfadeCancelled()
    }
}
