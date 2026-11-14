package com.akira.app.core.media

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepCrossfadeRegressionTest {
    private class Deck : DeckPlayer {
        var items = mutableListOf<MediaItem>()
        var position = 0L
        val listeners = mutableListOf<Player.Listener>()
        override var volume = 1f
        override val mediaItemCount get() = items.size
        override val currentMediaItemIndex get() = 0
        override val currentMediaItem get() = items.firstOrNull()
        override fun hasNextMediaItem() = items.size > 1
        override var shuffleModeEnabled = false
        override var repeatMode = Player.REPEAT_MODE_OFF
        override val duration = 200_000L
        override val currentPosition get() = position
        override val isPlaying get() = playWhenReady
        override var playWhenReady = false
        override var pauseAtEndOfMediaItems = false
        override fun nextWindowIndex(currentIdx: Int) = if (currentIdx + 1 < items.size) currentIdx + 1 else C.INDEX_UNSET
        override fun getMediaItemAt(index: Int) = items[index]
        override fun setMediaItems(items: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
            this.items = items.drop(startIndex).toMutableList()
            position = startPositionMs
        }
        override fun prepare() {}
        override fun play() { playWhenReady = true }
        override fun pause() { playWhenReady = false }
        override fun stop() { playWhenReady = false }
        override fun clearMediaItems() { items.clear() }
        override fun addListener(listener: Player.Listener) { listeners.add(listener) }
        override fun removeListener(listener: Player.Listener) { listeners.remove(listener) }
    }

    @Test fun endOfSongDuringMixRetainsItsGuardsAndCancellingRestoresNormalPlayback() = runTest {
        val outgoing = Deck().apply {
            items = mutableListOf("one", "two", "three").map { MediaItem.Builder().setMediaId(it).build() }.toMutableList()
            position = 188_000L
            play()
        }
        val incoming = Deck()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = CrossfadeEngine(outgoing, incoming, backgroundScope, {},
            clock = { testScheduler.currentTime }, mainDispatcher = dispatcher).apply {
            crossfadeDurationMs = 10_000L
            start()
        }
        advanceTimeBy(150)
        outgoing.position = 190_000L
        advanceTimeBy(150)
        assertTrue("Exercise the cancellation of installed crossfade guards", engine.isMixing)
        val timer = SleepTimerManager(backgroundScope, engine, { engine.activePlayer },
            onEndItemModeChanged = { enabled ->
                outgoing.pauseAtEndOfMediaItems = enabled
                incoming.pauseAtEndOfMediaItems = enabled
            }, clock = { testScheduler.currentTime }, dispatcher = dispatcher)
        timer.start(SleepTimerManager.END_OF_SONG)
        assertFalse(engine.isMixing)
        assertTrue(outgoing.pauseAtEndOfMediaItems)
        assertTrue(incoming.pauseAtEndOfMediaItems)
        assertTrue(engine.pauseAtEndOfItem)
        assertFalse(incoming.playWhenReady)
        timer.onSongEndedNaturally()
        assertFalse(outgoing.playWhenReady)
        assertFalse(outgoing.pauseAtEndOfMediaItems)
        assertFalse(incoming.pauseAtEndOfMediaItems)
        assertFalse(engine.pauseAtEndOfItem)
        assertEquals(0L, timer.activeMode.value)
        engine.release()
    }

    @Test fun timerExpiryWhileFocusPausedRevokesPendingResume() = runTest {
        val deck = Deck()
        val other = Deck()
        val state = TransientFocusResume().apply { lost(true, true) }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = CrossfadeEngine(deck, other, backgroundScope, {}, mainDispatcher = dispatcher)
        val timer = SleepTimerManager(backgroundScope, engine, { deck },
            clock = { testScheduler.currentTime }, dispatcher = dispatcher, onPlaybackStopped = state::cancel)
        timer.start(1_000)
        advanceTimeBy(1_100)
        assertFalse(state.consumeGain())
        assertEquals(0L, timer.activeMode.value)
        engine.release()
    }
}
