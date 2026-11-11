package com.akira.app.core.design.motion

import android.content.Context
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.akira.app.core.design.components.ContinuousMenuShape
import org.json.JSONObject
import kotlin.math.*

internal data class MorphElement(val center: Offset, val half: Size, val radius: Float) {
    val bounds get() = Rect(center.x-half.width, center.y-half.height, center.x+half.width, center.y+half.height)
}

internal data class SharedMorphFrame(val bounds: Rect, val elements: List<MorphElement>,
    val union: Float, val alpha: Float, val contentAlpha: Float, val sourceAlpha: Float,
    val rawBody:Rect?=null,val contentHeight:Float=0f,val contentRadius:Float=0f,
    val menuBlur:Float=0f,val seedCenter:Offset?=null,val seedBlur:Float=0f,val warp:FloatArray?=null,val light:FloatArray?=null,val shadow:FloatArray?=null,val style:Float?=null,val sourceFraction:Float=0f,val materialElements:List<MorphElement>?=null) {
    fun localElements() = elements.map { it.copy(center = it.center - bounds.topLeft) }
    fun localMaterialElements() = (materialElements ?: elements).map { it.copy(center = it.center - bounds.topLeft) }
    fun contentTransform(content: Size): PresentationContentTransform {
        rawBody?.let { raw ->
            return PresentationContentTransform(raw.width/content.width.coerceAtLeast(1f),raw.topLeft-bounds.topLeft,
                raw.height/contentHeight.coerceAtLeast(.001f))
        }
        val body = elements.first().bounds
        val scale = min(body.width / content.width.coerceAtLeast(1f), body.height / content.height.coerceAtLeast(1f)).coerceIn(.01f, 1f)
        return PresentationContentTransform(scale, body.topLeft - bounds.topLeft + Offset(
            (body.width-content.width*scale)/2f, (body.height-content.height*scale)/2f))
    }
}

/** One timeline for native menus, popovers and panels, exported from the font selector. */
internal class SharedPresentationMotion internal constructor(private val opening: List<Row>, private val closing: List<Row>) {
    internal data class Row(val time: Float, val points: List<FloatArray>, val radii: FloatArray,
        val count: Int, val union: Float, val alpha: Float, val content: Float, val seed: Float)
    fun durationMillis(dismissing: Boolean) = ((if (dismissing) closing else opening).last().time * 1000).roundToInt()
    fun openingExtent(fraction:Float):Float {
        val end=opening.last().points.first()[2]
        val start=opening.first().points.first()[2]
        val time=fraction.coerceIn(0f,1f)*opening.last().time
        val i=opening.indexOfFirst{it.time>=time}.let{if(it<0)opening.lastIndex else it}
        val a=opening[max(0,i-1)];val b=opening[i]
        val t=if(a===b)0f else (time-a.time)/(b.time-a.time)
        return ((a.points.first()[2]+(b.points.first()[2]-a.points.first()[2])*t-start)/(end-start)).coerceIn(0f,1.04f)
    }
    fun sample(fraction: Float, dismissing: Boolean, source: Rect, destination: Rect, radius: Float): SharedMorphFrame {
        val rows = if (dismissing) closing else opening
        val time = fraction.coerceIn(0f,1f) * rows.last().time
        val index = rows.indexOfFirst { it.time >= time }.let { if(it<0) rows.lastIndex else it }
        val a=rows[max(0,index-1)]; val b=rows[index]
        val t=if(a===b)0f else ((time-a.time)/(b.time-a.time)).coerceIn(0f,1f)
        fun mix(x:Float,y:Float)=x+(y-x)*t
        val seedRow=if(dismissing)closing.last() else opening.first()
        val targetRow=if(dismissing)closing.first() else opening.last()
        val seed=seedRow.points.first(); val target=targetRow.points.first()
        val sx=destination.width/(target[2]*2);val sy=destination.height/(target[3]*2)
        val p0=FloatArray(4){mix(a.points[0][it],b.points[0][it])}
        val expandX=((p0[2]-seed[2])/(target[2]-seed[2])).coerceIn(0f,1f)
        val expandY=if(dismissing)expandX else ((p0[3]-seed[3])/(target[3]-seed[3])).coerceIn(0f,1f)
        val flipX=if(source.center.x<destination.center.x)-1f else 1f
        val flipY=if(source.center.y<destination.center.y)-1f else 1f
        fun mapped(x:Float,y:Float)=destination.center+Offset((x-target[0])*sx*flipX,(y-target[1])*sy*flipY)
        val mappedSeed=mapped(seed[0],seed[1])
        val correction=source.center-mappedSeed
        val count=if(t<.5f)a.count else b.count
        val remaining=min(expandX,expandY)
        val elements=(0 until count).mapNotNull { i ->
            val p=FloatArray(4){mix(a.points[i][it],b.points[i][it])}
            if(p[2]<=.001f || p[3]<=.001f || (dismissing && i>0 && remaining<=.001f))null else {
                val detailScale=min(sx,sy)*(if(dismissing)remaining else 1f)
                val halfWidth=(if(i==0)p[2]*sx+(source.width/2-seed[2]*sx)*(1-expandX) else p[2]*detailScale).coerceAtLeast(.5f)
                val halfHeight=(if(i==0 && dismissing)source.height/2+(destination.height-source.height)/2*expandY
                    else if(i==0)p[3]*sy+(source.height/2-seed[3]*sy)*(1-expandY) else p[3]*detailScale).coerceAtLeast(.5f)
                val bodyCenter=mapped(p0[0],p0[1])+Offset(correction.x*(1-expandX),correction.y*(1-expandY))
                val center=if(i==0)bodyCenter else bodyCenter+Offset((p[0]-p0[0])*flipX,(p[1]-p0[1])*flipY)*detailScale
                val rr=mix(a.radii[i],b.radii[i]); val magnitude=if(i==0) {
                    val sourceRadius=min(source.width,source.height)/2
                    abs(rr)/32f*radius + (sourceRadius-abs(seedRow.radii.first())/32f*radius)*(1-min(expandX,expandY))
                } else abs(rr)*min(sx,sy)
                MorphElement(center,Size(halfWidth,halfHeight),sign(rr)*magnitude.coerceIn(0f,min(halfWidth,halfHeight)))
            }
        }.ifEmpty { listOf(MorphElement(source.center,Size(source.width/2,source.height/2),-min(source.width,source.height)/2)) }
        val union=mix(a.union,b.union)*min(sx,sy)*(if(dismissing)remaining else 1f)
        val pad=if(elements.size>1)abs(union)*.42f else 0f
        val bounds=Rect(elements.minOf{it.bounds.left}-pad,elements.minOf{it.bounds.top}-pad,
            elements.maxOf{it.bounds.right}+pad,elements.maxOf{it.bounds.bottom}+pad)
        // The source reappears while the returning shell fades, as in the font pane.
        val alpha=mix(a.alpha,b.alpha)
        val seedAlpha=if(dismissing && fraction>=1f)1f else mix(a.seed,b.seed)
        return SharedMorphFrame(bounds,elements,union,alpha,mix(a.content,b.content),seedAlpha)
    }
    companion object {
        fun load(context: Context): SharedPresentationMotion? = runCatching {
            val json=JSONObject(context.assets.open("visual-material/motion.json").bufferedReader().use{it.readText()})
            fun rows(key:String):List<Row> {
                val rows=json.getJSONArray(key)
                return (0 until rows.length()).map { i ->
                    val row=rows.getJSONObject(i);val points=row.getJSONArray("points");val radii=row.getJSONArray("radii");val shape=row.getJSONArray("shape")
                    Row(row.getDouble("time").toFloat(),(0 until 8).map{j->FloatArray(4){k->points.getJSONArray(j).getDouble(k).toFloat()}},
                        FloatArray(8){radii.getDouble(it).toFloat()},shape.getInt(0),shape.getDouble(1).toFloat(),shape.getDouble(2).toFloat(),
                        row.getDouble("contentAlpha").toFloat(),row.getDouble("sourceAlpha").toFloat())
                }
            }
            val closing=rows("closing")
            val lastVisible=closing.indexOfFirst { it.alpha<=.001f }.takeIf { it>=0 } ?: closing.lastIndex
            SharedPresentationMotion(rows("opening"),closing.take(lastVisible+1))
        }.getOrNull()
    }
}

private class MorphCorner(private val radius:()->Float):CornerSize {
    override fun toPx(shapeSize:Size,density:Density)=radius().coerceIn(0f,shapeSize.minDimension/2)
}

/** The shader, clip and shadow read the same multi-lobe outline, not a stretched rectangle. */
internal class SharedPresentationShape(private val frame:()->SharedMorphFrame?,private val radius:()->Float,
    private val density:Density):CornerBasedShape(MorphCorner(radius),MorphCorner(radius),MorphCorner(radius),MorphCorner(radius)) {
    private var cachedFrame:SharedMorphFrame?=null
    private var cachedSize=Size.Unspecified
    private var cached:Outline?=null
    fun currentFrame()=frame()
    override fun createOutline(size:Size,topStart:Float,topEnd:Float,bottomEnd:Float,bottomStart:Float,layoutDirection:LayoutDirection):Outline {
        val current=frame() ?: return ContinuousMenuShape(with(density){radius().toDp()},1f).createOutline(size,layoutDirection,density)
        if(cachedFrame===current && cachedSize==size)cached?.let{return it}
        val elements=current.localMaterialElements()
        if(elements.isEmpty())return Outline.Rectangle(Rect.Zero)
        val center=elements.first().center
        val extent=hypot(size.width,size.height)
        val path=Path()
        fun boundary(angle:Float):Offset {
            val axis=Offset(cos(angle),sin(angle))
            var low=0f;var high=extent
            repeat(20) {
                if (high-low > .02f) {
                    val mid=(low+high)/2
                    if(distance(center+axis*mid,elements,current.union)<=0f)low=mid else high=mid
                }
            }
            return center+axis*((low+high)/2)
        }
        fun subdivide(start:Float,from:Offset,end:Float,to:Offset,depth:Int) {
            val middle=(start+end)/2
            val point=boundary(middle)
            val chord=to-from
            val lengthSquared=chord.x*chord.x+chord.y*chord.y
            val t=if(lengthSquared>0f)(((point-from).x*chord.x+(point-from).y*chord.y)/lengthSquared).coerceIn(0f,1f) else 0f
            val error=(point-(from+chord*t)).getDistance()
            if(error>.12f && depth<10) {
                subdivide(start,from,middle,point,depth+1)
                subdivide(middle,point,end,to,depth+1)
            } else path.lineTo(to.x,to.y)
        }
        // Angular steps alone leave visible facets on tall, narrow menus.
        // Subdivide by physical-pixel error so the clip follows the optical SDF.
        val first=boundary(0f)
        path.moveTo(first.x,first.y)
        var previous=first
        for(i in 1..32) {
            val end=i*2f*PI.toFloat()/32
            val point=if(i==32)first else boundary(end)
            subdivide((i-1)*2f*PI.toFloat()/32,previous,end,point,0)
            previous=point
        }
        path.close()
        return Outline.Generic(path).also{cachedFrame=current;cachedSize=size;cached=it}
    }
    override fun copy(topStart:CornerSize,topEnd:CornerSize,bottomEnd:CornerSize,bottomStart:CornerSize)=this
    private fun distance(p:Offset,elements:List<MorphElement>,blend:Float):Float {
        fun sdf(e:MorphElement):Float {
            val q=p-e.center;val b=e.half;val r=abs(e.radius)
            val ox=abs(q.x)-b.width;val oy=abs(q.y)-b.height
            if(r<=.001f)return min(max(ox,oy),0f)+hypot(max(ox,0f),max(oy,0f))
            if(e.radius>=0)return min(max(ox+r,oy+r),0f)+hypot(max(ox+r,0f),max(oy+r,0f))-r
            val rr=r*1.52866495f
            val cx=(2.891557f-b.width*2*1.44577849f/rr).coerceIn(0f,1f)
            val cy=(2.891557f-b.height*2*1.44577849f/rr).coerceIn(0f,1f)
            val ex=ox+(rr+(r-rr)*max(cx,cy));val ey=oy+(rr+(r-rr)*max(cx,cy))
            val qx=max((ox+rr)/rr,0f);val qy=max((oy+rr)/rr,0f)
            val l=hypot(qx,qy);val hi=max(qx,qy);val t=if(hi==0f)0f else (min(qx,qy)/hi).coerceIn(0f,1f)
            val poly=((((-.926054f*t+3.1560099f)*t-3.64122f)*t+1.26803005f)*t)+.268531f
            val sup=l+1-1/(1-t*t*l.coerceIn(0f,1f)*poly)
            val circ=hypot(max(qx*1.52866495f-.528665f,0f),max(qy*1.52866495f-.528665f,0f))*.654165566f+.345834434f
            val sign=if(qy>qx)1f else -1f
            val a=sup+(circ-sup)*cx;val b2=sup+(circ-sup)*cy
            val g=a+(b2-a)*(.5f-sign+sign*t).coerceIn(0f,1f)
            return min(max(ex,ey),0f)+rr*(g-1)
        }
        var d=sdf(elements.first())
        for(i in 1 until elements.size) {
            val next=sdf(elements[i])
            d=if(blend>=-.001f)min(d,next)else {
                val width=-blend;val t=((next-d)/width).coerceIn(-5f,5f)
                val w=.5f+.5f*tanh(.797884583f*(t+.044715f*t*t*t))
                d*w+next*(1-w)-width*.398942292f*exp(-.5f*t*t)
            }
        }
        return d
    }
}
