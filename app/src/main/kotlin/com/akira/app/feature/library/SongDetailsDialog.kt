// Source attribution: THIRD_PARTY_NOTICES.md.
// Akira integration: shared presentation stack, Compose detents, local/online metadata and Inter (OFL).
package com.akira.app.feature.library

import com.akira.app.core.artwork.*
import com.akira.app.feature.lyrics.*
import com.akira.app.feature.player.*
import com.akira.app.feature.queue.*

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.akira.app.R
import com.akira.app.core.data.SongDetailsModel
import com.akira.app.core.data.SongMetadataReader
import com.akira.app.core.design.components.*
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.i18n.LocalStrings
import com.akira.app.core.model.Song
import com.akira.app.core.design.components.localizedText

@OptIn(ExperimentalTextApi::class)
private val DetailsInter = FontFamily(
    Font(R.font.inter_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600)))
)

@Composable fun SongDetailsDialog(song: Song, onDismiss: () -> Unit) = SongDetailsBottomSheet(song, onDismiss)

@Composable fun SongDetailsBottomSheet(song: Song, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val strings = LocalStrings.current
    val haptics = LocalHapticFeedback.current
    val ink = MaterialTheme.colorScheme.onSurface
    val details by produceState<SongDetailsModel?>(null, song.id, strings) {
        value = SongMetadataReader.getSongDetails(context, song, strings)
    }
    val rows = buildList {
        fun row(label: String, value: String?) {
            value?.trim()?.takeUnless { it.isBlank() || it == "—" || it == "0" }?.let { add(label to it) }
        }
        row(localizedText("Title", "Título"), song.title)
        row(localizedText("Artist", "Artista"), song.artist)
        row(strings.trackAlbum, details?.album ?: song.album)
        row(strings.trackAlbumArtist, details?.albumArtist)
        row(strings.trackGenre, details?.genre)
        row(localizedText("Composer", "Compositor"), details?.composer)
        row(localizedText("Date", "Fecha"), details?.releaseDate)
        row(localizedText("Track #", "Pista n.º"), details?.trackNumber)
        row(localizedText("Disc #", "Disco n.º"), details?.discNumber)
        row("ISRC", details?.isrc?.let(::detailIsrc))
        row(localizedText("Label", "Sello"), details?.label)
        row(localizedText("Copyright", "Derechos de autor"), details?.copyright)
        row(localizedText("Format", "Formato"), details?.container)
        row(localizedText("Codec", "Códec"), details?.realCodec?.lowercase(java.util.Locale.ROOT))
        row(strings.sampleRate, details?.sampleRate)
        row(strings.audioBitDepth, details?.bitDepth)
    }
    AkiraModalSheet(onDismiss, contentColor = ink, title = localizedText("Details", "Detalles"), style = AkiraPanelStyle.Details) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(localizedText("Details", "Detalles"), fontFamily = DetailsInter, fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold, color = ink, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss, contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier.widthIn(min = 44.dp)) {
                Text(strings.done, fontFamily = DetailsInter, fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold, color = Color(0xFFFF453A))
            }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), overscrollEffect = null,
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()) {
            item("song-header") {
                Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(70.dp).clip(RoundedCornerShape(10.dp)).background(ink.copy(alpha = .06f)), contentAlignment = Alignment.Center) {
                        Icon(AkiraIcons.Album, null, Modifier.size(28.dp), tint = ink.copy(alpha = .4f))
                        AsyncImage(song.albumArtUri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(song.title, fontFamily = DetailsInter, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = .38.sp,
                            fontWeight = FontWeight.SemiBold, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, fontFamily = DetailsInter, fontSize = 17.sp, lineHeight = 24.sp,
                            color = ink.copy(alpha = .58f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            itemsIndexed(rows, key = { _, row -> row.first }) { index, (label, value) ->
                MetadataRow(label, value, ink, index < rows.lastIndex) {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                        ?.setPrimaryClip(ClipData.newPlainText(label, value))
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    AkiraNotices.show("${strings.copiedToClipboard}: $value")
                }
            }
        }
    }
}

private fun detailIsrc(value: String): String {
    val compact = value.replace("-", "").replace(" ", "").uppercase(java.util.Locale.ROOT)
    return if (Regex("[A-Z]{2}[A-Z0-9]{3}[0-9]{7}").matches(compact))
        "${compact.take(2)}-${compact.substring(2, 5)}-${compact.substring(5, 7)}-${compact.substring(7)}"
    else value
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun MetadataRow(label: String, value: String, ink: Color, divider: Boolean, onCopy: () -> Unit) {
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onCopy,
        onLongClickLabel = localizedText("Copy", "Copiar")).padding(vertical = 7.dp)) {
        Text(label, fontFamily = DetailsInter, fontSize = 13.sp, lineHeight = 18.sp,
            fontWeight = FontWeight.Medium, color = ink.copy(alpha = .58f), letterSpacing = (-.08).sp)
        Spacer(Modifier.height(5.dp))
        Text(value, fontFamily = DetailsInter, fontSize = 17.sp, lineHeight = 24.sp, color = ink)
        if (divider) {
            Spacer(Modifier.height(7.dp))
            HorizontalDivider(thickness = .5.dp, color = Color.Black.copy(alpha = .12f))
        }
    }
}
