package com.akira.app.core.design.rendering

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

internal fun interactiveRefreshRate(rates: List<Float>): Float {
    val supported = rates.filter { it.isFinite() && it > 0f }
    return supported.filter { it <= 120.5f }.maxOrNull() ?: supported.minOrNull() ?: 0f
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.activity() else null
    else -> null
}

/** Vote for smooth interactive frames without starting a frame loop at rest. */
@Composable internal fun PreferInteractiveFrameRate() {
    val view = LocalView.current
    DisposableEffect(view) {
        if (Build.VERSION.SDK_INT >= 35) {
            val original = view.requestedFrameRate
            view.requestedFrameRate = 120f
            onDispose { if (Build.VERSION.SDK_INT >= 35 && view.requestedFrameRate == 120f) view.requestedFrameRate = original }
        } else {
            val window = (view.parent as? DialogWindowProvider)?.window ?: view.context.activity()?.window
            val display = view.display
            val mode = display?.mode
            val target = interactiveRefreshRate(display?.supportedModes.orEmpty().filter {
                it.physicalWidth == mode?.physicalWidth && it.physicalHeight == mode.physicalHeight
            }.map { it.refreshRate })
            val original = window?.attributes?.preferredRefreshRate ?: 0f
            if (window != null && target > 0f) window.attributes = window.attributes.apply { preferredRefreshRate = target }
            onDispose {
                if (window != null && window.attributes.preferredRefreshRate == target)
                    window.attributes = window.attributes.apply { preferredRefreshRate = original }
            }
        }
    }
}
