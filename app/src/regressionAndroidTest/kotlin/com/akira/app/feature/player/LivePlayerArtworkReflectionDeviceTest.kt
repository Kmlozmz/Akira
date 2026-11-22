package com.akira.app.feature.player

import android.os.SystemClock
import android.view.View
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.app.preview.AudioPreviewActivity
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.fail
import org.junit.Test

@SdkSuppress(minSdkVersion = 31)
class LivePlayerArtworkReflectionDeviceTest {
    @Test
    fun reflectsColorChangesFromTheRecordedNativeLayer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bounds = AtomicReference(Rect.Zero)
        val color = mutableIntStateOf(android.graphics.Color.BLUE)
        ActivityScenario.launch(AudioPreviewActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    val layer = rememberGraphicsLayer()
                    Box(Modifier.size(180.dp, 260.dp).background(Color.Black)
                        .onGloballyPositioned { bounds.set(it.boundsInWindow()) }) {
                        LivePlayerArtworkReflection(layer, artwork = { null }, opacity = { 1f },
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.59f))
                        Box(Modifier.fillMaxWidth().fillMaxHeight(.60f).drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        }) {
                            AndroidView(
                                factory = { View(it) },
                                update = { it.setBackgroundColor(color.intValue) },
                                modifier = Modifier.fillMaxWidth().fillMaxHeight(1f),
                            )
                        }
                    }
                }
            }
            fun assertReflection(red: Boolean) {
                val deadline = SystemClock.elapsedRealtime() + 5_000
                while (SystemClock.elapsedRealtime() < deadline) {
                    instrumentation.waitForIdleSync()
                    val area = bounds.get()
                    if (area.width > 0f) {
                        val frame = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                        val pixel = frame.getPixel((area.left + area.width * .55f).toInt(),
                            (area.top + area.height * .76f).toInt())
                        frame.recycle()
                        val difference = android.graphics.Color.red(pixel) - android.graphics.Color.blue(pixel)
                        if (if (red) difference > 60 else difference < -60) return
                    }
                    SystemClock.sleep(45)
                }
                fail("Expected the lower reflection to follow the current native artwork color")
            }
            assertReflection(red = false)
            scenario.onActivity { color.intValue = android.graphics.Color.RED }
            assertReflection(red = true)
        }
    }
}
