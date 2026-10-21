package com.akira.app.feature.library

import com.akira.app.feature.lyrics.*
import com.akira.app.feature.player.*
import com.akira.app.feature.queue.*
import androidx.compose.foundation.layout.heightIn

import com.akira.app.core.design.components.AkiraModalSheet

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.app.core.design.theme.AkiraFontFamily
import com.akira.app.core.design.theme.noRippleClickable
import com.akira.app.core.design.util.rememberAkiraHaptics

private data class SortFieldUi(
    val field: SortField,
    val label: com.akira.app.core.i18n.AkiraUiText,
    val icon: ImageVector
)

private val SortFieldOptions = listOf(
    SortFieldUi(SortField.TITLE, com.akira.app.core.i18n.AkiraUiText.SortNameTitle, Icons.Rounded.SortByAlpha),
    SortFieldUi(SortField.ARTIST, com.akira.app.core.i18n.AkiraUiText.SortNameArtist, Icons.Rounded.Person),
    SortFieldUi(SortField.ALBUM, com.akira.app.core.i18n.AkiraUiText.SortNameAlbum, Icons.Rounded.Album),
    SortFieldUi(SortField.DATE_ADDED, com.akira.app.core.i18n.AkiraUiText.SortNameDate, Icons.Rounded.CalendarMonth),
    SortFieldUi(SortField.DURATION, com.akira.app.core.i18n.AkiraUiText.SortNameDuration, Icons.Rounded.Timer)
)

/** Texto corto que describe el orden actual de un criterio (com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.SortAToZ), com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Newest)...). */
@Composable
private fun directionLabel(field: SortField, ascending: Boolean): String = when (field) {
    SortField.DATE_ADDED -> if (ascending) com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.OldestFirst) else com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.NewestFirst)
    SortField.DURATION -> if (ascending) com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.ShortestFirst) else com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.LongestFirst)
    else -> if (ascending) com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.SortAToZ) else com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.SortZToA)
}

/**
 * Hoja "Ordenar por".
 *
 * Lógica (antes había que reabrir la hoja tras cada toque porque se cerraba sola):
 *  - Tocar un criterio nuevo lo aplica con su dirección natural (texto: A→Z,
 *    fecha: lo más reciente arriba).
 *  - Tocar el criterio ya activo invierte la dirección.
 *  - El selector de abajo permite elegir la dirección con palabras claras.
 *  - La lista se reordena en vivo detrás de la hoja; se cierra arrastrando o
 *    tocando fuera.
 */
@Composable
internal fun SortSheet(current: SortOrder,onSelect: (SortOrder)->Unit,onDismiss: ()->Unit) {
    val strings=com.akira.app.core.i18n.LocalStrings.current
    val haptics=rememberAkiraHaptics()
    AkiraModalSheet(onDismiss,title=strings.sortTitle,
        style=com.akira.app.core.design.components.AkiraPanelStyle.Compact) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp),
            verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(strings.ui(com.akira.app.core.i18n.AkiraUiText.SortBy),
                style=androidx.compose.material3.MaterialTheme.typography.titleLarge)
            com.akira.app.feature.settings.SettingsGroup {
                SortFieldOptions.forEach {option ->
                    val selected=option.field==current.field
                    Row(Modifier.fillMaxWidth()
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha=if(selected).12f else .04f))
                        .selectable(selected,role=Role.RadioButton,onClick={
                            haptics.tick()
                            onSelect(if(selected)SortOrder.of(option.field,!current.ascending) else SortOrder.defaultFor(option.field))
                        }).heightIn(min=48.dp).padding(horizontal=16.dp,vertical=12.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        Icon(option.icon,null,Modifier.size(21.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(strings.ui(option.label),Modifier.weight(1f),style=androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                        if(selected)Icon(Icons.Rounded.Check,null,Modifier.size(20.dp))
                    }
                }
            }
            DirectionSelector(current.field,current.ascending) {ascending ->
                if(ascending!=current.ascending) {haptics.tick();onSelect(SortOrder.of(current.field,ascending))}
            }
        }
    }
}

@Composable
private fun DirectionSelector(
    field: SortField,
    ascending: Boolean,
    onSelect: (Boolean) -> Unit
) {
    // Cada par es (esAscendente, etiqueta). El primero es la dirección "natural".
    val options: List<Pair<Boolean, String>> = when (field) {
        SortField.DATE_ADDED -> listOf(false to com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Newest), true to com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Oldest))
        SortField.DURATION -> listOf(true to com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Shortest), false to com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Longest))
        else -> listOf(true to com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.SortAToZ), false to com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.SortZToA))
    }

    com.akira.app.core.design.components.AkiraSegmentedControl(
        options.map { it.first.toString() to it.second }, ascending.toString(),
        { onSelect(it.toBoolean()) }, androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .06f))
}
