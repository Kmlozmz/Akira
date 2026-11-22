package com.akira.app.feature.player

import com.akira.app.core.artwork.*
import com.akira.app.feature.library.*
import com.akira.app.feature.lyrics.*
import com.akira.app.feature.queue.*

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.cos
import kotlin.math.sin

/** Draws the player's ambient background from the current artwork palette. */
@Composable
internal fun rememberAmbientPhase(moving: Boolean): State<Float> {
    val reduced=com.akira.app.core.design.components.LocalContentMotionReduced.current
    val owner=LocalLifecycleOwner.current
    val phase=remember {mutableFloatStateOf(0f)}
    LaunchedEffect(owner,moving,reduced) {
        if(!moving || reduced)return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last=0L
            while(true)withFrameNanos { now ->
                if(last!=0L)phase.floatValue += ((now-last)/1_000_000_000f).coerceAtMost(.1f)*.48f
                last=now
            }
        }
    }
    return phase
}

@Composable
internal fun Modifier.playerDynamicBackdrop(colors: PlayerArtworkColors, moving: Boolean,
    opacity: () -> Float = { 1f }, subdued: Boolean = false): Modifier {
    val reduced=com.akira.app.core.design.components.LocalContentMotionReduced.current
    val context=androidx.compose.ui.platform.LocalContext.current.applicationContext
    val shader=remember(context) {
        if(android.os.Build.VERSION.SDK_INT>=33)runCatching {
            AmbientArtworkShader(context.assets.open("shaders/player_ambient.agsl").bufferedReader().use{it.readText()})
        }.getOrNull() else null
    }
    val phase=rememberAmbientPhase(moving)
    val duration=if(reduced)0 else 750
    val subduedMix = androidx.compose.animation.core.animateFloatAsState(if (subdued) 1f else 0f,
        tween(if (reduced) 0 else 270), label = "playerBackdropDepth")
    val base=animateColorAsState(playerArtworkBase(colors.primary),tween(duration),label="ambientBase")
    val palette=(0..4).map { i ->
        val source=colors.accents.getOrNull(i % colors.accents.size.coerceAtLeast(1)) ?: colors.primary
        // Bound contrast while preserving each actual hue. Warm whites stay
        // neutral, dark greys stay dark, secondary colours remain visible.
        val target=lerp(playerArtworkBase(colors.primary),playerArtworkBase(source),.72f)
        animateColorAsState(target,tween(duration),label="ambient$i")
    }
    return drawWithCache {
        val shade = Brush.verticalGradient(listOf(Color.Black.copy(alpha = .03f), Color.Black.copy(alpha = .12f)))
        val depth = Brush.verticalGradient(
            0f to Color(0xFF17181E).copy(alpha = .11f),
            .45f to Color(0xFF191B24).copy(alpha = .37f),
            1f to Color(0xFF172028).copy(alpha = .82f),
        )
        onDrawBehind {
            val alpha=opacity().coerceIn(0f,1f)
            val t=phase.value
            if(shader!=null && android.os.Build.VERSION.SDK_INT>=33) {
                shader.update(size,t,base.value,palette)
                drawRect(shader.brush,alpha=alpha)
            } else {
                drawRect(base.value, alpha = alpha)
                palette.forEachIndexed { i, color ->
                val angle=t*(.65f+i*.13f)+i*1.27f
                val center=Offset(size.width*(.45f+.36f*cos(angle)),size.height*(.46f+.34f*sin(angle*.91f+i)))
                val radius=size.maxDimension*(.38f+(i%2)*.1f)
                    drawRect(Brush.radialGradient(listOf(color.value.copy(alpha=.66f),color.value.copy(alpha=0f)),
                        center,radius),alpha=alpha)
                }
                drawRect(shade, alpha = alpha)
            }
            if (subduedMix.value > 0f) drawRect(depth, alpha = alpha * subduedMix.value)
        }
    }
}

@Suppress("UNUSED_PARAMETER")
internal fun useMotionArtwork(fullscreen: Boolean, lyrics: Boolean, queue: Boolean, landscape: Boolean,
    enabled: Boolean): Boolean = fullscreen && enabled && !landscape
