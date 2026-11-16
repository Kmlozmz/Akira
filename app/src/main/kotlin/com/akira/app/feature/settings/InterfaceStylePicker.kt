package com.akira.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.akira.app.core.design.components.localizedText

/** Same side-by-side library miniatures as the appearance editor's Style page. */
@Composable internal fun InterfaceStylePicker(glass: Boolean, enabled: Boolean, onSelect: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().background(settingsSurfaceColor(), RoundedCornerShape(24.dp))
        .selectableGroup().padding(horizontal = 12.dp, vertical = 26.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(false, true).forEach { option ->
            Column(Modifier.weight(1f).selectable(option == glass, enabled, role = Role.RadioButton,
                onClick = { onSelect(option) }).padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StyleMiniature(option, Modifier.width(112.dp).height(220.dp).clearAndSetSemantics {})
                Text(if (option) "Glass" else "Classic", style = MaterialTheme.typography.bodyLarge)
                Box(Modifier.size(26.dp).background(if (option == glass) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                    if (option == glass) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Composable private fun StyleMiniature(glass: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val ink = if (glass) Color.White else colors.onSurface
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(if (glass) Color(0xFF161321) else colors.background)) {
        if (glass) StyleCover(Modifier.fillMaxSize())
        if (glass) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .60f)))
        Text(localizedText("Library", "Biblioteca"), Modifier.padding(start = 10.dp, top = 14.dp),
            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ink)
        if (!glass) StyleCover(Modifier.padding(start = 10.dp, end = 10.dp, top = 43.dp).fillMaxWidth().height(89.dp).clip(RoundedCornerShape(8.dp)))
        Column(Modifier.padding(start = 10.dp, end = 10.dp, top = if (glass) 83.dp else 141.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(localizedText("Your music", "Tu música"), fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
            Text("Akira", fontSize = 8.sp, color = ink.copy(alpha = .65f))
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(horizontal = 7.dp, vertical = 10.dp).fillMaxWidth()
            .background(if (glass) Color.White.copy(alpha = .25f) else ink.copy(alpha = .08f), RoundedCornerShape(if (glass) 18.dp else 8.dp))
            .padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
            Icon(Icons.Rounded.Home, null, Modifier.size(14.dp), tint = ink.copy(alpha = .6f))
            Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(14.dp), tint = colors.primary)
            Icon(Icons.Rounded.Search, null, Modifier.size(14.dp), tint = ink.copy(alpha = .6f))
        }
    }
}

@Composable private fun StyleCover(modifier: Modifier) {
    Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF291A56), Color(0xFFC43D62), Color(0xFFEC855B)))), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MusicNote, null, Modifier.size(40.dp), tint = Color.White.copy(alpha = .8f))
    }
}
