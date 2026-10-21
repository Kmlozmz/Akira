package com.akira.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.akira.app.core.data.MediaStoreScanner
import com.akira.app.core.data.passesLibraryFilters
import com.akira.app.core.datastore.UserPreferences
import com.akira.app.core.datastore.UserPreferencesRepository
import com.akira.app.core.i18n.LocalStrings
import com.akira.app.core.i18n.AkiraUiText
import com.akira.app.core.model.Song
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.akira.app.core.design.navigation.rememberPageCollapse

/** Optional replacement for the old mandatory onboarding filter review. */
@Composable
internal fun LibraryExceptionsScreen(prefs: UserPreferences, repo: UserPreferencesRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val collapse = rememberPageCollapse(list)
    var songs by remember { mutableStateOf<List<Song>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        songs = null; failed = false
        try { songs = MediaStoreScanner(context).scanSongsInFolder("all", throwOnFailure = true) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    AkiraStudioScreen(strings.ui(AkiraUiText.SetupReviewFilters),onBack,headerProgress=collapse) {
        Column(Modifier.fillMaxSize().padding(start=24.dp,end=24.dp,top=126.dp,bottom=32.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(com.akira.app.core.design.components.localizedText("Choose songs, then keep them in your library or apply the filters again. This never deletes files.",
                "Selecciona canciones para conservarlas o volver a aplicar los filtros. Esto nunca borra archivos."),color=MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                failed -> {
                    Text(strings.ui(AkiraUiText.SetupScanFailed),color=MaterialTheme.colorScheme.error)
                    TextButton({attempt++}) {Text(strings.ui(AkiraUiText.Retry))}
                }
                songs==null -> CircularProgressIndicator(Modifier.size(28.dp))
                else -> LibraryFilterReview(songs.orEmpty(),prefs,repo,Modifier.weight(1f),list)
            }
        }
    }
}
