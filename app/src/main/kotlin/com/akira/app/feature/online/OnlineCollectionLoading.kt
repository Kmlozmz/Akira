package com.akira.app.feature.online

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.components.localizedText
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

/** Tracks load within the collection layout, keeping the header and navigation in place. */
@Composable internal fun OnlineCollectionLoading() {
    val description = localizedText("Loading songs", "Cargando canciones")
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(4) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp)))
                Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.fillMaxWidth(.7f).height(13.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(6.dp)))
                    Box(Modifier.fillMaxWidth(.45f).height(11.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(6.dp)))
                }
            }
        }
    }
}
