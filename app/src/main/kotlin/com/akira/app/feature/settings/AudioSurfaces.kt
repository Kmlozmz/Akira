@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.akira.app.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

/** Native audio controls inherit Akira's palette and typography. */
@Composable internal fun AkiraAudioTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRippleConfiguration provides RippleConfiguration(), content=content)
}

@Composable internal fun AudioDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AkiraAudioTheme {
        com.akira.app.core.design.components.AkiraModalSheet(onDismiss,
            style=com.akira.app.core.design.components.AkiraPanelStyle.Update) { content() }
    }
}
