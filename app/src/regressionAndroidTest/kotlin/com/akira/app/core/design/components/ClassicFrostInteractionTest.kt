package com.akira.app.core.design.components

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.akira.app.BuildConfig
import com.akira.app.MainActivity
import com.akira.app.core.design.glass.*
import com.akira.app.core.design.theme.AkiraTheme
import com.akira.app.core.i18n.*
import com.akira.app.core.online.OnlineAccount
import com.akira.app.feature.library.*
import com.akira.app.feature.collection.AkiraArtistMasthead
import com.akira.app.feature.online.ListeningModeContent
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/** Real RenderEffect output and native accessibility/touch paths; separate debug package only. */
class ClassicFrostInteractionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private var currentHost: MainActivity? = null
    private fun activity(): MainActivity {
        check(BuildConfig.DEBUG)
        automation
        var host: MainActivity? = null
        fun find() = instrumentation.runOnMainSync {
            host = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().firstOrNull()
        }
        find()
        if (host == null) instrumentation.targetContext.startActivity(Intent(instrumentation.targetContext,
            MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (host == null && SystemClock.elapsedRealtime() < deadline) { Thread.sleep(80); find() }
        return checkNotNull(host).also {
            currentHost = it
            instrumentation.runOnMainSync { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
    }
    @Composable private fun Theme(mode: String = "DARK", reduced: Boolean = false, opaque: Boolean = false,
        body: @Composable () -> Unit) {
        AkiraTheme(themeMode = mode) {
            CompositionLocalProvider(LocalStrings provides StringsEnglish, LocalContentColor provides MaterialTheme.colorScheme.onSurface, LocalLiquidGlassEnabled provides false,
                LocalGlassStyleEnabled provides false, LocalReduceTransparency provides opaque,
                LocalContentMotionReduced provides reduced, LocalAkiraMenuAnchors provides remember { AkiraMenuAnchors() }) {
                AkiraPresentationHost { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { body() } }
            }
        }
    }
    @Test fun frostedPanelsDiffuseTheLiveBackdropAndRespectReducedTransparency() {
        val host = activity()
        try {
            for ((mode, opaque) in listOf("LIGHT" to false, "DARK" to false, "DARK" to true)) {
                var blue by mutableStateOf(false)
                val rect = AtomicReference(Rect.Zero)
                instrumentation.runOnMainSync { host.setContent { Theme(mode, opaque = opaque) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stripe = 4.dp.toPx()
                        repeat((size.width / stripe).toInt() + 1) { i ->
                            val color = if (blue) Color(0xFF164FE8) else Color(0xFFE72B28)
                            drawRect(if (i % 2 == 0) color else color.copy(alpha = .35f),
                                topLeft = androidx.compose.ui.geometry.Offset(i * stripe, 0f),
                                size = androidx.compose.ui.geometry.Size(stripe, size.height))
                        }
                    }
                    AkiraModalSheet({}) {
                        Column(Modifier.fillMaxWidth().height(220.dp).onGloballyPositioned { rect.set(it.boundsInWindow()) }) {
                            Spacer(Modifier.height(100.dp))
                            Text("Frosted sample", Modifier.padding(horizontal = 24.dp))
                        }
                    }
                } } }
                await("Frosted sample"); Thread.sleep(600)
                val bounds = rect.get()
                assertTrue(bounds.width > 0f)
                fun sample(bitmap: Bitmap) = bitmap.getPixel(bounds.center.x.toInt(), (bounds.top + 25 * host.resources.displayMetrics.density).toInt())
                val redFrame = checkNotNull(automation.takeScreenshot())
                val red = sample(redFrame)
                save(host, redFrame, "classic-frost-$mode-$opaque-red")
                instrumentation.runOnMainSync { blue = true }; Thread.sleep(350)
                val blueFrame = checkNotNull(automation.takeScreenshot())
                val bluePixel = sample(blueFrame)
                val delta = abs(android.graphics.Color.red(red) - android.graphics.Color.red(bluePixel)) +
                    abs(android.graphics.Color.blue(red) - android.graphics.Color.blue(bluePixel))
                if (opaque) assertTrue("Reduced transparency must use a stable solid panel: $delta", delta < 6)
                else {
                    assertTrue("Live background tint must reach the frost in $mode: $delta", delta >= 25)
                    val y = (bounds.top + 25 * host.resources.displayMetrics.density).toInt()
                    val x = bounds.center.x.toInt()
                    val values = (-10..10).map { android.graphics.Color.blue(blueFrame.getPixel(x + it, y)) }
                    assertTrue("High frequency stripes must be diffused in $mode", values.max() - values.min() < 15)
                }
                save(host, blueFrame, "classic-frost-$mode-$opaque-blue")
                redFrame.recycle(); blueFrame.recycle()
            }
        } finally { instrumentation.runOnMainSync { host.setContent {} } }
    }
    @Test fun nestedMenusReturnWithoutLosingTheirParentAndDismissCleanly() {
        val host = activity()
        try {
            for (reduced in listOf(false, true)) {
                var menu by mutableStateOf(false)
                var modes by mutableStateOf(false)
                var selections = 0
                instrumentation.runOnMainSync { host.setContent { Theme(reduced = reduced) {
                    Box(Modifier.fillMaxSize().padding(24.dp)) {
                        TextButton({ menu = true }) { Text("Open menu") }
                        if (menu) AkiraContextMenu({ menu = false }, groups = listOf(listOf(
                            AkiraContextAction(Icons.Rounded.Headphones, "Listening mode", opensPresentation = true) { modes = true })))
                        if (modes) AkiraModalSheet({ modes = false }, style = AkiraPanelStyle.Popover) {
                            ListeningModeContent(OnlineAccount(true, "Test listener"), false,
                                onSelect = { assertTrue(it); selections++; modes = false }, onManage = {})
                        }
                    }
                } } }
                repeat(3) {
                    tap("Open menu")
                    if (it == 0 && !reduced) save(host, checkNotNull(automation.takeScreenshot()), "classic-menu")
                    tap("Listening mode"); await("Test listener")
                    if (it == 0 && !reduced) save(host, checkNotNull(automation.takeScreenshot()), "classic-account-popover")
                    assertNull(find("Listening mode", clickableOnly = true))
                    if (it == 0) {
                        automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                        Thread.sleep(400)
                    } else tap("Online music")
                    await("Listening mode")
                    automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                    Thread.sleep(350); await("Open menu")
                    assertNull(find("Online music"))
                }
                assertEquals(2, selections)
                save(host, checkNotNull(automation.takeScreenshot()), "classic-nested-closed-$reduced")
            }
        } finally { instrumentation.runOnMainSync { host.setContent {} } }
    }
    @Test fun listeningChoicesUseRealCallbacks() {
        val host = activity()
        var managed = false
        var online by mutableStateOf(false)
        try {
            instrumentation.runOnMainSync { host.setContent { Theme {
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    ListeningModeContent(OnlineAccount(true, "Test listener"), online,
                        onSelect = { online = it }, onManage = { managed = true })
                }
            } } }
            tap("Online music"); assertTrue(online)
            tap("Local library"); assertFalse(online)
            tap("Manage account"); assertTrue(managed)
            save(host, checkNotNull(automation.takeScreenshot()), "classic-account-choices")
        } finally { instrumentation.runOnMainSync { host.setContent {} } }
    }
    @Test fun openingAnAnchoredMenuRetainsTheRememberedPageState() {
        val host = activity()
        var mounts = 0
        var disposals = 0
        try {
            instrumentation.runOnMainSync { host.setContent { Theme {
                var menu by remember { mutableStateOf(false) }
                var count by remember { mutableIntStateOf(0) }
                DisposableEffect(Unit) { mounts++; onDispose { disposals++ } }
                Column {
                    Text("Remembered count $count")
                    AkiraIconButton(com.akira.app.core.design.icon.AkiraIcons.MoreHoriz, "Open remembered menu", { count++; menu = true })
                    if (menu) AkiraContextMenu({ menu = false }, groups = listOf(listOf(AkiraContextAction(null, "Remembered action") {})))
                }
            } } }
            tap("Open remembered menu")
            assertEquals("Opening the overlay must retain its owner", 1, mounts)
            assertEquals("Opening the overlay must not dispose its owner", 0, disposals)
            await("Remembered action")
        } finally { instrumentation.runOnMainSync { host.setContent {} } }
    }
    @Test fun libraryOverviewShowsCategoryLinksAndRefreshMenu() {
        val host = activity()
        var refreshed = 0
        var selected = ""
        try {
            instrumentation.runOnMainSync { host.setContent { Theme {
                LibraryOverview(localLibraryCategories().filter { it.key in listOf("Artists", "Albums", "Tracks") },
                    onSelect = { selected = it }, onAccount = {}, onRefresh = { refreshed++ })
            } } }
            tap("Albums"); assertEquals("Albums", selected)
            save(host, checkNotNull(automation.takeScreenshot()), "classic-library-overview")
            tap("More options"); await("Refresh library")
            save(host, checkNotNull(automation.takeScreenshot()), "classic-library-overview-menu")
            tap("Refresh library"); assertEquals(1, refreshed)
        } finally { instrumentation.runOnMainSync { host.setContent {} } }
    }
    @Test fun artistShufflePlayAndFavoriteAreSeparateAndPlayIsCentered() {
        val host = activity()
        var shuffleCalls = 0
        var playing by mutableStateOf(false)
        var favorite by mutableStateOf(false)
        try {
            instrumentation.runOnMainSync { host.setContent { Theme {
                Box(Modifier.fillMaxSize().safeDrawingPadding().padding(30.dp)) {
                    AkiraArtistMasthead("Test artist", "3 songs", favorite, playing,
                        onPlay = { playing = !playing }, onFavorite = { favorite = !favorite },
                        onShuffle = { shuffleCalls++ })
                }
            } } }
            fun bounds(label: String): android.graphics.Rect {
                var node: AccessibilityNodeInfo? = await(label)
                while (node != null && !node.isClickable) node = node.parent
                return android.graphics.Rect().also { checkNotNull(node).getBoundsInScreen(it) }
            }
            val left = bounds("Shuffle")
            val play = bounds("Play")
            val right = bounds("Favorite artist")
            assertTrue(left.right <= play.left && play.right <= right.left)
            assertTrue(play.width() > left.width() && play.width() > right.width())
            assertTrue(abs(left.centerY() - play.centerY()) <= 1)
            assertTrue(abs(right.centerY() - play.centerY()) <= 1)
            tap("Shuffle"); assertEquals(1, shuffleCalls)
            tap("Play"); await("Pause")
            tap("Favorite artist"); assertTrue(favorite)
            tap("Pause"); await("Play")
            save(host, checkNotNull(automation.takeScreenshot()), "artist-transport")
        } finally { instrumentation.runOnMainSync { host.setContent {} } }
    }
    private fun find(text: String, clickableOnly: Boolean = false): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT >= 33) automation.clearCache()
        fun visit(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isVisibleToUser && (node.text?.toString() == text || node.contentDescription?.toString() == text) && (!clickableOnly || node.isClickable)) return node
            repeat(node.childCount) { visit(node.getChild(it))?.let { result -> return result } }
            return null
        }
        return visit(automation.rootInActiveWindow)
    }
    private fun await(text: String): AccessibilityNodeInfo {
        val deadline = SystemClock.elapsedRealtime() + 8_000
        while (SystemClock.elapsedRealtime() < deadline) { find(text)?.let { return it }; Thread.sleep(60) }
        automation.takeScreenshot()?.let { save(checkNotNull(currentHost), it, "failure-classic-${text.replace(' ', '-')}") }
        dumpTree("failure-classic-${text.replace(' ', '-')}")
        error("Missing Classic fixture control: $text")
    }
    private fun tap(text: String) {
        var node: AccessibilityNodeInfo? = await(text)
        while (node != null && !node.isClickable) node = node.parent
        assertTrue(checkNotNull(node).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        Thread.sleep(400)
    }
    private fun dumpTree(name: String) {
        val output = StringBuilder()
        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null) return
            output.append(" ".repeat(depth)).append(node.text).append(" | ").append(node.contentDescription)
                .append(" visible=").append(node.isVisibleToUser).append(" click=").append(node.isClickable).append('\n')
            repeat(node.childCount) { visit(node.getChild(it), depth + 1) }
        }
        visit(automation.rootInActiveWindow, 0)
        File(checkNotNull(currentHost).getExternalFilesDir(null), "onboarding-audit/$name.txt").writeText(output.toString())
    }
    private fun save(host: MainActivity, bitmap: Bitmap, name: String) {
        val folder = File(host.getExternalFilesDir(null), "onboarding-audit").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
