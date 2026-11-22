package com.akira.app.feature.collection

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.akira.app.core.design.components.*
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.design.theme.CurrentAkiraFontFamily as AkiraFontFamily

@Composable
internal fun AkiraArtistMasthead(name: String, stats: String, favorite: Boolean,
    playing: Boolean, onPlay: ()->Unit, onFavorite: ()->Unit, onShuffle: ()->Unit, modifier: Modifier=Modifier) {
    val ink=LocalDetailInk.current
    Column(modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
        Text(name,color=ink,fontFamily=AkiraFontFamily,fontSize=30.sp,lineHeight=34.sp,
            fontWeight=FontWeight.Bold,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        Text(stats,Modifier.padding(top=5.dp),color=ink.copy(alpha=.62f),fontFamily=AkiraFontFamily,
            fontSize=11.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        CollectionPlaybackActions(playing, onPlay, onShuffle) {
            AkiraIconButton(if (favorite) AkiraIcons.StarFilled else AkiraIcons.Star,
                localizedText("Favorite artist", "Artista favorito"),
                onFavorite, tint = ink, glyphSize = 22.dp, surfaceSize = 48.dp, popOnTrue = favorite)
        }
    }
}

@Composable
internal fun CollectionPlaybackActions(
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val ink = LocalDetailInk.current
    Row(
        Modifier.widthIn(max = if (trailing == null) 224.dp else 284.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AkiraIconButton(AkiraIcons.Shuffle, localizedText("Shuffle", "Aleatorio"),
            onShuffle, tint = ink, glyphSize = 22.dp, surfaceSize = 48.dp)
        PlayPill(
            isPlaying = isPlaying,
            onClick = onPlay,
            modifier = Modifier.weight(1f),
            height = 48.dp,
            fill = ink,
            ink = if (ink.luminance() > .5f) Color(0xFF242124) else Color.White,
            contentPadding = PaddingValues(horizontal = 10.dp),
        )
        trailing?.invoke()
    }
}
