package com.akira.app.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.akira.app.core.online.OnlineAccount
import com.akira.app.core.online.OnlineRepository
import com.akira.app.core.design.util.rememberAkiraHaptics
import com.akira.app.core.design.components.localizedText

/** Same account identity everywhere. The guest glyph also remains the image error/loading fallback. */
@Composable fun AkiraAccountAvatar(account: OnlineAccount, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Person, null, Modifier.fillMaxSize(.56f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (account.connected && !account.image.isNullOrBlank()) AsyncImage(account.image, null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable fun AkiraAccountButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember(context) { OnlineRepository.get(context) }
    val account by repository.account.collectAsStateWithLifecycle()
    val online by repository.enabled.collectAsStateWithLifecycle()
    val haptics = rememberAkiraHaptics()
    val label = if (online) localizedText("Account and settings", "Cuenta y ajustes") else localizedText("Settings", "Ajustes")
    Box(modifier.size(44.dp).clip(CircleShape).clickable { haptics.light(); onClick() }
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        if (online) AkiraAccountAvatar(account, Modifier.size(36.dp))
        else Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Settings, null, Modifier.size(25.dp), tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
