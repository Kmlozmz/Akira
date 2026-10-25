package com.akira.app.feature.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.akira.app.core.design.components.AkiraModalSheet
import com.akira.app.core.design.components.AkiraPanelStyle
import com.akira.app.core.design.theme.DarkColorScheme
import com.akira.app.core.design.components.localizedText

/** Photo, biography and system insets share one continuous surface. */
@Composable internal fun ArtistInformationSheet(name: String, image: String?, accent: Color, stats: String?,
    biography: String, onDismiss: () -> Unit) {
    val base = lerp(Color(0xFF101216), accent, .22f)
    MaterialTheme(colorScheme = DarkColorScheme) {
        AkiraModalSheet(onDismiss, contentColor = Color.White, title = name, style = AkiraPanelStyle.Page,
            containerColor = base, immersive = true) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .background(base)) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    if (image != null) AsyncImage(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = .7f), .12f to Color.Black.copy(alpha=.3f), .28f to Color.Transparent,
                        .35f to base.copy(alpha=.05f), .68f to base.copy(alpha=.6f), 1f to base)))
                    Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(localizedText("ABOUT THE ARTIST", "ACERCA DEL ARTISTA"), style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = .74f), letterSpacing = 1.2.sp)
                        Text(name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                Column(Modifier.padding(horizontal = 24.dp).padding(top = 4.dp, bottom = 24.dp)
                    .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    stats?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .72f)) }
                    Text(biography, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp), color = Color.White.copy(alpha = .92f))
                }
            }
        }
    }
}
