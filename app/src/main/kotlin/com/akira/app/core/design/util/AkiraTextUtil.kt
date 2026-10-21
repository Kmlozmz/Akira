package com.akira.app.core.design.util

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import com.akira.app.core.design.theme.AkiraFontFamily

/**
 * Builds an AnnotatedString that highlights every occurrence of the app name "Akira" in bold/extrabold.
 */
fun highlightAkira(
    text: String,
    boldWeight: FontWeight = FontWeight.Bold,
    boldColor: Color = Color.Unspecified
): AnnotatedString {
    val target = "Akira"
    return buildAnnotatedString {
        var currentIndex = 0
        while (currentIndex < text.length) {
            val idx = text.indexOf(target, currentIndex)
            if (idx == -1) {
                append(text.substring(currentIndex))
                break
            }
            if (idx > currentIndex) {
                append(text.substring(currentIndex, idx))
            }
            withStyle(
                SpanStyle(
                    fontWeight = boldWeight,
                    color = boldColor
                )
            ) {
                append(target)
            }
            currentIndex = idx + target.length
        }
    }
}

/**
 * Reusable Compose Text that automatically highlights every occurrence of "Akira" in bold.
 */
@Composable
fun AkiraText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = AkiraFontFamily,
    lineHeight: TextUnit = TextUnit.Unspecified,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    boldWeight: FontWeight = FontWeight.ExtraBold,
    boldColor: Color = Color.Unspecified,
    style: TextStyle = TextStyle.Default
) {
    Text(
        text = highlightAkira(text, boldWeight = boldWeight, boldColor = boldColor),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
        textAlign = textAlign,
        style = style
    )
}
