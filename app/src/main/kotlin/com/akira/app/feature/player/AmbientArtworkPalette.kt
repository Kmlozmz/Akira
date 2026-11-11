package com.akira.app.feature.player

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/** Merge small swatches by hue so fine artwork patterns are not discarded. */
internal fun ambientArtworkAccents(pixels: IntArray): List<Color> {
    val count=IntArray(16);val red=LongArray(16);val green=LongArray(16);val blue=LongArray(16)
    for(pixel in pixels) {
        if((pixel ushr 24)<128)continue
        val r=(pixel ushr 16 and 255);val g=(pixel ushr 8 and 255);val b=pixel and 255
        val high=maxOf(r,g,b);val low=minOf(r,g,b);val delta=high-low
        val saturation=if(high==0)0f else delta.toFloat()/high
        val bin=if(saturation<.16f || high<18)12+(high*4/256).coerceIn(0,3) else {
            val sector=when(high) {
                r -> (g-b).toFloat()/delta
                g -> (b-r).toFloat()/delta+2f
                else -> (r-g).toFloat()/delta+4f
            }
            (((sector+6f)%6f)*2).toInt().coerceIn(0,11)
        }
        count[bin]++;red[bin]+=r;green[bin]+=g;blue[bin]+=b
    }
    val total=count.sum().coerceAtLeast(1)
    val chosen=mutableListOf<Int>()
    for(bin in count.indices.sortedByDescending {count[it]}) {
        if(count[bin].toFloat()/total<.025f)continue
        if(bin<12 && chosen.any{it<12 && minOf(abs(bin-it),12-abs(bin-it))<=1})continue
        chosen.add(bin)
        if(chosen.size==5)break
    }
    return chosen.map {i -> Color((red[i]/count[i]).toInt(),(green[i]/count[i]).toInt(),(blue[i]/count[i]).toInt())}
}
