package com.akira.app.feature.player

import com.akira.app.core.artwork.*
import com.akira.app.feature.library.*
import com.akira.app.feature.lyrics.*
import com.akira.app.feature.queue.*

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.roundToInt

internal const val FullscreenArtworkHeightRatio = 1.54f

internal data class PlayerArtworkColors(val primary: Color, val left: Color = primary, val right: Color = primary,
    val accents: List<Color> = listOf(left, right, primary))

internal object PlayerArtworkPaletteCache : android.util.LruCache<String,PlayerArtworkColors>(128)

/** Select a populated colour family in the lower sleeve, rather than averaging
 * unrelated skin, lettering and background colours into grey. Neutral families
 * retain their actual luminance; sparse bright highlights cannot lift a dark cover.
 * Runs on the existing artwork IO path, never during drawing.
 */
internal fun artworkAmbientColors(bitmap: android.graphics.Bitmap, fallback: Color): PlayerArtworkColors {
    if(bitmap.hasAlpha()) {
        val pixels=IntArray(bitmap.width*bitmap.height)
        bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        if(pixels.none {android.graphics.Color.alpha(it)>=128})return PlayerArtworkColors(fallback)
    }
    fun sample(startX: Float, endX: Float): Color {
        val weights = DoubleArray(16)
        val red = DoubleArray(16); val green = DoubleArray(16); val blue = DoubleArray(16)
        val hsv = FloatArray(3)
        val left = (bitmap.width * startX).toInt()
        val right = (bitmap.width * endX).toInt().coerceAtLeast(left + 1)
        for (y in (bitmap.height * .5f).toInt() until bitmap.height step 2) {
            for (x in left until minOf(right, bitmap.width) step 2) {
                val pixel = bitmap.getPixel(x, y)
                if (android.graphics.Color.alpha(pixel) < 128) continue
                android.graphics.Color.colorToHSV(pixel, hsv)
                val neutral = hsv[1] < .16f || hsv[2] < .07f
                val family = if (neutral) 12 + (hsv[2] * 4).toInt().coerceIn(0, 3)
                    else (hsv[0] / 30).toInt().coerceIn(0, 11)
                val edge = kotlin.math.abs(x.toFloat() / bitmap.width - .5f) * 2f
                val lower = y.toFloat() / bitmap.height
                val weight = (.7 + 1.5 * lower + 1.1 * edge) * (if (neutral) 1.0 else 1.2)
                weights[family] += weight
                red[family] += android.graphics.Color.red(pixel) * weight
                green[family] += android.graphics.Color.green(pixel) * weight
                blue[family] += android.graphics.Color.blue(pixel) * weight
            }
        }
        val winner = weights.indices.maxByOrNull { i ->
            weights[i] + if (i < 12) .25 * (weights[(i+11)%12] + weights[(i+1)%12]) else 0.0
        } ?: return fallback
        val weight = weights[winner]
        if (weight < 1.0) return fallback
        return Color(android.graphics.Color.rgb((red[winner]/weight).toInt(),
            (green[winner]/weight).toInt(), (blue[winner]/weight).toInt()))
    }
    val primary = sample(0f, 1f)
    // Local variation stays subordinate to the same dominant colour family.
    fun local(color: Color): Color {
        val main = FloatArray(3); val other = FloatArray(3)
        android.graphics.Color.colorToHSV(primary.toArgb(), main)
        android.graphics.Color.colorToHSV(color.toArgb(), other)
        val hueDistance = kotlin.math.abs(main[0]-other[0]).let { minOf(it, 360f-it) }
        return if (main[1] > .2f && (other[1] < .16f || hueDistance > 45f)) primary
            else androidx.compose.ui.graphics.lerp(primary, color, .18f)
    }
    // Read the whole sleeve as well as its lower edge. Keep black/white in the
    // quantisation so a tiny bright logo cannot turn a dark photograph neon.
    val swatches=androidx.palette.graphics.Palette.from(bitmap).clearFilters().maximumColorCount(24).generate()
        .swatches.sortedByDescending { it.population }
    val total=swatches.sumOf {it.population}.coerceAtLeast(1)
    val colors=mutableListOf<Color>()
    for(swatch in swatches) {
        if(swatch.population.toFloat()/total < .045f) continue
        val color=Color(swatch.rgb)
        if(colors.none { existing ->
            val dr=existing.red-color.red; val dg=existing.green-color.green; val db=existing.blue-color.blue
            dr*dr+dg*dg+db*db < .065f
        })colors.add(color)
        if(colors.size==5)break
    }
    return PlayerArtworkColors(primary, local(sample(0f,.36f)), local(sample(.64f,1f)),
        colors.ifEmpty {listOf(primary)})
}

internal fun playerArtworkBase(primary: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(primary.toArgb(), hsv)
    val neutral = 1f-hsv[1]
    // Saturated sleeves keep depth and hue; dark neutrals stay dark, creams
    // stay light. No minimum-grey floor and no global saturation reduction.
    val neutralGain=.40f+.32f*hsv[2]
    val chromaticGain=(1.35f-1.05f*hsv[2]).coerceIn(.42f,1f)
    hsv[2] = (hsv[2]*(neutralGain*neutral+chromaticGain*(1f-neutral))).coerceIn(.015f,.88f)
    hsv[1] = (hsv[1]*1.04f).coerceAtMost(1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** Reveal the actual spatial backdrop through the lower cover. Masking the image
 * instead of painting a second opaque colour avoids a rectangular seam where
 * the differently shaded backdrop meets the artwork. Foreground controls are
 * outside this isolated layer and remain sharp. */
@androidx.compose.runtime.Composable
internal fun Modifier.playerArtworkFade(fullWidthPx: Float,
    strength: () -> Float = { 1f }): Modifier {
    // The travelling cover changes size every frame; compile once per owner,
    // outside drawWithCache's size-dependent block.
    val shader=androidx.compose.runtime.remember {
        if(android.os.Build.VERSION.SDK_INT>=33)android.graphics.RuntimeShader(ArtworkMaskShader) else null
    }
    return graphicsLayer {
        compositingStrategy=androidx.compose.ui.graphics.CompositingStrategy.Offscreen
    }.drawWithCache {
        var cachedStrength=-1f
        var mask=Brush.verticalGradient(listOf(Color.Black,Color.Black))
        val curvedMask=if(android.os.Build.VERSION.SDK_INT>=33 && shader!=null) {
            shader.setFloatUniform("dimensions",size.width.coerceAtLeast(1f),size.height.coerceAtLeast(1f))
            androidx.compose.ui.graphics.ShaderBrush(shader)
        } else null
        onDrawWithContent {
            drawContent()
            // DstIn's source-over-equivalent for strength 0..1: interpolate
            // the mask alpha towards opaque without ever fading the image twice.
            val cover=((size.width/fullWidthPx.coerceAtLeast(1f)-.24f)/.76f).coerceIn(0f,1f)
            val amount=strength().coerceIn(0f,1f)*cover*cover*(3f-2f*cover)
            if(amount>0f) {
                if(android.os.Build.VERSION.SDK_INT>=33 && shader!=null && curvedMask!=null) {
                    shader.setFloatUniform("amount",amount)
                    drawRect(curvedMask,blendMode=androidx.compose.ui.graphics.BlendMode.DstIn)
                } else {
                if(amount != cachedStrength) {
                    cachedStrength=amount
                    mask=Brush.verticalGradient(0f to Color.Black, .53f to Color.Black,
                        .62f to Color.Black.copy(alpha=1f-.16f*amount),
                        .71f to Color.Black.copy(alpha=1f-.50f*amount),
                        .80f to Color.Black.copy(alpha=1f-.84f*amount),
                        .89f to Color.Black.copy(alpha=1f-amount),
                        1f to Color.Black.copy(alpha=1f-amount))
                }
                drawRect(mask, blendMode=androidx.compose.ui.graphics.BlendMode.DstIn)
                }
            }
        }
    }
}

private const val ArtworkMaskShader="""
uniform float2 dimensions;
uniform float amount;
half4 main(float2 position) {
    float2 uv=position/dimensions;
    float x=uv.x*2.0-1.0;
    float t=clamp((uv.y-0.53+0.045*x*x)/0.36,0.0,1.0);
    float alpha=1.0-amount*t*t*(3.0-2.0*t);
    return half4(alpha);
}
"""

internal fun Modifier.playerArtworkBackdrop(base: Color, left: Color = base, right: Color = base, opacity: () -> Float = { 1f }): Modifier = drawWithCache {
    val color=Brush.horizontalGradient(0f to left,.5f to base,1f to right)
    val shade=Brush.verticalGradient(0f to Color.Transparent,.42f to Color.Transparent,
        .70f to Color.Black.copy(alpha=.04f),.85f to Color.Black.copy(alpha=.10f),1f to Color.Black.copy(alpha=.18f))
    onDrawBehind { val alpha = opacity().coerceIn(0f,1f); drawRect(color, alpha=alpha);drawRect(shade, alpha=alpha) }
}

/** All three player states decode the same cover size; a thumbnail never replaces
 * the full-size bitmap halfway through the shared movement. */
@androidx.compose.runtime.Composable
internal fun rememberPlayerArtworkRequest(uri: String?): coil.request.ImageRequest {
    val context=androidx.compose.ui.platform.LocalContext.current
    val config=androidx.compose.ui.platform.LocalConfiguration.current
    val density=androidx.compose.ui.platform.LocalDensity.current
    val width=(config.screenWidthDp*density.density).roundToInt().coerceAtLeast(1)
    return androidx.compose.runtime.remember(uri,context,width) {
        val key = "akira-player-art:v2:$width:$uri"
        coil.request.ImageRequest.Builder(context).data(uri).size(width)
            .memoryCacheKey(key).placeholderMemoryCacheKey(key).crossfade(false).build()
    }
}
