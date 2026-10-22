package com.akira.app.feature.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.app.R
import com.akira.app.core.design.components.localizedText
import com.akira.app.core.design.glass.glassContentColor
import com.akira.app.core.design.glass.liquidGlass
import com.akira.app.feature.player.dock.navigation.dockMiniHeightDp

/** A single text input survives browse, editing, IME movement and cancellation. */
@Composable internal fun SearchDock(session: SearchSession, onHome: () -> Unit, onSubmit: () -> Unit,
    modifier: Modifier = Modifier, reveal: Float = 1f, editingProgress: Float = 0f,
    heightOverride: androidx.compose.ui.unit.Dp? = null, drawHomeSurface: Boolean = true,
    active: Boolean = true) {
    val shape = RoundedCornerShape(percent = 50)
    val height = heightOverride ?: dockMiniHeightDp(LocalDensity.current.fontScale, inline = true).dp
    val slot = height + 10.dp
    val ink = glassContentColor()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val editing = editingProgress
    fun cancel() { focusManager.clearFocus(); keyboard?.hide(); session.close() }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()?.let { session.search(it); onSubmit() }
    }
    var voiceUnavailable by remember { mutableStateOf(false) }
    LaunchedEffect(session.editing, active) {
        if (session.editing && active) { focus.requestFocus(); keyboard?.show() }
        else { focusManager.clearFocus(); keyboard?.hide() }
    }
    val placeholder = localizedText("Artists, Songs and More", "Artistas, canciones y más")
    val searchLabel = localizedText("Search", "Buscar")
    BoxWithConstraints(modifier.fillMaxWidth().height(height)) {
        // The lens starts at the Search button's right-hand origin. Its input never changes parent.
        if (editing < .99f) IconButton(onClick = { cancel(); onHome() },
            modifier = Modifier.align(Alignment.CenterStart).size(height).graphicsLayer { alpha = (1f - editing) * reveal }
                .then(if (drawHomeSurface) Modifier.liquidGlass(shape) else Modifier)) {
            Icon(painterResource(R.drawable.ic_navigation_home_filled), localizedText("Home", "Inicio"), Modifier.size(25.dp), tint = ink)
        }
        val fieldWidth = ((maxWidth-slot) * reveal).coerceAtLeast(1.dp)
        val fieldOffset = maxWidth-fieldWidth-slot*editing*reveal
        Row(Modifier.offset(x = fieldOffset).width(fieldWidth).height(height)
            .graphicsLayer { alpha = reveal }.liquidGlass(shape).clip(shape)
            .clickable(enabled = active) { session.editing = true; focus.requestFocus(); keyboard?.show() }
            .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, Modifier.size(23.dp), tint = ink.copy(alpha = .75f))
            Spacer(Modifier.width(10.dp))
            BasicTextField(session.value, { session.value = it; session.editing = true }, singleLine = true, enabled = active,
                textStyle = MaterialTheme.typography.bodyLarge.merge(TextStyle(color = ink, fontSize = 16.sp)),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit(); keyboard?.hide() }),
                modifier = Modifier.weight(1f).focusRequester(focus)
                    .onFocusChanged { if (it.isFocused && active) session.editing = true }
                    .semantics { contentDescription = searchLabel },
                decorationBox = { input -> Box(contentAlignment = Alignment.CenterStart) {
                    if (session.value.text.isEmpty()) Text(placeholder, color = ink.copy(alpha = .62f),
                        fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    input()
                } })
            if (session.value.text.isNotEmpty()) IconButton(onClick = { session.value = androidx.compose.ui.text.input.TextFieldValue("") }, Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Cancel, localizedText("Clear search", "Borrar búsqueda"), Modifier.size(19.dp), tint = ink.copy(alpha = .65f))
            } else IconButton(onClick = {
                runCatching { voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)) }
                    .onFailure { voiceUnavailable = true }
            }, Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Mic, localizedText("Voice search", "Búsqueda por voz"), Modifier.size(21.dp), tint = ink.copy(alpha = .7f))
            }
        }
        if (editing > .01f) IconButton(onClick = ::cancel,
            modifier = Modifier.align(Alignment.CenterEnd).size(height)
                .graphicsLayer { alpha = editing }.liquidGlass(shape)) {
            Icon(Icons.Rounded.Close, localizedText("Cancel search", "Cancelar búsqueda"), tint = ink)
        }
    }
    if (voiceUnavailable) AlertDialog(onDismissRequest = { voiceUnavailable = false },
        text = { Text(localizedText("Voice search isn't available on this device.", "La búsqueda por voz no está disponible en este dispositivo.")) },
        confirmButton = { TextButton(onClick = { voiceUnavailable = false }) { Text(localizedText("OK", "Aceptar")) } })
}
