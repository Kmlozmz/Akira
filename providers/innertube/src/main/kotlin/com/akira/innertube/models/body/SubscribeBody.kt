// SubscribeBody.kt

package com.akira.innertube.models.body

import com.akira.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class SubscribeBody(
    val channelIds: List<String>,
    val context: Context,
)
