package com.akira.app.feature.setup

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.akira.app.core.design.components.localizedText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akira.app.core.i18n.LocalStrings
import com.akira.app.core.i18n.AkiraUiText
import com.akira.app.core.model.Song
import com.akira.app.R

@Composable
fun LibrarySetupScreen(onNavigateBack: () -> Unit, onSetupComplete: (List<Song>) -> Unit,
    modifier: Modifier = Modifier, onCustomize: ((List<Song>) -> Unit)? = null) {
    val context = LocalContext.current
    val model: LibrarySetupViewModel = viewModel()
    val state by model.controller.state.collectAsStateWithLifecycle()
    val audioPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    var audioGranted by remember { mutableStateOf(granted(audioPermission)) }
    var notificationGranted by remember { mutableStateOf(androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var requestedAudio by rememberSaveable { mutableStateOf(false) }
    var requestedNotifications by rememberSaveable { mutableStateOf(false) }
    var permissionRevision by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle, audioPermission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                audioGranted = granted(audioPermission)
                notificationGranted = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
                permissionRevision++
                if (!audioGranted) model.controller.reset()
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val requestAudio = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        audioGranted = it
        permissionRevision++ // A denial can change rationale without changing the granted boolean.
    }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationGranted = it
        permissionRevision++
    }
    LaunchedEffect(state.failure) {
        if (state.failure == SetupFailure.PERMISSION) audioGranted = granted(audioPermission)
    }
    fun back() {
        if (state.status == SetupStatus.IDLE) onNavigateBack() else model.controller.reset()
    }
    BackHandler(enabled = !state.saving) { back() }
    // Keep back consumed while committing the completion flag.
    BackHandler(enabled = state.saving) { }
    val onComplete by rememberUpdatedState(onSetupComplete)
    LaunchedEffect(state.completed) { if (state.completed) onComplete(state.songs) }
    val audioRationale = remember(context, audioPermission, permissionRevision) {
        (context as? Activity)?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, audioPermission) }.orFalse()
    }
    val notificationRationale = remember(context, permissionRevision) {
        Build.VERSION.SDK_INT >= 33 && (context as? Activity)?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
        }.orFalse()
    }
    val permanentlyDenied = requestedAudio && !audioGranted && !audioRationale
    val notificationSettings = Build.VERSION.SDK_INT >= 33 && requestedNotifications && !notificationGranted && !notificationRationale
    fun openSettings() {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }
    SetupLibraryContent(state, audioGranted, notificationGranted, true,
        onBack = { if (!state.saving) back() },
        onRequestAudio = { requestedAudio = true; requestAudio.launch(audioPermission) },
        onOpenSettings = ::openSettings,
        permanentlyDenied = permanentlyDenied,
        onNotifications = { if (Build.VERSION.SDK_INT >= 33) {
            if (notificationSettings) openSettings() else {
                requestedNotifications = true
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else openSettings() },
        onScan = {if(hasSetupPermissions(context))model.controller.start()}, onFinish = {
            if(hasSetupPermissions(context)) {if(onCustomize!=null)onCustomize(state.songs) else model.controller.finish()}
        }, modifier = modifier,
        notificationSettings = notificationSettings,
        readyLabel=if(onCustomize!=null)com.akira.app.core.design.components.localizedText("Continue","Continuar") else null)
}

private fun Boolean?.orFalse() = this ?: false

/** Counts, permission states and actions are supplied by the lifecycle-owned scan. */
@Composable
internal fun SetupLibraryContent(state: SetupState, audioGranted: Boolean, notificationGranted: Boolean,
    supportsNotifications: Boolean, onBack: () -> Unit, onRequestAudio: () -> Unit,
    onOpenSettings: () -> Unit, permanentlyDenied: Boolean, onNotifications: () -> Unit,
    onScan: () -> Unit, onFinish: () -> Unit, modifier: Modifier = Modifier,
    notificationSettings: Boolean = false, readyLabel: String? = null) {
    val strings = LocalStrings.current
    val ready = audioGranted && state.status == SetupStatus.READY
    val permissionsReady = audioGranted && (!supportsNotifications || notificationGranted)
    SetupLayout(
        localizedText(if(ready) "Music found" else "Add your music",
            if(ready) "Música encontrada" else "Añade tu música"),
        if(ready) localizedText("Continue to choose which files appear in your library.", "Continúa para elegir qué archivos aparecen en tu biblioteca.")
            else localizedText("Allow audio access to scan the music on this phone.", "Permite el acceso al audio para buscar música en este teléfono."),
        onBack, modifier, step = 1,
        caption = localizedText("Your files are never moved or deleted.", "Tus archivos no se mueven ni se eliminan."),
        footer = {
            when {
                !audioGranted -> SetupAction(strings.ui(if(permanentlyDenied) AkiraUiText.SetupOpenSettings else AkiraUiText.SetupAllowAudio)) {
                    if(permanentlyDenied) onOpenSettings() else onRequestAudio()
                }
                !permissionsReady -> SetupAction(localizedText("Continue", "Continuar"), false) {}
                state.status == SetupStatus.SCANNING -> SetupAction(strings.ui(AkiraUiText.SetupScanning), false) {}
                else -> SetupAction(if(ready && readyLabel != null) readyLabel else strings.ui(
                    if(state.saving) AkiraUiText.SetupSaving else if(ready) AkiraUiText.SetupEnter else AkiraUiText.SetupFindMusic),
                    enabled = !state.saving) { if(permissionsReady) { if(ready) onFinish() else onScan() } }
            }
        }
    ) {
        SetupLibrarySignal(state, ready)
        SetupCard {
            SetupPermissionHeading(com.akira.app.core.design.icon.AkiraIcons.Album,
                strings.ui(AkiraUiText.SetupAudioTitle), audioGranted)
            Text(when {
                !audioGranted -> strings.ui(if(permanentlyDenied) AkiraUiText.SetupDenied else AkiraUiText.SetupAudioDetail)
                state.status == SetupStatus.SCANNING -> localizedText("Finding your albums, artists and songs…", "Reuniendo tus álbumes, artistas y canciones…")
                ready -> localizedText("Next, you'll choose what stays in your collection.", "Ahora podrás elegir qué se queda en tu colección.")
                else -> localizedText("Access granted. You can scan your audio files now.", "Acceso permitido. Ya puedes buscar tus archivos de audio.")
            }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.status == SetupStatus.SCANNING) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer)
                Text(state.title.ifBlank { strings.ui(AkiraUiText.SetupScanning) }, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (ready && state.songs.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    indexedAudioFormats(state.songs).forEach { (format, count) ->
                        Text("$format · $count", Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh,
                            androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (ready && state.count == 0) SetupSecondaryAction(strings.ui(AkiraUiText.Retry), onScan)
        }
        if (supportsNotifications) SetupCard {
            SetupPermissionHeading(Icons.Rounded.NotificationsNone, strings.ui(AkiraUiText.SetupNotifications), notificationGranted)
            Text(localizedText("Pause, skip or return to your music from the notification panel and lock screen.",
                "Pausa, cambia de canción o vuelve a tu música desde las notificaciones y la pantalla de bloqueo."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton({ if(!notificationGranted) onNotifications() }, Modifier.fillMaxWidth().heightIn(min = 50.dp),
                enabled = !state.saving && audioGranted,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
                Icon(if(notificationGranted) com.akira.app.core.design.icon.AkiraIcons.Check else Icons.Rounded.NotificationsActive,
                    null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
                Text(strings.ui(if(notificationGranted) AkiraUiText.SetupNotificationsEnabled else if(notificationSettings)
                    AkiraUiText.SetupOpenSettings else AkiraUiText.SetupEnableNotifications))
            }
        }
        state.failure?.let { failure -> Text(strings.ui(when(failure) {
            SetupFailure.PERMISSION -> AkiraUiText.SetupPermissionLost
            SetupFailure.SCAN -> AkiraUiText.SetupScanFailed
            SetupFailure.SAVE -> AkiraUiText.SetupSaveFailed
        }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        if (!permissionsReady) Text(localizedText("Allow music and notifications to continue.", "Permite música y notificaciones para continuar."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun SetupPermissionHeading(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, granted: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(23.dp), tint = MaterialTheme.colorScheme.primary) }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if(granted) Icon(com.akira.app.core.design.icon.AkiraIcons.Check,
            localizedText("Allowed", "Permitido"), Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable private fun SetupLibrarySignal(state: SetupState, ready: Boolean) {
    val phase = setupAmbientPhase(7200)
    val accent = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxWidth().heightIn(min = 136.dp)
        .background(MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.RoundedCornerShape(28.dp))) {
        androidx.compose.foundation.Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
            repeat(24) { i ->
                val x = size.width * (.50f + i / 48f)
                val bar = (6f + 14f * kotlin.math.sin(phase.value * 2f + i * .6f).let { it * it }) * 1.dp.toPx()
                drawLine(accent.copy(alpha = .10f), androidx.compose.ui.geometry.Offset(x, size.height / 2 - bar),
                    androidx.compose.ui.geometry.Offset(x, size.height / 2 + bar), 3.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
        Row(Modifier.fillMaxWidth().padding(22.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            SetupEclipse(Modifier.size(66.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if(ready || state.status == SetupStatus.SCANNING) {
                    Text(state.count.toString(), style = MaterialTheme.typography.displayMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Text(localizedText(if(ready) "songs found" else "and counting…", if(ready) "canciones encontradas" else "y sumando…"),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                } else {
                    Text(localizedText("Local library", "Biblioteca local"), style = MaterialTheme.typography.titleLarge)
                    Text(localizedText("Not scanned yet", "Sin escanear"), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

internal fun hasSetupPermissions(context: android.content.Context): Boolean {
    val permission=if(Build.VERSION.SDK_INT>=33)Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    return ContextCompat.checkSelfPermission(context,permission)==PackageManager.PERMISSION_GRANTED &&
        androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
}

internal fun indexedAudioFormats(songs: List<Song>): List<Pair<String, Int>> = songs
    .map { it.format.trim().uppercase(java.util.Locale.ROOT).ifBlank { "OTHER" } }
    .groupingBy { it }.eachCount().entries
    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
    .map { it.key to it.value }
