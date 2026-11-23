package com.akira.app.feature.search

import com.akira.app.core.model.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalSearchIndexTest {
    @Test
    fun wordMatchingIgnoresAccentsAndPunctuationAndRanksTheExactSongFirst() = runBlocking {
        val exact = Song("exact", "Canción", "Álvaro Díaz", "Álbum", 10_000, "content://music/1")
        val prefix = exact.copy(id = "prefix", title = "Canción en vivo")
        val album = exact.copy(id = "album", title = "Otra", album = "Canción")
        val apostrophe = exact.copy(id = "apostrophe", title = "Ain’t it fun", artist = "Paramore")
        val index = LocalSearchIndex.build(listOf(album, prefix, exact, apostrophe))
        assertEquals(listOf(exact, prefix, album), index.search("cancion"))
        assertEquals(setOf(exact, prefix, album), index.search("cancion alvaro").toSet())
        assertEquals(listOf(apostrophe), index.search("aint   fun"))
        assertEquals(emptyList<Song>(), index.search("no match"))
    }
}
