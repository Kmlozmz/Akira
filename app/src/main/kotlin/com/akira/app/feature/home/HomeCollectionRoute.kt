package com.akira.app.feature.home

import com.akira.app.core.model.Song
import com.akira.app.feature.collection.DetailPage
import com.akira.app.feature.collection.localAlbumKey
import java.io.Serializable

enum class HomeCollectionKind { TRACKS, PLAYLIST, ALBUM, ARTIST }

data class HomeCollectionRoute(
    val kind: HomeCollectionKind,
    val identity: String,
    val title: String,
    val songIds: List<String>,
    val transitionKey: String? = null
) : Serializable {
    fun detailPage(): DetailPage? = when (kind) {
        HomeCollectionKind.ALBUM -> DetailPage(title, false, identity, transitionKey)
        HomeCollectionKind.ARTIST -> DetailPage(title, true, transitionKey = transitionKey)
        else -> null
    }

    fun tracks(library: List<Song>): List<Song> {
        val byId = library.associateBy { it.id }
        return songIds.mapNotNull(byId::get)
    }

    companion object {
        fun tracks(identity: String, title: String, songs: List<Song>) =
            HomeCollectionRoute(HomeCollectionKind.TRACKS, identity, title, songs.map { it.id })

        fun playlist(id: String, title: String, songs: List<Song>) =
            HomeCollectionRoute(HomeCollectionKind.PLAYLIST, id, title, songs.map { it.id }, "playlist-$id")

        fun album(song: Song, songs: List<Song>, transitionKey: String) =
            HomeCollectionRoute(HomeCollectionKind.ALBUM, localAlbumKey(song), song.album, songs.map { it.id }, transitionKey)

        fun artist(name: String, songs: List<Song>) =
            HomeCollectionRoute(HomeCollectionKind.ARTIST, name, name, songs.map { it.id }, "artist-$name")
    }
}

internal data class HomePlaylist(val id: String, val title: String, val songs: List<Song>)
