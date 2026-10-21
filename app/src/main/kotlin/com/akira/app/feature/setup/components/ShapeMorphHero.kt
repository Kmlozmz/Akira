package com.akira.app.feature.setup.components

import android.graphics.Matrix
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import com.akira.app.R
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** Morphs between cached polygons while keeping the application symbol centered. */
@Composable
fun ShapeMorphHero(
    modifier: Modifier = Modifier,
    shapeColor: Color = Color(0xFF16161C), // Deep graphite M3 shape
    logoTint: Color = Color.White,
    showLogo: Boolean = true
) {
    val shapes = remember { M3MorphShapes.shapeSequence }
    val morphs = remember(shapes) {
        List(shapes.size) { i ->
            Morph(shapes[i], shapes[(i + 1) % shapes.size])
        }
    }

    // Continuous progress tracker across all shapes (0f -> 1f -> 2f -> 3f -> 4f -> 5f -> ...)
    val continuousProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        var currentTarget = 0
        while (true) {
            currentTarget++
            continuousProgress.animateTo(
                targetValue = currentTarget.toFloat(),
                animationSpec = tween(
                    durationMillis = 1400,
                    easing = FastOutSlowInEasing
                )
            )
            // Resting hold at destination shape (450ms)
            delay(450)
        }
    }

    val currentTotal = continuousProgress.value
    val totalFloor = currentTotal.toInt()
    val stepIndex = totalFloor % shapes.size
    val fraction = (currentTotal - totalFloor).coerceIn(0f, 1f)

    // Volume breathing: gentle subtle pulse during deformation (peaks at 1.04x at mid-morph)
    val scaleFactor = 1.0f + 0.045f * sin(fraction * PI.toFloat())

    // Coordinated angular rotation: continuous accumulation of +72° per morph
    val rotationDegrees = currentTotal * 72f

    val activeMorph = morphs[stepIndex]

    Box(
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(maxWidth = 260.dp, maxHeight = 260.dp)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            val canvasSize = size.minDimension
            val centerOffset = Offset(size.width / 2f, size.height / 2f)

            // Pure morphing polygon with rotation, scaling, and translation
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees, 0f, 0f)
            val effectiveScale = (canvasSize * 0.36f) * scaleFactor
            matrix.postScale(effectiveScale, effectiveScale)
            matrix.postTranslate(centerOffset.x, centerOffset.y)

            val androidPath = activeMorph.toPath(progress = fraction)
            androidPath.transform(matrix)

            drawPath(
                path = androidPath.asComposePath(),
                color = shapeColor
            )
        }

        // 3. Official Akira Logo Mark centered directly in the hero stage
        if (showLogo) {
            Icon(
                painter = painterResource(id = R.drawable.ic_akira_logo),
                contentDescription = "Akira Logo",
                tint = logoTint,
                modifier = Modifier.size(106.dp)
            )
        }
    }
}
