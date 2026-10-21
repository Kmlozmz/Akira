package com.akira.app.core.design.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.theme.AkiraFontFamily

/** Reserved for player metadata; lists and sheets continue to use ordinary Text. */
@Composable
fun AkiraPlayerMarquee(text: String, fontSize: TextUnit, fontWeight: FontWeight,
    color: Color, modifier: Modifier = Modifier, lineHeight: TextUnit = fontSize * 1.3f,
    fontFamily: FontFamily = AkiraFontFamily) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontFamily = fontFamily, fontSize = fontSize,
        fontWeight = fontWeight, lineHeight = lineHeight)
    BoxWithConstraints(modifier.clipToBounds()) {
        val available = with(LocalDensity.current) { maxWidth.roundToPx() }
        val overflows = measurer.measure(text, style = style, maxLines = 1).size.width > available
        val fade = with(LocalDensity.current) { 10.dp.toPx() }
        val edges = if (overflows) Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val width = fade.coerceAtMost(size.width / 4)
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), endX = width),
                    size = Size(width, size.height), blendMode = BlendMode.DstIn)
                drawRect(Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - width, endX = size.width),
                    topLeft = Offset(size.width - width, 0f), size = Size(width, size.height), blendMode = BlendMode.DstIn)
            } else Modifier
        Text(text, color = color, style = style, maxLines = 1, overflow = TextOverflow.Clip,
            modifier = Modifier.fillMaxWidth().then(edges).then(if (overflows)
                Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1500,
                    repeatDelayMillis = 1500, velocity = 22.dp) else Modifier))
    }
}
