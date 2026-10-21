// GetTranscriptBody.kt

package com.akira.innertube.models.body

import com.akira.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetTranscriptBody(
    val context: Context,
    val params: String,
)
