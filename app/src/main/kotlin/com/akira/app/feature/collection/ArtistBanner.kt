// SPDX-License-Identifier: GPL-3.0-only
// Page ArtistDesign.kt bb28993: static local-artwork adapter for its feathered banner.
package com.akira.app.feature.collection
import android.graphics.Shader
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.layer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.akira.app.core.design.components.*
import com.akira.app.core.design.motion.*
import com.akira.app.core.design.navigation.*
import com.akira.app.core.design.rendering.*
import com.akira.app.feature.home.components.*
@Composable internal fun LocalArtistBanner(uri:String?,height:Dp,progress:()->Float,background:State<Color>,
    media: (@Composable BoxScope.() -> Unit)? = null) {
    val mediaLayer=rememberGraphicsLayer()
    val context=LocalContext.current
    val request=remember(uri,context) { coil.request.ImageRequest.Builder(context).data(uri)
        .placeholderMemoryCacheKey(uri?.let { com.akira.app.core.online.onlineArtworkUrl(it,512) }).build() }
    val blur=rememberCoverBlurAvailable()
    ScrollAwayCover(progress=progress,follow=-.8f,softFrom=.02f,modifier=Modifier.fillMaxWidth().height(height)) {
        Box(Modifier.fillMaxSize().then(if(blur) Modifier.drawWithContent {
            mediaLayer.record { this@drawWithContent.drawContent() };drawLayer(mediaLayer)
        } else Modifier)) {
            AsyncImage(request,null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
            media?.invoke(this)
        }
        if(blur) {
            BannerBlurBand(mediaLayer,true,.4f,22f)
            BannerBlurBand(mediaLayer,false,.55f,30f)
        }
        BannerScrims(background)
    }
}
@Composable
private fun BoxScope.BannerBlurBand(
    mediaLayer: GraphicsLayer,
    atTop: Boolean,
    heightFraction: Float,
    radius: Float,
) {
    val mask = remember(atTop) {
        if (atTop) {
            Brush.verticalGradient(0f to Color.Black, 0.35f to Color.Black.copy(alpha = 0.85f), 1f to Color.Transparent)
        } else {
            Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color.Black.copy(alpha = 0.85f), 1f to Color.Black)
        }
    }
    DownscaledBlur(
        radius = { BlurEffectCache.effectiveRadius(radius) },
        tileMode = Shader.TileMode.CLAMP,
        mask = mask,
        modifier = Modifier
            .align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight(heightFraction),
    ) { fullSize ->
        val dy = if (atTop) 0f else fullSize.height - mediaLayer.size.height.toFloat()
        translate(top = dy) { drawLayer(mediaLayer) }
    }
}

/** a little dim for the white text, darker under the status bar, and the foot sinking into the page */
@Composable
private fun BoxScope.BannerScrims(background: State<Color>) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .drawWithCache {
                val h = size.height
                val top = Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.5f),
                    1f to Color.Transparent,
                    endY = h * 0.3f,
                )
                onDrawBehind {
                    val bg = background.value
                    drawRect(Color.Black.copy(alpha = 0.14f))
                    drawRect(top, size = Size(size.width, h * 0.3f))
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.5f to bg.copy(alpha = 0.55f),
                            0.82f to bg.copy(alpha = 0.92f),
                            1f to bg,
                            startY = h * 0.42f,
                            endY = h,
                        ),
                        topLeft = Offset(0f, h * 0.42f),
                        size = Size(size.width, h * 0.58f),
                    )
                }
            }
    )
}
