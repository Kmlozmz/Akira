package com.akira.app.core.updates

/** Selects an authored language section, retaining unsectioned notes from older releases. */
internal fun localizedReleaseNotes(notes: String, language: String): String {
    val heading = Regex("(?im)^#{1,6}\\s+(English|Español|Spanish|en|es)\\s*$")
    val markers = heading.findAll(notes).toList()
    if (markers.isEmpty()) return notes
    val sections = markers.mapIndexed { index, marker ->
        val key = if (marker.groupValues[1].lowercase() in setOf("es", "español", "spanish")) "es" else "en"
        key to notes.substring(marker.range.last + 1, markers.getOrNull(index + 1)?.range?.first ?: notes.length).trim()
    }.toMap()
    val requested = language.substringBefore('-').substringBefore('_').lowercase()
    return sections[requested]?.takeIf { it.isNotBlank() }
        ?: sections["en"]?.takeIf { it.isNotBlank() }
        ?: sections.values.firstOrNull { it.isNotBlank() }.orEmpty()
}
