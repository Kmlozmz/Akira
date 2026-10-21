package com.akira.app.feature.library

import com.akira.app.core.artwork.*
import com.akira.app.feature.lyrics.*
import com.akira.app.feature.player.*
import com.akira.app.feature.queue.*

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akira.app.core.model.Song

/** Queue and saved playlists share a stable drag gesture and floating row. */
@Composable
internal fun EditableSongList(songs: List<Song>, onClick: (Song) -> Unit,
    onRemove: (Song) -> Unit, onOrder: (List<Song>) -> Unit, modifier: Modifier = Modifier) {
    ReorderableSongList(songs, onClick, onRemove,
        onMove = { from, to -> onOrder(songs.toMutableList().apply { add(to, removeAt(from)) }) },
        modifier = modifier, bottomPadding = 120.dp)
}
