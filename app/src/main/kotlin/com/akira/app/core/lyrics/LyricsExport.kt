package com.akira.app.core.lyrics

/** Portable sidecar: retain provided word timestamps and vocalist tags. */
fun LoadedLyrics.sidecarText(): String {
    if (!isSynced) return plain.joinToString("\n")
    fun time(ms: Long): String {
        val safe = ms.coerceAtLeast(0)
        return "%02d:%02d.%03d".format(java.util.Locale.ROOT, safe / 60000, safe / 1000 % 60, safe % 1000)
    }
    return synced.joinToString("\n") { line ->
        val role = when (line.vocalist) { Vocalist.PRIMARY -> ""; Vocalist.SECONDARY -> "[v2] "; Vocalist.DUET -> "[duet] " }
        val text = if (line.words.isEmpty()) line.text else line.words.joinToString(" ") { "<${time(it.timeMs)}>${it.text.trim()}" }
        "[${time(line.timeMs)}]$role$text"
    }
}
