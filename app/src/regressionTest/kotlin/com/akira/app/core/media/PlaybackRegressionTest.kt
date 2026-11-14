package com.akira.app.core.media

import androidx.media3.common.Player
import com.akira.app.core.model.Song
import com.akira.app.core.model.SongKey
import com.akira.app.core.online.sessionOperationIsCurrent
import com.akira.app.core.online.OnlineSessionEpoch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.lang.reflect.Proxy
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class PlaybackRegressionTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun sessionInvalidationRejectsOldResultsAndCommitNotifiesEveryReconnect() = runTest {
        val epoch = OnlineSessionEpoch()
        val observed = mutableListOf<Long>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { epoch.changes.toList(observed) }
        val previous = epoch.get()
        epoch.incrementAndGet()
        assertFalse(sessionOperationIsCurrent(previous, epoch.get(), true))
        assertEquals("Do not start refresh while credentials are changing", listOf(0L), observed)
        epoch.commit()
        epoch.incrementAndGet()
        epoch.commit()
        assertEquals("A same-account reconnect must still notify the collector", listOf(0L, 1L, 2L), observed)
    }

    @Test fun explicitPauseAndStopRevokeResumeEvenWithoutPlayerEvents() {
        val state = TransientFocusResume()
        val underlying = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, _, _ -> null } as Player
        val player = QueueTransactionPlayer(underlying, state::cancel)
        for (action in listOf<() -> Unit>({ player.pause() }, { player.stop() }, { player.clearMediaItems() },
                { player.playWhenReady = false }, { player.setMediaItems(emptyList()) })) {
            state.lost(willResume = true, wasPlaying = true)
            action()
            assertFalse("A later focus gain must not restart playback", state.consumeGain())
        }
    }

    @Test fun transientFocusStillResumesOnceAndPermanentLossRevokesIt() {
        val state = TransientFocusResume()
        state.lost(true, true)
        state.lost(true, false)
        assertTrue(state.consumeGain())
        assertFalse(state.consumeGain())
        state.lost(true, true)
        state.lost(false, false)
        assertFalse(state.consumeGain())
    }

    @Test fun queueEditsDoNotRevokeResumeForTheSameTrack() {
        val state = TransientFocusResume()
        val underlying = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, _, _ -> null } as Player
        val player = QueueTransactionPlayer(underlying, state::cancel)
        state.lost(true, true)
        player.addMediaItems(emptyList())
        assertTrue(state.consumeGain())
    }

    @Test fun physicalDeletionRemovesCurrentAndDuplicateOccurrencesInTraversalOrder() {
        val order = listOf("second", "other", "current", "third")
        val occurrences = mapOf("current" to "deleted", "second" to "deleted", "third" to "deleted", "other" to "kept")
        assertEquals(listOf("other"), QueueOrder.removeSong(order, occurrences, "deleted"))
        assertEquals(emptyList<String>(), QueueOrder.removeSong(listOf("current"), occurrences, "deleted"))
        assertEquals(order, QueueOrder.removeSong(order, occurrences, "absent"))
    }

    @Test fun queueOptimizationPreservesPreviousInsertionAndRestorationSemantics() {
        val random = Random(731)
        repeat(1000) {
            val order = List(random.nextInt(30)) { random.nextInt(20).toString() }.distinct()
            val saved = List(random.nextInt(30)) { random.nextInt(30).toString() }
            assertEquals(saved.distinct().filter(order::contains) + order.filterNot(saved::contains), QueueOrder.restore(order, saved))
            val fresh = saved.distinct().filterNot(order::contains)
            val current = order.firstOrNull()
            val expected = order.toMutableList().apply { addAll((order.indexOf(current) + 1).coerceIn(0, order.size), fresh) }
            assertEquals(expected, QueueOrder.insert(order, current, saved, "NEXT"))
        }
    }

    @Test fun onlineLyricsIdentityDiffersEvenWithEqualOrUnknownDurations() {
        for (duration in listOf(0L, 180_000L)) {
            val first = Song("youtube:abcdefghijk", "A", "Artist", "Album", duration, "akira-youtube://abcdefghijk")
            val second = first.copy(id = "youtube:lmnopqrstuv", contentUri = "akira-youtube://lmnopqrstuv")
            assertNotEquals(SongKey.of(first), SongKey.of(second))
        }
    }

    @Test fun lateAccountResultsAreRejectedAfterSessionChangeOrDisable() {
        assertTrue(sessionOperationIsCurrent(7, 7, true))
        assertFalse(sessionOperationIsCurrent(7, 8, true))
        assertFalse(sessionOperationIsCurrent(7, 7, false))
        assertFalse(sessionOperationIsCurrent(7, 9, true))
    }
}
