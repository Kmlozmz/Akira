package com.akira.app.core.design.motion

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.*

/** The approved font selector's scheduled springs, evaluated in logical pixels. */
internal class FontPresentationActor(val menu: Rect, val anchor: Rect, val density: Float = 1f, private val expandedRadius:Double = 32.0, private val directReturn:Boolean=false, private val sourceSurface:Boolean=false, private val sourceMaterial:Boolean=true) {
    private class Axis(var position: Double) {
        var target=position; var velocity=0.0; var period=.35; var damping=.75
        var mask=0; var lastTarget=Double.NaN
        fun step(dt:Double, thresholds:DoubleArray?=null):Boolean {
            if(thresholds!=null && lastTarget!=target){lastTarget=target;mask=0}
            if(position==target && velocity==0.0)return false
            val omega=2*PI/period; val displacement=position-target
            val y:Double; val v:Double
            if(damping<1){
                val wd=omega*sqrt(1-damping*damping); val decay=exp(-damping*omega*dt)
                val c=cos(wd*dt);val s=sin(wd*dt);val k=(velocity+damping*omega*displacement)/wd
                y=decay*(displacement*c+k*s);v=decay*(velocity*c-(damping*omega*k+displacement*wd)*s)
            } else if(damping==1.0){
                val decay=exp(-omega*dt);val k=velocity+omega*displacement
                y=decay*(displacement+k*dt);v=decay*(k-omega*(displacement+k*dt))
            } else {
                val h=omega*sqrt(damping*damping-1);val r1=-damping*omega+h;val r2=-damping*omega-h
                val c2=(velocity-r1*displacement)/(r2-r1);val c1=displacement-c2
                y=c1*exp(r1*dt)+c2*exp(r2*dt);v=r1*c1*exp(r1*dt)+r2*c2*exp(r2*dt)
            }
            position=target+y;velocity=v
            if(thresholds==null){if(abs(y)<.0001 && abs(v)<.001){position=target;velocity=0.0;return false};return true}
            val error=hypot(target-position,damping*period/PI*velocity)
            thresholds.forEachIndexed{i,t->if(error<=t)mask=mask or (1 shl i)}
            if(mask and ((1 shl thresholds.size)-1)==((1 shl thresholds.size)-1)){position=target;velocity=0.0;return false}
            return true
        }
        fun error(coefficient:Double=period*damping/PI)=hypot(target-position,velocity*coefficient)
    }
    private val order=listOf(0x4b,0x4f,0x53,0x5f,0x63,0x77,0x7f,0x87,0x8f,0x6f,0x73)
    private val thresholds=mapOf(0x4b to doubleArrayOf(.001,4.273504273504274e-6),
        0x4f to doubleArrayOf(1.0/3),0x53 to doubleArrayOf(.001),0x5f to doubleArrayOf(.001),
        0x63 to doubleArrayOf(.001),0x77 to doubleArrayOf(1.0/3,.006153846153846155),
        0x7f to doubleArrayOf(1.0/3),0x87 to doubleArrayOf(.05),0x8f to doubleArrayOf(.05),
        0x6f to doubleArrayOf(.01),0x73 to doubleArrayOf(.01))
    private val axes=(order+0x97).associateWith { Axis(when(it){0x4f->menu.width/2.0;0x53->anchor.width.toDouble();0x73->1.0;0x8f->20.0;else->0.0}) }
    private val queue=mutableListOf<Pair<Double,()->Unit>>()
    private var sharedMask=0;private var sharedTarget=Double.NaN
    var elapsed=0.0;private set
    var closing=false;private set
    var collapsed=false;private set
    fun value(field:Int)=axes.getValue(field).position
    private fun drive(field:Int,target:Double,period:Double,damping:Double){axes.getValue(field).apply{this.target=target;this.period=period;this.damping=damping}}
    private fun later(delay:Double,action:()->Unit){queue.add(elapsed+delay to action)}
    private val arc get()=80*((menu.height-60)/140.0).coerceIn(.5,1.0)
    fun open(){
        closing=false;collapsed=false
        drive(0x4b,1.0,.35,.75);drive(0x4f,menu.width.toDouble(),.35,.75)
        later(.07){drive(0x4f,expandedRadius,.35,.75)};drive(0x53,anchor.width.toDouble(),.21,1.0)
        drive(0x5f,-arc,.14,1.0);later(.07){drive(0x5f,0.0,.35,.75)}
        drive(0x6f,1.0,.2,1.0);drive(0x73,1.0,.35,.85)
        drive(0x77,4.0,.21,1.0);later(.084){drive(0x77,0.0,.315,.8)}
        drive(0x7f,20.0,.07,1.0);later(.21){drive(0x7f,0.0,.42,1.0)}
        for(f in listOf(0x87,0x8f))drive(f,min(menu.width,menu.height)*.3,.35,.75)
        later(.084){for(f in listOf(0x87,0x8f))drive(f,0.0,.42,1.0)}
    }
    fun close(){
        if(closing)return
        closing=true;elapsed=0.0;queue.clear()
        drive(0x4b,0.0,.49,.8);drive(0x4f,menu.width/2.0,.49,.8);drive(0x53,anchor.width.toDouble(),.49,.8)
        drive(0x5f,-arc,.14,1.0);later(.07){drive(0x5f,0.0,.49,.8)}
        drive(0x63,arc/2,.49,.8);later(.105){drive(0x63,0.0,.49,.8)}
        drive(0x6f,0.0,.2,1.0);drive(0x73,0.0,.35,.85)
        drive(0x77,4.0,.21,1.0);later(.07){drive(0x77,0.0,.315,.8)}
        drive(0x7f,20.0,.07,1.0);later(.07){drive(0x7f,0.0,.42,1.0)}
        for(f in listOf(0x87,0x8f))drive(f,min(anchor.width,anchor.height)*.3,.49,.8)
        later(.07){for(f in listOf(0x87,0x8f))drive(f,0.0,.28,1.0)}
        drive(0x97,1.0,.49,.8)
    }
    fun step(seconds:Double):Boolean {
        var remaining=min(seconds,.25);var active=false
        while(remaining>1e-9){
            val dt=min(remaining,1.0/240);remaining-=dt;elapsed+=dt
            var i=0;while(i<queue.size){if(queue[i].first<=elapsed)queue.removeAt(i).second() else i++}
            for(field in order){
                val axis=axes.getValue(field);val shared=field!=0x4b && field!=0x77
                if(shared){axis.mask=sharedMask;axis.lastTarget=sharedTarget}
                active=axis.step(dt,thresholds.getValue(field)) || active
                if(shared){sharedMask=axis.mask;sharedTarget=axis.lastTarget}
            }
            active=axes.getValue(0x97).step(dt) || active
        }
        if(closing && directReturn && value(0x4b)<=0)collapsed=true
        else if(closing && queue.isEmpty() && settled())collapsed=true
        return active || queue.isNotEmpty()
    }
    private fun settled():Boolean {
        val t=1/density.toDouble()
        fun e(f:Int,c:Double?=null)=axes.getValue(f).let{it.error(c ?: it.period*it.damping/PI)}
        return e(0x4b)<=4.273504273504274e-6*t/.001 && e(0x4f)<=t && e(0x53)<=t && e(0x5f)<=t &&
            e(0x63,.12477747538404593)<=t && e(0x77)<=1.0/3 && e(0x7f)<=t && e(0x87)<=.05 && e(0x8f)<=.05 &&
            e(0x6f,.06366197723675814)<=.01 && e(0x73,.09469719113967773)<=.01 && e(0x97,.12477747538404593)<=.002
    }
    private fun progress(bit:Int)=axes.getValue(0x4b).let{if(it.mask and bit!=0)it.target else it.position}
    private fun mix(a:Double,b:Double,p:Double)=a*(1-p)+b*p
    private fun rect(center:Offset,w:Double,h:Double)=Rect(center.x-w.toFloat()/2,center.y-h.toFloat()/2,center.x+w.toFloat()/2,center.y+h.toFloat()/2)
    fun frame():SharedMorphFrame {
        if(collapsed && directReturn) {
            val end=Rect(anchor.left*density,anchor.top*density,anchor.right*density,anchor.bottom*density)
            return SharedMorphFrame(end,listOf(MorphElement(end.center,Size(end.width/2,end.height/2),-end.size.minDimension/2)),
                0f,if(sourceSurface)1f else 0f,0f,1f,seedCenter=end.center,sourceFraction=if(sourceSurface)1f else 0f,materialElements=if(sourceMaterial)null else emptyList())
        }
        val g=progress(2).let{if(directReturn)it.coerceIn(0.0,1.0)else it}
        val p=value(0x4b).coerceIn(0.0,1.0);val effective=progress(1).coerceIn(0.0,1.0)
        val fg=value(0x6f).coerceIn(0.0,1.0);val extra=value(0x73).coerceIn(0.0,1.0)
        val controlReturn=directReturn && closing
        val bodyScale=if(controlReturn)mix(anchor.width.toDouble()/menu.width,1.0,g)
            else max(0.0,mix(anchor.height/(2.0*max(menu.width,menu.height)),1.0,g))
        val viewportHeight=if(controlReturn)mix(anchor.height.toDouble(),menu.height.toDouble(),g)/bodyScale.coerceAtLeast(.001)
            else max(0.0,mix(menu.width.toDouble(),menu.height.toDouble(),g))
        val seedScale=max(0.0,mix(1.0,.25,g))
        val center=anchor.center+(menu.center-anchor.center)*g.toFloat()
        val raw=if(collapsed)anchor else rect(center+Offset(0f,(value(0x5f)*bodyScale*(if(controlReturn)g else 1.0)).toFloat()),menu.width*bodyScale,viewportHeight*bodyScale)
        val seedCenter=anchor.center+(menu.center-anchor.center)*(.25*g).toFloat()+Offset(0f,((if(directReturn)0.0 else value(0x63)*seedScale)).toFloat())
        val seed=if(collapsed)anchor else rect(seedCenter,max(value(0x53),0.0)*seedScale,anchor.height*seedScale)
        val inputRadius=if(controlReturn)mix(min(anchor.width,anchor.height)/2.0,expandedRadius,g)
            else min(max(value(0x4f),0.0),min(menu.width.toDouble(),mix(menu.width.toDouble(),menu.height.toDouble(),g))/2)*bodyScale
        val sigma=max(value(0x7f),0.0)
        val bodyElement=OpticalMenuGeometry.convert(raw,inputRadius,sigma)?.let {
            MorphElement(it.first.center,Size(it.first.width/2,it.first.height/2),-it.second.toFloat())
        }
        val elements=buildList {
            bodyElement?.let { add(it) }
            OpticalMenuGeometry.convert(seed,if(sourceSurface)min(anchor.width,anchor.height)/2.0 else 0.0,sigma)?.let{add(MorphElement(it.first.center,Size(it.first.width/2,it.first.height/2),(if(sourceSurface)-it.second else it.second).toFloat()))}
        }
        val alpha=(((1-fg)+fg*extra)*p).coerceIn(0.0,1.0)
        fun smooth(value:Double)=value.coerceIn(0.0,1.0).let{it*it*(3-2*it)}
        val menuAlpha=if(controlReturn)smooth((p-.08)/.92) else effective*(if(closing)fg*extra else 1.0)
        val seedAlpha=if(controlReturn)1-smooth(p/.28) else if(collapsed)1.0 else (1-effective)*(if(closing)(1-value(0x6f)).coerceIn(0.0,1.0) else 1.0)
        val blur=axes.getValue(0x77).let{if(it.mask and 1!=0)it.target else it.position}
        val bounds=Rect(min(raw.left,seed.left),min(raw.top,seed.top),max(raw.right,seed.right),max(raw.bottom,seed.bottom))
        fun px(r:Rect)=Rect(r.left*density,r.top*density,r.right*density,r.bottom*density)
        return SharedMorphFrame(px(bounds),elements.map{it.copy(center=it.center*density,half=Size(it.half.width*density,it.half.height*density),radius=it.radius*density)},
            (-2*sigma*density).toFloat(),if(directReturn && sourceSurface)1f else alpha.toFloat(),menuAlpha.toFloat(),seedAlpha.toFloat(),
            rawBody=px(raw),contentHeight=max(.001,viewportHeight).toFloat()*density,
            contentRadius=if(bodyScale>0)(inputRadius/bodyScale).toFloat()*density else 0f,
            menuBlur=hypot(4*(1-effective),blur).toFloat()*density,seedCenter=seed.center*density,
            seedBlur=hypot(4*effective,blur).toFloat()*density,
            warp=floatArrayOf(-max(value(0x87),0.0).toFloat(),value(0x8f).toFloat(),1f,if(closing)(.5*effective).toFloat() else .5f),
            light=run {val t=if(closing)min(1.0,2.28*(1-value(0x4b)))else 0.0
                floatArrayOf(sin((115.0/24+125.0/24*t)*PI/180).toFloat(),0f,cos((5317.0/96-325.0/96*t)*PI/180).toFloat(),34f/3)},
            shadow=floatArrayOf((if(alpha<=0)0.0 else (if(closing)max(.012,.1555-.1995*(1-value(0x4b))) else .1555)*(fg*extra*p/alpha)).toFloat(),
                (if(closing)max(2.0,10.541666666666666-11.875*(1-value(0x4b)))else 10.541666666666666).toFloat(),8f,0f),
            style=if(closing)(-16-57*value(0x4b)).coerceIn(-60.0,-22.0).toFloat()else -60f,
            sourceFraction=if(directReturn && sourceSurface){val t=((.35-p)/.35).coerceIn(0.0,1.0);(t*t*(3-2*t)).toFloat()}else 0f,
            materialElements=if(sourceMaterial)null else listOfNotNull(bodyElement).map {
                it.copy(center=it.center*density,half=Size(it.half.width*density,it.half.height*density),radius=it.radius*density)
            })
    }
}

/** Optical support conversion used by the font selector before its SDF union. */
internal object OpticalMenuGeometry {
    private val nodes=doubleArrayOf(-2.27950708050106,-1.597682635152605,-.947788391240164,-.314240376254359,.314240376254359,.947788391240164,1.597682635152605,2.27950708050106)
    private val weights=doubleArrayOf(.003905390584629,.05160798561588,.2604923102642,.5701352362625,.5701352362625,.2604923102642,.05160798561588,.003905390584629)
    fun convert(rect:Rect,radius:Double,sigma:Double):Pair<Rect,Double>? {
        val hx=rect.width/2.0;val hy=rect.height/2.0;if(hx<=0 || hy<=0)return null
        val r=min(radius,min(hx,hy));if(sigma<=0)return rect to r
        if(abs(2*hx-2*hy)<=.5 && r>=min(hx,hy)-.5){
            val squared=min(hx,hy).pow(2)-(sigma*PI/2)*sigma;if(squared<=0)return null
            val half=sqrt(squared).toFloat();return Rect(rect.center-Offset(half,half),Size(half*2,half*2)) to half.toDouble()
        }
        val diagonal=hypot(hx,hy)
        val offsets=DoubleArray(nodes.size){nodes[it]*sqrt(2.0)*sigma}
        fun distance(t:Double,dx:Double,dy:Double):Double {
            var total=0.0;var normalizer=0.0;val support=1+2.8*sigma
            for(i in nodes.indices){val x=t*dx+offsets[i];if(abs(x)>hx+support)continue
                for(j in nodes.indices){val y=t*dy+offsets[j];if(abs(y)>hy+support)continue
                    val w=weights[i]*weights[j];val qx=abs(x)-hx+r;val qy=abs(y)-hy+r
                    val ax=max(qx,0.0);val ay=max(qy,0.0)
                    val outside=if(ax==0.0)ay else if(ay==0.0)ax else sqrt(ax*ax+ay*ay)
                    total+=w*(min(max(qx,qy),0.0)+outside-r);normalizer+=w
                }
            };return if(normalizer<=0)1000.0 else total/normalizer
        }
        fun root(dx:Double,dy:Double):Double {
            if(distance(0.0,dx,dy)>=0)return -1.0
            var lo=0.0;var hi=diagonal+sigma*4
            repeat(64){if(hi-lo<=.01)return (lo+hi)/2;val mid=(lo+hi)/2;if(distance(mid,dx,dy)<0)lo=mid else hi=mid}
            return (lo+hi)/2
        }
        val rx=root(1.0,0.0);val ry=root(0.0,1.0);val rd=max(0.0,root(hx/diagonal,hy/diagonal))
        val ix=if(rx<0)hx else max(0.0,hx-rx);val iy=if(ry<0)hy else max(0.0,hy-ry)
        if(ix>=hx || iy>=hy)return null
        val ax=max(0.0,hx-ix-rd*hx/diagonal);val ay=max(0.0,hy-iy-rd*hy/diagonal)
        val out=Rect(rect.left+ix.toFloat(),rect.top+iy.toFloat(),rect.right-ix.toFloat(),rect.bottom-iy.toFloat())
        return out to min(ax+ay+sqrt(2*ax*ay),out.size.minDimension/2.0)
    }
}
