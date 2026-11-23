package com.akira.app.core.media

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.akira.app.core.media.MediaItemMapper.queueIdentity
import com.akira.app.core.model.Song
import kotlin.random.Random

internal fun shuffledPlaylistIndexes(count: Int, first: Int, random: Random = Random.Default): IntArray =
    if (first !in 0 until count) IntArray(0)
    else (listOf(first) + (0 until count).filterNot { it == first }.shuffled(random)).toIntArray()

internal fun rouletteQueue(winner: Song, pool: List<Song>, random: Random = Random.Default): List<Song> =
    listOf(winner) + pool.distinctBy { it.id }.filterNot { it.id == winner.id }.shuffled(random)

internal fun remainingQueueIndexes(order: List<Int>, currentIndex: Int): List<Int> {
    val position = order.indexOf(currentIndex)
    return if (position < 0) emptyList() else order.drop(position)
}

/** UI indexes follow playback traversal, never the physical index in a shuffled playlist. */
fun playbackQueueIndexes(player: Player): List<Int> {
    if(player is MediaController) {
        val extras=player.playlistMetadata.extras
        val order=if(extras?.containsKey(QueueEditCommand.SERVICE_SHUFFLE)==true &&
            extras.getBoolean(QueueEditCommand.SERVICE_SHUFFLE)==player.shuffleModeEnabled)
            extras.getStringArrayList(QueueEditCommand.SERVICE_ORDER) else null
        return queueIndexesFromServiceOrder((0 until player.mediaItemCount).map {player.getMediaItemAt(it).queueIdentity()},
            order) {timelineQueueIndexes(player)}
    }
    return timelineQueueIndexes(player)
}

private fun timelineQueueIndexes(player: Player): List<Int> {
    val count = player.mediaItemCount
    val timeline = player.currentTimeline
    if (!player.shuffleModeEnabled || timeline.windowCount != count) return (0 until count).toList()
    val indexes = ArrayList<Int>(count)
    var index = timeline.getFirstWindowIndex(true)
    val visited = HashSet<Int>()
    while (index in 0 until count && visited.add(index)) {
        indexes.add(index)
        index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
        if (index == C.INDEX_UNSET) break
    }
    indexes.addAll((0 until count).filterNot(visited::contains))
    return indexes
}

/** Reject an order from another queue generation while controller events catch up. */
internal fun queueIndexesFromServiceOrder(physical: List<String>, order: List<String>?, fallback: () -> List<Int>): List<Int> {
    if(order==null || order.size!=physical.size || order.toSet().size!=order.size)return fallback()
    val positions=physical.withIndex().associate {it.value to it.index}
    if(positions.size!=physical.size || order.any {it !in positions})return fallback()
    return order.map {positions.getValue(it)}
}

internal object QueueOrder {
    /** Descending contiguous ranges keep physical indexes valid and avoid one
     * timeline publication per removed song (Clear can remove thousands). */
    fun removalRanges(physical: List<String>, keep: Set<String>): List<IntRange> {
        val ranges=ArrayList<IntRange>()
        var end=physical.lastIndex
        while(end>=0) {
            if(physical[end] in keep) { end--;continue }
            var start=end
            while(start>0 && physical[start-1] !in keep)start--
            ranges.add(start..end);end=start-1
        }
        return ranges
    }
    fun insert(order: List<String>, current: String?, additions: List<String>, mode: String, before: String? = null): List<String> {
        val existing = order.toHashSet()
        val items = additions.filter { existing.add(it) }
        val index = when (mode) {
            "NEXT" -> (order.indexOf(current) + 1).coerceIn(0, order.size)
            "BEFORE" -> order.indexOf(before).takeIf { it >= 0 } ?: order.size
            else -> order.size
        }
        return order.toMutableList().apply { addAll(index, items) }
    }
    fun restore(order: List<String>, saved: List<String>): List<String> {
        val remaining = order.toMutableSet()
        return saved.filter { remaining.remove(it) } + order.filter(remaining::contains)
    }

    /** Unlike ordinary REMOVE, physical deletion includes the active occurrence. */
    fun removeSong(order: List<String>, occurrences: Map<String, String>, songId: String): List<String> =
        order.filterNot { occurrences[it] == songId }
    fun move(order: List<String>, from: String, target: String): List<String> {
        val old = order.indexOf(from); val destination = order.indexOf(target)
        if (old < 0 || destination < 0 || old == destination) return order
        return order.toMutableList().apply { add(destination, removeAt(old)) }
    }
    fun keepCurrent(order: List<String>, current: String?): List<String> =
        current?.takeIf(order::contains)?.let(::listOf) ?: order
}

internal object QueueEditCommand {
    const val INITIAL_SHUFFLE = "akira_initial_shuffle"
    const val REQUIRE_SHUFFLED_ENTRY = "akira_require_shuffled_entry"
    const val SERVICE_ORDER = "akira_queue_occurrence_order"
    const val SERVICE_SHUFFLE = "akira_queue_shuffle"
    const val ACTION = "com.akira.app.EDIT_QUEUE"
    const val OP = "operation"
    const val ITEMS = "items"
    const val MODE = "mode"
    const val ENTRY = "entry"
    const val SONG_ID = "song_id"
    const val ORDER = "order"
    const val TARGET = "target"
}
