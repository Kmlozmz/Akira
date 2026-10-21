package com.akira.app.core.design.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Confirmation/editor popups use the same window and material as every other sheet. */
@Composable
fun AkiraConfirmationDialog(onDismissRequest: () -> Unit, title: @Composable () -> Unit,
    text: @Composable () -> Unit, confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit) {
    AkiraModalSheet(onDismissRequest) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
            Box(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(alpha = .75f)) { text() }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                dismissButton()
                Spacer(Modifier.width(12.dp))
                confirmButton()
            }
        }
    }
}
