package com.akira.app.feature.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.fail
import org.junit.Test

class PlayerArtworkAtmosphereDeviceTest {
    @Test
    fun mirrorsTheCoverVerticallyAtTheFloorAndKeepsItsPalette() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val artwork = File.createTempFile("player-atmosphere-", ".png", instrumentation.targetContext.cacheDir)
        val blue = android.graphics.Color.rgb(28, 54, 125)
        val orange = android.graphics.Color.rgb(202, 119, 61)
        val bitmap = Bitmap.createBitmap(112, 112, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(blue)
        canvas.drawRect(56f, 27f, 112f, 79f, Paint().apply { color = orange })
        artwork.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        try {
            val bounds = AtomicReference(Rect.Zero)
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.enableEdgeToEdge()
                    activity.setContent {
                        val atmosphere = rememberPlayerArtworkAtmosphere(artwork.toURI().toString(), 0L)
                        if (atmosphere != null) {
                            Box(Modifier.size(180.dp, 260.dp)
                                .playerArtworkAtmosphere(atmosphere, opacity = { 1f })
                                .onGloballyPositioned { bounds.set(it.boundsInWindow()) })
                        }
                    }
                }
                val deadline = SystemClock.elapsedRealtime() + 5_000
                while (SystemClock.elapsedRealtime() < deadline) {
                    instrumentation.waitForIdleSync()
                    val area = bounds.get()
                    if (area.width > 0f) {
                        val frame = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                        fun pixel(x: Float, y: Float) = frame.getPixel(
                            (area.left + area.width * x).toInt(), (area.top + area.height * y).toInt(),
                        )
                        val left = pixel(.22f, .54f)
                        val topSideOfFloor = pixel(.78f, .41f)
                        val reflectedSideOfFloor = pixel(.78f, .55f)
                        val reflectedOrangeBand = pixel(.78f, .71f)
                        val footer = pixel(.78f, .94f)
                        frame.recycle()
                        if (android.graphics.Color.blue(left) - android.graphics.Color.red(left) > 35 &&
                            android.graphics.Color.blue(topSideOfFloor) - android.graphics.Color.red(topSideOfFloor) > 30 &&
                            android.graphics.Color.blue(reflectedSideOfFloor) - android.graphics.Color.red(reflectedSideOfFloor) > 30 &&
                            android.graphics.Color.red(reflectedOrangeBand) - android.graphics.Color.blue(reflectedOrangeBand) > 35 &&
                            android.graphics.Color.red(footer) < android.graphics.Color.red(reflectedOrangeBand) - 25) return
                    }
                    SystemClock.sleep(45)
                }
                fail("Expected matching blue fields at the floor, a reflected orange band and a darker footer")
            }
        } finally {
            artwork.delete()
        }
    }
}
