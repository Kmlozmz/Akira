package com.akira.app.feature.player

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.fail
import org.junit.Test

class PlayerRhythmLightingDeviceTest {
    @Test
    fun shiftingALightKeepsTheBottomAndLeadingEdgeCovered() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bounds = AtomicReference(Rect.Zero)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.enableEdgeToEdge()
                activity.setContent {
                    Box(Modifier.size(180.dp, 260.dp)
                        .onGloballyPositioned { bounds.set(it.boundsInWindow()) }
                        .drawBehind {
                            drawRect(Color.Blue)
                            drawShiftedPlayerLight(
                                SolidColor(Color.Red),
                                Offset(size.width * .20f, -size.height * .12f),
                                alpha = 1f,
                            )
                        })
                }
            }
            val deadline = SystemClock.elapsedRealtime() + 5_000
            while (SystemClock.elapsedRealtime() < deadline) {
                instrumentation.waitForIdleSync()
                val area = bounds.get()
                if (area.width > 0f) {
                    val frame = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                    fun isRed(x: Float, y: Float): Boolean {
                        val pixel = frame.getPixel((area.left + area.width * x).toInt(),
                            (area.top + area.height * y).toInt())
                        return android.graphics.Color.red(pixel) > 220 && android.graphics.Color.blue(pixel) < 40
                    }
                    val covered = isRed(.06f, .51f) && isRed(.55f, .96f)
                    frame.recycle()
                    if (covered) return
                }
                SystemClock.sleep(45)
            }
            fail("Moving the light left an uncovered band within the player backdrop")
        }
    }
}
