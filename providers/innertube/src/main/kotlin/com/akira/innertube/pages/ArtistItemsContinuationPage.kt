// ArtistItemsContinuationPage.kt

package com.akira.innertube.pages

import com.akira.innertube.models.YTItem

data class ArtistItemsContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
