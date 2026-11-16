package com.akira.app.feature.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import com.akira.app.core.design.components.LocalContentMotionReduced
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.components.localizedText
import com.akira.app.feature.settings.settingsSurfaceColor

/** Each permission has its own purpose, action and stable granted state. */
@Composable internal fun OnboardingPermissions(audio: Boolean, notifications: Boolean, enabled: Boolean,
    onAudio: () -> Unit, onNotifications: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PermissionCard(Icons.Rounded.LibraryMusic, localizedText("Music and audio", "Música y audio"),
            localizedText("Find the songs saved on this phone.", "Encuentra las canciones guardadas en este teléfono."),
            localizedText("Songs, albums and artists in your library.", "Canciones, álbumes y artistas en tu biblioteca."), audio, enabled, onAudio)
        PermissionCard(Icons.Rounded.Notifications, localizedText("Notifications", "Notificaciones"),
            localizedText("Keep playback controls within reach.", "Ten los controles de reproducción siempre a mano."),
            localizedText("Pause or skip from notifications and the lock screen.", "Pausa o cambia de canción desde las notificaciones y la pantalla de bloqueo."), notifications, enabled, onNotifications)
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(audio, notifications).forEach { granted ->
                Box(Modifier.weight(1f).height(4.dp).background(if (granted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(50)))
            }
        }
        Text(localizedText("${listOf(audio, notifications).count { it }} of 2 permissions allowed", "${listOf(audio, notifications).count { it }} de 2 permisos concedidos"),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp).semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable private fun PermissionCard(icon: ImageVector, title: String, detail: String, benefit: String, granted: Boolean,
    enabled: Boolean, onAllow: () -> Unit) {
    val reduced = LocalContentMotionReduced.current
    val colors = MaterialTheme.colorScheme
    val iconFill by animateColorAsState(colors.primary.copy(alpha = if (granted) .18f else .10f),
        if (reduced) snap() else tween(180), label = "permissionState")
    Column(Modifier.fillMaxWidth().background(settingsSurfaceColor(), RoundedCornerShape(24.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(46.dp).background(iconFill, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(25.dp), tint = colors.primary)
            }
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (granted) Icon(Icons.Rounded.CheckCircle, localizedText("Allowed", "Permitido"), Modifier.size(25.dp), tint = colors.primary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            Text(benefit, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if (granted) Text(localizedText("Allowed", "Permitido"), style = MaterialTheme.typography.labelMedium,
            color = colors.primary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        else FilledTonalButton(onAllow, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .semantics { contentDescription = title + ": " + detail }, shape = RoundedCornerShape(14.dp)) {
            Text(localizedText("Allow", "Permitir"))
        }
    }
}
