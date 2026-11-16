package com.akira.app.feature.setup

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.components.localizedText
import com.akira.app.feature.settings.InterfaceStylePicker

/** Native counterpart of Settings > Style: compare both interfaces before choosing. */
@Composable internal fun OnboardingStyle(glass: Boolean, enabled: Boolean, onSelect: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        InterfaceStylePicker(glass, enabled, onSelect)
        Text(if (glass) localizedText("Translucent layers and fluid transitions.", "Capas translúcidas y transiciones fluidas.")
            else localizedText("Solid surfaces and familiar menus.", "Superficies sólidas y menús familiares."),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(localizedText("You can change this later in Settings.", "Puedes cambiarlo después en Ajustes."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
