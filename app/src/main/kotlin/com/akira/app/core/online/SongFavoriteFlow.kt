package com.akira.app.core.online

import android.content.Context
import com.akira.app.core.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Online likes belong to the connected account, local favorites to the device library. */
internal fun songFavoriteFlow(context: Context, song: Song): Flow<Boolean> =
    if (song.contentUri.startsWith("akira-youtube:")) OnlineRepository.get(context).likedIds.map { song.id.removePrefix("youtube:") in it }
    else com.akira.app.core.database.AppDatabase.getInstance(context).favoritesDao().isFavorite(song.id)
