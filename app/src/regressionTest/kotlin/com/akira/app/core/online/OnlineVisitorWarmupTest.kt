package com.akira.app.core.online

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnlineVisitorWarmupTest {
    @Test fun firstRequestReturnsImmediatelyAndConcurrentRequestsShareOneWarmup() = runTest {
        val gate = CompletableDeferred<String>()
        var calls = 0
        var value: String? = null
        val warmup = OnlineVisitorWarmup(backgroundScope, { testScheduler.currentTime }, { 0L }, { value == null },
            { calls++; gate.await() }, { loaded, _ -> value = loaded })
        repeat(20) { warmup.request() }
        runCurrent()
        assertEquals(1, calls)
        assertNull(value)
        gate.complete("visitor"); runCurrent()
        assertEquals("visitor", value)
        warmup.request(); runCurrent()
        assertEquals(1, calls)
    }

    @Test fun failedWarmupHasACooldownAndCanRetryWithoutBlockingBrowsing() = runTest {
        var calls = 0
        val warmup = OnlineVisitorWarmup(backgroundScope, { testScheduler.currentTime }, { 0L }, { true },
            { calls++; error("offline") }, { _, _ -> error("Failed metadata published") })
        warmup.request(); runCurrent()
        advanceTimeBy(59_999); warmup.request(); runCurrent()
        assertEquals(1, calls)
        advanceTimeBy(1); warmup.request(); runCurrent()
        assertEquals(2, calls)
    }

    @Test fun timeoutCancelsTheOptionalRequestAndPublishesNothing() = runTest {
        var cancelled = false
        val warmup = OnlineVisitorWarmup(backgroundScope, { testScheduler.currentTime }, { 0L }, { true },
            { try { awaitCancellation() } finally { cancelled = true } }, { _, _ -> error("Timed out data published") })
        warmup.request(); runCurrent(); advanceTimeBy(3_000); runCurrent()
        assertTrue(cancelled)
    }

    @Test fun changingAccountsDiscardsAnOldResultAndStartsANewWarmup() = runTest {
        var epoch = 0L
        var calls = 0
        val gate = CompletableDeferred<String>()
        val published = mutableListOf<Pair<String, Long>>()
        val warmup = OnlineVisitorWarmup(backgroundScope, { testScheduler.currentTime }, { epoch }, { true },
            { calls++; if (calls == 1) gate.await() else "new" }, { value, generation -> published += value to generation })
        warmup.request(); runCurrent()
        epoch = 1; gate.complete("old"); runCurrent()
        assertTrue(published.isEmpty())
        warmup.request(); runCurrent()
        assertEquals(listOf("new" to 1L), published)
    }

    @Test fun disablingCancelsPendingWorkAndReenablingCanRetryImmediately() = runTest {
        var enabled = true
        var calls = 0
        var published: String? = null
        val gate = CompletableDeferred<String>()
        val warmup = OnlineVisitorWarmup(backgroundScope, { testScheduler.currentTime }, { 0L }, { enabled },
            { calls++; if (calls == 1) gate.await() else "new" }, { value, _ -> published = value })
        warmup.request(); runCurrent(); enabled = false; warmup.cancel(); gate.complete("old"); runCurrent()
        assertNull(published)
        enabled = true; warmup.request(); runCurrent()
        assertEquals("new", published)
    }
}
