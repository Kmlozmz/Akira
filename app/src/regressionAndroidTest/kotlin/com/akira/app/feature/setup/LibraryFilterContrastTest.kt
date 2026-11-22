package com.akira.app.feature.setup

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.akira.app.BuildConfig
import com.akira.app.MainActivity
import com.akira.app.core.datastore.*
import com.akira.app.core.design.components.*
import com.akira.app.core.design.glass.*
import com.akira.app.core.design.theme.AkiraTheme
import com.akira.app.core.i18n.*
import com.akira.app.core.model.Song
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Real popup pixels and selection behaviour, populated with synthetic audio metadata only. */
class LibraryFilterContrastTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation

    @Test fun rowsStayDistinctAndExceptionsWorkInBothThemesAndStyles() {
        check(BuildConfig.DEBUG)
        var host: MainActivity? = null
        fun findHost() = instrumentation.runOnMainSync {
            host = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
        }
        findHost()
        if (host == null) instrumentation.targetContext.startActivity(Intent(instrumentation.targetContext,
            MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (host == null && SystemClock.elapsedRealtime() < deadline) { Thread.sleep(60); findHost() }
        val activity = checkNotNull(host)
        val repo = UserPreferencesRepository(activity)
        val songs = (1..4).map { i -> Song("contrast-$i", "Voice note $i", "Test artist", "Test album", i * 5_000L,
            "content://akira-test/$i", path = "/Music/WhatsApp/test-$i.wav") }
        try {
            for ((mode, glass) in listOf("LIGHT" to false, "DARK" to false, "LIGHT" to true, "DARK" to true)) {
                var prefs by mutableStateOf(UserPreferences(minDurationSeconds = 60, excludedFolderPaths = setOf("WhatsApp"), liquidGlassEnabled = glass))
                var shown by mutableStateOf(true)
                instrumentation.runOnMainSync {
                    activity.setContent {
                        AkiraTheme(themeMode = mode) {
                            val material = rememberSharedGlassMaterial()
                            CompositionLocalProvider(LocalStrings provides StringsEnglish, LocalSharedGlassMaterial provides material) {
                                ProvideGlassPreferences(prefs) {
                                    AkiraPresentationHost {
                                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                                            Text("Library behind popup")
                                            if (shown) OnboardingExcludedFiles(songs, prefs, repo, persist = { ids, keep ->
                                                prefs = prefs.copy(whitelistedSongIds = if (keep) prefs.whitelistedSongIds + ids else prefs.whitelistedSongIds - ids)
                                            }, onClose = { shown = false })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                await("Voice note 1"); await("Voice note 2")
                Thread.sleep(450)
                fun row(text: String): Rect {
                    var node: AccessibilityNodeInfo? = await(text)
                    while (node != null && !node.isCheckable) node = node.parent
                    return Rect().also { checkNotNull(node).getBoundsInScreen(it) }
                }
                val first = row("Voice note 1"); val second = row("Voice note 2")
                val bitmap = checkNotNull(automation.takeScreenshot())
                val x = first.left + (4 * activity.resources.displayMetrics.density).toInt()
                val fill = bitmap.getPixel(x, first.centerY())
                val gap = bitmap.getPixel(x, (first.bottom + second.top) / 2)
                val delta = abs(android.graphics.Color.red(fill) - android.graphics.Color.red(gap)) +
                    abs(android.graphics.Color.green(fill) - android.graphics.Color.green(gap)) + abs(android.graphics.Color.blue(fill) - android.graphics.Color.blue(gap))
                assertTrue("Track surfaces must be distinct from the popup in $mode glass=$glass", delta >= 24)
                val folder = File(activity.getExternalFilesDir(null), "onboarding-audit").apply { mkdirs() }
                File(folder, "contrast-$mode-${if (glass) "Glass" else "Classic"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                tap("Voice note 1"); tap("Include (1)"); await("Library updated.")
                tap("Restored"); await("Voice note 1")
                tap("Close"); await("Library behind popup")
            }
        } finally { instrumentation.runOnMainSync { activity.setContent {} } }
    }

    private fun await(text: String): AccessibilityNodeInfo {
        fun visit(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isVisibleToUser && node.text?.toString() == text) return node
            repeat(node.childCount) { visit(node.getChild(it))?.let { result -> return result } }
            return null
        }
        val deadline = SystemClock.elapsedRealtime() + 8_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (Build.VERSION.SDK_INT >= 33) automation.clearCache()
            visit(automation.rootInActiveWindow)?.let { return it }
            Thread.sleep(60)
        }
        automation.takeScreenshot()?.let { bitmap ->
            val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "onboarding-audit").apply { mkdirs() }
            File(folder, "failure-contrast.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        error("Missing contrast fixture control: $text")
    }
    private fun tap(text: String) {
        var node: AccessibilityNodeInfo? = await(text)
        while (node != null && !node.isClickable) node = node.parent
        assertTrue(checkNotNull(node).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        Thread.sleep(400)
    }
}
