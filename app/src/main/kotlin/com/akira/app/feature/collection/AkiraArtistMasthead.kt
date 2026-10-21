package com.akira.app.feature.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.akira.app.core.design.components.*
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.design.theme.AkiraFontFamily
import com.akira.app.core.design.components.localizedText

@Composable
internal fun AkiraArtistMasthead(name: String, stats: String, background: Color, favorite: Boolean,
    playing: Boolean, onPlay: ()->Unit, onFavorite: ()->Unit, onInfo: ()->Unit, modifier: Modifier=Modifier) {
    val source=rememberAkiraMenuSource(icon=AkiraIcons.Info,hasOwnSurface=true)
    val ink=LocalDetailInk.current
    Column(modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
        Text(name,color=ink,fontFamily=AkiraFontFamily,fontSize=30.sp,lineHeight=34.sp,
            fontWeight=FontWeight.Bold,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        Text(stats,Modifier.padding(top=5.dp),color=ink.copy(alpha=.62f),fontFamily=AkiraFontFamily,
            fontSize=11.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(16.dp))
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                AkiraIconButton(AkiraIcons.Info,localizedText("Artist information","Información del artista"),
                    {source.open(onInfo)},modifier=source.modifier,tint=ink,glyphSize=20.dp)
                Box(Modifier.size(56.dp).background(ink.copy(alpha=.85f),CircleShape),contentAlignment=Alignment.Center) {
                    AkiraIconButton(if(playing)AkiraIcons.Pause else AkiraIcons.Play,localizedText("Play / pause","Reproducir / pausar"),
                        onPlay,tint=background,glyphSize=32.dp,showBackground=false)
                }
                AkiraIconButton(if(favorite)AkiraIcons.StarFilled else AkiraIcons.Star,localizedText("Favorite artist","Artista favorito"),
                    onFavorite,tint=ink,glyphSize=22.dp,popOnTrue=favorite)
            }
    }
}
