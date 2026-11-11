package com.akira.app.feature.home

import com.akira.app.core.model.Song
import com.akira.app.core.model.SongKey
import com.akira.app.feature.collection.localAlbumKey

/** Derived collections survive tab disposal; a new song snapshot replaces the whole index. */
internal class HomeSongIndex(songs:List<Song>) {
    val byId=songs.associateBy{it.id}
    val keyById=songs.associate{it.id to SongKey.of(it).encoded}
    val byKey=songs.associateBy{keyById.getValue(it.id)}
    val added=songs.sortedByDescending{if(it.dateAddedSec>0)it.dateAddedSec else it.dateModifiedSec}.take(30)
    val favorites=songs.filter{it.isFavorite}
    val albums=songs.groupBy(::localAlbumKey).values.filter{it.first().album.isNotBlank()}.take(20)
    val artists=songs.groupBy{it.artist}.values.filter{it.first().artist.isNotBlank()}.take(20)
}

internal class HomeSongIndexCache {
    private var input:List<Song>?=null
    private var index:HomeSongIndex?=null
    fun get(songs:List<Song>):HomeSongIndex {
        if(input!==songs){input=songs;index=HomeSongIndex(songs)}
        return index!!
    }
}
