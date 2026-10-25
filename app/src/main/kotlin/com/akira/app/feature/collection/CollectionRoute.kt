package com.akira.app.feature.collection

import com.akira.app.core.model.Song

data class DetailPage(val title: String, val artist: Boolean, val albumKey: String? = null, val transitionKey: String? = null)
fun localAlbumKey(song: Song): String = "${song.albumId}|${song.album.lowercase()}|${if(song.albumId==0L)song.artist.lowercase() else ""}"
fun albumDetail(song: Song, transitionKey: String? = null) = DetailPage(song.album, false, localAlbumKey(song), transitionKey)
fun artistDetail(name: String) = DetailPage(name, true)
