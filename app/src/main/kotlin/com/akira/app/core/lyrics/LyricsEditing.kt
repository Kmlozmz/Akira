package com.akira.app.core.lyrics

/** Reject mixed timed/untimed lines rather than silently dropping the user's edits. */
fun parseEditedLyrics(text: String): LoadedLyrics {
    require(text.toByteArray(Charsets.UTF_8).size <= 400_000)
    val parsed = LyricsParser.parseLrcText(text)
    require(parsed.hasLyrics)
    if (parsed.isSynced) {
        val timed = Regex("\\[\\d{1,3}:[0-5]\\d(?:[.:,]\\d{2,3})?]")
        val header = Regex("\\[(ar|al|ti|au|by|length|offset|re|ve|tool|creator|version|source|la|replaygain)\\s*:.*]", RegexOption.IGNORE_CASE)
        require(text.lineSequence().filter { it.isNotBlank() }.all { timed.containsMatchIn(it) || header.matches(it.trim()) })
        require(parsed.synced.size <= 20_000)
    } else {
        // The shared parser caps plain imports at 400 lines. Never silently truncate an edit.
        require(text.lineSequence().count { it.isNotBlank() } <= 400)
    }
    return parsed
}
