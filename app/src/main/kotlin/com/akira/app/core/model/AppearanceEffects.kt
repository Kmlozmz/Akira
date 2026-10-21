package com.akira.app.core.model

/** Preserve old Space choices as an independent effect on the base theme. */
fun baseAppearanceId(storedId: String): String = AppearancePreset.fromId(storedId).let {
    if (it.space) AppearancePreset.MIDNIGHT.id else it.id
}
fun spaceEnabledForTheme(storedId: String, enabledThemes: Set<String>?): Boolean =
    enabledThemes?.contains(baseAppearanceId(storedId)) ?: AppearancePreset.fromId(storedId).space
