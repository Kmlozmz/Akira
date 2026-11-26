package com.akira.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.i18n.LocalStrings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.app.core.design.navigation.AkiraBackButton
import com.akira.app.core.design.navigation.NavigationBackSlot
import com.akira.app.core.design.theme.CurrentAkiraFontFamily

@Composable internal fun settingsSurfaceColor():Color =
    if(MaterialTheme.colorScheme.background.luminance()>.5f)MaterialTheme.colorScheme.surfaceContainer else Color(0xFF1C1C1E)

@Composable internal fun SettingsLeadingIcon(icon:ImageVector,enabled:Boolean=true,content:(@Composable ()->Unit)?=null,tint:Color=MaterialTheme.colorScheme.primary) {
    Box(Modifier.size(28.dp),contentAlignment=Alignment.Center) {
        if(content!=null)content() else Icon(icon,null,Modifier.size(23.dp),tint=tint.copy(alpha=if(enabled)1f else .38f))
    }
}

/** Navigation geometry follows the selected style; navigation itself is silent. */
@Composable internal fun SettingsPageHeader(title:String,onBack:()->Unit,modifier:Modifier=Modifier,
    topInset:androidx.compose.ui.unit.Dp=0.dp,trailing:(@Composable ()->Unit)?=null,
    backLabel:String=LocalStrings.current.settingsTitle) {
    val background=MaterialTheme.colorScheme.background
    Box(modifier.fillMaxWidth().height(topInset+88.dp)) {
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to background,.7f to background,.99f to background.copy(alpha=0f))))
        Layout(modifier=Modifier.fillMaxWidth().padding(top=topInset+14.dp,start=20.dp,end=20.dp).height(48.dp),content={
            SettingsBackControl(backLabel, onBack)
            Text(title,fontFamily=CurrentAkiraFontFamily,fontSize=17.sp,fontWeight=FontWeight.SemiBold,
                maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurface)
            Box { trailing?.invoke() }
        }) { children,constraints ->
            val width=constraints.maxWidth;val height=constraints.maxHeight
            val side=constraints.copy(minWidth=0,maxWidth=(width*.36f).toInt(),minHeight=0)
            val leading=children[0].measure(side);val actions=children[2].measure(side)
            val inset=maxOf(leading.width,actions.width)+12.dp.roundToPx()
            val heading=children[1].measure(constraints.copy(minWidth=0,minHeight=0,maxWidth=(width-2*inset).coerceAtLeast(1)))
            layout(width,height) {
                leading.placeRelative(0,(height-leading.height)/2)
                heading.placeRelative((width-heading.width)/2,(height-heading.height)/2)
                actions.placeRelative(width-actions.width,(height-actions.height)/2)
            }
        }
    }
}

@Composable
internal fun SettingsBackControl(label: String, onBack: () -> Unit) {
    if (com.akira.app.core.design.glass.LocalGlassStyleEnabled.current) {
        AkiraBackButton(onBack, haptic = false)
        return
    }
    val interaction = remember { MutableInteractionSource() }
    val classicBack: @Composable (Boolean) -> Unit = { interactive ->
        Row(Modifier.heightIn(min = 48.dp).then(if (interactive)
            Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onBack)
        else Modifier.alpha(0f).clearAndSetSemantics {}),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(androidx.compose.ui.res.painterResource(com.akira.app.R.drawable.ic_navigation_back),
                null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, fontFamily = CurrentAkiraFontFamily, fontSize = 17.sp,
                color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    NavigationBackSlot(placeholder = { classicBack(false) }) { classicBack(true) }
}
