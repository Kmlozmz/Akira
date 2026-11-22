package com.akira.app.feature.player

internal fun playerControlsVisible(expanded: Boolean, queue: Boolean, lyrics: Boolean,
    hasLyrics: Boolean, hidden: Boolean, revealedByScroll: Boolean, queueReading: Boolean = false): Boolean =
    expanded && if (queue) !queueReading else !lyrics || !hasLyrics || !hidden || revealedByScroll
