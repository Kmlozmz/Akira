package com.akira.app.feature.player

import com.akira.app.feature.library.*

internal fun playerControlsVisible(expanded: Boolean, queue: Boolean, lyrics: Boolean,
    hasLyrics: Boolean, hidden: Boolean, revealedByScroll: Boolean): Boolean =
    expanded && !queue && (!(lyrics && hasLyrics) || !hidden || revealedByScroll)
