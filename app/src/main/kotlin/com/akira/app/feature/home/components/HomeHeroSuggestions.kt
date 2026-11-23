package com.akira.app.feature.home.components

internal const val HomeHeroSlideLimit = 12

/** Give each shelf a turn and fill remaining slots if the library has few artists or covers. */
internal fun <T> homeHeroSuggestions(
    shelves: List<List<T>>,
    limit: Int,
    key: (T) -> String,
    diversityKey: (T) -> String?,
): List<T> {
    if (limit <= 0) return emptyList()
    val keys = hashSetOf<String>()
    val diversity = hashSetOf<String>()
    val selected = mutableListOf<T>()
    val reserves = mutableListOf<T>()
    for (index in 0 until (shelves.maxOfOrNull { it.size } ?: 0)) {
        for (shelf in shelves) {
            val item = shelf.getOrNull(index) ?: continue
            if (!keys.add(key(item))) continue
            val distinctGroup = diversityKey(item)
            if (distinctGroup == null || diversity.add(distinctGroup)) {
                selected += item
                if (selected.size == limit) return selected
            } else {
                reserves += item
            }
        }
    }
    return selected + reserves.take(limit - selected.size)
}
