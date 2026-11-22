package com.akira.app.core.online

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.app.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Public guest requests, inside the isolated debug package; never reads the production account. */
class OnlineGuestTimingTest {
    @Test fun guestFeedLoadsWhileOptionalMetadataWarmsInTheBackground() = runBlocking {
        check(BuildConfig.DEBUG && BuildConfig.APPLICATION_ID.endsWith(".debug"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = OnlineRepository.get(context)
        withTimeout(10_000) { repository.sessionReady.first { it } }
        check(!repository.account.value.connected) { "Guest timing requires an isolated guest debug session" }
        val wasEnabled = repository.enabled.value
        val cached = repository.cachedPage("home", repository.account.value) != null
        val started = SystemClock.elapsedRealtime()
        val report = JSONObject().put("cachedHomeAtStart", cached)
        try {
            val firstVisible = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(30_000) { repository.homePages.first { it?.second?.shelves?.any { shelf -> shelf.items.isNotEmpty() } == true } }
                SystemClock.elapsedRealtime() - started
            }
            repository.setEnabled(true)
            val page = withTimeout(30_000) { repository.home(force = true) }
            report.put("firstContentMs", firstVisible.await()).put("completeMs", SystemClock.elapsedRealtime() - started)
                .put("sections", page.shelves.size).put("items", page.shelves.sumOf { it.items.size }).put("success", true)
            assertTrue("The guest feed must retain visible content without blocking on visitor metadata", page.shelves.any { it.items.isNotEmpty() })
        } catch (error: Exception) {
            report.put("success", false).put("errorType", error.javaClass.simpleName)
            throw error
        } finally {
            repository.setEnabled(wasEnabled)
            File(context.getExternalFilesDir(null), "onboarding-audit").apply { mkdirs() }
                .resolve("online-guest-timing.json").writeText(report.toString(2))
        }
    }
}
