// ExplorePage.kt

package com.akira.innertube.pages

import com.akira.innertube.models.AlbumItem
import kotlinx.serialization.Serializable

@Serializable
data class ExplorePage(
    val newReleaseAlbums: List<AlbumItem>,
    val moodAndGenres: List<MoodAndGenres.Item>,
)
