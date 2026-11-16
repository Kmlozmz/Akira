package com.akira.app.feature.setup

import android.content.Intent
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.akira.app.MainActivity
import com.akira.app.core.design.backdrop.*
import com.akira.app.core.design.rendering.PreparedRuntimeShaders
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class OnboardingRenderTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun host(): MainActivity {
        var activity: MainActivity? = null
        val deadline = SystemClock.elapsedRealtime() + 15_000
        instrumentation.targetContext.startActivity(Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        while (activity == null && SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync { activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull() }
            if (activity == null) Thread.sleep(50)
        }
        return checkNotNull(activity)
    }

    @Test fun revealClockDoesNotRecomposeTheDestination() {
        val activity = host()
        val compositions = AtomicInteger()
        val frames = AtomicInteger()
        val done = CountDownLatch(1)
        instrumentation.runOnMainSync {
            activity.setContent {
                val progress = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    progress.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
                    done.countDown()
                }
                Box(Modifier.fillMaxSize().onboardingReveal { frames.incrementAndGet(); progress.value }.background(Color.White)) {
                    SideEffect { compositions.incrementAndGet() }
                    Text("Actual destination content")
                }
            }
        }
        assertTrue(done.await(15, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        assertTrue("Reveal must render intermediate frames", frames.get() >= 3)
        assertEquals("Drawing the reveal must not recompose destination content", 1, compositions.get())
        val folder = File(activity.getExternalFilesDir(null), "onboarding-audit").apply { mkdirs() }
        File(folder, "reveal-render.json").writeText("""{"durationMs":750,"draws":${frames.get()},"destinationCompositions":${compositions.get()}}""")
    }

    @Test fun preparedShadersKeepUniformsIndependentAndObserveReusedArrays() {
        val activity = host()
        val source = "uniform float4 tint; half4 main(float2 p) { return half4(tint); }"
        PreparedRuntimeShaders.prepare(source)
        Thread.sleep(300)
        val first = RuntimeShader(source)
        val second = RuntimeShader(source)
        assertNotSame(first.asAndroidRuntimeShader(), second.asAndroidRuntimeShader())
        val reused = floatArrayOf(1f, 0f, 0f, 1f)
        first.setFloatUniform("tint", reused)
        second.setFloatUniform("tint", 0f, 1f, 0f, 1f)
        reused[0] = 0f; reused[2] = 1f
        first.setFloatUniform("tint", reused)
        first.setFloatUniform("tint", reused)
        val a = android.graphics.Paint().apply { shader = first.asAndroidRuntimeShader() }
        val b = android.graphics.Paint().apply { shader = second.asAndroidRuntimeShader() }
        instrumentation.runOnMainSync {
            activity.setContent {
                Canvas(Modifier.fillMaxSize()) {
                    drawIntoCanvas {
                        it.nativeCanvas.drawRect(0f, 0f, size.width / 2, size.height, a)
                        it.nativeCanvas.drawRect(size.width / 2, 0f, size.width, size.height, b)
                    }
                }
            }
        }
        Thread.sleep(500)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        assertEquals(android.graphics.Color.BLUE, bitmap.getPixel(bitmap.width / 4, bitmap.height / 2))
        assertEquals(android.graphics.Color.GREEN, bitmap.getPixel(bitmap.width * 3 / 4, bitmap.height / 2))
        bitmap.recycle()
    }
}
