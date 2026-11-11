package com.akira.app.feature.player

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.akira.app.core.design.theme.CurrentAkiraFontFamily as AkiraFontFamily

@Composable
internal fun PlayerTimestamp(text: String, referencePortrait: Boolean) {
    Text(text, fontSize=12.sp,fontFamily=AkiraFontFamily,
        fontWeight=if(referencePortrait)FontWeight.Medium else FontWeight.Normal,
        letterSpacing=if(referencePortrait).6.sp else 0.sp,color=Color.White.copy(alpha=.62f))
}
