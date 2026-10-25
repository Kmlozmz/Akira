package com.akira.app.feature.lyrics

import com.akira.app.core.artwork.*
import com.akira.app.feature.library.*
import com.akira.app.feature.player.*
import com.akira.app.feature.queue.*
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.liveRegion

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.annotation.OptIn
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.os.Build
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import android.content.ContentResolver
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import com.akira.app.core.i18n.LocalStrings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.akira.app.core.design.theme.AkiraFontFamily
import com.akira.app.core.design.theme.noRippleClickable
import com.akira.app.core.model.Song
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.akira.app.core.database.AppDatabase
import com.akira.app.core.database.model.LyricOffsetEntity

import com.akira.app.core.lyrics.LoadedLyrics
import com.akira.app.core.lyrics.LyricLine
import com.akira.app.core.lyrics.LyricWord
import com.akira.app.core.lyrics.LyricsParser
import com.akira.app.core.lyrics.LyricsRepository
import com.akira.app.core.lyrics.LyricTiming
import com.akira.app.core.lyrics.Vocalist

internal fun loadLyricsForSong(
    resolver: ContentResolver,
    contentUri: String,
    audioPath: String,
    title: String,
    artist: String,
    mimeType: String,
    songId: String = ""
): LoadedLyrics = LyricsRepository.loadLyricsForSong(resolver, contentUri, audioPath, title, artist, mimeType, songId)

private fun synthesizeWordTimings(text: String, startMs: Long, durationMs: Long): List<LyricWord> =
    LyricsParser.synthesizeWordTimings(text, startMs, durationMs)

/** Palabra lista para pintar: texto (con espacio final si procede) y su ventana de relleno. */
private data class LyricWordSpan(val text: String, val startMs: Long, val endMs: Long)

private val LyricTextStyle = TextStyle(
    fontFamily = AkiraFontFamily,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 30.sp,
    lineHeight = 37.sp
)

/** Estima el canto por caracteres para no extender el barrido sobre un silencio. */
private fun estimateSungDurationMs(text: String, lineDurationMs: Long): Long {
    val chars = text.count { !it.isWhitespace() }
    return if (lineDurationMs <= 6_000L) (lineDurationMs * 0.92f).toLong()
    else minOf(lineDurationMs, chars * 90L + 800L)
}

private fun buildWordSpans(line: LyricLine, nextLineTimeMs: Long?): List<LyricWordSpan> {
    val lineEnd = nextLineTimeMs ?: (line.timeMs + 4_000L)
    val lineDuration = (lineEnd - line.timeMs).coerceIn(300L, 30_000L)
    val timings = if (line.words.size >= 2) line.words
    else synthesizeWordTimings(line.text, line.timeMs, estimateSungDurationMs(line.text, lineDuration))
    if (timings.isEmpty()) return emptyList()
    return timings.mapIndexed { i, w ->
        val nextStart = timings.getOrNull(i + 1)?.timeMs
        val rawEnd = nextStart ?: lineEnd.coerceAtLeast(w.timeMs + 300L)
        // Tope: una palabra suelta antes de un silencio no se rellena durante segundos.
        val end = minOf(rawEnd, w.timeMs + 2_200L).coerceAtLeast(w.timeMs + 120L)
        LyricWordSpan(
            text = if (i < timings.lastIndex) w.text + " " else w.text,
            startMs = w.timeMs,
            endMs = end
        )
    }
}

/** Respuesta de un resorte amortiguado subamortiguado (0 → 1 con un leve rebote). */
private fun springStep(t: Float, zeta: Float = 0.7f, omega: Float = 11f): Float {
    if (t <= 0f) return 0f
    val wd = omega * kotlin.math.sqrt(1f - zeta * zeta)
    val decay = kotlin.math.exp(-zeta * omega * t)
    return 1f - decay * (kotlin.math.cos(wd * t) + (zeta * omega / wd) * kotlin.math.sin(wd * t))
}

/** La capa deja margen alrededor de la palabra para no recortar el halo del barrido. */
private fun Modifier.lyricSweepMask(
    startMs: Long,
    endMs: Long,
    smoothMs: State<Long>
): Modifier = this.drawWithContent {
    val duration = (endMs - startMs).coerceAtLeast(1L).toFloat()
    val progress = ((smoothMs.value - startMs) / duration).coerceIn(0f, 1f)
    when {
        progress <= 0f -> Unit
        progress >= 1f -> drawContent()
        else -> {
            val w = size.width
            val h = size.height
            val pad = 48.dp.toPx()
            val feather = minOf(14.dp.toPx(), w)
            val domain = w + feather
            val lead = progress * domain
            val solidUntil = ((lead - feather) / domain).coerceIn(0f, 1f)
            val fadeUntil = (lead / domain).coerceIn(0f, 1f)
            drawIntoCanvas { canvas ->
                canvas.saveLayer(Rect(-pad, -pad, w + pad, h + pad), Paint())
                drawContent()
                drawRect(
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black,
                            solidUntil to Color.Black,
                            fadeUntil to Color.Transparent,
                            1f to Color.Transparent
                        ),
                        startX = 0f,
                        endX = domain
                    ),
                    topLeft = Offset(-pad, -pad),
                    size = Size(w + 2 * pad, h + 2 * pad),
                    blendMode = BlendMode.DstIn
                )
                canvas.restore()
            }
        }
    }
}

@Composable
private fun LyricWordText(
    span: LyricWordSpan,
    isCurrentLine: Boolean,
    smoothMs: State<Long>
) {
    if (!isCurrentLine) {
        Text(text = span.text, style = LyricTextStyle, color = Color.White)
        return
    }
    val started by remember(span) { derivedStateOf { smoothMs.value >= span.startMs } }
    val context = LocalContext.current
    val reduceMotion = remember(context) { com.akira.app.core.design.components.isReduceMotionActive(context) }
    Box(Modifier.graphicsLayer {
        if (!reduceMotion) {
            val p = ((smoothMs.value - span.startMs).toFloat() / (span.endMs - span.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
            val lift = kotlin.math.sin(p * Math.PI).toFloat()
            translationY = -2.dp.toPx() * lift
            scaleX = 1f + .012f * lift; scaleY = 1f + .012f * lift
        }
    }) {
        Text(text = span.text, style = LyricTextStyle, color = Color.White.copy(alpha = 0.40f))
        if (started) {
            Text(
                text = span.text,
                style = LyricTextStyle.copy(shadow = androidx.compose.ui.graphics.Shadow(Color.White.copy(alpha = .25f), Offset.Zero, 8f)),
                color = Color.White,
                modifier = Modifier.lyricSweepMask(span.startMs, span.endMs, smoothMs)
            )
        }
    }
}

/** El flujo por palabras conserva los saltos al cambiar la línea activa. */
@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricLineRow(
    rowIndex: Int,
    line: LyricLine,
    nextTimeMs: Long?,
    distance: Int,
    smoothMs: State<Long>,
    pushOffsetFor: (Int) -> Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val isCurrent = distance == 0
    val targetAlpha = lyricFocusAlpha(distance)
    val targetBlurDp = lyricFocusBlur(distance)
    val targetScale = if (isCurrent) 1f else 0.94f

    val focusSpec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 240f)
    val alphaAnim = animateFloatAsState(targetAlpha, focusSpec, label = "lyric_alpha")
    val blurAnim = animateFloatAsState(targetBlurDp, focusSpec, label = "lyric_blur")
    val scaleAnim = animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 300f),
        label = "lyric_scale"
    )

    val spans = remember(line, nextTimeMs) { buildWordSpans(line, nextTimeMs) }

    val horizontalArrangement = when (line.vocalist) {
        Vocalist.SECONDARY -> Arrangement.End
        Vocalist.DUET -> Arrangement.Center
        Vocalist.PRIMARY -> Arrangement.Start
    }
    val transformOrigin = when (line.vocalist) {
        Vocalist.SECONDARY -> TransformOrigin(1f, 0.5f)
        Vocalist.DUET -> TransformOrigin(0.5f, 0.5f)
        Vocalist.PRIMARY -> TransformOrigin(0f, 0.5f)
    }

    FlowRow(
        horizontalArrangement = horizontalArrangement,
        modifier = Modifier
            .fillMaxWidth()
            .lyricActions(onSeek = onClick, onShare = onLongClick)
            .graphicsLayer {
                translationY = pushOffsetFor(rowIndex)
                this.alpha = alphaAnim.value
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
                this.transformOrigin = transformOrigin
                val radiusPx = blurAnim.value * density
                renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && radiusPx > 0.5f) {
                    BlurEffect(radiusPx, radiusPx, TileMode.Decal)
                } else null
            }
    ) {
        spans.forEach { span ->
            LyricWordText(span = span, isCurrentLine = isCurrent, smoothMs = smoothMs)
        }
    }
}

// DstIn permite desvanecer los bordes sin depender del color de fondo.
private fun Modifier.lyricsEdgeFade(topFadeDp: androidx.compose.ui.unit.Dp, bottomFadeDp: androidx.compose.ui.unit.Dp): Modifier =
    // La capa Offscreen evita que DstIn enmascare también el fondo dinámico.
    this.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
        drawContent()
        val h = size.height
        if (h <= 0f) return@drawWithContent
        val top = (topFadeDp.toPx() / h).coerceIn(0f, 0.6f)
        val bottom = (bottomFadeDp.toPx() / h).coerceIn(0f, 0.6f)
        if (top <= 0f && bottom <= 0f) return@drawWithContent
        drawRect(
            brush = Brush.verticalGradient(
                // DstIn desvanece los extremos transparentes y conserva el centro opaco.
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    top to Color.Black,
                    (1f - bottom) to Color.Black,
                    1f to Color.Transparent
                ),
                startY = 0f,
                endY = h
            ),
            blendMode = BlendMode.DstIn
        )
    }

// Los silencios ocupan filas propias para conservar el primer verso durante la intro.
internal sealed interface LyricRow {
    data class Line(val index: Int, val line: LyricLine) : LyricRow
    data class Silence(val glyph: String, val key: String, val sourceIndex: Int? = null, val startMs: Long = 0, val endMs: Long = 0) : LyricRow
}

internal fun buildLyricRows(
    lines: List<LyricLine>,
    minGapMs: Long,
    durationMs: Long = 0L
): List<LyricRow> {
    if (lines.isEmpty()) return emptyList()
    val rows = ArrayList<LyricRow>(lines.size + 4)
    val first = lines.first()
    if (first.timeMs > 3_000L && !isInstrumentalGlyph(first.text)) rows.add(LyricRow.Silence("♪", "intro", startMs = 0, endMs = first.timeMs))
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (isInstrumentalGlyph(line.text)) {
            val firstIndex = i
            while (i + 1 < lines.size && isInstrumentalGlyph(lines[i + 1].text)) i++
            val end = lines.getOrNull(i + 1)?.timeMs
                ?: durationMs.takeIf { it > lines[i].timeMs } ?: (lines[i].timeMs + 3000L)
            rows.add(LyricRow.Silence(line.text.trim(), "marker_$firstIndex", firstIndex,
                if (firstIndex == 0) 0L else line.timeMs, end))
        } else {
            rows.add(LyricRow.Line(i, line))
            val next = lines.getOrNull(i + 1)
            if (next != null && !isInstrumentalGlyph(next.text) && next.timeMs - line.timeMs >= minGapMs) {
                val start = maxOf(line.timeMs + 4000L, (line.words.lastOrNull()?.timeMs ?: line.timeMs) + 1000L)
                if (next.timeMs > start) rows.add(LyricRow.Silence("♫", "gap_$i", startMs = start, endMs = next.timeMs))
            }
        }
        i++
    }
    return rows
}

@Composable
internal fun LyricsHero(
    song: Song,
    lyricLines: List<LyricLine>,
    plainLyricLines: List<String>,
    currentPositionMs: Long,
    activeLineKey: Int,
    isPlaying: Boolean = true,
    onSeekLyric: (Long) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOptionsClick: () -> Unit,
    onCoverClick: () -> Unit,
    lyricsLoading: Boolean = false,
    lyricsLookupFailed: Boolean = false,
    onlineLyricsEnabled: Boolean = false,
    lyricsProvider: String = "LRCLIB",
    internetAvailable: Boolean = false,
    onSearchOnline: () -> Unit = {},
    onSaveLyrics: () -> Unit = {},
    onExportLyrics: () -> Unit = {},
    onEditLyrics: () -> Unit = {},
    onRemoveSavedLyrics: () -> Unit = {},
    lyricsSaved: Boolean = false,
    lyricsSaving: Boolean = false,
    showSourcesControl: Boolean = true,
    onOpenLyricsSources: () -> Unit = {},
    onRetryLyrics: () -> Unit = {},
    onUserScroll: (Float) -> Unit = {},
    onScrollBoundary: (Boolean) -> Unit = {},
    onListTopChanged: (Float) -> Unit = {},
    thumbModifier: Modifier = Modifier,
    actionsReferencePortrait: Boolean = true,
    actionsInk: Color = Color.White
) {
    val listState = rememberLazyListState()
    val isSynced = lyricLines.isNotEmpty()
    val density = LocalDensity.current
    val context = LocalContext.current
    val strings = LocalStrings.current
    val coroutineScope = rememberCoroutineScope()
    val haptics = com.akira.app.core.design.util.rememberAkiraHaptics()
    val lyricOffsetDao = remember(context) { AppDatabase.getInstance(context).lyricOffsetDao() }
    var shareCandidates by remember(song.id) { mutableStateOf<List<String>?>(null) }
    val songKey = remember(song.path, song.durationMs) { com.akira.app.core.model.SongKey.of(song).encoded }
    val savedOffsetEntity by lyricOffsetDao.observeOffsetForSong(songKey).collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(songKey) {
        withContext(Dispatchers.IO) { AppDatabase.getInstance(context).playbackHistoryDao().resolveOffset(song.id, songKey) }
    }
    val savedOffsetMs = LyricTiming.clampOffsetMs(savedOffsetEntity?.offsetMs ?: 0L)

    var liveOffsetMs by remember(song.id, savedOffsetMs) { mutableLongStateOf(savedOffsetMs) }
    var showCalibrationSheet by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    LaunchedEffect(savedOffsetMs) {
        liveOffsetMs = savedOffsetMs
    }

    val lyricsTopPadding = 16.dp

    val effectivePositionMs = LyricTiming.effectivePositionMs(currentPositionMs, liveOffsetMs)
    val smoothMs = if (isSynced) rememberSmoothPositionMs(effectivePositionMs, isPlaying)
        else rememberUpdatedState(effectivePositionMs)

    // Un hueco menor a 9 s se trata como respiración entre versos, no como pausa instrumental.
    val instrumentalGapMs = 9_000L
    val lyricRows = remember(lyricLines, song.durationMs) { buildLyricRows(lyricLines, instrumentalGapMs, song.durationMs) }

    // Interludes have their own intervals, including gaps without an explicit lyric marker.
    val activeRow by remember(lyricRows) {
        derivedStateOf {
            val position = smoothMs.value
            lyricRows.indexOfLast { row ->
                when (row) {
                    is LyricRow.Line -> position >= row.line.timeMs
                    is LyricRow.Silence -> position >= row.startMs
                }
            }
        }
    }

    // Las filas inferiores vuelven a su posición con un retraso escalonado tras el scroll.
    var pushPx by remember { mutableFloatStateOf(0f) }
    var pushAnchorRow by remember { mutableIntStateOf(0) }
    var pushElapsedMs by remember { mutableLongStateOf(10_000L) }
    var pushId by remember { mutableIntStateOf(0) }
    val pushOffsetFor = remember<(Int) -> Float> {
        { rowIdx ->
            val px = pushPx
            if (px == 0f) 0f
            else {
                val steps = (rowIdx - pushAnchorRow).coerceIn(0, 7)
                val t = (pushElapsedMs - steps * 34L).coerceAtLeast(0L) / 1000f
                px * (1f - springStep(t))
            }
        }
    }

    // La dirección del scroll controla el transporte; el límite evita que el player robe el gesto.
    LaunchedEffect(listState) {
        var lastIndex = listState.firstVisibleItemIndex
        androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex }
            .collect { idx ->
                val delta = idx - lastIndex
                if (delta != 0) {
                    lastIndex = idx
                    onUserScroll(if (delta < 0) -1f else 1f)
                }
            }
    }
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.canScrollBackward }
            .collect { back -> onScrollBoundary(back) }
    }

    // El scroll fija la fila arriba; el resorte por fila aporta el retraso escalonado.
    var lastScrolledRow by remember(song.id) { mutableIntStateOf(-1) }
    var previousPosition by remember(song.id) { mutableLongStateOf(currentPositionMs) }
    var seekGeneration by remember(song.id) { mutableIntStateOf(0) }
    LaunchedEffect(currentPositionMs) {
        if (currentPositionMs < previousPosition - 1_000L || currentPositionMs > previousPosition + 1_500L) {
            lastScrolledRow = -1
            pushPx = 0f
            seekGeneration++
        }
        previousPosition = currentPositionMs
    }
    LaunchedEffect(activeRow, song.id, seekGeneration) {
        val row = activeRow
        if (row < 0 || row == lastScrolledRow) return@LaunchedEffect
        val previous = lastScrolledRow
        lastScrolledRow = row
        // Sólo se anima el avance natural (1-3 filas) y si el usuario no está arrastrando.
        val animate = previous >= 0 &&
            kotlin.math.abs(row - previous) <= 3 &&
            !listState.isScrollInProgress
        val before = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == row }?.offset
        try {
            listState.scrollToItem(row)
        } catch (_: Exception) {
            return@LaunchedEffect
        }
        if (animate && before != null) {
            // layoutInfo se actualiza en el siguiente measure; el destino usa el padding conocido.
            val after = with(density) { lyricsTopPadding.toPx() }
            val delta = (before - after).toFloat()
            if (delta > 0f) {
                // Si aún quedaba un empuje a medias, se suma el resto para no dar tirones.
                pushPx = delta + pushOffsetFor(pushAnchorRow)
                pushAnchorRow = row
                pushElapsedMs = 0L
                pushId += 1
            }
        }
    }

    // Reloj del empuje: un frame-clock sólo mientras dura la animación.
    LaunchedEffect(pushId) {
        if (pushId == 0) return@LaunchedEffect
        var start = -1L
        while (true) {
            val now = withFrameMillis { it }
            if (start < 0L) start = now
            val elapsed = now - start
            pushElapsedMs = elapsed
            if (elapsed > 1_100L) break
        }
        pushPx = 0f
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .then(thumbModifier)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .noRippleClickable { onCoverClick() },
                contentAlignment = Alignment.Center
            ) {
                if (!song.albumArtUri.isNullOrBlank()) {
                    AsyncImage(
                        model = rememberPlayerArtworkRequest(song.albumArtUri),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AkiraFontFamily,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = AkiraFontFamily,
                    color = Color.White.copy(alpha = 0.60f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box {
                PlayerTrackActions(isFavorite,actionsInk,actionsReferencePortrait,
                    onFavorite=onToggleFavorite,onMore={showOptionsMenu=true})

                if (showOptionsMenu) LyricsOptionsSheet(lyricLines.isNotEmpty() || plainLyricLines.isNotEmpty(), isSynced,
                    lyricsSaved, lyricsSaving, { showOptionsMenu = false },
                    { showOptionsMenu = false; onSaveLyrics() }, { showOptionsMenu = false; onExportLyrics() },
                    { showOptionsMenu = false; onEditLyrics() }, { showOptionsMenu = false; onRemoveSavedLyrics() },
                    { showOptionsMenu = false; onOpenLyricsSources() },
                    { haptics.click(); showOptionsMenu = false; showCalibrationSheet = true },
                    { showOptionsMenu = false; onOptionsClick() })

            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showSourcesControl && (lyricLines.isNotEmpty() || plainLyricLines.isNotEmpty()),
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(240)) + androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(280)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180)) + androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(240))
        ) {
            androidx.compose.material3.TextButton(
                onClick = onOpenLyricsSources,
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
            ) {
                Icon(com.akira.app.core.design.icon.AkiraIcons.Lyrics, null, Modifier.size(18.dp), tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(strings.ui(com.akira.app.core.i18n.AkiraUiText.LyricsSourcesTitle), color = Color.White)
                Spacer(Modifier.weight(1f))
                Icon(com.akira.app.core.design.icon.AkiraIcons.ChevronRight, null, Modifier.size(18.dp), tint = Color.White.copy(alpha = .65f))
            }
        }
        when {
            isSynced -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        // Difuminado en los bordes: difumina las líneas que van
                        // desapareciendo arriba sin oscurecer la fila activa ni desplazar la lista.
                        .onGloballyPositioned { onListTopChanged(it.positionInRoot().y) }
                        .lyricsEdgeFade(topFadeDp = 20.dp, bottomFadeDp = 132.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    // El padding inferior permite subir la última estrofa hasta la fila activa.
                    contentPadding = PaddingValues(top = lyricsTopPadding, bottom = 320.dp, start = 10.dp, end = 10.dp),
                    flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()
                ) {
                    // Jerarquía: la línea activa manda; el resto retrocede en brillo,
                    // desenfoque y escala (ver LyricLineRow).
                    itemsIndexed(lyricRows, key = { _, row ->
                        when (row) {
                            is LyricRow.Line -> "l_${row.index}"
                            is LyricRow.Silence -> row.key
                        }
                    }) { rowIdx, row ->
                        when (row) {
                            is LyricRow.Silence -> {
                                InstrumentalMarker(row.startMs, row.endMs, smoothMs, rowIdx - activeRow, Modifier.fillMaxWidth()
                                    .graphicsLayer { translationY = pushOffsetFor(rowIdx) })
                            }
                            is LyricRow.Line -> {
                                LyricLineRow(
                                    rowIndex = rowIdx,
                                    line = row.line,
                                    nextTimeMs = lyricLines.getOrNull(row.index + 1)?.timeMs,
                                    distance = rowIdx - activeRow,
                                    smoothMs = smoothMs,
                                    pushOffsetFor = pushOffsetFor,
                                    onClick = { onSeekLyric(LyricTiming.seekPositionMs(row.line.timeMs, liveOffsetMs)) },
                                    onLongClick = { shareCandidates = lyricLines.drop(row.index).take(5).map { it.text } }
                                )
                            }
                        }
                    }
                }
            }
            plainLyricLines.isNotEmpty() -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 64.dp, start = 10.dp, end = 10.dp),
                    flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()
                ) {
                    items(plainLyricLines.size) { idx ->
                        Text(
                            text = plainLyricLines[idx],
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = AkiraFontFamily,
                            color = Color.White.copy(alpha = 0.75f),
                            lineHeight = 25.sp,
                            modifier = Modifier.fillMaxWidth().lyricActions(
                                onShare = { shareCandidates = plainLyricLines.drop(idx).take(5) }
                            )
                        )
                    }
                }
            }
            else -> {
                LyricsUnavailable(
                    loading = lyricsLoading,
                    failed = lyricsLookupFailed,
                    online = onlineLyricsEnabled,
                    provider = lyricsProvider,
                    internetAvailable = internetAvailable,
                    onSearchOnline = onSearchOnline,
                    onSources = onOpenLyricsSources,
                    onRetry = onRetryLyrics,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }

    shareCandidates?.let { lines -> LyricCardSheet(song, lines) { shareCandidates = null } }
    if (showCalibrationSheet && isSynced) {
        LyricCalibrationSheet(
            initialOffsetMs = savedOffsetMs,
            currentPositionMs = currentPositionMs,
            lyricLines = lyricLines,
            onDismiss = {
                liveOffsetMs = savedOffsetMs
                showCalibrationSheet = false
            },
            onOffsetChanged = { newOffset ->
                liveOffsetMs = newOffset
            },
            onSaveOffset = { finalOffset ->
                val offsetMs = LyricTiming.clampOffsetMs(finalOffset)
                liveOffsetMs = offsetMs
                coroutineScope.launch(Dispatchers.IO) {
                    lyricOffsetDao.upsertOffset(
                        LyricOffsetEntity(
                            songId = songKey,
                            offsetMs = offsetMs,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        )
    }
}


internal fun isInstrumentalGlyph(text: String): Boolean = text.trim().let { value ->
    value.isNotEmpty() && value.any { !it.isWhitespace() } &&
        value.all { it.isWhitespace() || it in "♪♫♬♩.…·•" }
}

private fun lyricFocusAlpha(distance: Int): Float = when (distance) {
    0 -> 1f
    1 -> .35f
    2 -> .14f
    3 -> .06f
    -1 -> .50f
    -2 -> .30f
    else -> if (distance > 0) .03f else .05f
}

private fun lyricFocusBlur(distance: Int): Float = when (kotlin.math.abs(distance)) {
    0 -> 0f
    1 -> 2.5f
    2 -> 6f
    3 -> 10f
    else -> 15f
}

internal fun instrumentalDotFill(positionMs: Long, startMs: Long, endMs: Long): Float =
    ((positionMs - startMs).toFloat() / ((endMs - startMs).coerceAtLeast(1) * .75f)).coerceIn(0f, 1f)

@Composable
private fun InstrumentalMarker(startMs: Long, endMs: Long, smoothMs: State<Long>, distance: Int, modifier: Modifier = Modifier) {
    val label = LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.InstrumentalMarker)
    val focus = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 240f)
    val alpha = animateFloatAsState(lyricFocusAlpha(distance), focus, label = "interludeAlpha")
    val blur = animateFloatAsState(lyricFocusBlur(distance), focus, label = "interludeBlur")
    val scale = animateFloatAsState(if (distance == 0) 1f else .94f, spring(1f, 300f), label = "interludeScale")
    androidx.compose.foundation.Canvas(modifier.height(40.dp).semantics { contentDescription = label }
        .graphicsLayer {
            this.alpha = alpha.value
            scaleX = scale.value
            scaleY = scale.value
            transformOrigin = TransformOrigin(0f, .5f)
            val radius = blur.value * density
            renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && radius > .5f)
                BlurEffect(radius, radius, TileMode.Decal) else null
        }) {
        val radius = 5.5.dp.toPx()
        val fill = instrumentalDotFill(smoothMs.value, startMs, endMs)
        repeat(3) { dot ->
            val center = Offset(radius + dot * 19.dp.toPx(), 16.dp.toPx())
            val dotFill = (fill * 3f - dot).coerceIn(0f, 1f)
            drawCircle(Color.White.copy(alpha = .28f), radius, center)
            clipRect(left = center.x - radius, right = center.x - radius + 2 * radius * dotFill) {
                drawCircle(Color.White, radius, center)
            }
        }
    }
}


@Composable
internal fun LyricsUnavailable(loading: Boolean, online: Boolean, provider: String, onSources: () -> Unit,
    onRetry: () -> Unit, internetAvailable: Boolean, onSearchOnline: () -> Unit, modifier: Modifier = Modifier,
    failed: Boolean = false) {
    val strings=LocalStrings.current
    val providerName=com.akira.app.core.lyrics.LyricsProvider.fromId(provider).displayName
    val automatic=provider=="AUTO"
    val title=when {
        loading -> com.akira.app.core.design.components.localizedText("Finding the words", "Buscando las palabras")
        failed -> com.akira.app.core.design.components.localizedText("Couldn't connect", "No se pudo conectar")
        else -> strings.noLyrics
    }
    val detail=when {
        loading && online && automatic -> com.akira.app.core.design.components.localizedText("Comparing available lyrics across all sources…", "Comparando las letras disponibles de todas las fuentes…")
        loading && online -> com.akira.app.core.design.components.localizedText("Searching $providerName for this song…", "Buscando esta canción en $providerName…")
        loading -> strings.ui(com.akira.app.core.i18n.AkiraUiText.LyricsSearchingLocal)
        failed && automatic -> com.akira.app.core.design.components.localizedText("Some sources couldn't be checked. Try again or choose a source for this song.", "No se pudieron consultar algunas fuentes. Reintenta o elige una para esta canción.")
        failed -> com.akira.app.core.design.components.localizedText("The provider didn't respond. You can try again or choose another source.", "El proveedor no respondió. Puedes reintentar o elegir otra fuente.")
        !internetAvailable -> com.akira.app.core.design.components.localizedText("No saved lyrics for this song. Connect to search online.", "Esta canción no tiene una letra guardada. Conéctate para buscarla.")
        else -> com.akira.app.core.design.components.localizedText("Look up this song and keep its lyrics for offline listening.", "Encuentra la letra de esta canción y guárdala para escuchar sin conexión.")
    }
    Column(modifier.padding(horizontal=20.dp,vertical=20.dp),horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.Center) {
        Icon(com.akira.app.core.design.icon.AkiraIcons.Lyrics,null,Modifier.size(36.dp),tint=Color.White.copy(alpha=.8f))
        Spacer(Modifier.height(20.dp))
        Text(title,fontFamily=AkiraFontFamily,fontSize=24.sp,fontWeight=FontWeight.Bold,color=Color.White,
            textAlign=androidx.compose.ui.text.style.TextAlign.Center,
            modifier=Modifier.semantics {liveRegion=androidx.compose.ui.semantics.LiveRegionMode.Polite})
        Spacer(Modifier.height(10.dp))
        Text(detail,Modifier.widthIn(max=320.dp),fontFamily=AkiraFontFamily,fontSize=15.sp,lineHeight=22.sp,
            color=Color.White.copy(alpha=.72f),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        if(internetAvailable || loading) androidx.compose.material3.Button(
            onClick=if(online)onRetry else onSearchOnline,enabled=!loading,
            modifier=Modifier.widthIn(max=300.dp).heightIn(min=48.dp),shape=RoundedCornerShape(50),
            colors=androidx.compose.material3.ButtonDefaults.buttonColors(containerColor=Color.White,contentColor=Color.Black,
                disabledContainerColor=Color.White.copy(alpha=.16f),disabledContentColor=Color.White.copy(alpha=.85f))) {
            if(loading) androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp,color=Color.White.copy(alpha=.8f))
            else Icon(androidx.compose.material.icons.Icons.Rounded.Search,null,Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(if(loading) com.akira.app.core.design.components.localizedText("Searching…", "Buscando…")
                else if(online) com.akira.app.core.design.components.localizedText("Search again", "Buscar de nuevo")
                else if(automatic) strings.ui(com.akira.app.core.i18n.AkiraUiText.LyricsAutoSearch)
                else com.akira.app.core.design.components.localizedText("Search $providerName", "Buscar en $providerName"),fontWeight=FontWeight.SemiBold)
        }
        androidx.compose.material3.TextButton(onClick=onSources,modifier=Modifier.heightIn(min=48.dp)) {
            Text(com.akira.app.core.design.components.localizedText("Lyrics sources", "Fuentes de letras"),color=Color.White.copy(alpha=.8f))
            Spacer(Modifier.width(6.dp))
            Icon(com.akira.app.core.design.icon.AkiraIcons.ChevronRight,null,Modifier.size(14.dp),tint=Color.White.copy(alpha=.7f))
        }
    }
}
