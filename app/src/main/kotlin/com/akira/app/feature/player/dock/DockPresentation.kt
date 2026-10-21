package com.akira.app.feature.player.dock

import com.akira.app.feature.home.*

private fun reveal(progress: Float, end: Float): Float {
    val t = (1f - progress / end).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal fun playerCompactHeaderReveal(progress: Float): Float = reveal(progress, .65f)
internal fun dockMiniReveal(progress: Float): Float = reveal(progress, .24f)
internal fun dockNavigationReveal(progress: Float): Float = reveal(progress, .13f)

/** Recovery comes from the travelling surface's spring. A second sine-wave
 * offset made the closing panel change direction near the dock. */
internal fun dockMiniReturnOffsetDp(progress: Float): Float = 0f

/** Material hands back to the dock before the travelling artwork finishes.
 * Fading the entire player would also fade its one shared cover. */
internal fun playerMaterialReveal(progress: Float): Float {
    val t = ((progress - .02f) / .22f).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
