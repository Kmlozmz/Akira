package com.akira.app.core.design.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

internal object GlassContrast {
    const val DarkLuminance = .075f
    const val LightLuminance = .55f
    const val SecondaryAlpha = .8f

    fun background(lightInk: Boolean): Color {
        val linear = if (lightInk) DarkLuminance else LightLuminance
        val channel = 1.055f * Math.pow(linear.toDouble(), 1.0 / 2.4).toFloat() - .055f
        return Color(channel, channel, channel)
    }

    fun readableAccent(color: Color, lightInk: Boolean): Color {
        val background = background(lightInk)
        val target = if (lightInk) Color.White else Color.Black
        fun contrast(ink: Color): Float {
            val foreground = ink.compositeOver(background).luminance()
            val surface = background.luminance()
            return (maxOf(foreground, surface) + .05f) / (minOf(foreground, surface) + .05f)
        }
        if (contrast(color) >= 4.6f) return color
        var low = 0f
        var high = 1f
        repeat(16) {
            val middle = (low + high) * .5f
            if (contrast(lerp(color, target, middle)) >= 4.6f) high = middle else low = middle
        }
        return lerp(color, target, high)
    }
}
