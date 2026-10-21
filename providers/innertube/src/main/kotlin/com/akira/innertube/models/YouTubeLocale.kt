// YouTubeLocale.kt

package com.akira.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class YouTubeLocale(
    val gl: String,
    val hl: String,
)
