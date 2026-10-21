package com.akira.app.core.lyrics

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Representa el vocalista o rol asignado a una línea lírica en canciones solistas o a dúo.
 */
enum class Vocalist {
    PRIMARY,   // Vocalista 1 / Principal (alineado a la izquierda)
    SECONDARY, // Vocalista 2 (alineado a la derecha)
    DUET       // Ambos / Coro / Dúo simultáneo (centrado)
}

/**
 * Marca de tiempo y texto para una palabra o sílaba en Enhanced LRC (karaoke).
 */
data class LyricWord(
    val timeMs: Long,
    val text: String
)

/**
 * Línea lírica sincronizada con su marca de tiempo, texto limpio, palabras y vocalista.
 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val vocalist: Vocalist = Vocalist.PRIMARY
)

/**
 * Contenedor de letras cargadas (sincronizadas o texto plano).
 */
data class LoadedLyrics(
    val synced: List<LyricLine> = emptyList(),
    val plain: List<String> = emptyList()
) {
    val hasLyrics: Boolean get() = synced.isNotEmpty() || plain.isNotEmpty()
    val isSynced: Boolean get() = synced.isNotEmpty()
}

/**
 * Parser unificado de letras LRC (estándar, extendido/palabra por palabra, dúo y texto plano).
 */
object LyricsParser {

    private val LrcTimestampRegex = Regex("""\[(\d{1,3}):([0-5]\d)(?:[.:,](\d{2,3}))?]""")
    private val LrcWordRegex = Regex("""<(\d{1,3}):([0-5]\d)(?:[.:,](\d{2,3}))?>""")
    private val LrcHeaderRegex = Regex(
        """\s*\[(ar|al|ti|au|by|length|offset|re|ve|tool|creator|version|source|la|replaygain)\s*:.*]\s*""",
        RegexOption.IGNORE_CASE
    )
    private val LrcOffsetRegex = Regex("""\s*\[offset\s*:\s*([+-]?\d+)]\s*""", RegexOption.IGNORE_CASE)

    // Patrones para detección de vocalistas (v1:, [v1], (v1), etc.)
    private val VocalistPrimaryPrefixRegex = Regex(
        """^(?:\[v1\]|\(v1\)|\[1\]|\(1\)|v1\s*:|1\s*:)\s*""",
        RegexOption.IGNORE_CASE
    )
    private val VocalistSecondaryPrefixRegex = Regex(
        """^(?:\[v2\]|\(v2\)|\[2\]|\(2\)|v2\s*:|2\s*:)\s*""",
        RegexOption.IGNORE_CASE
    )
    private val VocalistDuetPrefixRegex = Regex(
        """^(?:\[both\]|\(both\)|\[all\]|\(all\)|\[duet\]|\(duet\)|both\s*:|all\s*:|duet\s*:)\s*""",
        RegexOption.IGNORE_CASE
    )

    fun parseLrcText(rawText: String): LoadedLyrics {
        if (rawText.isBlank()) return LoadedLyrics()

        val lines = rawText.lineSequence()
        val parsed = mutableListOf<LyricLine>()
        val plainFallback = mutableListOf<String>()
        var offsetMs = 0L

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            // 1. Manejo de etiqueta [offset:+/-ms]
            LrcOffsetRegex.find(line)?.let { m ->
                offsetMs = m.groupValues[1].toLongOrNull() ?: 0L
                continue
            }

            // 2. Omitir metadatos de cabecera como [ar: artista], [ti: título], etc.
            if (LrcHeaderRegex.matches(line)) continue

            // 3. Extraer timestamps de línea [mm:ss.xx]
            val timeMatches = LrcTimestampRegex.findAll(line).toList()
            if (timeMatches.isEmpty()) {
                val clean = stripAnyTimestamp(line)
                if (clean.isNotBlank()) plainFallback.add(clean)
                continue
            }

            // Obtener el cuerpo de texto tras el último timestamp de línea
            val body = line.substring(timeMatches.last().range.last + 1).trim()

            // Detectar vocalista y remover prefijos de voz
            val (vocalist, cleanBody) = extractVocalistAndClean(body)

            val cleanText = stripAnyTimestamp(cleanBody)
            val words = parseWordTimings(cleanBody)

            for (tm in timeMatches) {
                val t = timeFromStamp(tm.groupValues[1], tm.groupValues[2], tm.groupValues[3]) ?: continue
                val adjusted = (t + offsetMs).coerceAtLeast(0L)
                val shiftedWords = if (offsetMs != 0L) {
                    words.map { it.copy(timeMs = (it.timeMs + offsetMs).coerceAtLeast(0L)) }
                } else words
                parsed.add(LyricLine(adjusted, cleanText, shiftedWords, vocalist))
            }
        }

        val sorted = parsed.sortedBy { it.timeMs }
        return if (sorted.isNotEmpty()) LoadedLyrics(synced = sorted)
        else LoadedLyrics(plain = plainFallback.take(400))
    }

    /**
     * Identifica si la línea corresponde a Vocalist 1, 2 o Dúo, y remueve la marca técnica.
     */
    fun extractVocalistAndClean(body: String): Pair<Vocalist, String> {
        var trimmed = body.trim()

        if (VocalistDuetPrefixRegex.containsMatchIn(trimmed)) {
            trimmed = trimmed.replace(VocalistDuetPrefixRegex, "").trim()
            return Vocalist.DUET to trimmed
        }
        if (VocalistSecondaryPrefixRegex.containsMatchIn(trimmed)) {
            trimmed = trimmed.replace(VocalistSecondaryPrefixRegex, "").trim()
            return Vocalist.SECONDARY to trimmed
        }
        if (VocalistPrimaryPrefixRegex.containsMatchIn(trimmed)) {
            trimmed = trimmed.replace(VocalistPrimaryPrefixRegex, "").trim()
            return Vocalist.PRIMARY to trimmed
        }

        return Vocalist.PRIMARY to trimmed
    }

    /**
     * Parsea timestamps de Enhanced LRC con marcas de palabras tipo `<mm:ss.xx>`.
     */
    fun parseWordTimings(body: String): List<LyricWord> {
        if (!LrcWordRegex.containsMatchIn(body)) return emptyList()
        val words = mutableListOf<LyricWord>()
        val matches = LrcWordRegex.findAll(body).toList()
        matches.forEachIndexed { i, m ->
            val t = timeFromStamp(m.groupValues[1], m.groupValues[2], m.groupValues[3]) ?: return@forEachIndexed
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else body.length
            val raw = body.substring(m.range.last + 1, end).trim()
            val text = raw.trim()
            if (text.isNotBlank()) words.add(LyricWord(t, text))
        }
        return words
    }

    /**
     * Sintetiza marcas de tiempo por palabra distribuyendo la duración en base a la longitud de cada palabra.
     */
    fun synthesizeWordTimings(text: String, startMs: Long, durationMs: Long): List<LyricWord> {
        val words = text.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val weights = words.map { it.length.coerceAtLeast(1) }
        val totalWeight = weights.sum().coerceAtLeast(1)
        val safeDuration = durationMs.coerceIn(300L, 60_000L)
        val out = ArrayList<LyricWord>(words.size)
        var acc = 0
        words.forEachIndexed { i, w ->
            out.add(LyricWord(startMs + (safeDuration * acc / totalWeight), w))
            acc += weights[i]
        }
        return out
    }

    fun timeFromStamp(min: String, sec: String, frac: String?): Long? {
        val m = min.toLongOrNull() ?: return null
        val s = sec.toLongOrNull() ?: return null
        if (s !in 0..59) return null
        val msPart = when (frac?.length ?: 0) {
            0 -> 0L
            1 -> (frac!!.toLongOrNull() ?: 0L) * 100L
            2 -> (frac!!.toLongOrNull() ?: 0L) * 10L
            else -> frac!!.take(3).toLongOrNull() ?: 0L
        }
        return m * 60_000L + s * 1000L + msPart
    }

    fun stripAnyTimestamp(text: String): String =
        text.replace(LrcWordRegex, " ").replace(LrcTimestampRegex, " ").replace(Regex("""\s+"""), " ").trim()

    /**
     * Decodifica bytes con detección automática de codificación (UTF-8 con/sin BOM, UTF-16, GBK, ISO-8859-1).
     */
    fun decodeLyricBytes(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
        }
        val utf8 = String(bytes, StandardCharsets.UTF_8)
        if (!utf8.contains('\uFFFD')) return utf8
        return try {
            val gbk = Charset.forName("GB18030")
            val gbkStr = String(bytes, gbk)
            if (!gbkStr.contains('\uFFFD')) gbkStr else String(bytes, StandardCharsets.ISO_8859_1)
        } catch (_: Exception) {
            String(bytes, StandardCharsets.ISO_8859_1)
        }
    }
}
