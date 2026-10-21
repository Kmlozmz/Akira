package com.akira.app.feature.player

import com.akira.app.feature.library.*
import com.akira.app.feature.lyrics.*
import com.akira.app.feature.queue.*

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.components.AkiraIconButton
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.i18n.LocalStrings

/** The normal player and lyric header share the complete action geometry. */
@Composable
internal fun PlayerTrackActions(isFavorite: Boolean, ink: Color, referencePortrait: Boolean,
    onFavorite: ()->Unit, onMore: ()->Unit) {
    val strings=LocalStrings.current
    Row(verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(if(referencePortrait)0.dp else 2.dp)) {
        AkiraIconButton(icon=if(isFavorite)AkiraIcons.StarFilled else AkiraIcons.Star,
            contentDescription=strings.favorite,
            tint=if(isFavorite)Color(0xFFFFD15C) else ink.copy(alpha=if(referencePortrait).85f else .65f),
            popOnTrue=isFavorite,glyphSize=if(referencePortrait)20.dp else 22.dp,
            showBackground=referencePortrait,surfaceSize=30.dp,glassSurface=false,onClick=onFavorite)
        AkiraIconButton(icon=AkiraIcons.MoreHoriz,contentDescription=strings.options,
            modifier=Modifier.offset(x=if(referencePortrait)(-2).dp else 0.dp),
            tint=ink.copy(alpha=if(referencePortrait).85f else .7f),
            glyphSize=if(referencePortrait)24.dp else 22.dp,
            showBackground=referencePortrait,surfaceSize=30.dp,glassSurface=false,onClick=onMore)
    }
}
