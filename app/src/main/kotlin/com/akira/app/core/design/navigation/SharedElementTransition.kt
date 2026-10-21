// SPDX-License-Identifier: GPL-3.0-only
// Page bb28993, local collections use stable album-/playlist- keys.
// SharedElementTransition.kt

package com.akira.app.core.design.navigation

import com.akira.app.core.design.components.*
import com.akira.app.core.design.motion.*
import com.akira.app.core.design.rendering.*

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedContentScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * shared cover between a grid card and the screen it opens. playlists open with the full container
 * transform (the whole screen grows out of the card), albums keep the plain cover morph.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedArtworkElement(
    key: String?,
    cornerRadius: Dp = ContentCardCornerRadius,
    coverUrl: String? = null,
): Modifier {
    if (key == null) return this
    // playlists and albums open the same way now: the page grows out of the card
    if (key.startsWith("playlist-") || key.startsWith("album-")) return containerTransformSource(key, cornerRadius, coverUrl)
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalNavAnimatedContentScope.current ?: return this
    return with(sharedTransitionScope) {
        this@sharedArtworkElement.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
}
