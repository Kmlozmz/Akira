package com.akira.app.feature.library

import com.akira.app.core.artwork.*
import com.akira.app.feature.lyrics.*
import com.akira.app.feature.player.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.akira.app.core.i18n.LocalStrings
import com.akira.app.core.i18n.StringsSpanish


@Composable
internal fun CollectionArtwork(uris: List<String?>, fallback: String, modifier: Modifier = Modifier) {
    val covers = uris.filterNotNull().filter { it.isNotBlank() }.distinct().take(4)
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF202024)), contentAlignment = Alignment.Center) {
        Text(fallback.take(2).uppercase(), modifier = Modifier.clearAndSetSemantics {},
            color = Color.White.copy(alpha = .75f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        if (covers.size == 1) AsyncImage(covers.first(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else if (covers.size > 1) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                covers.chunked((covers.size + 1) / 2).forEach { column ->
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        column.forEach { uri ->
                            AsyncImage(uri, null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun collectionCount(count: Int): String = if (count == 1) LocalStrings.current.oneSong else LocalStrings.current.nSongs.format(count)
