package com.akira.app.feature.home

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Color as PixelColor
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.akira.app.MainActivity
import com.akira.app.core.design.components.LocalContentMotionReduced
import com.akira.app.core.design.glass.*
import com.akira.app.core.design.backdrop.backdrops.layerBackdrop
import com.akira.app.core.design.backdrop.backdrops.rememberLayerBackdrop
import com.akira.app.core.design.navigation.LocalPageHeaderBackdrop
import com.akira.app.core.design.rendering.GlassState
import com.akira.app.core.design.theme.DarkColorScheme
import com.akira.app.feature.collection.PlaylistDetailScaffold
import com.akira.app.feature.collection.ArtistBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

/** Runs against the separate debug application, without touching production data. */
@OptIn(ExperimentalComposeApi::class)
class HomeHeaderRenderingTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun activity(): MainActivity {
        // Connect accessibility before Compose builds its virtual semantics tree.
        instrumentation.uiAutomation
        var result: MainActivity? = null
        fun find() = instrumentation.runOnMainSync {
            result = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
        }
        find()
        if (result == null) instrumentation.targetContext.startActivity(
            Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val deadline = android.os.SystemClock.elapsedRealtime() + 12_000
        while (result == null && android.os.SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(80)
            find()
        }
        return checkNotNull(result) { "Test activity did not resume" }.also { host ->
            instrumentation.runOnMainSync {
                host.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    private fun dispose(host: MainActivity) = instrumentation.runOnMainSync {
        host.setContent {}
        host.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    @Test fun continuousHomeScrollUpdatesLayersWithoutRecomposingTheHeader() {
        val host = activity()
        val progress = mutableFloatStateOf(.2f)
        instrumentation.runOnMainSync {
            host.setContent {
                MaterialTheme(colorScheme = DarkColorScheme) {
                    HomeTopBar({ progress.floatValue }, remember { GlassState() }, { 0f }, 1500f)
                }
            }
        }
        try {
            Thread.sleep(1200)
            instrumentation.waitForIdleSync()
            val before = Recomposer.runningRecomposers.value.associateWith { it.changeCount }
            assertTrue("No active composition", before.isNotEmpty())
            repeat(20) { frame ->
                instrumentation.runOnMainSync { progress.floatValue = .21f + frame * .01f }
                Thread.sleep(35)
            }
            instrumentation.waitForIdleSync()
            before.forEach { (recomposer, changes) ->
                assertEquals("Continuous scroll recomposed the Home header", changes, recomposer.changeCount)
            }
        } finally { dispose(host) }
    }

    @Test fun albumBackSamplesItsOwnBodyBeforeAndAfterScrolling() {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 33)
        val host = activity()
        val list = LazyListState()
        instrumentation.runOnMainSync {
            host.setContent {
                MaterialTheme(colorScheme = DarkColorScheme) {
                    val material = rememberSharedGlassMaterial()
                    CompositionLocalProvider(LocalLiquidGlassEnabled provides true,
                        LocalSharedGlassMaterial provides material,
                        LocalLiquidGlassOpacity provides { 0f }, LocalLiquidGlassDiffusion provides { 0f },
                        LocalContentMotionReduced provides true) {
                        PlaylistDetailScaffold(null, null, list, {}, {}, showCover = false, headerHeight = 100.dp) {
                            item("red") { Canvas(Modifier.fillMaxWidth().height(1200.dp)) { drawRect(Color.Red) } }
                            item("blue") { Canvas(Modifier.fillMaxWidth().height(1200.dp)) { drawRect(Color.Blue) } }
                        }
                    }
                }
            }
        }
        try {
            Thread.sleep(900)
            val first = backPixel()
            assertTrue("Album back lacks its red page source: ${Integer.toHexString(first)}",
                PixelColor.red(first) > PixelColor.blue(first) + 20)
            runBlocking { withContext(Dispatchers.Main) { list.scrollToItem(1) } }
            Thread.sleep(500)
            val second = backPixel()
            assertTrue("Album back did not follow the blue scrolled row: ${Integer.toHexString(second)}",
                PixelColor.blue(second) > PixelColor.red(second) + 20)
        } finally { dispose(host) }
    }

    @Test fun artistBackAndShareCapsuleKeepTheirLiveSourceAfterCollapsing() {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 33)
        val host = activity()
        val source = mutableStateOf(Color.Red)
        val progress = mutableFloatStateOf(0f)
        instrumentation.runOnMainSync {
            host.setContent {
                MaterialTheme(colorScheme = DarkColorScheme) {
                    val scene = rememberLayerBackdrop()
                    val material = rememberSharedGlassMaterial()
                    CompositionLocalProvider(LocalAppBackdrop provides scene, LocalPageHeaderBackdrop provides scene,
                        LocalLiquidGlassEnabled provides true, LocalSharedGlassMaterial provides material,
                        LocalLiquidGlassOpacity provides { 0f }, LocalLiquidGlassDiffusion provides { 0f },
                        LocalContentMotionReduced provides true) {
                        Box(Modifier.fillMaxSize()) {
                            Canvas(Modifier.fillMaxSize().layerBackdrop(scene)) { drawRect(source.value) }
                            ArtistBar("Artist", remember { GlassState() }, { progress.floatValue }, Color.Black,
                                {}, {}, {}, {})
                        }
                    }
                }
            }
        }
        try {
            for (collapse in listOf(0f, 1f)) {
                instrumentation.runOnMainSync { progress.floatValue = collapse; source.value = Color.Red }
                Thread.sleep(500)
                val redBack = backPixel()
                val redShare = backPixel(listOf("Share", "Compartir"))
                // Only the Canvas reads source: changing it cannot recompose the header.
                instrumentation.runOnMainSync { source.value = Color.Blue }
                Thread.sleep(300)
                val blueBack = backPixel()
                val blueShare = backPixel(listOf("Share", "Compartir"))
                for ((name, red, blue) in listOf(Triple("back", redBack, blueBack), Triple("share", redShare, blueShare))) {
                    assertTrue("Artist $name missed red source at collapse=$collapse",
                        PixelColor.red(red) > PixelColor.blue(red) + 20)
                    assertTrue("Artist $name froze its source at collapse=$collapse",
                        PixelColor.blue(blue) > PixelColor.red(blue) + 20)
                }
            }
        } finally { dispose(host) }
    }

    private fun backPixel(descriptions: List<String> = listOf("Back", "Atrás")): Int {
        instrumentation.waitForIdleSync()
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.contentDescription?.toString() in descriptions) return node
            repeat(node.childCount) { find(node.getChild(it))?.let { found -> return found } }
            return null
        }
        val automation = instrumentation.uiAutomation
        var found: AccessibilityNodeInfo? = null
        val deadline = android.os.SystemClock.elapsedRealtime() + 2500
        while (found == null && android.os.SystemClock.elapsedRealtime() < deadline) {
            found = find(automation.rootInActiveWindow)
            if (found == null) Thread.sleep(50)
        }
        var button = checkNotNull(found) { "Control $descriptions not exposed" }
        while (!button.isClickable && button.parent != null) button = button.parent
        val bounds = Rect().also(button::getBoundsInScreen)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            // Stay inside the circular face, away from the white chevron and rim.
            return bitmap.getPixel((bounds.left + bounds.width() * .8f).toInt(), bounds.centerY())
        } finally { bitmap.recycle() }
    }
}
