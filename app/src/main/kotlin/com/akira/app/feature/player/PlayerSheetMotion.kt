// SPDX-License-Identifier: GPL-3.0-only
// Page bb28993, BottomSheet.kt PlayerSheetAnimationSpec / SheetSwipeCommit.
package com.akira.app.feature.player

import com.akira.app.core.artwork.*
import com.akira.app.feature.library.*
import com.akira.app.feature.lyrics.*

import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp

internal val PlayerSheetSwipeCommit = 8.dp

/** Direct manipulation keeps finger velocity; taps settle promptly without a long tail.
 * The former Page spring (80/.96/.0002) kept the sheet moving for over a second. */
internal fun playerSheetAnimationSpec() = spring<Float>(
    dampingRatio = .86f,
    stiffness = 650f,
    visibilityThreshold = .0015f,
)

/** One coordinate for the entire route: no early stop or second trajectory. */
internal fun playerArtworkTravelProgress(progress: Float): Float = progress.coerceIn(0f,1f)

internal fun playerArtworkSettledAlpha(progress: Float): Float =
    if(progress >= .999f)1f else 0f

internal fun playerArtworkTravellerAlpha(progress: Float): Float =
    if(progress > .001f && progress < .999f)1f else 0f

internal fun playerCoverRestScale(isPlaying: Boolean, fullArtwork: Boolean, landscape: Boolean): Float =
    if (!isPlaying && !fullArtwork && !landscape) .73f else 1f
