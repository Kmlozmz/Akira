package com.akira.app.feature.setup

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Source dynamics and matched levels use distinct audio symbols. */
internal object SetupAudioIcons {
    val Original = ImageVector.Builder("SourceWaveform", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(3f, 10f); lineTo(3f, 14f)
            moveTo(7.5f, 6f); lineTo(7.5f, 18f)
            moveTo(12f, 3f); lineTo(12f, 21f)
            moveTo(16.5f, 8f); lineTo(16.5f, 16f)
            moveTo(21f, 10f); lineTo(21f, 14f)
        }
    }.build()
    val Balanced = ImageVector.Builder("MatchedLevels", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(4f, 7f); lineTo(20f, 7f)
            moveTo(4f, 17f); lineTo(20f, 17f)
            moveTo(7f, 10f); lineTo(7f, 14f)
            moveTo(12f, 10f); lineTo(12f, 14f)
            moveTo(17f, 10f); lineTo(17f, 14f)
        }
    }.build()
}
