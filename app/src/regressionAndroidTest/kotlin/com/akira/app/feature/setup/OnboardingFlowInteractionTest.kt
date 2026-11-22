package com.akira.app.feature.setup

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.akira.app.BuildConfig
import com.akira.app.MainActivity
import com.akira.app.core.datastore.UserPreferencesRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Exercises the real startup host, scanner, floating editor and completion handoff on an isolated emulator. */
class OnboardingFlowInteractionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation

    @Test fun permissionsScanPopupAndAnimatedEntry() {
        check(BuildConfig.DEBUG)
        check(BuildConfig.APPLICATION_ID.endsWith(".debug"))
        val physicalDebugAllowed = InstrumentationRegistry.getArguments().getString("allowPhysicalDebug") == "true"
        assumeTrue("Physical debug testing must be explicitly enabled", physicalDebugAllowed || Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish"))
        val context = instrumentation.targetContext
        val repo = UserPreferencesRepository(context)
        runBlocking { repo.setLanguage("en"); repo.setThemeMode("LIGHT"); repo.setLiquidGlassEnabled(false); repo.setOnboardingCompleted(false) }
        automation.grantRuntimePermission(BuildConfig.APPLICATION_ID,
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT >= 33) automation.grantRuntimePermission(BuildConfig.APPLICATION_ID, Manifest.permission.POST_NOTIFICATIONS)
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))

        awaitText("Get started", 25_000)
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>()
                .forEach { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
        Thread.sleep(700)
        screenshot("01-welcome")
        tap("Get started")
        awaitText("Glass or Classic.")
        tap("Glass")
        runBlocking { withTimeout(30_000) { repo.userPreferences.first { it.liquidGlassEnabled } } }
        awaitText("Classic", 30_000)
        tap("Classic")
        runBlocking { withTimeout(30_000) { repo.userPreferences.first { !it.liquidGlassEnabled } } }
        screenshot("01b-style")
        tap("Continue")
        tap("Continue")
        awaitText("Music and audio")
        awaitText("Notifications")
        screenshot("02-permissions")
        tap("Continue")
        awaitText("Scan")
        assertNull(findText("Continue"))
        screenshot("03-before-scan")
        tap("Scan")
        awaitText("Continue", 30_000)
        screenshot("04-library")
        tap("Review excluded audio", scroll = true)
        awaitText("Excluded")
        awaitText("Restored")
        screenshot("05-floating-editor")
        tap("Close")
        tap("Continue")
        tap("Continue")
        tap("Continue")
        awaitText("Enter Akira")
        screenshot("06-welcome-final")
        Thread.sleep(450)
        screenshot("07-welcome-final-motion")
        val started = SystemClock.elapsedRealtime()
        tap("Enter Akira", settle = 0)
        Thread.sleep(400)
        assertNotNull("Completion must not skip the visible departure", findText("Enter Akira"))
        screenshot("08-entry-wipe")
        val deadline = started + 20_000
        while (findText("Enter Akira") != null && SystemClock.elapsedRealtime() < deadline) Thread.sleep(60)
        assertNull("The actual app must replace onboarding", findText("Enter Akira"))
        assertTrue("The save must not cut off the departure", SystemClock.elapsedRealtime() - started >= 850)
        Thread.sleep(850)
        screenshot("09-in-app")
    }

    private fun screenshot(name: String) {
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "onboarding-audit").apply { mkdirs() }
        automation.takeScreenshot()?.let { bitmap ->
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun findText(text: String): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT >= 33) automation.clearCache()
        fun visit(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isVisibleToUser && node.text?.toString() == text) return node
            repeat(node.childCount) { visit(node.getChild(it))?.let { result -> return result } }
            return null
        }
        return visit(automation.rootInActiveWindow)
    }

    private fun awaitText(text: String, timeout: Long = 10_000): AccessibilityNodeInfo {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < deadline) {
            findText(text)?.let { return it }
            Thread.sleep(60)
        }
        screenshot("failure")
        val tree = StringBuilder()
        fun dump(node: AccessibilityNodeInfo?, depth: Int = 0) {
            if (node == null) return
            val bounds = android.graphics.Rect().also(node::getBoundsInScreen)
            tree.appendLine("${" ".repeat(depth)}${node.className} text=${node.text} visible=${node.isVisibleToUser} enabled=${node.isEnabled} scroll=${node.isScrollable} bounds=$bounds")
            repeat(node.childCount) { dump(node.getChild(it), depth + 1) }
        }
        dump(automation.rootInActiveWindow)
        File(instrumentation.targetContext.getExternalFilesDir(null), "onboarding-audit/failure-tree.txt").writeText(tree.toString())
        error("Missing onboarding control: $text")
    }

    private fun tap(text: String, scroll: Boolean = false, settle: Long = 700) {
        if (scroll) repeat(7) {
            if (findText(text) != null) return@repeat
            fun scrollNode(node: AccessibilityNodeInfo?): Boolean {
                if (node == null) return false
                if (node.isScrollable && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
                repeat(node.childCount) { if (scrollNode(node.getChild(it))) return true }
                return false
            }
            scrollNode(automation.rootInActiveWindow)
            Thread.sleep(150)
        }
        var node: AccessibilityNodeInfo? = awaitText(text)
        while (node != null && !node.isClickable) node = node.parent
        assertNotNull("No click target for $text", node)
        assertTrue("Click failed for $text", node!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        Thread.sleep(settle)
    }
}
