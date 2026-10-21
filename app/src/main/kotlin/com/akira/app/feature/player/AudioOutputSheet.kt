package com.akira.app.feature.player

import com.akira.app.core.artwork.*
import com.akira.app.feature.library.*
import com.akira.app.feature.lyrics.*
import com.akira.app.feature.queue.*

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.collectIsPressedAsState

import android.content.*
import android.media.AudioManager
import android.media.MediaRouter
import android.media.AudioDeviceInfo
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import com.akira.app.core.design.theme.AkiraFontFamily
import com.akira.app.core.design.icon.AkiraIcons
import androidx.core.content.ContextCompat
import com.akira.app.core.design.components.AkiraModalSheet
import com.akira.app.core.design.components.AkiraNotices
import com.akira.app.core.design.util.rememberAkiraHaptics
import com.akira.app.core.i18n.LocalStrings
import com.akira.app.core.i18n.StringsSpanish
import com.akira.app.feature.settings.AudioSlider

private data class OutputDeviceItem(val route: MediaRouter.RouteInfo, val name: String, val isActive: Boolean, val icon: ImageVector)

/** Local music routes, rather than raw AudioDeviceInfo entries (which include call profiles). */
@Composable
fun AudioOutputSheet(song: com.akira.app.core.model.Song? = null, isPlaying: Boolean = false, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val router = remember { context.getSystemService(MediaRouter::class.java) }
    val haptics = rememberAkiraHaptics()
    var routeRevision by remember { mutableIntStateOf(0) }
    val hasWired = remember(routeRevision) { audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE) } }
    var routes by remember { mutableStateOf(emptyList<MediaRouter.RouteInfo>()) }
    var selected by remember { mutableStateOf<MediaRouter.RouteInfo?>(null) }
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var volumeGesture by remember {mutableStateOf(false)}
    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    fun refresh() {
        routeRevision++
        selected = router.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO)
        routes = (0 until router.routeCount).map { router.getRouteAt(it) }
            .filter { it.supportedTypes and MediaRouter.ROUTE_TYPE_LIVE_AUDIO != 0 &&
                it.playbackType == MediaRouter.RouteInfo.PLAYBACK_TYPE_LOCAL && it.isEnabled }
            .distinct()
        if(!volumeGesture)volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
    }
    DisposableEffect(router) {
        refresh()
        val callback = object : MediaRouter.SimpleCallback() {
            override fun onRouteAdded(router: MediaRouter, info: MediaRouter.RouteInfo) = refresh()
            override fun onRouteRemoved(router: MediaRouter, info: MediaRouter.RouteInfo) = refresh()
            override fun onRouteChanged(router: MediaRouter, info: MediaRouter.RouteInfo) = refresh()
            override fun onRouteSelected(router: MediaRouter, type: Int, info: MediaRouter.RouteInfo) = refresh()
            override fun onRouteVolumeChanged(router: MediaRouter, info: MediaRouter.RouteInfo) = refresh()
        }
        router.addCallback(MediaRouter.ROUTE_TYPE_LIVE_AUDIO, callback)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1) == AudioManager.STREAM_MUSIC) refresh()
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { router.removeCallback(callback); context.unregisterReceiver(receiver) }
    }
    val ink = MaterialTheme.colorScheme.onSurface
    val reduced=com.akira.app.core.design.components.LocalContentMotionReduced.current
    var expandedDevices by remember { mutableStateOf(false) }
    fun systemDevices() {
        haptics.click()
        runCatching { context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { AkiraNotices.show(strings.library.error) }
    }
    AkiraModalSheet(onDismissRequest=onDismiss,title=strings.audioOutput,
        style=com.akira.app.core.design.components.AkiraPanelStyle.AudioOutput) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val preferred=330.dp
            Column(Modifier.fillMaxWidth().height(minOf(maxHeight,preferred)).padding(horizontal=12.dp),
                verticalArrangement=Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(top=2.dp,bottom=4.dp,start=2.dp,end=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    coil.compose.AsyncImage(song?.albumArtUri,null,contentScale=androidx.compose.ui.layout.ContentScale.Crop,
                        modifier=Modifier.size(60.dp).clip(RoundedCornerShape(9.dp)).background(ink.copy(alpha=.08f)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                        Text(song?.title ?: strings.audioOutput,fontFamily=AkiraFontFamily,fontSize=14.sp,
                            fontWeight=FontWeight.SemiBold,color=ink,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        song?.let { Text(it.artist,fontFamily=AkiraFontFamily,fontSize=12.sp,color=ink.copy(alpha=.60f),
                            maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                    }
                    Spacer(Modifier.width(8.dp))
                    AudioRouteWaveform(isPlaying,ink.copy(alpha=.7f),Modifier.size(20.dp))
                }
                Column(Modifier.fillMaxWidth().heightIn(max=180.dp).verticalScroll(rememberScrollState()).selectableGroup(),
                    verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    val ordered=routes.sortedBy { if(it===selected)0 else 1 }
                    (if(expandedDevices)ordered else ordered.take(4)).forEach { route ->
                        key(route) {
                        val dev=OutputDeviceItem(route,
                            if(route===router.defaultRoute && !hasWired)strings.deviceSpeaker else route.name.toString(),
                            route===selected,
                            if(route.deviceType==MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH)Icons.Rounded.Bluetooth
                            else if(hasWired)Icons.Outlined.Headphones else Icons.Outlined.Smartphone)
                        val rowInk=if(dev.isActive)Color(0xFF242731) else ink.copy(alpha=.90f)
                        val rowFill by animateColorAsState(if(dev.isActive)Color.White.copy(alpha=.30f) else ink.copy(alpha=.10f),
                            if(reduced)snap() else spring(.85f,1000f),label="outputRouteSelection")
                        val touch=remember { MutableInteractionSource() }
                        val pressed by touch.collectIsPressedAsState()
                        val active=dev.isActive && (volumeGesture || pressed)
                        val rowHeight by animateDpAsState(if(active)55.dp else 46.dp,
                            if(reduced)snap() else spring(.8f,800f),label="routeVolumeExpansion")
                        // Reserve the full expansion before contact. A second
                        // size animation on the scroll viewport cropped its bottom.
                        Box(Modifier.fillMaxWidth().height(if(dev.isActive)56.dp else 46.dp)) {
                        Box(Modifier.fillMaxWidth().height(rowHeight.coerceAtMost(56.dp)).align(Alignment.Center).clip(RoundedCornerShape(50)).background(rowFill)
                            .then(if(dev.isActive)Modifier.semantics {
                                contentDescription=strings.volume
                                progressBarRangeInfo=ProgressBarRangeInfo(volume,0f..max.toFloat(),max-1)
                                setProgress {target ->
                                    volume=target.coerceIn(0f,max.toFloat())
                                    audio.setStreamVolume(AudioManager.STREAM_MUSIC,volume.roundToInt(),0);true
                                }
                            }.pointerInput(audio,max,dev.route) {
                                var start=0f;var distance=0f
                                detectHorizontalDragGestures(
                                    onDragStart={volumeGesture=true;start=volume;distance=0f},
                                    onDragEnd={volumeGesture=false;refresh()},onDragCancel={volumeGesture=false;refresh()}) {change,amount ->
                                    change.consume();distance+=amount
                                    volume=(start+distance/size.width.coerceAtLeast(1)*max).coerceIn(0f,max.toFloat())
                                    audio.setStreamVolume(AudioManager.STREAM_MUSIC,volume.roundToInt(),0)
                                }
                            } else Modifier)
                            .selectable(dev.isActive,interactionSource=touch,indication=null,role=Role.RadioButton,onClick={
                                if(!dev.isActive) {
                                    haptics.tick()
                                    runCatching {router.selectRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO,dev.route)}
                                        .onFailure {AkiraNotices.show(strings.library.error)};refresh()
                                }
                            })) {
                            if(dev.isActive)Box(Modifier.fillMaxWidth((volume/max).coerceIn(0f,1f)).fillMaxHeight()
                                .background(Color.White.copy(alpha=.52f)))
                            Row(Modifier.fillMaxSize().padding(horizontal=15.dp),verticalAlignment=Alignment.CenterVertically) {
                                Icon(dev.icon,null,Modifier.size(20.dp),tint=rowInk)
                                Spacer(Modifier.width(10.dp))
                                Text(dev.name,Modifier.weight(1f),fontFamily=AkiraFontFamily,fontSize=15.sp,color=rowInk)
                                if(dev.isActive)Icon(AkiraIcons.Check,strings.inUse,Modifier.size(20.dp),tint=rowInk)
                            }
                        }
                        }
                        }
                    }
                    if(routes.size>4 && !expandedDevices) TextButton(onClick={expandedDevices=true},
                        modifier=Modifier.fillMaxWidth().heightIn(min=42.dp).background(ink.copy(alpha=.10f),RoundedCornerShape(50)),
                        colors=ButtonDefaults.textButtonColors(contentColor=ink)) {
                        Text(com.akira.app.core.design.components.localizedText("Show more","Mostrar más"),fontFamily=AkiraFontFamily,fontSize=15.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AudioRouteWaveform(playing: Boolean, ink: Color, modifier: Modifier) {
    val reduced=com.akira.app.core.design.components.LocalContentMotionReduced.current
    val phase=if(playing && !reduced) {
        val transition=androidx.compose.animation.core.rememberInfiniteTransition(label="routeWaveform")
        val time by transition.animateFloat(0f,6.283185f,
            androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(850,
                easing=androidx.compose.animation.core.LinearEasing)),label="routeWavePhase")
        time
    } else 0f
    androidx.compose.foundation.Canvas(modifier) {
        repeat(5) { index ->
            val height=size.height*(.22f+.55f*kotlin.math.abs(kotlin.math.sin(phase+index*.85f)))
            val width=size.width*.08f
            drawRoundRect(ink,androidx.compose.ui.geometry.Offset(size.width*(.13f+index*.17f),(size.height-height)/2),
                androidx.compose.ui.geometry.Size(width,height),androidx.compose.ui.geometry.CornerRadius(width/2))
        }
    }
}
