package com.akira.app.feature.setup

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.akira.app.BuildConfig
import com.akira.app.MainActivity
import com.akira.app.feature.settings.SettingsToggleRow
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.design.theme.DarkColorScheme
import com.akira.app.core.design.theme.LightColorScheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test

/** Native pointer regression: the first update must not freeze the row's callback. */
class SetupToggleInteractionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun repeatedThumbAndLabelTapsKeepWorkingAfterAsynchronousUpdates() {
        check(BuildConfig.DEBUG) { "Run this suite only against the isolated debug app" }
        instrumentation.uiAutomation
        var host: MainActivity? = null
        fun find() = instrumentation.runOnMainSync {
            host = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().firstOrNull()
        }
        find()
        if (host == null) instrumentation.targetContext.startActivity(Intent(instrumentation.targetContext,
            MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val deadline = SystemClock.elapsedRealtime() + 12_000
        while (host == null && SystemClock.elapsedRealtime() < deadline) { Thread.sleep(60); find() }
        val activity = checkNotNull(host)
        val checked = mutableStateOf(false)
        var changes = 0
        try {
            for (settingsRow in listOf(false, true)) for (light in listOf(false, true)) {
                instrumentation.runOnMainSync {
                    checked.value = false; changes = 0
                    activity.setContent {
                        MaterialTheme(colorScheme = if (light) LightColorScheme else DarkColorScheme) {
                            val scope = rememberCoroutineScope()
                            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
                                val change: (Boolean) -> Unit = { next ->
                                    changes++
                                    scope.launch { delay(30); checked.value = next }
                                }
                                if (settingsRow) SettingsToggleRow(AkiraIcons.Appearance, "Toggle regression", "", checked.value, change)
                                else SetupToggle("Toggle regression", checked.value, change)
                            }
                        }
                    }
                }
                repeat(8) { index ->
                    val node = awaitSwitch(index % 2 != 0)
                    val bounds = Rect().also(node::getBoundsInScreen)
                    // Alternate the original Akira switch and the label; each gesture commits once.
                    val x = if (index % 2 == 0) bounds.right - 24f * activity.resources.displayMetrics.density else bounds.left + bounds.width() * .25f
                    val y = bounds.exactCenterY()
                    val now = SystemClock.uptimeMillis()
                    for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                        val event = MotionEvent.obtain(now, now + if (action == MotionEvent.ACTION_UP) 55 else 0, action, x, y, 0)
                        event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                        // UiAutomation injection waits for system window animations. Dispatch through
                        // this app's actual decor view so an OEM's perpetual animation cannot stall the test.
                        try { instrumentation.runOnMainSync {
                            val decor = activity.window.decorView
                            val origin = IntArray(2).also(decor::getLocationOnScreen)
                            event.offsetLocation(-origin[0].toFloat(), -origin[1].toFloat())
                            assertTrue(decor.dispatchTouchEvent(event))
                        } } finally { event.recycle() }
                    }
                    awaitSwitch(index % 2 == 0)
                    instrumentation.runOnMainSync { assertEquals(index + 1, changes) }
                }
            }
        } finally { instrumentation.runOnMainSync { activity.setContent {} } }
    }

    private fun awaitSwitch(expected: Boolean): AccessibilityNodeInfo {
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isCheckable && node.isClickable) return node
            repeat(node.childCount) { find(node.getChild(it))?.let { result -> return result } }
            return null
        }
        val deadline = SystemClock.elapsedRealtime() + 4_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
            find(instrumentation.uiAutomation.rootInActiveWindow)?.let { if (it.isChecked == expected) return it }
            Thread.sleep(40)
        }
        error("Switch did not update to $expected")
    }
}
