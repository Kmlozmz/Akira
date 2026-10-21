// SPDX-License-Identifier: GPL-3.0-only
package com.akira.app.core.design.components

import com.akira.app.core.design.motion.*
import com.akira.app.core.design.navigation.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.akira.app.core.design.icon.AkiraIcons

val LocalContentObscured = staticCompositionLocalOf { false }
val LocalContentMotionReduced = staticCompositionLocalOf { false }
val ContentCardCornerRadius = 20.dp

@Composable
fun rememberContentMotionReduced(): Boolean {
    val resolver = androidx.compose.ui.platform.LocalContext.current.contentResolver
    fun read() = android.provider.Settings.Global.getFloat(resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = read() }
        }
        resolver.registerContentObserver(android.provider.Settings.Global.getUriFor(android.provider.Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

@Composable
fun localizedText(english: String, spanish: String): String =
    if (com.akira.app.core.i18n.LocalStrings.current.locale.startsWith("es")) spanish else english

@Composable
internal fun ItemThumbnail(thumbnailUrl: String?, isActive: Boolean, isPlaying: Boolean,
    shape: Shape = RoundedCornerShape(18.dp), modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
        Icon(AkiraIcons.Play, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
        AsyncImage(model = thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize())
        PlayingIndicatorBox(isActive=isActive,playWhenReady=isPlaying,shape=shape,modifier=Modifier.fillMaxSize())
    }
}
