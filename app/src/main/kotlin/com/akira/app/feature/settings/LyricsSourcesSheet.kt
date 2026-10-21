package com.akira.app.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.app.core.design.components.AkiraModalSheet
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.design.util.rememberAkiraHaptics
import com.akira.app.core.i18n.LocalStrings
import com.akira.app.core.i18n.AkiraUiText
import com.akira.app.core.lyrics.LyricsProvider

/** A draft selector: changing its segment never starts a request or saves preferences. */
@Composable
internal fun LyricsSourcesSheet(enabled: Boolean, provider: String, onDismiss: () -> Unit,
    forCurrentSong: Boolean = false, onSave: (Boolean, String) -> Unit) {
    val strings = LocalStrings.current
    val haptics = rememberAkiraHaptics()
    val original = if(forCurrentSong)LyricsProvider.fromId(provider) else LyricsProvider.AUTO
    val providers=if(forCurrentSong)LyricsProvider.entries else listOf(LyricsProvider.AUTO)
    fun nameOf(source: LyricsProvider)=if(source==LyricsProvider.AUTO)strings.ui(AkiraUiText.LyricsAutoName) else source.displayName
    var selected by remember(enabled, provider) { mutableIntStateOf(if (!enabled) 0 else providers.indexOf(original)+1) }
    val name = if(selected==0)strings.ui(AkiraUiText.LyricsLocalShort) else nameOf(providers[selected-1])
    AkiraModalSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(strings.ui(AkiraUiText.LyricsChooseSource), style = MaterialTheme.typography.titleLarge)
                Text(strings.ui(if (forCurrentSong) AkiraUiText.LyricsSongSelectorHint else AkiraUiText.LyricsSourcesIntro),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .65f), fontSize = 14.sp)
            }
            Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                (listOf(strings.ui(AkiraUiText.LyricsLocalOnly))+providers.map(::nameOf)).forEachIndexed { index, title ->
                    val active = selected == index
                    val fill by androidx.compose.animation.animateColorAsState(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = if (active) .12f else .04f), tween(180), label = "source_choice")
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(fill)
                        .selectable(active, role = Role.RadioButton, onClick = { haptics.tick(); selected = index })
                        .padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(AkiraIcons.Lyrics, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .75f))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(strings.ui(if(index==0) AkiraUiText.LyricsLocalCompact else when(providers[index-1]) {
                                LyricsProvider.AUTO -> AkiraUiText.LyricsAutoCompact
                                LyricsProvider.LRCLIB -> AkiraUiText.LyricsLrclibCompact
                                LyricsProvider.LYRICS_OVH -> AkiraUiText.LyricsOvhCompact
                                LyricsProvider.KUGOU -> AkiraUiText.LyricsKugouCompact
                                LyricsProvider.NETEASE -> AkiraUiText.LyricsNeteaseCompact
                            }),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f), fontSize = 13.sp)
                        }
                        Box(Modifier.size(28.dp).clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (active) 1f else .08f)), contentAlignment = Alignment.Center) {
                            if (active) Icon(AkiraIcons.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.surface)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { haptics.light(); onDismiss() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .07f), contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)) { Text(strings.cancel) }
                Button(onClick = { haptics.confirm(); onSave(selected != 0, if(selected==0) LyricsProvider.LRCLIB.name else providers[selected-1].name) },
                    modifier = Modifier.weight(1.4f).heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, contentColor = androidx.compose.material3.MaterialTheme.colorScheme.surface)) {
                    Text(if (selected == 0) strings.ui(AkiraUiText.LyricsUseLocal) else if(providers[selected-1]==LyricsProvider.AUTO)
                        strings.ui(if(forCurrentSong)AkiraUiText.LyricsAutoSearch else AkiraUiText.LyricsAutoUse) else
                        strings.ui(if (forCurrentSong) AkiraUiText.LyricsSearchProvider else AkiraUiText.LyricsUseProvider).format(name))
                }
            }
        }
    }
}
