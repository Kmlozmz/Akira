package com.akira.app.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.akira.app.core.design.components.localizedText

@Composable
internal fun LibraryRefreshError(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(localizedText("Couldn't refresh your library. Showing saved songs.",
            "No se pudo actualizar la biblioteca. Se muestran las canciones guardadas."),
            color = MaterialTheme.colorScheme.onSurface)
        TextButton(onClick = onRetry) { Text(localizedText("Retry", "Reintentar")) }
    }
}
