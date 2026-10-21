// SPDX-License-Identifier: GPL-3.0-only
// Adapted from Page bb28993 PlaylistDetail.kt. Local files, semantic contrast and Akira glass adapter.
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.akira.app.feature.collection

import com.akira.app.core.design.components.akiraMenuButton
import com.akira.app.core.design.motion.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.akira.app.R
import com.akira.app.core.design.theme.AkiraFontFamily
import com.akira.app.core.design.components.*
import com.akira.app.core.design.navigation.*
import com.akira.app.core.design.rendering.*
import com.akira.app.feature.home.components.*
import com.akira.app.core.design.glass.LocalLiquidGlassEnabled
import com.akira.app.core.design.glass.liquidGlass
import com.akira.app.core.design.glass.LocalAppBackdrop
import com.akira.app.core.design.backdrop.backdrops.rememberLayerBackdrop
import com.akira.app.core.design.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.launch

internal val PlaylistInset = 36.dp
internal val PlaylistTracking = (-0.06).em
internal val PillContent: Color @Composable get() = if (LocalDetailInk.current.luminance() > .5f) Color(0xFF2F2F2F) else Color.White
internal val PillFill: Color @Composable get() = LocalDetailInk.current.copy(alpha = 0.64f)
internal val TrackShape = RoundedCornerShape(9.dp)

// a pressed track lights a pill from one side of the screen to the other, like the swipe slab
private val TrackHighlight: androidx.compose.foundation.Indication @Composable get() = RoundedHighlight(100.dp, (-10).dp, color = LocalDetailInk.current.copy(alpha=.12f))

internal val PlaylistTitleStyle = TextStyle(
    fontFamily = AkiraFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 28.sp,
    letterSpacing = PlaylistTracking,
)
internal val PlaylistCreatorStyle = TextStyle(
    fontFamily = AkiraFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 15.sp,
    lineHeight = 18.sp,
    letterSpacing = (-0.04).em,
)
internal val PlaylistStatsStyle = TextStyle(
    fontFamily = AkiraFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = (-0.02).em,
)
internal val PillLabelStyle = TextStyle(
    fontFamily = AkiraFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    letterSpacing = PlaylistTracking,
)
internal val TrackTitleStyle = TextStyle(
    fontFamily = AkiraFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 17.sp,
    letterSpacing = (-0.03).em,
)
internal val TrackSubtitleStyle = TextStyle(
    fontFamily = AkiraFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 11.5.sp,
    lineHeight = 14.sp,
    letterSpacing = (-0.02).em,
)
internal val SectionTitleStyle = TextStyle(
    fontFamily = AkiraFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    letterSpacing = PlaylistTracking,
)
internal val ReceiptSmallStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 8.5.sp,
    letterSpacing = 0.04.em,
)
internal val ReceiptCenterStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 10.sp,
    letterSpacing = 0.42.em,
)

data class CreatorUi(
    val name: String,
    val avatarUrl: String? = null,
    val artistId: String? = null,
)

data class FeaturedArtistUi(
    val id: String,
    val name: String,
    /** when the caller already has the photo (an artist page's shelves do), no lookup is made */
    val thumbnailUrl: String? = null,
)

@Composable
fun playlistCoverHeight(): Dp = (LocalConfiguration.current.screenHeightDp * 0.6f).dp

/**
 * shell every playlist screen sits in. it's what grows out of the tapped Home card, it takes its
 * colour from the bottom of the cover, draws the cover behind the list (riding up, zooming in and
 * going soft as you scroll, then fading out), and runs the top bar where a small copy of the cover
 * springs out of the back button once the header has scrolled away.
 */
@Composable
fun PlaylistDetailScaffold(
    sharedElementKey: String?,
    coverUrl: String?,
    lazyListState: LazyListState,
    onBackClick: () -> Unit,
    onBackLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMoreClick: (() -> Unit)? = null,
    showCover: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(),
    topBarOverride: (@Composable () -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    /**
     * how far the list scrolls before the top bar turns to glass. the cover band by default; a
     * page without a cover passes its shorter header so the bar frosts as the title slides under it
     */
    headerHeight: Dp? = null,
    /**
     * the generated pages (no cover of their own): the collapsed pill is a full capsule with a round
     * cover, like the Library's search capsule, and the theme colour sinks to black down the page
     */
    roundCapsule: Boolean = false,
    /**
     * a cover drawn in code instead of [coverUrl]'s picture (Liked songs). it fills the modifier
     * it's given; the backdrop and the small copy in the collapsed bar both draw it
     */
    cover: (@Composable (Modifier) -> Unit)? = null,
    /** the theme colour for a drawn [cover], which has no picture to sample one from */
    coverThemeColor: Color? = null,
    content: LazyListScope.() -> Unit,
) {
    val DetailInk = LocalDetailInk.current
    // the tapped card's cover comes first: the screen grows out of that image, so it has to be
    // there from the first frame, and keeping it means the cover never swaps mid-way
    val handedCover = remember(sharedElementKey) { containerTransformCover(sharedElementKey) }
    val coverUrl = handedCover ?: coverUrl

    val sampledThemeColor by rememberCoverThemeColor(if (cover != null) null else coverUrl)
    val themeColor = if (cover != null) coverThemeColor else sampledThemeColor
    val background = animateColorAsState(
        targetValue = themeColor ?: MaterialTheme.colorScheme.background,
        animationSpec = if (LocalContentMotionReduced.current) snap() else tween(600),
        label = "playlistTheme",
    )

    // the bottom nav scrim picks this up, so the whole screen reads as one colour. a page that
    // sinks to black hands it black, or the nav would sit in a band of the theme colour
    val accent = LocalDetailAccentColor.current
    SideEffect { accent.value = if (roundCapsule) Color.Black else themeColor }
    DisposableEffect(Unit) { onDispose { accent.value = null } }

    val coverHeight = playlistCoverHeight()
    val collapseHeightPx = with(LocalDensity.current) { (headerHeight ?: coverHeight).toPx() }
    val scrollProgress = remember(lazyListState, collapseHeightPx) {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (lazyListState.firstVisibleItemScrollOffset / collapseHeightPx).coerceIn(0f, 1f)
            }
        }
    }

    // flips once the title has slid under the top bar; a soft spring with a little bounce on the
    // way in, a calmer one on the way out
    val collapsed by remember(scrollProgress) { derivedStateOf { scrollProgress.value > 0.55f } }
    val morph = remember { Animatable(0f) }
    val reduced = LocalContentMotionReduced.current
    LaunchedEffect(collapsed, reduced) {
        if (reduced) morph.snapTo(if (collapsed) 1f else 0f)
        else morph.animateTo(
            targetValue = if (collapsed) 1f else 0f,
            animationSpec = spring(dampingRatio = if (collapsed) 0.62f else 0.9f, stiffness = 360f),
        )
    }
    val glassState = remember { GlassState() }
    val pageBackdrop = rememberLayerBackdrop()
    val elastic = rememberAkiraBouncingEffect()
    val scope = rememberCoroutineScope()

    CompositionLocalProvider(LocalAppBackdrop provides pageBackdrop) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .containerTransformTarget(sharedElementKey)
            .then(
                if (roundCapsule) {
                    // pinned to the screen, not the list: the colour holds at the top and settles
                    // into black over the lower half, wherever the rows have scrolled to
                    Modifier.drawBehind {
                        drawRect(Color.Black)
                        drawRect(
                            Brush.verticalGradient(
                                0f to background.value,
                                0.34f to background.value.copy(alpha = 0.86f),
                                0.78f to background.value.copy(alpha = 0.18f),
                                1f to Color.Transparent,
                            )
                        )
                    }
                } else {
                    Modifier.drawBehind { drawRect(background.value) }
                }
            )
            .then(modifier)
    ) {
        // the frosted top bar samples the cover and the list together. it sits beside this box,
        // never inside it, so the capture can't end up containing the bar that draws it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassRoot(glassState, isActive = { morph.value > 0.01f }).layerBackdrop(pageBackdrop)
                .background(background.value)
        ) {
            if (showCover) {
                PlaylistCoverBackdrop(
                    coverUrl = coverUrl,
                    cover = cover,
                    coverHeight = coverHeight,
                    scrollProgress = { scrollProgress.value },
                    background = background,
                    pull = {elastic?.topPull?:0f},
                )
            }

            LazyColumn(
                state = lazyListState,
                overscrollEffect = elastic,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
                content = content,
             flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling())
        }

        if (topBarOverride != null) {
            topBarOverride()
        } else {
            PlaylistTopBar(
                glassState = glassState,
                morph = { morph.value },
                coverUrl = coverUrl,
                cover = cover,
                background = background,
                onBackClick = onBackClick,
                onBackLongClick = onBackLongClick,
                onMoreClick = onMoreClick,
                onMiniCoverClick = { scope.launch { lazyListState.animateScrollToItem(0) } },
                roundCapsule = roundCapsule,
            )
        }

        overlay()
    }
    }
}

// the backdrop is pinned behind the list, so it has to travel most of the way up on its own
private const val CoverFollow = 0.8f
private const val CoverSoftBy = 0.55f

@Composable
private fun PlaylistCoverBackdrop(
    coverUrl: String?,
    cover: (@Composable (Modifier) -> Unit)?,
    coverHeight: Dp,
    scrollProgress: () -> Float,
    background: State<Color>,
    pull: () -> Float = {0f},
) {
    val DetailInk = LocalDetailInk.current
    val context = LocalContext.current
    // the card already holds the 544px copy in memory, so that shows instantly while the sharp one loads
    val request = remember(coverUrl) {
        ImageRequest.Builder(context)
            .data(coverUrl)
            .placeholderMemoryCacheKey(coverUrl?.let { com.akira.app.core.online.onlineArtworkUrl(it,512) })
            .build()
    }
    // where the whole frame can't be blurred (before android 12, or with blur turned off) the
    // picture alone goes soft instead: a copy blurred once, small and off the main thread, that
    // the scroll crossfades to
    val frameBlur = rememberCoverBlurAvailable()
    val softRequest: ImageRequest? = null

    ScrollAwayCover(
        progress = scrollProgress,
        follow = -CoverFollow,
        modifier = Modifier
            .fillMaxWidth()
            .height(coverHeight).akiraMediaStretch(pull),
    ) {
        if (cover != null) {
            cover(Modifier.fillMaxSize())
        } else {
            if (softRequest != null) {
                AsyncImage(
                    model = softRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        // skipped entirely until the scroll starts revealing it
                        .graphicsLayer { alpha = if (scrollProgress() > 0f) 1f else 0f },
                )
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (softRequest != null) {
                            Modifier.graphicsLayer {
                                alpha = 1f - smoothstep(0f, CoverSoftBy, scrollProgress())
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                            }
                        } else {
                            Modifier
                        }
                    ),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithCache {
                    val bg = background.value
                    val top = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.34f),
                        1f to Color.Transparent,
                        endY = size.height * 0.24f,
                    )
                    val bottom = Brush.verticalGradient(
                        0.56f to Color.Transparent,
                        0.84f to bg.copy(alpha = 0.78f),
                        1f to bg,
                    )
                    onDrawBehind {
                        drawRect(top, size = Size(size.width, size.height * 0.24f))
                        drawRect(bottom)
                    }
                }
        )
    }
}

@Composable
private fun PlaylistTopBar(
    glassState: GlassState,
    morph: () -> Float,
    coverUrl: String?,
    cover: (@Composable (Modifier) -> Unit)?,
    background: State<Color>,
    onBackClick: () -> Unit,
    onBackLongClick: () -> Unit,
    onMoreClick: (() -> Unit)?,
    onMiniCoverClick: () -> Unit,
    roundCapsule: Boolean = false,
) {
    val DetailInk = LocalDetailInk.current
    val showChrome by remember { derivedStateOf { morph() > 0.01f } }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier = Modifier.fillMaxWidth().height(statusBarTop + 96.dp).graphicsLayer {
        clip = true; compositingStrategy = CompositingStrategy.Offscreen
    }) {
        if (showChrome) {
            // the theme colour underneath keeps the sharp rows from showing through the frost
            ProgressiveGlassBackground(
                state = glassState,
                maxBlurRadius = { 34f * morph().coerceIn(0f, 1f) },
                tint = background.value.copy(alpha = 0.4f),
                foundationColor = background.value,
                direction = BlurDirection.BottomToTop,
                steps = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarTop + 96.dp)
                    .graphicsLayer { alpha = morph().coerceIn(0f, 1f) }
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 6.dp)
                .height(52.dp)
                .padding(start = 26.dp, end = 30.dp)
        ) {
            BackPill(
                morph = morph,
                glassState = glassState,
                coverUrl = coverUrl,
                cover = cover,
                tint = background.value,
                onBackClick = onBackClick,
                onBackLongClick = onBackLongClick,
                onMiniCoverClick = onMiniCoverClick,
                round = roundCapsule,
            )
            Spacer(Modifier.weight(1f))
            if (onMoreClick != null) {
                DetailHeaderControl(R.drawable.ic_more_horizontal,localizedText("Options","Opciones"),onMoreClick)
            }
        }
    }
}

/**
 * expanded it's only the back button. collapsed it's the 99x47 glass pill from the Figma, with a
 * small copy of the cover that grows out of the back button and settles beside it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackPill(
    morph: () -> Float,
    glassState: GlassState,
    coverUrl: String?,
    cover: (@Composable (Modifier) -> Unit)?,
    tint: Color,
    onBackClick: () -> Unit,
    onBackLongClick: () -> Unit,
    onMiniCoverClick: () -> Unit,
    round: Boolean = false,
) {
    val DetailInk = LocalDetailInk.current
    val density = LocalDensity.current
    // the round one is a true capsule, ends fully rounded like the Library's search field
    val pillShape = RoundedCornerShape(50)
    val miniShape = if (round) CircleShape else TrackShape

    Box(
        modifier = Modifier
            .size(width = 108.dp, height = 52.dp)
            .graphicsLayer { translationX = with(density) { 10.dp.toPx() } * morph() }
    ) {
        // the pill's size follows the spring in the layout pass, so it stretches out of the back
        // button (and overshoots a hair) without recomposing anything
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .layout { measurable, _ ->
                    val m = morph().coerceAtLeast(0f)
                    val width = lerp(40.dp, 108.dp, m).roundToPx()
                    val height = lerp(40.dp, 52.dp, m).roundToPx()
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout(width, height) { placeable.place(0, 0) }
                }
                .graphicsLayer { alpha = morph().coerceIn(0f, 1f) }
        ) {
            Box(Modifier.matchParentSize().clip(pillShape).then(
                Modifier.liquidGlass(pillShape)))

        }

        DetailHeaderControl(R.drawable.ic_navigation_back,localizedText("Back","Atrás"),onBackClick,
            Modifier.align(Alignment.CenterStart).graphicsLayer {
                translationX = with(density) { 6.dp.toPx() } * morph()
            },onLongClick=onBackLongClick)

        val showMini by remember { derivedStateOf { morph() > 0.02f } }
        if (showMini && (cover != null || !coverUrl.isNullOrEmpty())) {
            val miniModifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = 61.dp)
                .graphicsLayer {
                    // starts tucked under the back button, small, and springs out to its slot;
                    // the spring overshoots a touch, so it lands with a little give
                    val m = morph()
                    translationX = with(density) { (-45).dp.toPx() } * (1f - m)
                    val s = 0.35f + 0.65f * m
                    scaleX = s
                    scaleY = s
                    alpha = m.coerceIn(0f, 1f)
                    shape = miniShape
                    clip = true
                }
                .size(37.dp)
                .clickable(onClick = onMiniCoverClick)
            if (cover != null) {
                cover(miniModifier)
            } else {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = miniModifier,
                )
            }
        }
    }
}

/**
 * title, who made it, a stats line, then Play / shuffle (sf) / add to library (rn).
 *
 * with a [description] (albums) the line under the creator is the description and the stats move
 * below it. [compact] is for the pages with no cover of their own: the block sits at the top under
 * the bar instead of at the foot of a cover band, with a bigger title, and [below] can hang a search
 * capsule under the buttons.
 */
@Composable
fun PlaylistHeader(
    title: String,
    creators: List<CreatorUi>,
    stats: String,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSaved: Boolean? = null,
    onSaveClick: () -> Unit = {},
    sortControl: (@Composable () -> Unit)? = null,
    description: String? = null,
    onCreatorClick: ((CreatorUi) -> Unit)? = null,
    compact: Boolean = false,
    below: (@Composable () -> Unit)? = null,
) {
    val DetailInk = LocalDetailInk.current
    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(
                start = PlaylistInset + 1.dp,
                end = PlaylistInset,
                bottom = 14.dp,
            )
        ) {
            Text(
                text = title,
                style = if (compact) PlaylistCompactTitleStyle else PlaylistTitleStyle,
                color = DetailInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (creators.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                CreatorRow(creators, onCreatorClick)
            }
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(5.dp))
                PlaylistDescription(description)
            }
            if (stats.isNotEmpty()) {
                Spacer(Modifier.height(if (description.isNullOrBlank()) 3.dp else 6.dp))
                Text(
                    text = stats,
                    style = PlaylistStatsStyle,
                    color = DetailInk.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayPill(isPlaying = isPlaying, onClick = onPlayClick)
                Spacer(Modifier.width(9.dp))
                CircleAction(
                    icon = R.drawable.ic_collection_shuffle,
                    contentDescription = "Shuffle",
                    onClick = onShuffleClick,
                    spinOnClick = true,
                )
                if (isSaved != null) {
                    Spacer(Modifier.width(4.dp))
                    CircleAction(
                        icon = if (isSaved) R.drawable.ic_collection_saved else R.drawable.ic_collection_add,
                        contentDescription = if (isSaved) "In your library" else "Add to library",
                        onClick = onSaveClick,
                    )
                }
            }
            if (sortControl != null) {
                Spacer(Modifier.height(12.dp))
                sortControl()
            }
        }
        if (below != null) {
            Spacer(Modifier.height(4.dp))
            below()
            Spacer(Modifier.height(10.dp))
        }
    }

    if (compact) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = playlistCompactHeaderTop())
        ) {
            body()
        }
    } else {
        val coverHeight = playlistCoverHeight()
        Box(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = coverHeight * 0.975f),
            contentAlignment = Alignment.BottomStart,
        ) {
            Column { body() }
        }
    }
}

/** where a compact header's title starts: under the status bar and the back button's row */
@Composable
fun playlistCompactHeaderTop(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 78.dp

/** a compact header's rough height, for [PlaylistDetailScaffold]'s headerHeight */
@Composable
fun playlistCompactHeaderHeight(): Dp = playlistCompactHeaderTop() + 120.dp

internal val PlaylistCompactTitleStyle = TextStyle(
    fontFamily = AkiraFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 32.sp,
    lineHeight = 36.sp,
    letterSpacing = PlaylistTracking,
)

/**
 * an album's description where the stats line used to be: two lines, and a tap opens the rest in
 * place. the height eases with the text so the rows below glide down instead of jumping.
 */
@Composable
internal fun PlaylistDescription(text: String, modifier: Modifier = Modifier) {
    val DetailInk = LocalDetailInk.current
    var expanded by remember(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    Text(
        text = text,
        style = PlaylistStatsStyle.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
        color = DetailInk.copy(alpha = 0.74f),
        maxLines = if (expanded) Int.MAX_VALUE else 2,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        modifier = modifier
            .animateContentSize(spring(dampingRatio = 0.9f, stiffness = 420f))
            .clickable(
                enabled = overflows || expanded,
                interactionSource = null,
                indication = null,
            ) { expanded = !expanded },
    )
}

/** play and pause swap with a little pop, and the pill eases to the new label's width */
@Composable
internal fun PlayPill(isPlaying: Boolean, onClick: () -> Unit) {
    val DetailInk = LocalDetailInk.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .pressBounce(interaction)
            .height(33.dp)
            .clip(RoundedCornerShape(50))
            .background(PillFill)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .animateContentSize(spring(dampingRatio = 0.7f, stiffness = 500f))
            .padding(start = 21.dp, end = 17.dp)
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = { glyphSwap() },
            label = "playPillGlyph",
        ) { playing ->
            Icon(
                painter = painterResource(if (playing) R.drawable.ic_compact_pause else R.drawable.ic_compact_play),
                contentDescription = null,
                tint = PillContent,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(9.dp))
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = { labelSwap() },
            label = "playPillLabel",
        ) { playing ->
            Text(
                text = if (playing) localizedText("Pause", "Pausa") else localizedText("Play", "Reproducir"),
                style = PillLabelStyle,
                color = PillContent,
            )
        }
    }
}

/** the round buttons squish when pressed; shuffle gives its arrows a spin, and a changed icon pops in */
@Composable
internal fun CircleAction(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    spinOnClick: Boolean = false,
) {
    val DetailInk = LocalDetailInk.current
    val interaction = remember { MutableInteractionSource() }
    val spin = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .pressBounce(interaction)
            .size(33.dp)
            .clip(CircleShape)
            .background(PillFill)
            .clickable(interactionSource = interaction, indication = null) {
                if (spinOnClick) {
                    scope.launch {
                        spin.snapTo(0f)
                        spin.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
                    }
                }
                onClick()
            }
    ) {
        AnimatedContent(
            targetState = icon,
            transitionSpec = { glyphSwap() },
            label = "circleActionIcon",
        ) { res ->
            Icon(
                painter = painterResource(res),
                contentDescription = contentDescription,
                tint = PillContent,
                modifier = Modifier
                    .size(17.dp)
                    .graphicsLayer { rotationZ = spin.value * 360f },
            )
        }
    }
}

/** an icon swap: the new one springs up from small while the old one shrinks away */
private fun <S> AnimatedContentTransitionScope<S>.glyphSwap(): ContentTransform =
    (scaleIn(initialScale = 0.4f, animationSpec = spring(dampingRatio = 0.5f, stiffness = 700f)) + fadeIn(tween(120)))
        .togetherWith(scaleOut(targetScale = 0.4f, animationSpec = tween(110)) + fadeOut(tween(90)))
        .using(SizeTransform(clip = false))

/** a label swap: the new word rises in as the old one lifts out */
private fun <S> AnimatedContentTransitionScope<S>.labelSwap(): ContentTransform =
    (slideInVertically(spring(dampingRatio = 0.75f, stiffness = 600f)) { it / 2 } + fadeIn(tween(140)))
        .togetherWith(slideOutVertically(tween(120)) { -it / 2 } + fadeOut(tween(100)))
        .using(SizeTransform(clip = false))

/** creator avatars side by side, overlapping when there's more than one */
@Composable
private fun CreatorRow(creators: List<CreatorUi>, onClick: ((CreatorUi) -> Unit)? = null) {
    val DetailInk = LocalDetailInk.current
    val shown = creators.take(3)
    val avatar = 16.dp
    val step = 11.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // the lead creator opens; an album by several artists leads with the first of them
        modifier = if (onClick != null) {
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onClick(creators.first()) }
        } else {
            Modifier
        },
    ) {
        Box(modifier = Modifier.size(width = avatar + step * (shown.size - 1), height = avatar)) {
            shown.forEachIndexed { index, creator ->
                CreatorAvatar(
                    creator = creator,
                    size = avatar,
                    modifier = Modifier
                        .offset(x = step * index)
                        .zIndex((shown.size - index).toFloat())
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = when (creators.size) {
                1 -> creators[0].name
                2 -> "${creators[0].name} & ${creators[1].name}"
                else -> "${creators[0].name}, ${creators[1].name} & ${creators.size - 2} more"
            },
            style = PlaylistCreatorStyle,
            color = DetailInk.copy(alpha = 0.88f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PlaylistTrackRow(
    thumbnailUrl: String?,
    title: String,
    subtitle: String,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    /** an artist's row: a round photo instead of the rounded square */
    coverShape: androidx.compose.ui.graphics.Shape = TrackShape,
    /** applied to the cover itself, e.g. to open an album growing out of it */
    coverModifier: Modifier = Modifier,
    /** a song's id: marks the row when the song is downloaded or an imported file */
    songId: String? = null,
) {
    val rowMenuSource=com.akira.app.core.design.components.rememberAkiraMenuSource(hideOnOpen=false,trailingControl=true)

    val DetailInk = LocalDetailInk.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.then(rowMenuSource.modifier)
            .fillMaxWidth()
            .drawBehind {
                val start = (PlaylistInset + 39.dp + 11.dp).toPx()
                val y = size.height - .5.dp.toPx() / 2f
                drawLine(DetailInk.copy(alpha = .10f), Offset(start, y),
                    Offset(size.width - 24.dp.toPx(), y), strokeWidth = .5.dp.toPx())
            }
            .combinedClickable(
                interactionSource = null,
                indication = TrackHighlight,
                onClick = onClick,
                onLongClick = { rowMenuSource.open(onLongClick) },
            )
            .padding(start = PlaylistInset, end = 24.dp, top = 9.dp, bottom = 9.dp)
    ) {
        Box(
            modifier = Modifier
                .size(39.dp)
                .then(coverModifier)
                .clip(coverShape)
                .background(BoneColor)
        ) {
            ItemThumbnail(
                thumbnailUrl = thumbnailUrl,
                isActive = isActive,
                isPlaying = isPlaying,
                shape = coverShape,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 11.dp)
        ) {
            Text(
                text = title,
                style = TrackTitleStyle,
                color = DetailInk.copy(alpha = if (isActive) 1f else 0.94f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = TrackSubtitleStyle,
                color = DetailInk.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (trailing != null) {
            trailing()
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .akiraMenuButton(onMenuClick)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_horizontal),
                    contentDescription = null,
                    tint = DetailInk.copy(alpha = 0.78f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun PlaylistEndOfLine(songCount: Int, totalSeconds: Int, modifier: Modifier = Modifier) {
    val minutes = totalSeconds / 60
    Row(modifier.fillMaxWidth().padding(horizontal=PlaylistInset).padding(top=18.dp,bottom=6.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        Text(localizedText("$songCount ${if(songCount==1)"song" else "songs"}","$songCount ${if(songCount==1)"canción" else "canciones"}"),
            fontFamily=AkiraFontFamily,fontSize=12.sp,fontWeight=FontWeight.Medium,color=LocalDetailInk.current.copy(alpha=.62f))
        Text("$minutes min",fontFamily=AkiraFontFamily,fontSize=12.sp,fontWeight=FontWeight.Medium,color=LocalDetailInk.current.copy(alpha=.62f))
    }
}

/** header for the sections after the list, like "Featured Artist >" */
@Composable
fun PlaylistSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = true,
) {
    val DetailInk = LocalDetailInk.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(start = PlaylistInset - 4.dp, end = PlaylistInset)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Text(text = title, style = SectionTitleStyle, color = DetailInk, maxLines = 1)
        if (showChevron) {
            Spacer(Modifier.width(6.dp))
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right_small),
                contentDescription = null,
                tint = DetailInk.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}


@Composable private fun CreatorAvatar(creator: CreatorUi, size: Dp, modifier: Modifier = Modifier) {
    val DetailInk = LocalDetailInk.current
    AsyncImage(creator.avatarUrl, null, contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(CircleShape).background(DetailInk.copy(alpha=.12f)))
}
