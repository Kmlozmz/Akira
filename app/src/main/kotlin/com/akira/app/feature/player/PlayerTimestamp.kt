package com.akira.app.feature.player

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.akira.app.core.design.theme.AkiraFontFamily

internal fun playerTimestampColor(background: Color): Color {
    val ink = if (background.luminance() > .179f) Color.Black else Color.White
    fun contrast(alpha: Float): Float {
        val text = ink.copy(alpha = alpha).compositeOver(background).luminance()
        val surface = background.luminance()
        return (maxOf(text, surface) + .05f) / (minOf(text, surface) + .05f)
    }
    var low = .55f
    var high = 1f
    if (contrast(low) >= 4.6f) return ink.copy(alpha = low)
    repeat(16) {
        val middle = (low + high) * .5f
        if (contrast(middle) >= 4.6f) high = middle else low = middle
    }
    return ink.copy(alpha = high)
}

@Composable
internal fun PlayerTimestamp(text: String, referencePortrait: Boolean, background: Color = Color.Black) {
    val ink = remember(background) { playerTimestampColor(background) }
    Text(text, fontSize = 12.sp,
        fontFamily = if (referencePortrait) FontFamily.SansSerif else AkiraFontFamily,
        fontWeight = if (referencePortrait) FontWeight.Medium else FontWeight.Normal,
        letterSpacing = if (referencePortrait) .6.sp else 0.sp,
        color = ink)
}
