package com.akira.app.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.akira.app.core.design.theme.CurrentAkiraFontFamily as AkiraFontFamily

/** Identifies the subject of a contextual menu without repeating navigation actions. */
@Composable
fun AkiraContextHeader(title: String, subtitle: String, artwork: String? = null, icon: ImageVector? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=16.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        if(artwork != null || icon != null) Box(Modifier.size(44.dp).clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha=.08f)),contentAlignment=Alignment.Center) {
            if(artwork != null) AsyncImage(artwork,null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
            else icon?.let { Icon(it,null,Modifier.size(23.dp),tint=MaterialTheme.colorScheme.onSurface) }
        }
        Column(Modifier.weight(1f)) {
            Text(title,fontFamily=AkiraFontFamily,fontSize=15.sp,lineHeight=19.sp,fontWeight=FontWeight.SemiBold,
                maxLines=2,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurface)
            if(subtitle.isNotBlank()) Text(subtitle,Modifier.padding(top=3.dp),fontFamily=AkiraFontFamily,
                fontSize=12.sp,lineHeight=16.sp,maxLines=1,overflow=TextOverflow.Ellipsis,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
