package com.akira.app.core.design.navigation

internal fun pageCollapseProgress(offset: Int, scrollRange: Int?, travel: Float): Float {
    if (offset <= 0 || scrollRange == 0) return 0f
    val distance = minOf(travel, scrollRange?.toFloat() ?: travel).coerceAtLeast(1f)
    return (offset / distance).coerceIn(0f, 1f)
}
