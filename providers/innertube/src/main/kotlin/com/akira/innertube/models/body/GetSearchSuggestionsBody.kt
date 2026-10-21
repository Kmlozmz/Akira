// GetSearchSuggestionsBody.kt

package com.akira.innertube.models.body

import com.akira.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetSearchSuggestionsBody(
    val context: Context,
    val input: String,
)
