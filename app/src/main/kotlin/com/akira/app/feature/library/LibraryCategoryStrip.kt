package com.akira.app.feature.library

import com.akira.app.core.artwork.*
import com.akira.app.feature.lyrics.*
import com.akira.app.feature.player.*
import com.akira.app.feature.queue.*

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.*
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.*
import androidx.compose.ui.text.font.FontWeight
import com.akira.app.core.design.glass.*
import com.akira.app.feature.player.dock.*
import com.akira.app.core.design.backdrop.Backdrop
import com.akira.app.core.design.backdrop.backdrops.*
import com.akira.app.feature.player.dock.navigation.*
import com.akira.app.core.design.components.LocalContentMotionReduced

/** Uses the dock's lens and spring, with recordings excluding the moving lens. */
@Composable internal fun LibraryCategoryStrip(tabs: List<Pair<String,String>>,selected: String,
    onSelect: (String)->Unit,backdrop: Backdrop) {
    val index=tabs.indexOfFirst {it.first==selected}.coerceAtLeast(0)
    val scope=rememberCoroutineScope()
    val motion=remember {GlassPillMotion(scope,index)}
    val reduced=LocalContentMotionReduced.current
    val touch=remember {MutableInteractionSource()}
    val pressed by touch.collectIsPressedAsState()
    var dragging by remember {mutableStateOf(false)}
    val currentIndex by rememberUpdatedState(index)
    val currentSelect by rememberUpdatedState(onSelect)
    LaunchedEffect(pressed) {if(pressed)motion.startDrag() else if(!dragging)motion.release(currentIndex)}
    motion.reduceMotion=reduced
    val density=LocalDensity.current
    val rtl=LocalLayoutDirection.current==LayoutDirection.Rtl
    val bar=rememberLayerBackdrop();val labels=rememberLayerBackdrop()
    val fill=MaterialTheme.colorScheme.onSurface.copy(alpha=.12f)
    val lens=LocalLiquidGlassEnabled.current && isGlassSupported() && !reduced
    var rowSize by remember {mutableStateOf(IntSize.Zero)}
    LaunchedEffect(index,reduced) {if(reduced)motion.snapTo(index) else motion.animateTo(index)}
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal=24.dp)) {
        val minimum=with(density) {(64.dp.toPx()*density.fontScale).toDp()}*tabs.size
        val viewport=maxWidth
        val width=maxOf(viewport,minimum)
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Box(Modifier.width(width).height(50.dp)) {
                Box(Modifier.matchParentSize().layerBackdrop(bar).liquidGlass(RoundedCornerShape(50),backdrop,
                    shadowStrength=if(MaterialTheme.colorScheme.background.luminance()>.5f).18f else 1f))
                Box(Modifier.matchParentSize().padding(4.dp)) {
                    val cell=rowSize.width.toFloat()/tabs.size
                    val rest=Size(cell,rowSize.height.toFloat())
                    val lifted=Size(cell+with(density){5.dp.toPx()},rowSize.height+with(density){8.dp.toPx()})
                    motion.stepDp=cell/density.density
                    val center: ()->Offset={
                        val x=(motion.position.coerceIn(0f,tabs.lastIndex.toFloat())+.5f)*cell
                        Offset(if(rtl)rowSize.width-x else x,rowSize.height/2f)
                    }
                    if(cell>0 && (!lens || motion.isFlat))FlatSelectionPill(fill,
                        Modifier.matchParentSize().pillPlacement(center,{rest}))
                    Row(Modifier.fillMaxSize().onSizeChanged {rowSize=it}.layerBackdrop(labels)
                        .pointerInput(cell,rtl,width,viewport) {
                            if(cell<=0 || width>viewport)return@pointerInput
                            var total=0f
                            var origin=0
                            detectHorizontalDragGestures(
                                onDragStart={origin=currentIndex;total=0f;dragging=true;motion.startDrag()},
                                onDragCancel={dragging=false;motion.release(currentIndex)},
                                onDragEnd={
                                    val ratio=total/cell
                                    val shift=when {ratio>.35f->maxOf(1,ratio.roundToInt());ratio<-.35f->minOf(-1,ratio.roundToInt());else->0}
                                    val next=(origin+shift).coerceIn(0,tabs.lastIndex)
                                    dragging=false;motion.release(next)
                                    if(next!=currentIndex)currentSelect(tabs[next].first)
                                }) {change,delta ->
                                change.consume();total+=delta*(if(rtl)-1 else 1)
                                motion.dragTo((origin+total/cell).coerceIn(-.2f,tabs.lastIndex+.2f))
                            }
                        }) {
                        tabs.forEach {(id,label)->
                            Box(Modifier.weight(1f).fillMaxHeight().selectable(id==selected,interactionSource=touch,indication=null,role=Role.Tab,onClick={onSelect(id)}),contentAlignment=Alignment.Center) {
                                Text(label,fontSize=13.sp,maxLines=1,fontWeight=if(id==selected)FontWeight.Bold else FontWeight.Medium,
                                    color=MaterialTheme.colorScheme.onSurface.copy(alpha=if(id==selected)1f else .65f))
                            }
                        }
                    }
                    if(lens && cell>0 && !motion.isFlat)CompositionLocalProvider(LocalAppBackdrop provides backdrop) {
                        GlassSelectionPill(motion,fill,bar,labels,Modifier.matchParentSize().pillPlacement(center,{motion.liveSize(rest,lifted)}))
                    }
                }
            }
        }
    }
}
