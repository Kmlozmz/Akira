// PlaylistContinuationPage.kt

package com.akira.innertube.pages

import com.akira.innertube.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)
