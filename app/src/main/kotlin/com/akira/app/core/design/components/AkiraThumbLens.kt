package com.akira.app.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalDensity
import com.akira.app.core.design.backdrop.Backdrop
import com.akira.app.core.design.backdrop.drawBackdrop
import com.akira.app.core.design.glass.AkiraThumbLensMaterial
import kotlin.math.roundToInt

/** Measured optics shared by the switch and slider; no scaled backdrop texture. */
@Composable
internal fun AkiraThumbLens(centerX:Float,centerY:Float,width:Float,height:Float,pressure:Float,
    backdrop:Backdrop,material:AkiraThumbLensMaterial,glass:Boolean=true) {
    val density=LocalDensity.current.density
    val face=Modifier.offset{IntOffset(((centerX-width/2)*density).roundToInt(),
        ((centerY-height/2)*density).roundToInt())}.size(width.dp,height.dp)
    if(!glass){Box(face.background(Color.White,CircleShape));return}
    Box(face.drawBackdrop(backdrop=backdrop,shape={CircleShape},
            effects={material.apply(this,pressure.coerceIn(0f,1f))},
            highlight={null},shadow=null,innerShadow=null,clipOutput=false,
            onDrawBackdrop={draw->if(pressure>.001f)draw()},
            onDrawSurface={drawRoundRect(Color.White.copy(alpha=if(material.failed)1f else 1-pressure.coerceIn(0f,1f)),
                cornerRadius=CornerRadius(size.minDimension/2))})
    )
}
