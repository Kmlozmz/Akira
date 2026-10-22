package com.akira.innertube.utils

import com.akira.innertube.models.YTItem
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Metadata snapshots contain no authenticated HTTP client or playback URLs. */
object YTItemJson {
    private val format = Json { ignoreUnknownKeys = true }
    private val items = ListSerializer(YTItem.serializer())
    fun encode(value: List<YTItem>): String = format.encodeToString(items, value)
    fun decode(value: String): List<YTItem> = format.decodeFromString(items, value)
}
