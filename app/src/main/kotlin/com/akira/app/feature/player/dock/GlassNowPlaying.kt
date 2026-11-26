// SPDX-License-Identifier: GPL-3.0-only
// Source attribution: THIRD_PARTY_NOTICES.md.
package com.akira.app.feature.player.dock

import com.akira.app.core.design.glass.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.akira.app.core.model.Song
import com.akira.app.core.design.components.localizedText
import com.akira.app.core.design.components.LocalContentMotionReduced
import com.akira.app.core.design.components.akiraTransportPress
import kotlinx.coroutines.delay
@Composable
internal fun GlassNowPlaying(
    song: Song,
    compactFraction: Float,
    isPlaying: Boolean,
    isLoading: Boolean,
    contentColor: Color,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onHoldStart: (() -> (() -> Unit))? = null,
    onExpand: () -> Unit,
    controlsLocked: Boolean,
    onBlockedControl: () -> Unit,
    artworkVisible: Boolean,
    onMiniPositioned: (LayoutCoordinates) -> Unit,
    onArtPositioned: (LayoutCoordinates, androidx.compose.ui.unit.Dp) -> Unit,
    pull: MiniPlayerPull?,
    modifier: Modifier = Modifier,
    drawGlass: Boolean = true,
    classicAccent: Color? = null,
) {
    val ink = rememberGlassInk(contentColor)
    val resolvedContentColor = ink.color
    val reduced = LocalContentMotionReduced.current
    val swipeScope=rememberCoroutineScope()
    val swipe=remember {MiniPlayerSwipe(swipeScope,song)}
    SideEffect {swipe.update(song);swipe.reduced=reduced}
    DisposableEffect(swipe) {onDispose {swipe.dispose()}}
    val visibleSong=swipe.shownSong
    val loadingIndicator by produceState(false, isLoading) {
        // Gapless local tracks can buffer for only a few frames on a skip.
        if (isLoading) delay(150)
        value = isLoading
    }
    var nextPulse by remember {mutableIntStateOf(0)}
    val pressSource = remember { MutableInteractionSource() }
    val foldDeformation=LocalGlassDeformation.current
    val response=com.akira.app.core.design.components.rememberAkiraPressResponse(false, .4f, contactLighting = false)
    val density=androidx.compose.ui.platform.LocalDensity.current
    val inheritedStrain=com.akira.app.core.design.glass.LocalGlassStrain.current
    val elastic=com.akira.app.core.design.glass.LocalLiquidGlassEnabled.current && !com.akira.app.core.design.components.LocalContentMotionReduced.current
    val surfaceShape=remember(response,density,inheritedStrain,elastic) {
        com.akira.app.core.design.motion.ElasticGlassShape({Float.MAX_VALUE},{0f},
            { if(elastic)inheritedStrain()+response.strain() else androidx.compose.ui.geometry.Offset.Zero },density,preserveCapsuleEnds=true)
    }

    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val compact = compactFraction.coerceIn(0f, 1f)
    val expanded = 1f - compact
    val artSize = if (drawGlass) (34f - 2f * compact).dp else 42.dp
    val artCorner = if (drawGlass) (8f - 2f * compact).dp else 10.dp
    val glyphSlot = if (drawGlass) (34f - 2f * compact).dp else 40.dp
    val glyphSize = if (drawGlass) (28f - 4f * compact).dp else 24.dp
    val colors = MaterialTheme.colorScheme
    val classicActionColor = classicAccent ?: colors.secondaryContainer
    val classicActionInk = if (classicAccent == null) colors.onSecondaryContainer
        else if (classicActionColor.luminance() > .34f) Color(0xFF1B1818) else Color.White
    val classicNextColor = (classicAccent ?: resolvedContentColor).copy(alpha = .16f)
    val actionCorner = if (drawGlass) null else animateDpAsState(
        if (isPlaying || loadingIndicator) 13.dp else 20.dp,
        if (reduced) snap() else tween(145), label = "classicTransportCorner",
    )
    val playSlot = if (drawGlass) glyphSlot else 50.dp

    CompositionLocalProvider(LocalGlassDeformation provides {
        (foldDeformation()+response.amount()*.35f).coerceIn(0f,1f)
    }) {
    Box(
        // The live dock measures this one surface at every spring step.
        // Centre its content in that changing height, without replacing it.
        contentAlignment = Alignment.Center,
        // The gestures and the tap belong to the whole glass surface, not the
        // row centred in it. Inline, that row is shorter than the pill it sits
        // in, and a pull started in the strip above or below it went nowhere —
        // on a bar only 45dp tall to begin with.
        modifier = Modifier
            .miniPlayerGestures(
                pull = pull,
                onNext = {
                    onNext()
                },
                onPrevious = {
                    onPrevious()
                },
                locked = controlsLocked,
                onBlocked = onBlockedControl,
                swipe = swipe,
            )
            .then(response.modifier).then(modifier).then(ink.modifier)
            .then(if (drawGlass) Modifier.liquidGlass(surfaceShape).clip(surfaceShape) else Modifier)
            .onGloballyPositioned { onMiniPositioned(it) }
            .clickable(
                interactionSource = pressSource,
                indication = null,
                onClick = onExpand,
            ),
    ) {
        CompositionLocalProvider(com.akira.app.core.design.components.LocalGlassPressHandled provides true) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = if (artworkVisible) 1f else 0f }
                .padding(
                    horizontal = (12f - 4f * compact).dp,
                    vertical = 0.dp,
                ),
        ) {
            // Only the sleeve and metadata slide. The transport buttons stay
            // anchored and the viewport prevents text crossing over them.
            Box(Modifier.weight(1f).clipToBounds().onSizeChanged {swipe.width=it.width.toFloat()}) {
                Row(Modifier.fillMaxWidth().graphicsLayer {translationX=swipe.offset},
                    verticalAlignment=Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(artSize)
                            // Opening the full player uses these actual live bounds,
                            // including an interrupted fold between the two densities.
                            .onGloballyPositioned { onArtPositioned(it, artCorner) }
                            .graphicsLayer { alpha = if (artworkVisible) 1f else 0f }
                            .clip(RoundedCornerShape(artCorner))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!visibleSong.albumArtUri.isNullOrBlank()) com.akira.app.core.widget.WidgetTransitionArtwork(
                            visibleSong.albumArtUri, Modifier.fillMaxSize(),
                            imageRequest = com.akira.app.feature.player.rememberPlayerArtworkRequest(visibleSong.albumArtUri, thumbnail = true),
                            reportWidgetTransition = false)
                        else Icon(Icons.Rounded.MusicNote, null, tint = resolvedContentColor.copy(alpha = .65f), modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width((10f - 2f * compact).dp))
                    // A playing song keeps both lines in the one-row dock. Only their
                    // available width changes; no artist fade or vertical title snap.
                    Box(Modifier.weight(1f).height((30f * fontScale).dp), contentAlignment = Alignment.CenterStart) {
                        Text(visibleSong.title, style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp, lineHeight = 16.sp, fontFamily = com.akira.app.core.design.theme.CurrentAkiraFontFamily,
                            fontWeight = FontWeight.SemiBold), color = resolvedContentColor,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().graphicsLayer { translationY = (-7f * fontScale).dp.toPx() })
                        Text(visibleSong.artist, style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.sp, lineHeight = 14.sp, fontFamily = com.akira.app.core.design.theme.CurrentAkiraFontFamily),
                            color = resolvedContentColor.copy(alpha = .88f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().graphicsLayer {
                                translationY = (8f * fontScale).dp.toPx()
                            })
                    }
                }
            }
            MiniControlButton(
                onClick = onPlayPause,
                enabled = !loadingIndicator,
                modifier = Modifier.width(playSlot).height(glyphSlot),
                surface = if (actionCorner == null) Modifier else Modifier.background(
                    if (loadingIndicator) classicNextColor else classicActionColor, RoundedCornerShape(actionCorner.value)),
            ) {
                    com.akira.app.core.design.components.AkiraAnimatedGlyph(
                        icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        label = localizedText(if (isPlaying) "Pause" else "Play", if (isPlaying) "Pausar" else "Reproducir"),
                        tint = if (drawGlass) resolvedContentColor else classicActionInk,
                        modifier = Modifier.size(glyphSize).graphicsLayer { alpha = if (loadingIndicator) 0f else 1f }
                            .then(if (loadingIndicator) Modifier.clearAndSetSemantics {} else Modifier),
                    )
                    if (loadingIndicator) CircularProgressIndicator(
                        color = resolvedContentColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size((22f - 4f * compact).dp),
                    )
            }
            // Dropped inline: the collapsed row is sharing its width with the
            // tab pill and the Search circle, and the title is what has to
            // survive that, not a second transport button.
            if (expanded > .001f) {
                Spacer(Modifier.width((8f * expanded).dp))
                MiniControlButton(
                    onClick = {
                            nextPulse++
                        onNext()
                    },
                    enabled = !controlsLocked,
                    onHoldStart = onHoldStart,
                    modifier = Modifier.width(glyphSlot * expanded).height(glyphSlot)
                        .graphicsLayer { alpha = expanded }
                        .then(if (expanded < .5f) Modifier.clearAndSetSemantics {} else Modifier),
                    surface = if (drawGlass) Modifier else Modifier.background(classicNextColor, RoundedCornerShape(13.dp)),
                ) {
                    MiniPlayerNextGlyph(
                        tint = resolvedContentColor.copy(alpha = if (controlsLocked) .3f else 1f),
                        trigger = nextPulse,
                    )
                }
            }
        }
    }
    }
}

}

@Composable
private fun MiniControlButton(onClick: () -> Unit, modifier: Modifier=Modifier,
    enabled: Boolean=true,onHoldStart: (() -> (() -> Unit))?=null,
    surface: Modifier = Modifier, content: @Composable () -> Unit) {
    var pressed by remember {mutableStateOf(false)}
    val response=com.akira.app.core.design.components.rememberAkiraPressResponse(pressed, contactLighting = false)
    Box(modifier.then(response.modifier).then(if(enabled)Modifier.akiraTransportPress(onClick,onHoldStart,
        onPressedChanged={pressed=it}) else Modifier).then(surface),
        contentAlignment=Alignment.Center) { content() }
}

@Composable internal fun MiniPlayerNextGlyph(tint: Color,trigger: Int=0) {
    com.akira.app.feature.player.AkiraTransportGlyph(1,trigger,localizedText("Next", "Siguiente"),
        Modifier.size(25.dp),tint)
}
