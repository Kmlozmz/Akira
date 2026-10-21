// LibraryContinuationPage.kt

package com.akira.innertube.pages

import com.akira.innertube.models.YTItem

data class LibraryContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
