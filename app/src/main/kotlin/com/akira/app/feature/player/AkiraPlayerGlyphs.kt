package com.akira.app.feature.player

import com.akira.app.feature.library.*
import com.akira.app.feature.lyrics.*

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Player-only optical masters. Other app icons retain their own size and weight. */
internal object AkiraPlayerGlyphs {
    private fun speaker(waves: Boolean) = ImageVector.Builder(
        if(waves)"PlayerVolumeHigh" else "PlayerVolumeLow",24.dp,24.dp,24f,24f
    ).apply {
        path(fill=SolidColor(Color.White),stroke=SolidColor(Color.White),
            strokeLineWidth=1f,strokeLineJoin=StrokeJoin.Round) {
            val left=if(waves)1f else 3f
            val neck=if(waves)5.5f else 7.5f
            val face=if(waves)11f else 13f
            moveTo(left,9f);lineTo(neck,9f);lineTo(face,4.5f)
            lineTo(face,19.5f);lineTo(neck,15f);lineTo(left,15f);close()
        }
        if(waves) path(stroke=SolidColor(Color.White),strokeLineWidth=1.35f,
            strokeLineCap=StrokeCap.Round) {
            moveTo(14.5f,8.3f);curveTo(16.4f,10.4f,16.4f,13.6f,14.5f,15.7f)
            moveTo(17.5f,5.8f);curveTo(20.9f,9.1f,20.9f,14.9f,17.5f,18.2f)
            moveTo(20.4f,3.6f);curveTo(24.1f,8.1f,24.1f,15.9f,20.4f,20.4f)
        }
    }.build()

    val VolumeLow = speaker(false)
    val VolumeHigh = speaker(true)

    val Airplay = ImageVector.Builder("PlayerAudioRoute",24.dp,24.dp,24f,24f).apply {
        path(stroke=SolidColor(Color.White),strokeLineWidth=1.65f,
            strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round) {
            moveTo(12f,14.2f);lineTo(17.5f,21.5f);lineTo(6.5f,21.5f);close()
            moveTo(5.9f,17.1f);curveTo(2.6f,14.2f,2.4f,9.2f,5.3f,5.9f)
            curveTo(8.8f,1.7f,15.2f,1.7f,18.7f,5.9f)
            curveTo(21.6f,9.2f,21.4f,14.2f,18.1f,17.1f)
            moveTo(8.1f,14.6f);curveTo(5.9f,12.7f,5.9f,9.7f,7.9f,7.6f)
            curveTo(10.1f,5.2f,13.9f,5.2f,16.1f,7.6f)
            curveTo(18.1f,9.7f,18.1f,12.7f,15.9f,14.6f)
            moveTo(10.3f,12.1f);curveTo(8.8f,10.6f,10.1f,8.5f,12f,8.5f)
            curveTo(13.9f,8.5f,15.2f,10.6f,13.7f,12.1f)
        }
    }.build()
}
