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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
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
import android.os.SystemClock
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.first

import com.akira.app.core.lyrics.LoadedLyrics
import com.akira.app.core.lyrics.LyricLine
import com.akira.app.core.lyrics.LyricWord
import com.akira.app.core.lyrics.LyricsParser
import com.akira.app.core.lyrics.LyricsRepository
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

// ─────────────────────────────────────────────────────────────────────────────
// Motor de letras sincronizadas
//
// La línea activa se pinta con un BARRIDO continuo dentro de cada palabra (estilo

// suave y un glow que sólo existe donde ya hay relleno. Las demás líneas se
// atenúan y desenfocan con la distancia, y al cambiar de verso la lista se mueve
// con un empuje elástico (resorte amortiguado) escalonado por fila.
// ─────────────────────────────────────────────────────────────────────────────

/** Palabra lista para pintar: texto (con espacio final si procede) y su ventana de relleno. */
private data class LyricWordSpan(val text: String, val startMs: Long, val endMs: Long)

private val LyricTextStyle = TextStyle(
    fontFamily = AkiraFontFamily,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 30.sp,
    lineHeight = 37.sp
)

/**
 * Cuánto dura realmente el canto de una línea cuando no hay tiempos por palabra.
 * Una línea seguida de un hueco largo (instrumental) no debe barrerse durante todo
 * el hueco: se estima por número de caracteres.
 */
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

/**
 * Posición de reproducción interpolada por frame. El reproductor publica la
 * posición cada ~250 ms; con eso el relleno de una palabra avanzaría a saltos.
 * Aquí se avanza con el reloj del sistema y sólo se re-ancla cuando la posición
 * real se desvía (seek, buffering) o cambia play/pausa.
 */
@Composable
private fun rememberSmoothPositionMs(positionMs: Long, isPlaying: Boolean): State<Long> {
    val smooth = remember { mutableLongStateOf(positionMs) }
    val latestPos by rememberUpdatedState(positionMs)
    val latestPlaying by rememberUpdatedState(isPlaying)
    LaunchedEffect(Unit) {
        var anchorPos = latestPos
        var anchorAt = SystemClock.elapsedRealtime()
        var lastSeen = latestPos
        while (true) {
            if (!latestPlaying) {
                // En pausa: seguir la posición (por si se hace seek) hasta que vuelva a sonar.
                snapshotFlow { Pair(latestPos, latestPlaying) }
                    .first { (pos, playing) ->
                        smooth.longValue = pos
                        playing
                    }
                anchorPos = latestPos
                anchorAt = SystemClock.elapsedRealtime()
                lastSeen = latestPos
            }
            withFrameMillis { }
            val now = SystemClock.elapsedRealtime()
            val seen = latestPos
            if (seen != lastSeen) {
                lastSeen = seen
                val predicted = anchorPos + (now - anchorAt)
                if (kotlin.math.abs(seen - predicted) > 150L) {
                    anchorPos = seen
                    anchorAt = now
                }
            }
            smooth.longValue = anchorPos + (now - anchorAt)
        }
    }
    return smooth
}

/**
 * Dibuja el contenido sólo hasta la fracción de progreso de la palabra, con un
 * borde suave (feather). Usa una capa propia ligeramente MÁS GRANDE que la palabra
 * para que el glow (sombra) no se recorte en los bordes del texto.
 */
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
        // Capa base: la palabra aún sin cantar.
        Text(text = span.text, style = LyricTextStyle, color = Color.White.copy(alpha = 0.40f))
        // Capa de relleno: blanca con halo suave, revelada de izquierda a derecha.
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

/**
 * Una línea de letra. TODAS las líneas se componen como un flujo de palabras (así
 * el salto de línea es idéntico antes y después de ser la activa y el texto no
 * "se reacomoda" al cambiar de verso). El foco (opacidad, desenfoque y escala) se
 * anima con resortes en vez de saltar.
 *
 * Jerarquía visual: la línea activa es la única plenamente visible. Las vecinas
 * bajan MUCHO de brillo (antes la siguiente estaba al 38 % y sin desenfoque, por
 * eso competía con la activa).
 */
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
    val targetAlpha = when (distance) {
        0 -> 1f
        1 -> 0.35f
        2 -> 0.14f
        3 -> 0.06f
        -1 -> 0.50f
        -2 -> 0.30f
        else -> if (distance > 0) 0.03f else 0.05f
    }
    val targetBlurDp = when (kotlin.math.abs(distance)) {
        0 -> 0f
        1 -> 2.5f
        2 -> 6f
        3 -> 10f
        else -> 15f
    }
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
            .graphicsLayer {
                // Empuje elástico entre versos.
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
            .pointerInput(onClick, onLongClick) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() })
            }
    ) {
        spans.forEach { span ->
            LyricWordText(span = span, isCurrentLine = isCurrent, smoothMs = smoothMs)
        }
    }
}

// Máscara de desvanecido vertical: difumina el contenido en los bordes
// superior e inferior en lugar de cortarlo en seco. Usa BlendMode.DstIn,
// así funciona sea cual sea el color del fondo (no es un rectángulo opaco).
private fun Modifier.lyricsEdgeFade(topFadeDp: androidx.compose.ui.unit.Dp, bottomFadeDp: androidx.compose.ui.unit.Dp): Modifier =
    // Offscreen: aísla la letra en su propia capa. Sin esto, el DstIn del
    // degradado también afecta al fondo dinámico que hay detrás y deja un
    // rectángulo oscuro visible en vez de difuminar sólo el texto.
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
                // DstIn conserva el contenido donde el brush es opaco y lo
                // borra donde es transparente: por eso los bordes van en
                // Transparent (desvanecen) y el centro en opaco (se mantiene).
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

// Fila que se dibuja en la lista: puede ser un verso real o un marcador de
// silencio. Los marcadores son FILAS PROPIAS, no sustituyen al verso: así el
// primer verso nunca "aparece de la nada" cuando arranca la canción.
internal sealed interface LyricRow {
    data class Line(val index: Int, val line: LyricLine) : LyricRow
    data class Silence(val glyph: String, val key: String, val sourceIndex: Int? = null, val startMs: Long = 0, val endMs: Long = 0) : LyricRow
}

// Construye las filas incluyendo marcadores de silencio: "♪" en la intro y
// "♫" en las pausas considerables entre versos.
internal fun buildLyricRows(
    lines: List<LyricLine>,
    minGapMs: Long
): List<LyricRow> {
    if (lines.isEmpty()) return emptyList()
    val rows = ArrayList<LyricRow>(lines.size + 4)
    val first = lines.first()
    if (first.timeMs > 3_000L && !isInstrumentalGlyph(first.text)) rows.add(LyricRow.Silence("♪", "intro", startMs = 0, endMs = first.timeMs))
    lines.forEachIndexed { i, line ->
        rows.add(if (isInstrumentalGlyph(line.text)) LyricRow.Silence(line.text.trim(), "marker_$i", i, line.timeMs, lines.getOrNull(i + 1)?.timeMs ?: line.timeMs + 3000) else LyricRow.Line(i, line))
        val next = lines.getOrNull(i + 1) ?: return@forEachIndexed
        if (next.timeMs - line.timeMs >= minGapMs) {
            rows.add(LyricRow.Silence("♫", "gap_$i", startMs = line.timeMs + 4000L, endMs = next.timeMs))
        }
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
    val savedOffsetMs = savedOffsetEntity?.offsetMs ?: 0L

    var liveOffsetMs by remember(song.id, savedOffsetMs) { mutableLongStateOf(savedOffsetMs) }
    var showCalibrationSheet by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    LaunchedEffect(savedOffsetMs) {
        liveOffsetMs = savedOffsetMs
    }

    // Fila activa fijada a 16 dp bajo la cabecera (rango 12-20 dp, hueco <= 24 dp)
    val lyricsTopPadding = 16.dp

    // Reloj suave: la posición real llega ~4 veces por segundo; entre medias se
    // interpola por frame para que el relleno de cada palabra sea continuo.
    val effectivePositionMs = (currentPositionMs + liveOffsetMs).coerceAtLeast(0L)
    val smoothMs = rememberSmoothPositionMs(effectivePositionMs, isPlaying)

    // Intro: antes de la primera línea no hay línea activa (-1). Se deriva del
    // reloj suave, así sólo recompone cuando REALMENTE cambia de verso.
    val activeIndex by remember(lyricLines) {
        derivedStateOf {
            if (!isSynced) -1
            else {
                val p = smoothMs.value
                if (p < lyricLines.first().timeMs) -1
                else lyricLines.indexOfLast { it.timeMs <= p }.coerceAtLeast(0)
            }
        }
    }

    // MARKERS DE SILENCIO: muy restrictivos a propósito. Con 4s salían notas
    // por todas partes porque casi cualquier verso tiene un respiro de 4-5s.
    // Sólo cuenta como pausa real un hueco de 9s o más (intro/outro/break).
    val instrumentalGapMs = 9_000L
    val lyricRows = remember(lyricLines) { buildLyricRows(lyricLines, instrumentalGapMs) }

    // Fila activa. OJO: la lista incluye filas de silencio (♪/♫), así que el índice
    // de FILA no coincide con el de LÍNEA. Antes el scroll usaba el índice de línea
    // y, tras el primer marcador de silencio, la letra se desalineaba.
    val activeRow by remember(lyricRows) {
        derivedStateOf {
            val currentLineIndex = activeIndex
            if (currentLineIndex < 0) {
                if (lyricRows.firstOrNull() is LyricRow.Silence) 0 else -1
            } else {
                lyricRows.indexOfFirst { (it is LyricRow.Line && it.index == currentLineIndex) || (it is LyricRow.Silence && it.sourceIndex == currentLineIndex) }
            }
        }
    }

    // Empuje elástico: tras mover la lista de golpe, cada fila arranca visualmente
    // en su posición anterior (pushPx) y vuelve a 0 siguiendo un resorte. Las filas
    // de la línea activa hacia abajo se retrasan unos ms cada una (efecto cascada).
    // Todo se lee en la fase de dibujo: no recompone nada por frame.
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

    // Al hacer scroll aparecen de nuevo los controles; al subir se ocultan.
    // Además avisamos al player para que NO robe el gesto vertical mientras la
    // lista aún tiene letras por recorrer (si no, cerrar el reproductor se
    // comía el scroll de las lyrics).
    // Dirección del scroll: scrollear ABAJO (buscar letras anteriores) vuelve a
    // enseñar los controles; scrollear ARRIBA los vuelve a ocultar.
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

    // Auto-scroll: la fila activa queda pegada arriba. El salto en sí es instantáneo
    // (scrollToItem) y el movimiento visible lo aporta el empuje elástico por fila;
    // así cada línea puede tener su propio retraso, cosa imposible con un único
    // animateScrollToItem lineal.
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
            // El desplazamiento final NO se lee de layoutInfo: scrollToItem muta el
            // estado de scroll al instante, pero visibleItemsInfo sólo se recalcula en
            // el siguiente measure, así que leerlo aquí devolvía la posición ANTIGUA
            // (delta == 0) y el empuje elástico no llegaba a animarse nunca.
            // El destino es analítico: scrollToItem deja la fila pegada al inicio de la
            // ventana, es decir justo tras el contentPadding superior.
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
        // Mini header (como el video): portada + título/artista + star + …
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
                    // Fila activa anclada a 16 dp bajo la cabecera (hueco <= 24 dp).
                    // Padding inferior amplio (320 dp) para que la última estrofa pueda subir
                    // hasta la posición activa de lectura sin quedar encallada abajo.
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
                                // Marcador de silencio: fila propia, atenuada.
                                InstrumentalMarker(row.startMs, row.endMs, smoothMs, Modifier.fillMaxWidth()
                                    .graphicsLayer { translationY = pushOffsetFor(rowIdx) })
                            }
                            is LyricRow.Line -> {
                                LyricLineRow(
                                    rowIndex = rowIdx,
                                    line = row.line,
                                    nextTimeMs = lyricLines.getOrNull(row.index + 1)?.timeMs,
                                    distance = row.index - activeIndex,
                                    smoothMs = smoothMs,
                                    pushOffsetFor = pushOffsetFor,
                                    onClick = { onSeekLyric((row.line.timeMs - liveOffsetMs).coerceAtLeast(0L)) },
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
                            modifier = Modifier.fillMaxWidth().pointerInput(idx, plainLyricLines) {
                                detectTapGestures(onLongPress = { shareCandidates = plainLyricLines.drop(idx).take(5) })
                            }
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
                liveOffsetMs = finalOffset
                coroutineScope.launch(Dispatchers.IO) {
                    lyricOffsetDao.upsertOffset(
                        LyricOffsetEntity(
                            songId = songKey,
                            offsetMs = finalOffset,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        )
    }
}


internal fun isInstrumentalGlyph(text: String): Boolean = text.trim().let { value ->
    value.isNotEmpty() && value.all { it.isWhitespace() || it in "♪♫♬♩" }
}

/** The reference fills all three dots together, then holds before the next verse. */
internal fun instrumentalDotFill(positionMs: Long, startMs: Long, endMs: Long): Float =
    ((positionMs - startMs).toFloat() / ((endMs - startMs).coerceAtLeast(1) * .75f)).coerceIn(0f, 1f)

@Composable
private fun InstrumentalMarker(startMs: Long, endMs: Long, smoothMs: State<Long>, modifier: Modifier = Modifier) {
    val label = LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.InstrumentalMarker)
    androidx.compose.foundation.Canvas(modifier.height(40.dp).semantics { contentDescription = label }) {
        val radius = 5.5.dp.toPx()
        val fill = instrumentalDotFill(smoothMs.value, startMs, endMs)
        repeat(3) { dot ->
            val center = Offset(radius + dot * 19.dp.toPx(), 16.dp.toPx())
            drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color.White.copy(alpha = .18f * fill), Color.Transparent), center, radius * 2f), radius * 2f, center)
            drawCircle(Color.White.copy(alpha = .28f), radius, center)
            clipRect(top = center.y + radius - 2 * radius * fill, bottom = center.y + radius) {
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
