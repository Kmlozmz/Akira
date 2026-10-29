package com.akira.app.core.design.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Akira vectors use rounded edges and joins with Material 3 Expressive proportions. */
object AkiraIcons {
    /** Three centred lines, progressively shorter like the supplied filter. */
    val Filter: ImageVector by lazy {
        ImageVector.Builder("AkiraFilter",24.dp,24.dp,24f,24f).apply {
            path(stroke=SolidColor(Color.White),strokeLineWidth=2.1f,strokeLineCap=StrokeCap.Round) {
                moveTo(3f,6f);lineTo(21f,6f)
                moveTo(6f,12f);lineTo(18f,12f)
                moveTo(9f,18f);lineTo(15f,18f)
            }
        }.build()
    }
    /** Target + centre dot: locate the playing entry, not another queue menu. */
    val Locate: ImageVector by lazy {
        ImageVector.Builder("AkiraLocate", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.White), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(19f,12f)
                curveTo(19f,15.866f,15.866f,19f,12f,19f)
                curveTo(8.134f,19f,5f,15.866f,5f,12f)
                curveTo(5f,8.134f,8.134f,5f,12f,5f)
                curveTo(15.866f,5f,19f,8.134f,19f,12f)
                close()
                moveTo(12f,2f);lineTo(12f,5f)
                moveTo(12f,19f);lineTo(12f,22f)
                moveTo(2f,12f);lineTo(5f,12f)
                moveTo(19f,12f);lineTo(22f,12f)
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(14.4f,12f)
                curveTo(14.4f,13.325f,13.325f,14.4f,12f,14.4f)
                curveTo(10.675f,14.4f,9.6f,13.325f,9.6f,12f)
                curveTo(9.6f,10.675f,10.675f,9.6f,12f,9.6f)
                curveTo(13.325f,9.6f,14.4f,10.675f,14.4f,12f)
                close()
            }
        }.build()
    }

    val Download: ImageVector by lazy {
        ImageVector.Builder("AkiraDownload", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 3f); lineTo(12f, 15f); moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
                moveTo(4f, 16f); lineTo(4f, 19f); quadTo(4f, 21f, 6f, 21f); lineTo(18f, 21f); quadTo(20f, 21f, 20f, 19f); lineTo(20f, 16f)
            }
        }.build()
    }
    val Edit: ImageVector by lazy {
        ImageVector.Builder("AkiraEdit", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(4f, 20f); lineTo(5f, 14f); lineTo(16f, 3f); quadTo(17f, 2f, 18f, 3f)
                lineTo(21f, 6f); quadTo(22f, 7f, 21f, 8f); lineTo(10f, 19f); close()
                moveTo(14f, 5f); lineTo(19f, 10f)
            }
        }.build()
    }
    val Appearance: ImageVector by lazy {
        ImageVector.Builder("AkiraAppearance", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 3f); curveTo(6f, 3f, 3f, 7f, 3f, 12f); curveTo(3f, 17f, 7f, 21f, 12f, 21f)
                curveTo(14f, 21f, 15f, 20f, 14f, 18f); curveTo(13f, 16f, 14f, 14f, 17f, 14f)
                curveTo(21f, 14f, 22f, 11f, 20f, 7f); curveTo(18f, 4f, 15f, 3f, 12f, 3f); close()
                moveTo(7f, 10f); lineTo(7.1f, 10f); moveTo(11f, 7f); lineTo(11.1f, 7f); moveTo(16f, 8f); lineTo(16.1f, 8f)
            }
        }.build()
    }
    val Check: ImageVector by lazy {
        ImageVector.Builder("AkiraCheck", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.White), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(5f, 12.5f); lineTo(10f, 17f); lineTo(19f, 7f)
            }
        }.build()
    }
    val ChevronRight: ImageVector by lazy {
        ImageVector.Builder("AkiraChevronRight", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.White), strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f)
            }
        }.build()
    }

    val Play: ImageVector by lazy {
        ImageVector.Builder(
            name = "AkiraPlay",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.4f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Triangle slightly inset so round stroke maintains visual 24x24 bounds
                moveTo(8.2f, 6.2f)
                lineTo(8.2f, 17.8f)
                lineTo(18.2f, 12.0f)
                close()
            }
        }.build()
    }

    val Pause: ImageVector by lazy {
        ImageVector.Builder(
            name = "AkiraPause",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Filled 40 px bars at a 57 px pitch in the supplied reference.
            for (left in listOf(5.85f, 13.08f)) {
                path(fill = SolidColor(Color.White)) {
                    val right = left + 5.08f
                    val top = 4.45f; val bottom = 19.55f; val r = 1.5f
                    moveTo(left+r,top);lineTo(right-r,top)
                    quadTo(right,top,right,top+r);lineTo(right,bottom-r)
                    quadTo(right,bottom,right-r,bottom);lineTo(left+r,bottom)
                    quadTo(left,bottom,left,bottom-r);lineTo(left,top+r)
                    quadTo(left,top,left+r,top);close()
                }
            }
        }.build()
    }

    val FastForward: ImageVector by lazy {
        ImageVector.Builder(
            name = "AkiraFastForward",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Left triangle with round stroke
            path(
                fill = SolidColor(Color.White),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5.6f, 7.2f)
                lineTo(5.6f, 16.8f)
                lineTo(12.2f, 12.0f)
                close()
            }
            // Right triangle with round stroke
            path(
                fill = SolidColor(Color.White),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12.6f, 7.2f)
                lineTo(12.6f, 16.8f)
                lineTo(19.2f, 12.0f)
                close()
            }
        }.build()
    }

    val FastRewind: ImageVector by lazy {
        ImageVector.Builder(
            name = "AkiraFastRewind",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Left triangle pointing left
            path(
                fill = SolidColor(Color.White),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(11.4f, 7.2f)
                lineTo(11.4f, 16.8f)
                lineTo(4.8f, 12.0f)
                close()
            }
            // Right triangle pointing left
            path(
                fill = SolidColor(Color.White),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(18.4f, 7.2f)
                lineTo(18.4f, 16.8f)
                lineTo(11.8f, 12.0f)
                close()
            }
        }.build()
    }

    val Shuffle: ImageVector by lazy {
        ImageVector.Builder(
            name = "AkiraShuffle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Continuous stroke: bottom-left to top-right
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 17f)
                curveTo(7.5f, 17f, 10.5f, 13.5f, 13.5f, 10.5f)
                curveTo(15.5f, 8.5f, 17f, 7f, 20f, 7f)
            }
            // Top arrowhead pointing right
            path(
                fill = SolidColor(Color.White),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(16f, 4f)
                lineTo(20.5f, 7f)
                lineTo(16f, 10f)
                close()
            }

            // Crossing stroke with visible gap at intersection:
            // Segment A: top-left towards center (stops before intersection)
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 7f)
                curveTo(6.8f, 7f, 8.8f, 9.2f, 10.2f, 10.8f)
            }
            // Segment B: resumes after intersection towards bottom-right
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(13.8f, 14.8f)
                curveTo(15.2f, 16.2f, 17f, 17f, 20f, 17f)
            }
            // Bottom arrowhead pointing right
            path(
                fill = SolidColor(Color.White),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(16f, 14f)
                lineTo(20.5f, 17f)
                lineTo(16f, 20f)
                close()
            }
        }.build()
    }

    val Repeat: ImageVector by lazy {
        ImageVector.Builder(
            name = "AkiraRepeat",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Top loop going right
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 12f)
                lineTo(6f, 8.5f)
                curveTo(6f, 6.5f, 7.8f, 5f, 10f, 5f)
                lineTo(18.5f, 5f)
            }
            path(
                fill = SolidColor(Color.White),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(15.5f, 2.5f)
                lineTo(19.8f, 5f)
                lineTo(15.5f, 7.5f)
                close()
            }

            // Bottom loop going left
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(18f, 12f)
                lineTo(18f, 15.5f)
                curveTo(18f, 17.5f, 16.2f, 19f, 14f, 19f)
                lineTo(5.5f, 19f)
            }
            path(
                fill = SolidColor(Color.White),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(8.5f, 21.5f)
                lineTo(4.2f, 19f)
                lineTo(8.5f, 16.5f)
                close()
            }
        }.build()
    }

    val RepeatOne: ImageVector by lazy {
        ImageVector.Builder(
            name = "AkiraRepeatOne",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Top loop going right
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 12f)
                lineTo(6f, 8.5f)
                curveTo(6f, 6.5f, 7.8f, 5f, 10f, 5f)
                lineTo(18.5f, 5f)
            }
            path(
                fill = SolidColor(Color.White),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(15.5f, 2.5f)
                lineTo(19.8f, 5f)
                lineTo(15.5f, 7.5f)
                close()
            }

            // Bottom loop going left
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(18f, 12f)
                lineTo(18f, 15.5f)
                curveTo(18f, 17.5f, 16.2f, 19f, 14f, 19f)
                lineTo(5.5f, 19f)
            }
            path(
                fill = SolidColor(Color.White),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(8.5f, 21.5f)
                lineTo(4.2f, 19f)
                lineTo(8.5f, 16.5f)
                close()
            }

            // Legible centered numeral 1
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10.8f, 10.8f)
                lineTo(12.0f, 9.8f)
                lineTo(12.0f, 14.5f)
                moveTo(10.5f, 14.5f)
                lineTo(13.5f, 14.5f)
            }
        }.build()
    }

    val Autoplay: ImageVector by lazy {
        ImageVector.Builder(
            name = "AkiraAutoplay",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Continuous infinity curve (lemniscate)
                moveTo(12f, 12f)
                curveTo(9.5f, 8.5f, 5.5f, 8.5f, 5.5f, 12f)
                curveTo(5.5f, 15.5f, 9.5f, 15.5f, 12f, 12f)
                curveTo(14.5f, 8.5f, 18.5f, 8.5f, 18.5f, 12f)
                curveTo(18.5f, 15.5f, 14.5f, 15.5f, 12f, 12f)
                close()
            }
        }.build()
    }

    val Lyrics: ImageVector by lazy {
        ImageVector.Builder(
            name = "AkiraLyrics",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Speech bubble outline with rounded corners and tail
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(7.5f, 4.5f)
                lineTo(16.5f, 4.5f)
                curveTo(19.2f, 4.5f, 21.2f, 6.5f, 21.2f, 9.0f)
                lineTo(21.2f, 13.0f)
                curveTo(21.2f, 15.5f, 19.2f, 17.5f, 16.5f, 17.5f)
                lineTo(8.5f, 17.5f)
                lineTo(5.0f, 20.8f)
                curveTo(4.4f, 21.4f, 3.5f, 21.0f, 3.5f, 20.0f)
                lineTo(3.5f, 9.0f)
                curveTo(3.5f, 6.5f, 5.5f, 4.5f, 7.5f, 4.5f)
                close()
            }
            // Double quotation marks inside the bubble
            path(fill = SolidColor(Color.White)) {
                // First quote
                moveTo(8.8f, 8.8f)
                curveTo(7.9f, 8.8f, 7.2f, 9.5f, 7.2f, 10.4f)
                curveTo(7.2f, 11.3f, 7.9f, 12.0f, 8.8f, 12.0f)
                curveTo(9.4f, 12.0f, 9.8f, 11.6f, 10.1f, 11.1f)
                curveTo(10.1f, 12.4f, 9.1f, 13.5f, 7.8f, 13.8f)
                lineTo(8.1f, 14.6f)
                curveTo(10.1f, 14.1f, 11.1f, 12.4f, 11.1f, 10.4f)
                curveTo(11.1f, 9.5f, 10.1f, 8.8f, 8.8f, 8.8f)
                close()

                // Second quote
                moveTo(14.6f, 8.8f)
                curveTo(13.7f, 8.8f, 13.0f, 9.5f, 13.0f, 10.4f)
                curveTo(13.0f, 11.3f, 13.7f, 12.0f, 14.6f, 12.0f)
                curveTo(15.2f, 12.0f, 15.6f, 11.6f, 15.9f, 11.1f)
                curveTo(15.9f, 12.4f, 14.9f, 13.5f, 13.6f, 13.8f)
                lineTo(13.9f, 14.6f)
                curveTo(15.9f, 14.1f, 16.9f, 12.4f, 16.9f, 10.4f)
                curveTo(16.9f, 9.5f, 15.9f, 8.8f, 14.6f, 8.8f)
                close()
            }
        }.build()
    }

    val Info: ImageVector by lazy {
        ImageVector.Builder("AkiraInfo",24.dp,24.dp,24f,24f).apply {
            path(stroke=SolidColor(Color.White),strokeLineWidth=1.8f,strokeLineCap=StrokeCap.Round) {
                moveTo(10.5f,10f);lineTo(12f,10f);lineTo(12f,18f)
                moveTo(9.5f,18f);lineTo(14.5f,18f)
            }
            path(fill=SolidColor(Color.White)) {
                moveTo(13.3f,5.7f);curveTo(13.3f,7.4f,10.7f,7.4f,10.7f,5.7f)
                curveTo(10.7f,4f,13.3f,4f,13.3f,5.7f);close()
            }
        }.build()
    }
    // Original Akira vector: concentric transmission rings and a routing triangle.
    val Airplay: ImageVector by lazy {
        ImageVector.Builder("AkiraAudioRoute",24.dp,24.dp,24f,24f).apply {
            path(fill=SolidColor(Color.White)) {
                moveTo(12f,14f);lineTo(17f,21f);lineTo(7f,21f);close()
            }
            path(stroke=SolidColor(Color.White),strokeLineWidth=1.35f,strokeLineCap=StrokeCap.Round) {
                moveTo(6f,17f);curveTo(2.8f,14.1f,2.5f,9.3f,5.3f,6f)
                curveTo(8.8f,1.8f,15.2f,1.8f,18.7f,6f)
                curveTo(21.5f,9.3f,21.2f,14.1f,18f,17f)
                moveTo(8.3f,14.6f);curveTo(6.2f,12.8f,6.1f,9.8f,7.9f,7.8f)
                curveTo(10.1f,5.4f,13.9f,5.4f,16.1f,7.8f)
                curveTo(17.9f,9.8f,17.8f,12.8f,15.7f,14.6f)
                moveTo(10.4f,12.5f);curveTo(8.8f,10.8f,10.1f,8.7f,12f,8.7f)
                curveTo(13.9f,8.7f,15.2f,10.8f,13.6f,12.5f)
            }
        }.build()
    }

    val Queue: ImageVector by lazy {
        ImageVector.Builder(
            name = "AkiraQueue",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Three bullet dots
            path(fill = SolidColor(Color.White)) {
                // Dot 1
                moveTo(5.5f, 6.5f)
                curveTo(4.7f, 6.5f, 4.0f, 5.8f, 4.0f, 5.0f)
                curveTo(4.0f, 4.2f, 4.7f, 3.5f, 5.5f, 3.5f)
                curveTo(6.3f, 3.5f, 7.0f, 4.2f, 7.0f, 5.0f)
                curveTo(7.0f, 5.8f, 6.3f, 6.5f, 5.5f, 6.5f)
                close()

                // Dot 2
                moveTo(5.5f, 13.5f)
                curveTo(4.7f, 13.5f, 4.0f, 12.8f, 4.0f, 12.0f)
                curveTo(4.0f, 11.2f, 4.7f, 10.5f, 5.5f, 10.5f)
                curveTo(6.3f, 10.5f, 7.0f, 11.2f, 7.0f, 12.0f)
                curveTo(7.0f, 12.8f, 6.3f, 13.5f, 5.5f, 13.5f)
                close()

                // Dot 3
                moveTo(5.5f, 20.5f)
                curveTo(4.7f, 20.5f, 4.0f, 19.8f, 4.0f, 19.0f)
                curveTo(4.0f, 18.2f, 4.7f, 17.5f, 5.5f, 17.5f)
                curveTo(6.3f, 17.5f, 7.0f, 18.2f, 7.0f, 19.0f)
                curveTo(7.0f, 19.8f, 6.3f, 20.5f, 5.5f, 20.5f)
                close()
            }
            // Three horizontal lines
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(9.5f, 5.0f)
                lineTo(20.0f, 5.0f)
                moveTo(9.5f, 12.0f)
                lineTo(20.0f, 12.0f)
                moveTo(9.5f, 19.0f)
                lineTo(18.0f, 19.0f)
            }
        }.build()
    }

    val MoreHoriz: ImageVector by lazy {
        ImageVector.Builder(
            name = "AkiraMoreHoriz",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                // Punto izquierdo (centro 6,12 radio 2.3).
                moveTo(6f, 14.3f)
                curveTo(4.7f, 14.3f, 3.7f, 13.3f, 3.7f, 12f)
                curveTo(3.7f, 10.7f, 4.7f, 9.7f, 6f, 9.7f)
                curveTo(7.3f, 9.7f, 8.3f, 10.7f, 8.3f, 12f)
                curveTo(8.3f, 13.3f, 7.3f, 14.3f, 6f, 14.3f)
                close()

                // Punto central (centro 12,12 radio 2.3).
                moveTo(12f, 14.3f)
                curveTo(10.7f, 14.3f, 9.7f, 13.3f, 9.7f, 12f)
                curveTo(9.7f, 10.7f, 10.7f, 9.7f, 12f, 9.7f)
                curveTo(13.3f, 9.7f, 14.3f, 10.7f, 14.3f, 12f)
                curveTo(14.3f, 13.3f, 13.3f, 14.3f, 12f, 14.3f)
                close()

                // Punto derecho (centro 18,12 radio 2.3).
                moveTo(18f, 14.3f)
                curveTo(16.7f, 14.3f, 15.7f, 13.3f, 15.7f, 12f)
                curveTo(15.7f, 10.7f, 16.7f, 9.7f, 18f, 9.7f)
                curveTo(19.3f, 9.7f, 20.3f, 10.7f, 20.3f, 12f)
                curveTo(20.3f, 13.3f, 19.3f, 14.3f, 18f, 14.3f)
                close()
            }
        }.build()
    }

    val ReorderHandle: ImageVector by lazy {
        ImageVector.Builder(
            name = "AkiraReorderHandle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(4f, 7f)
                lineTo(20f, 7f)
                moveTo(4f, 12f)
                lineTo(20f, 12f)
                moveTo(4f, 17f)
                lineTo(20f, 17f)
            }
        }.build()
    }

    val Star: ImageVector by lazy {
        ImageVector.Builder(
            name = "AkiraStar",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3f)
                lineTo(14.8f, 8.8f)
                lineTo(21.2f, 9.7f)
                lineTo(16.6f, 14.2f)
                lineTo(17.7f, 20.6f)
                lineTo(12f, 17.6f)
                lineTo(6.3f, 20.6f)
                lineTo(7.4f, 14.2f)
                lineTo(2.8f, 9.7f)
                lineTo(9.2f, 8.8f)
                close()
            }
        }.build()
    }

    val StarFilled: ImageVector by lazy {
        ImageVector.Builder(
            name = "AkiraStarFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 3.2f)
                lineTo(14.7f, 8.7f)
                lineTo(20.8f, 9.6f)
                lineTo(16.4f, 13.9f)
                lineTo(17.4f, 20f)
                lineTo(12f, 17.2f)
                lineTo(6.6f, 20f)
                lineTo(7.6f, 13.9f)
                lineTo(3.2f, 9.6f)
                lineTo(9.3f, 8.7f)
                close()
            }
        }.build()
    }

    val Close: ImageVector by lazy {
        ImageVector.Builder(
            name = "AkiraClose",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(6f, 6f)
                lineTo(18f, 18f)
                moveTo(18f, 6f)
                lineTo(6f, 18f)
            }
        }.build()
    }

    val Trash: ImageVector by lazy {
        ImageVector.Builder(
            name = "AkiraTrash",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Top bar & handle
                moveTo(4f, 6f)
                lineTo(20f, 6f)
                moveTo(9f, 6f)
                curveTo(9f, 4.5f, 10f, 3.5f, 11.5f, 3.5f)
                lineTo(12.5f, 3.5f)
                curveTo(14f, 3.5f, 15f, 4.5f, 15f, 6f)

                // Can body
                moveTo(6f, 6f)
                lineTo(7f, 19f)
                curveTo(7.1f, 20.1f, 8f, 21f, 9.1f, 21f)
                lineTo(14.9f, 21f)
                curveTo(16f, 21f, 16.9f, 20.1f, 17f, 19f)
                lineTo(18f, 6f)

                // Inner ribs
                moveTo(10f, 10f)
                lineTo(10f, 17f)
                moveTo(14f, 10f)
                lineTo(14f, 17f)
            }
        }.build()
    }

    val Share: ImageVector by lazy {
        ImageVector.Builder(
            name = "AkiraShare",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 12f)
                lineTo(4f, 19f)
                curveTo(4f, 20.1f, 4.9f, 21f, 6f, 21f)
                lineTo(18f, 21f)
                curveTo(19.1f, 21f, 20f, 20.1f, 20f, 19f)
                lineTo(20f, 12f)

                moveTo(12f, 15f)
                lineTo(12f, 3.5f)
                moveTo(7.5f, 8f)
                lineTo(12f, 3.5f)
                lineTo(16.5f, 8f)
            }
        }.build()
    }

    val Album: ImageVector by lazy {
        ImageVector.Builder(
            name = "AkiraAlbum",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(12f, 3f)
                curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
                curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
                curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
                curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
                close()

                moveTo(12f, 10f)
                curveTo(13.1f, 10f, 14f, 10.9f, 14f, 12f)
                curveTo(14f, 13.1f, 13.1f, 14f, 12f, 14f)
                curveTo(10.9f, 14f, 10f, 13.1f, 10f, 12f)
                curveTo(10f, 10.9f, 10.9f, 10f, 12f, 10f)
                close()

                moveTo(7.5f, 12f)
                curveTo(7.5f, 9.51f, 9.51f, 7.5f, 12f, 7.5f)
            }
        }.build()
    }

    val Artist: ImageVector by lazy {
        ImageVector.Builder(
            name = "AkiraArtist",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 4f)
                curveTo(14.2f, 4f, 16f, 5.8f, 16f, 8f)
                curveTo(16f, 10.2f, 14.2f, 12f, 12f, 12f)
                curveTo(9.8f, 12f, 8f, 10.2f, 8f, 8f)
                curveTo(8f, 5.8f, 9.8f, 4f, 12f, 4f)
                close()

                moveTo(4.5f, 20f)
                curveTo(4.5f, 16.5f, 7.8f, 15f, 12f, 15f)
                curveTo(16.2f, 15f, 19.5f, 16.5f, 19.5f, 20f)
            }
        }.build()
    }

    /** Sleep-timer hourglass with a continuous rounded stroke, a 24x24 viewport and white fill. */
    val SleepTimer: ImageVector by lazy {
        ImageVector.Builder(
            name = "AkiraSleepTimer",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Clock outline.
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3.2f)
                curveTo(16.7f, 3.2f, 20.5f, 7.0f, 20.5f, 11.7f)
                curveTo(20.5f, 16.4f, 16.7f, 20.2f, 12f, 20.2f)
                curveTo(7.3f, 20.2f, 3.5f, 16.4f, 3.5f, 11.7f)
                curveTo(3.5f, 7.0f, 7.3f, 3.2f, 12f, 3.2f)
                close()
            }
            // Manecillas
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(12f, 7.6f)
                lineTo(12f, 12f)
                lineTo(15.4f, 14.2f)
            }
        }.build()
    }
}
