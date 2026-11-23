package com.akira.app.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

internal val CompactHomeFeatureHeight = 134.dp
internal val EditorialHomeFeatureHeight = 252.dp

@Composable
internal fun HomeFeaturedMusic(
    title: String,
    reason: String,
    artwork: String?,
    action: String,
    compact: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    val content = @Composable { editorial: Boolean ->
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(reason, style = MaterialTheme.typography.labelMedium,
                color = if (editorial) Color.White.copy(alpha = .88f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, style = if (editorial) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                color = if (editorial) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = if (editorial) 2 else 1, overflow = TextOverflow.Ellipsis)
            Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(action, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (compact) {
        Row(modifier.fillMaxWidth().heightIn(min = CompactHomeFeatureHeight).clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            FeatureArtwork(artwork, Modifier.size(82.dp).clip(RoundedCornerShape(12.dp)))
            Box(Modifier.weight(1f)) { content(false) }
        }
    } else {
        Box(modifier.fillMaxWidth().heightIn(min = EditorialHomeFeatureHeight).clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            FeatureArtwork(artwork, Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                0f to Color.Black.copy(alpha = .08f),
                .42f to Color.Black.copy(alpha = .42f),
                1f to Color.Black.copy(alpha = .87f),
            )))
            Box(Modifier.align(Alignment.BottomStart).padding(18.dp)) { content(true) }
        }
    }
}

@Composable
private fun FeatureArtwork(artwork: String?, modifier: Modifier) {
    AsyncImage(
        model = artwork?.takeIf(String::isNotBlank),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}
