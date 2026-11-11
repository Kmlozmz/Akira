package com.akira.app.core.design.motion

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.akira.app.core.design.system.AkiraContactSpec as Spec
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

internal data class GlassContactTarget(val scale:Offset,val offset:Offset)

/** A whole-surface affine response, shared with Appearance's contact model. */
internal fun glassContactTarget(size:Size,position:Offset,down:Offset,held:Boolean,
    navigationButton:Boolean=false):GlassContactTarget {
    if(!held)return GlassContactTarget(Offset(1f,1f),Offset.Zero)
    val delta=position-down
    val bounded=Offset(position.x.coerceIn(0f,size.width),position.y.coerceIn(0f,size.height))-down
    val resistance=if(navigationButton)Spec.buttonResistance else Spec.paneResistance
    fun pull(a:Float,b:Float)=abs(b)/(4*resistance)+(abs(a)-abs(b))/resistance
    val gx=pull(delta.x,bounded.x);val gy=pull(delta.y,bounded.y)
    val extent=if(navigationButton)min(size.width,size.height)else max(size.width,size.height)
    val base=1f+(if(navigationButton)Spec.buttonMargin else Spec.paneMargin)/extent.coerceAtLeast(1f)
    return GlassContactTarget(Offset(base+gx-gy,base-gx+gy),
        Offset(size.width*gx*sign(delta.x),size.height*gy*sign(delta.y)))
}
