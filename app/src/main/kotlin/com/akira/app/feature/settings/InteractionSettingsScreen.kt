package com.akira.app.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.components.AkiraSwitch
import com.akira.app.core.design.components.localizedText
import com.akira.app.core.design.motion.InteractionPreferences

@Composable
internal fun InteractionSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    AkiraStudioScreen(localizedText("Interaction", "Interacción"), onBack) {
        Column(Modifier.fillMaxWidth().verticalScroll(
            com.akira.app.core.design.navigation.LocalPageScrollState.current ?: rememberScrollState())
            .padding(top = 118.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SettingsGroup {
                InteractionOption(localizedText("Haptic feedback", "Respuesta háptica"),
                    localizedText("Feel slider steps and control changes.", "Siente los pasos de los sliders y los cambios de los controles."),
                    InteractionPreferences.hapticsEnabled) { InteractionPreferences.setHaptics(context, it) }
                InteractionOption(localizedText("Reduce motion", "Reducir movimiento"),
                    localizedText("Open menus and change screens with less movement.", "Abre menús y cambia de pantalla con menos movimiento."),
                    InteractionPreferences.reducedMotion) { InteractionPreferences.setReducedMotion(context, it) }
            }
        }
    }
}

@Composable
private fun InteractionOption(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        AkiraSwitch(checked, onChange, modifier = Modifier.semantics { contentDescription = title })
    }
}
