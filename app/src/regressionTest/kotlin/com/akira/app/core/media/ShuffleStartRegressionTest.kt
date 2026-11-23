package com.akira.app.core.media

import com.akira.app.core.model.Song
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShuffleStartRegressionTest {
    @Test
    fun everyOtherTrackIsAfterTheSelectedTrackInRandomOrder() {
        val orders = (0..20).map { shuffledPlaylistIndexes(7, 5, Random(it)).toList() }
        assertTrue(orders.all { order -> order.first() == 5 && order.sorted() == (0..6).toList() })
        assertTrue(orders.distinct().size > 1)
        assertEquals(listOf(0), shuffledPlaylistIndexes(1, 0).toList())
    }

    @Test
    fun rouletteStartsTheWinnerAndBuildsItsQueueFromTheCurrentPool() {
        fun song(id: String) = Song(id, id, "Artist", "Album", 10_000, "content://music/$id")
        val winner = song("winner")
        val first = song("first")
        val second = song("second")
        val result = rouletteQueue(winner, listOf(first, winner, second, first), Random(42))
        assertEquals(winner, result.first())
        assertEquals(setOf(first, second), result.drop(1).toSet())
        assertEquals(3, result.size)
    }
}
