package com.akira.app.feature.setup

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class OnboardingCompletionTest {
    @Test fun `explicit skip completes without starting scan and is recorded separately`() = runBlocking {
        var scans = 0
        var result: SetupState? = null
        val controller = SetupController(this, { scans++; error("Permission not granted") }, { result = it })
        controller.skipLibrary()
        controller.finish(); yield()
        assertEquals(0, scans)
        assertTrue(controller.state.value.completed)
        assertEquals(true, result?.librarySkipped)
        assertTrue(result!!.songs.isEmpty())
    }

    @Test fun `scan failure never silently becomes a library skip`() = runBlocking {
        var writes = 0
        val controller = SetupController(this, { throw java.io.IOException() }, { writes++ })
        controller.start(); yield(); controller.finish(); yield()
        assertFalse(controller.state.value.librarySkipped)
        assertFalse(controller.state.value.completed)
        assertEquals(SetupFailure.SCAN, controller.state.value.failure)
        assertEquals(0, writes)
        controller.skipLibrary(); controller.finish(); yield()
        assertEquals(1, writes)
        assertTrue(controller.state.value.completed)
    }

    @Test fun `skipping active scan rejects late progress and errors from cancelled work`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val controller = SetupController(this, { progress ->
            started.complete(Unit)
            try { awaitCancellation() }
            finally {
                withContext(NonCancellable) { progress(99, "Old result") }
                throw java.io.IOException("Old scanner stopped")
            }
        }, {})
        controller.start(); started.await()
        controller.skipLibrary(); yield()
        assertTrue(controller.state.value.librarySkipped)
        assertEquals(SetupStatus.READY, controller.state.value.status)
        assertEquals(0, controller.state.value.count)
        assertNull(controller.state.value.failure)
    }

    @Test fun `rescanning after skip restores the actual scan requirement`() = runBlocking {
        val controller = SetupController(this, { throw SecurityException() }, { fail() })
        controller.skipLibrary()
        controller.start(); yield()
        assertFalse(controller.state.value.librarySkipped)
        assertEquals(SetupFailure.PERMISSION, controller.state.value.failure)
        controller.finish(); yield()
        assertFalse(controller.state.value.completed)
    }

    @Test fun `pending preferences prevent completion and retry keeps the library result`() = runBlocking {
        val writes = SetupPreferenceWrites(this)
        val release = CompletableDeferred<Unit>()
        writes.submit("sound") { release.await() }
        val controller = SetupController(this, { emptyList() }, {
            check(writes.pending.value == 0 && writes.failures.value.isEmpty())
        })
        controller.skipLibrary(); controller.finish(); yield()
        assertEquals(SetupFailure.SAVE, controller.state.value.failure)
        assertFalse(controller.state.value.completed)
        release.complete(Unit); yield()
        controller.finish(); yield()
        assertTrue(controller.state.value.completed)
        assertTrue(controller.state.value.librarySkipped)
    }
}
