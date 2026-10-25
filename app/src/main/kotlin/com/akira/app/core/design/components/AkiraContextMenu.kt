// Copyright 2026 zarzet (MIT). See THIRD_PARTY_NOTICES.md.
package com.akira.app.core.design.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.*
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import com.akira.app.core.design.theme.AkiraFontFamily
import com.akira.app.core.i18n.akiraStrings
import com.akira.app.core.design.components.LocalContentMotionReduced
import kotlinx.coroutines.launch
import kotlin.math.*

class AkiraMenuAnchors {
    internal var rect: Rect? = null
    internal var source: AkiraMenuSource? = null
    internal var openedAt = 0L
    internal var hiddenSource by mutableStateOf<AkiraMenuSource?>(null)
    internal fun freshRect(): Rect? = rect.takeIf { android.os.SystemClock.uptimeMillis() - openedAt < 700L }
    internal fun freshSource(): AkiraMenuSource? = source.takeIf { freshRect() != null }
    internal fun clear() { rect = null; source = null; openedAt = 0L }
}
val LocalAkiraMenuAnchors = staticCompositionLocalOf<AkiraMenuAnchors?> { null }
class AkiraMenuSource(private val host: AkiraMenuAnchors?, private val hideOnOpen: Boolean = true,
    private val trailingControl: Boolean = false,
    internal val icon: ImageVector? = com.akira.app.core.design.icon.AkiraIcons.MoreHoriz,
    internal val hasOwnSurface: Boolean = false) {
    private var rect: Rect? = null
    private var openingBounds: Rect? = null
    internal var controlSizePx = 48f
    internal var visualInsetPx = 0f
    internal var visualVerticalInsetPx = 0f
    internal var surfaceContent: (@Composable () -> Unit)? = null
    internal var attached by mutableStateOf(true)
    internal var progressProvider by mutableStateOf<(() -> Float)?>(null)
    internal var returningProvider by mutableStateOf<(() -> Boolean)?>(null)
    internal var contactProvider by mutableStateOf<(() -> Float)?>(null)
    internal var contactStrain by mutableStateOf<(() -> Offset)?>(null)
    internal fun presentationProgress(): Float = progressProvider?.invoke() ?: 0f
    internal fun presentationAlpha(): Float {
        if (!hideOnOpen || progressProvider == null || host?.hiddenSource !== this) return 1f
        val presence = presentationSourcePresence(presentationProgress())
        return if (hasOwnSurface) {
            if (returningProvider?.invoke() == true) 1f - presence else 0f
        } else 1f - .55f * presence
    }
    internal fun responseAmount(): Float = max(contactProvider?.invoke() ?: 0f,
        sin(presentationProgress().coerceIn(0f,1f)*PI.toFloat())*.7f)
    /** Applied to the whole shared capsule, including its sampled material. */
    internal val containerModifier: Modifier = Modifier.graphicsLayer {
        val p=responseAmount()
        scaleX=1f+.035f*p;scaleY=1f-.055f*p
        translationY=2.dp.toPx()*p
    }
    private val focus = androidx.compose.ui.focus.FocusRequester()
    internal fun captureBounds(bounds: Rect, isAttached: Boolean) { rect=bounds;attached=isAttached }
    val modifier: Modifier = Modifier.onGloballyPositioned { captureBounds(it.boundsInWindow(),it.isAttached) }
        .then(Modifier.graphicsLayer {
            alpha = presentationAlpha()
        })
        .then(Modifier.focusRequester(focus))
    fun open(action: () -> Unit) {
        openingBounds = rawBounds()
        host?.rect = openingBounds
        host?.source = this
        host?.openedAt = android.os.SystemClock.uptimeMillis()
        action()
    }
    // A responsive parent changes boundsInWindow while flexing. Feeding that
    // movement back into the destination restarts the travel on every frame.
    // Capture the contact once; the next opening takes a fresh layout anchor.
    internal fun bounds(): Rect? = if(!attached)null else if(progressProvider!=null)openingBounds ?: rawBounds() else rawBounds()
    private fun rawBounds(): Rect? = if(!attached) null else rect?.let { raw ->
            if (trailingControl) {
                val edge=min(raw.height,controlSizePx)
                Rect(raw.right-edge,raw.center.y-edge/2,raw.right,raw.center.y+edge/2)
            }
            else Rect(raw.left+visualInsetPx,raw.top+visualInsetPx+visualVerticalInsetPx,
                raw.right-visualInsetPx,raw.bottom-visualInsetPx-visualVerticalInsetPx)
        }
    internal fun restoreFocus() { runCatching { focus.requestFocus() } }
}
@Composable fun rememberAkiraMenuSource(hideOnOpen: Boolean = true, trailingControl: Boolean = false,
    icon: ImageVector? = com.akira.app.core.design.icon.AkiraIcons.MoreHoriz,
    hasOwnSurface: Boolean = false):AkiraMenuSource {
    val host=LocalAkiraMenuAnchors.current
    val source=remember(host,hideOnOpen,trailingControl,icon,hasOwnSurface){AkiraMenuSource(host,hideOnOpen,trailingControl,icon,hasOwnSurface)}
    source.controlSizePx=with(androidx.compose.ui.platform.LocalDensity.current){48.dp.toPx()}
    DisposableEffect(source) { source.attached=true;onDispose{source.attached=false} }
    return source
}
@Composable fun Modifier.akiraMenuButton(onClick:()->Unit):Modifier {
    val source=rememberAkiraMenuSource()
    val haptics=com.akira.app.core.design.util.rememberAkiraHaptics()
    return then(source.modifier).clickable(role=Role.Button){haptics.light();source.open(onClick)}
}
data class AkiraContextAction(val icon:ImageVector?,val label:String,val selected:Boolean=false,
    val destructive:Boolean=false,val opensPresentation:Boolean=false,val hasSubmenu:Boolean=false,val onClick:()->Unit)

/** Places a menu beside its trigger while keeping it inside the safe window bounds. */
internal fun anchoredMenuPosition(anchor:Rect?,window:Size,child:Size,margin:Float,gap:Float,preferAbove:Boolean,
    direction: LayoutDirection = LayoutDirection.Ltr, attachedSurface: Boolean = false):Offset {
    val right=max(margin,window.width-margin-child.width)
    val bottom=max(margin,window.height-margin-child.height)
    val x=anchor?.let { if(direction==LayoutDirection.Rtl)it.left else it.right-child.width }?:((window.width-child.width)/2)
    var y=window.height*.18f
    if(anchor!=null){
        val above=if(attachedSurface)anchor.bottom-child.height else anchor.top-child.height-gap
        val below=if(attachedSurface)anchor.top else anchor.bottom+gap
        y=if(preferAbove){if(above>=margin)above else if(below<=bottom)below else margin}
            else if(below<=bottom)below else if(above>=margin)above else bottom
    }
    return Offset(x.coerceIn(margin,right),y.coerceIn(margin,bottom))
}

/** Source geometry and material are owned by the shared presentation host. */
enum class AkiraMenuStyle { Actions, Choices }

@Composable fun AkiraContextMenu(onDismiss: () -> Unit, quickActions: List<AkiraContextAction> = emptyList(),
    groups: List<List<AkiraContextAction>>, preferAbove: Boolean = false, dense: Boolean = false,
    style: AkiraMenuStyle = AkiraMenuStyle.Actions) {
    val presentation = LocalAkiraPresentationState.current
    val options = akiraStrings().options
    AkiraPresentation(AkiraPresentationKind.Menu, onDismiss, preferAbove) {
        fun activate(action: AkiraContextAction) {
            if (action.opensPresentation) action.onClick()
            else if (presentation != null) {
                // Commit transport immediately; the host retains the outgoing
                // surface while it returns. Nested panels instead retarget it.
                val entry = presentation.entries.lastOrNull { !it.removed }
                if (entry != null) {
                    action.onClick()
                    presentation.closing = entry
                }
            } else { action.onClick(); onDismiss() }
        }
        BoxWithConstraints(Modifier.semantics { paneTitle = options }) {
            val groupsContent: @Composable ColumnScope.() -> Unit = {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    groups.filter { it.isNotEmpty() }.forEachIndexed { index, group ->
                        if (index > 0 || quickActions.isNotEmpty()) HorizontalDivider(
                            Modifier.padding(horizontal = 16.dp, vertical = if (index == 0) 0.dp else if (dense) 4.dp else 8.dp),
                            thickness = .5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .14f))
                        group.forEach { MenuActionRow(it, false, dense, enabled = presentation?.closing == null, style = style) { activate(it) } }
                    }
                }
            }
            val shortcutColumns = if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.4f) 2 else quickActions.size.coerceIn(1,4)
            val shortcuts: @Composable () -> Unit = {
                if (quickActions.isNotEmpty()) Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    quickActions.chunked(shortcutColumns).forEach { actions ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            actions.forEach { action -> Box(Modifier.weight(1f)) {
                                MenuActionRow(action, true, dense, enabled = presentation?.closing == null) { activate(action) }
                            } }
                            repeat(shortcutColumns - actions.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            if (maxHeight < 360.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale * 17 > 28)
                Column(Modifier.verticalScroll(rememberScrollState())) { shortcuts(); groupsContent() }
            else Column { shortcuts(); Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), content = groupsContent) }
        }
    }
}

@Composable private fun MenuActionRow(action:AkiraContextAction,compact:Boolean,dense:Boolean,enabled:Boolean,
    style: AkiraMenuStyle = AkiraMenuStyle.Actions,onClick:()->Unit) {
    val color=if(action.destructive)MaterialTheme.colorScheme.error else if(action.selected && style==AkiraMenuStyle.Actions)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val interaction=remember{androidx.compose.foundation.interaction.MutableInteractionSource()}
    val pressed by interaction.collectIsPressedAsState()
    val response=rememberAkiraPressResponse(pressed)
    val source=rememberAkiraMenuSource(hideOnOpen=false,icon=action.icon)
    val modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).then(if(action.opensPresentation)source.modifier else Modifier).then(response.modifier).background(color.copy(alpha=if(pressed).07f else 0f),androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
        .clickable(interactionSource=interaction,indication=null,enabled=enabled,role=Role.Button,
            onClick={if(action.opensPresentation)source.open(onClick) else onClick()})
        .semantics{selected=action.selected}
        .padding(horizontal=if(compact)4.dp else 16.dp,vertical=if(compact){if(dense)6.dp else 10.dp}else if(dense)8.dp else 14.dp)
    val label:@Composable ()->Unit={Text(action.label,fontFamily=AkiraFontFamily,fontSize=(if(compact)12 else 16).sp,
        fontWeight=if(compact)FontWeight.SemiBold else FontWeight.Normal,textAlign=if(compact)TextAlign.Center else TextAlign.Start,color=color)}
    if(compact)Column(modifier,horizontalAlignment=Alignment.CenterHorizontally){action.icon?.let { Icon(it,null,Modifier.size(21.dp),tint=color) };Spacer(Modifier.height(if(dense)4.dp else 7.dp));label()}
    else Row(modifier,verticalAlignment=Alignment.CenterVertically){
        if(style==AkiraMenuStyle.Choices) Box(Modifier.size(18.dp),contentAlignment=Alignment.Center) {
            if(action.selected) Icon(Icons.Rounded.Check,null,Modifier.size(15.dp),tint=color)
        } else action.icon?.let { Icon(it,null,Modifier.size(22.dp),tint=color) }
        Spacer(Modifier.width(if(style==AkiraMenuStyle.Choices)10.dp else if(dense)12.dp else 16.dp));Box(Modifier.weight(1f)){label()}
        if(action.hasSubmenu)Icon(com.akira.app.core.design.icon.AkiraIcons.ChevronRight,null,Modifier.size(16.dp),tint=color.copy(alpha=.6f))}
}
