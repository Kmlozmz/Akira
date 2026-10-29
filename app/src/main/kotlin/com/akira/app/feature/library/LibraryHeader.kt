package com.akira.app.feature.library

import com.akira.app.core.artwork.*
import com.akira.app.feature.lyrics.*
import com.akira.app.feature.player.*
import com.akira.app.feature.queue.*
import com.akira.app.core.design.glass.liquidGlass
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.app.core.design.theme.AkiraFontFamily
import com.akira.app.core.design.theme.noRippleClickable
import com.akira.app.core.design.util.rememberAkiraHaptics
import java.text.NumberFormat

import com.akira.app.core.i18n.LocalStrings

/** Library header containing the title, search field and tabs. */
@Composable
internal fun LibraryHeader(
    songCount: Int,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    onSortClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    showCategories: Boolean = true,
    collapseProgress: Float = 0f,
    backdropOverride: com.akira.app.core.design.backdrop.Backdrop? = null
) {
    val haptics = rememberAkiraHaptics()
    val strings = LocalStrings.current
    var isSearchVisible by remember { mutableStateOf(searchQuery.isNotEmpty()) }
    val controlsBackdrop=libraryControlsBackdrop(backdropOverride)

    Column(modifier = modifier.fillMaxWidth()) {
        com.akira.app.core.design.components.SearchGlassTopBar(
            progressProvider={collapseProgress},title=title ?: strings.appName,
            modifier=Modifier.height((128f-62f*collapseProgress.coerceIn(0f,1f)).dp),
            trailing=null,
            field={SearchField(searchQuery,onSearchChange,strings.searchHint,Modifier.fillMaxWidth())})
        Spacer(Modifier.height(18.dp))

        // Library tabs.
        if(showCategories) {
        val tabs = listOf(
            "Tracks" to strings.tabTracks,
            "Albums" to strings.tabAlbums,
            "Artists" to strings.tabArtists,
            "Folders" to strings.tabFolders,
            "Playlists" to strings.library.lists
        )
        LibraryCategoryStrip(tabs,selectedTab,{key ->
            if(key!=selectedTab) {haptics.tick();onTabSelected(key)}
        },controlsBackdrop)

        Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    // A 44 dp touch target around a 22 dp foreground icon.
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    var value by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = androidx.compose.ui.text.input.TextFieldValue.Saver) {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(query))
    }
    androidx.compose.runtime.LaunchedEffect(query) {
        if (value.text != query) value = androidx.compose.ui.text.input.TextFieldValue(query, androidx.compose.ui.text.TextRange(query.length))
    }
    Row(modifier,verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    com.akira.app.core.design.components.SearchField(
        value = value,
        onValueChange = { value = it; onQueryChange(it.text) },
        onSearch = { focusManager.clearFocus(); keyboard?.hide() },
        placeholder = placeholder, modifier = Modifier.weight(1f), focused = focused,
        focusRequester = focusRequester, onFocusChanged = { focused = it },
        leading = {
            Icon(Icons.Rounded.Search,null,Modifier.size(20.dp),
                tint=androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailing = if (query.isNotEmpty()) { {
            Icon(androidx.compose.ui.res.painterResource(com.akira.app.R.drawable.ic_search_close), LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.ClearSearch),
                Modifier.size(20.dp).noRippleClickable { value = androidx.compose.ui.text.input.TextFieldValue(); onQueryChange("") },
                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        } } else null,
    )
        if(focused)androidx.compose.material3.TextButton({focusManager.clearFocus();keyboard?.hide()}) {
            Text(LocalStrings.current.cancel,fontSize=14.sp)
        }
    }
}

@Composable
private fun LibraryTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val textColor by animateColorAsState(
        targetValue = if (selected) androidx.compose.material3.MaterialTheme.colorScheme.onSurface else androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
        label = "tab_color"
    )
    // Grow the indicator dot from its center using a soft spring.
    val indicator by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tab_indicator"
    )

    Box(
        modifier = Modifier
            .heightIn(min=44.dp)
            .clip(RoundedCornerShape(50))
            .background(if(selected)androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha=.12f) else Color.Transparent)
            .selectable(selected,role=Role.Tab,onClick=onClick)
            .padding(horizontal=8.dp,vertical=10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontFamily = AkiraFontFamily,
            color = textColor,
            maxLines = 1
        )
    }
}
