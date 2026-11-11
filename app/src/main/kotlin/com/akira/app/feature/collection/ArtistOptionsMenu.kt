package com.akira.app.feature.collection

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.runtime.Composable
import com.akira.app.core.design.components.*
import com.akira.app.core.design.icon.AkiraIcons

/** Local and online artist pages share the same menu; provider-only actions are explicit. */
@Composable
internal fun ArtistOptionsMenu(name:String,subtitle:String,favorite:Boolean,onDismiss:()->Unit,
    onPlay:()->Unit,onShuffle:()->Unit,onFavorite:()->Unit,onAlbums:(()->Unit)?,
    onInformation:()->Unit,onShare:()->Unit,onRadio:(()->Unit)?=null) {
    AkiraContextMenu(onDismiss=onDismiss,
        header={AkiraContextHeader(name,subtitle,icon=AkiraIcons.Artist)},
        quickActions=listOf(
            AkiraContextAction(AkiraIcons.Play,localizedText("Play","Reproducir"),onClick=onPlay),
            AkiraContextAction(AkiraIcons.Shuffle,localizedText("Shuffle","Aleatorio"),onClick=onShuffle)),
        groups=listOf(
            listOf(AkiraContextAction(if(favorite)AkiraIcons.StarFilled else AkiraIcons.Star,
                if(favorite)localizedText("Remove from Favorites","Quitar de favoritos")
                else localizedText("Favorite artist","Artista favorito"),selected=favorite,onClick=onFavorite)),
            buildList {
                onAlbums?.let { add(AkiraContextAction(AkiraIcons.Album,localizedText("Browse albums","Ver álbumes"),onClick=it)) }
                add(AkiraContextAction(AkiraIcons.Info,localizedText("About this artist","Acerca del artista"),
                    opensPresentation=true,onClick=onInformation))
                onRadio?.let { add(AkiraContextAction(Icons.Rounded.Radio,
                    localizedText("Start artist radio","Iniciar radio del artista"),onClick=it)) }
            },
            listOf(AkiraContextAction(AkiraIcons.Share,localizedText("Share artist","Compartir artista"),onClick=onShare))))
}
