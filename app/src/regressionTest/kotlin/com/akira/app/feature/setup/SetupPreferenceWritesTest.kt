package com.akira.app.feature.setup

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SetupPreferenceWritesTest {
    @Test fun `closing an awaiting sheet does not cancel the persistent write`() = runBlocking {
        val writes = SetupPreferenceWrites(this)
        val release = CompletableDeferred<Unit>()
        var saved = false
        val waitingSheet = launch {
            writes.submit("exceptions") { release.await(); saved = true }.join()
        }
        yield()
        waitingSheet.cancelAndJoin()
        assertEquals(1, writes.pending.value)
        release.complete(Unit); yield()
        assertTrue(saved)
        assertEquals(0, writes.pending.value)
    }

    @Test fun `rapid choices are serialized and all pending work is tracked`() = runBlocking {
        val writes = SetupPreferenceWrites(this)
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        writes.submit("cover") { events += "first-start"; release.await(); events += "first-end" }
        writes.submit("cover") { events += "second" }
        yield()
        assertEquals(2, writes.pending.value)
        assertEquals(listOf("first-start"), events)
        release.complete(Unit); yield(); yield()
        assertEquals(listOf("first-start", "first-end", "second"), events)
        assertEquals(0, writes.pending.value)
        assertTrue(writes.failures.value.isEmpty())
    }

    @Test fun `failed choices block completion until a successful explicit retry`() = runBlocking {
        val writes = SetupPreferenceWrites(this)
        var attempts = 0
        writes.submit("crossfade") { if (attempts++ == 0) throw java.io.IOException() }
        yield()
        assertEquals(setOf("crossfade"), writes.failures.value)
        assertEquals(0, writes.pending.value)
        writes.retry(); yield()
        assertEquals(2, attempts)
        assertTrue(writes.failures.value.isEmpty())
    }

    @Test fun `a later successful choice supersedes an old failure`() = runBlocking {
        val writes = SetupPreferenceWrites(this)
        var oldAttempts = 0
        writes.submit("normalization") { oldAttempts++; throw java.io.IOException() }
        yield()
        writes.submit("normalization") {}
        yield(); writes.retry(); yield()
        assertEquals(1, oldAttempts)
        assertTrue(writes.failures.value.isEmpty())
    }

    @Test fun `older failed write does not replace the retry for the latest choice`() = runBlocking {
        val writes = SetupPreferenceWrites(this)
        val release = CompletableDeferred<Unit>()
        var firstAttempts = 0
        var secondAttempts = 0
        writes.submit("artwork") { firstAttempts++; release.await(); throw java.io.IOException() }
        writes.submit("artwork") { if (secondAttempts++ == 0) throw java.io.IOException() }
        release.complete(Unit); yield(); yield()
        writes.retry(); yield()
        assertEquals(1, firstAttempts)
        assertEquals(2, secondAttempts)
        assertTrue(writes.failures.value.isEmpty())
    }
}
