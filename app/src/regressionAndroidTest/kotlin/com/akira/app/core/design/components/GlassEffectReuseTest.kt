package com.akira.app.core.design.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.app.core.design.backdrop.RuntimeShader
import com.akira.app.core.design.backdrop.backdrops.rememberCanvasBackdrop
import com.akira.app.core.design.backdrop.drawBackdrop
import com.akira.app.core.design.backdrop.effects.blur
import com.akira.app.core.design.backdrop.internal.RuntimeShaderEffect
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@SdkSuppress(minSdkVersion = 33)
class GlassEffectReuseTest {
    @Test
    fun shaderEffectIsReusedUntilAnOpticalInputChanges() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val shader = RuntimeShader("""
                uniform shader content;
                uniform float intensity;
                half4 main(float2 p) { return content.eval(p) * intensity; }
            """.trimIndent())
            shader.setFloatUniform("intensity", .5f)
            val effect = RuntimeShaderEffect(shader, "content")
            shader.setFloatUniform("intensity", .5f)
            assertSame(effect, RuntimeShaderEffect(shader, "content"))
            shader.setFloatUniform("intensity", .5001f)
            assertNotSame(effect, RuntimeShaderEffect(shader, "content"))
        }
    }

    @Test
    fun backdropRedrawKeepsItsEffectsAndOpticalChangesInvalidateThem() {
        val backdropBlue = mutableFloatStateOf(0f)
        val radius = mutableFloatStateOf(4f)
        val sourceDraws = AtomicInteger()
        val appliedEffects = AtomicInteger()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    val source = rememberCanvasBackdrop {
                        drawRect(Color(red = 1f, green = 0f, blue = backdropBlue.floatValue))
                        sourceDraws.incrementAndGet()
                    }
                    Box(Modifier.size(96.dp).drawBackdrop(
                        backdrop = source,
                        shape = { RoundedCornerShape(16.dp) },
                        effects = {
                            blur(radius.floatValue)
                            appliedEffects.incrementAndGet()
                        },
                        shadow = null,
                        highlight = null,
                    ))
                }
            }
            fun await(message: String, condition: () -> Boolean) {
                val deadline = android.os.SystemClock.uptimeMillis() + 3_000
                while (!condition() && android.os.SystemClock.uptimeMillis() < deadline) {
                    instrumentation.waitForIdleSync()
                    android.os.SystemClock.sleep(16)
                }
                assertTrue(message, condition())
            }
            await("Glass did not draw") { sourceDraws.get() > 0 && appliedEffects.get() > 0 }
            val initialApplications = appliedEffects.get()
            val initialDraws = sourceDraws.get()
            scenario.onActivity { backdropBlue.floatValue = 1f }
            await("Live backdrop did not redraw") { sourceDraws.get() > initialDraws }
            assertEquals(initialApplications, appliedEffects.get())
            scenario.onActivity { radius.floatValue = 9.25f }
            await("The new radius did not update the effect") { appliedEffects.get() > initialApplications }
        }
    }
}
