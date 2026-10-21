// SPDX-License-Identifier: GPL-3.0-only
package com.akira.app.core.design.components

import com.akira.app.core.design.motion.*
import com.akira.app.core.design.navigation.*
import com.akira.app.core.design.rendering.*

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.akira.app.R
import com.akira.app.core.i18n.LocalStrings
import com.akira.app.core.model.Song

class SongSwipeCallbacks(val next: (Song) -> Unit, val queue: (Song) -> Unit)
val LocalSongSwipeActions = staticCompositionLocalOf<SongSwipeCallbacks?> { null }

/** Local files already belong to the library: the purple action schedules next. */
@Composable
fun LocalSongSwipeRow(song: Song, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val actions = LocalSongSwipeActions.current
    val strings = LocalStrings.current
    SwipeActionsBox(modifier, enabled = actions != null && song.contentUri.isNotBlank(),
        start = { SwipeAction(R.drawable.ic_play_next, SwipeSaveColor, strings.playNext) { actions?.next?.invoke(song) } },
        end = { SwipeAction(R.drawable.ic_add_to_queue, SwipeQueueColor, strings.addToQueue) { actions?.queue?.invoke(song) } },
        content = content)
}
