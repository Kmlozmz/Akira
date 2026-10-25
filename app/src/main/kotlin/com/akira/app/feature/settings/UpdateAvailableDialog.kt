package com.akira.app.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.akira.app.BuildConfig
import com.akira.app.core.design.brand.AkiraBrand
import com.akira.app.core.design.components.AkiraModalSheet
import com.akira.app.core.design.components.AkiraPanelStyle
import com.akira.app.core.design.components.LocalAkiraPageSheetState
import com.akira.app.core.design.components.LocalAkiraPresentationState
import com.akira.app.core.design.components.localizedText
import com.akira.app.core.updates.AppUpdate
import com.akira.app.core.updates.UpdateCheck
import com.akira.app.core.updates.UpdateException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** Owns installation once, whether an update is opened from Home or Settings. */
@Composable
internal fun UpdatesHost(allowAutomaticPrompt: Boolean, model: UpdatesViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val update = (state.result as? UpdateCheck.Available)?.update
    var waitingForPermission by rememberSaveable { mutableStateOf(false) }
    var resumeInstaller by remember { mutableStateOf(false) }
    var permissionNeeded by remember { mutableStateOf(false) }
    var installerError by remember { mutableStateOf(false) }
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        model.installerReturned()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (waitingForPermission) {
            waitingForPermission = false
            permissionNeeded = !model.updates.canInstall()
            resumeInstaller = !permissionNeeded && model.state.value.dialogVisible
        }
    }
    fun install(candidate: AppUpdate) {
        if (state.downloaded == null || state.preparingInstaller) return
        installerError = false
        if (!model.updates.canInstall()) {
            model.installationStarted()
            permissionNeeded = true
            waitingForPermission = true
            runCatching { permission.launch(model.updates.installPermissionIntent()) }
                .onFailure { waitingForPermission = false; installerError = true }
            return
        }
        model.installationStarted(preparing = true)
        scope.launch {
            try {
                val intent = model.prepareInstaller(candidate)
                if (model.state.value.dialogVisible) installer.launch(intent)
                permissionNeeded = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (error is UpdateException) model.installationFailed(error) else installerError = true
            } finally { model.installerPrepared() }
        }
    }
    var startupReady by remember { mutableStateOf(false) }
    var foreground by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                model.beginForeground()
                foreground++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        delay(3500)
        startupReady = true
        model.checkAutomatically()
    }
    val busy = LocalAkiraPresentationState.current?.entries?.isNotEmpty() == true ||
        LocalAkiraPageSheetState.current?.entries?.isNotEmpty() == true
    LaunchedEffect(startupReady, foreground, allowAutomaticPrompt, busy, state.result, state.dialogVisible) {
        if (startupReady && allowAutomaticPrompt && !busy && !state.dialogVisible) model.presentIfEligible()
    }
    LaunchedEffect(state.installWhenReady, state.downloaded, state.downloading, state.dialogVisible) {
        if (state.installWhenReady && state.downloaded != null && !state.downloading && state.dialogVisible) {
            update?.let(::install)
        }
    }
    LaunchedEffect(resumeInstaller) {
        if (resumeInstaller) { resumeInstaller = false; update?.let(::install) }
    }
    LaunchedEffect(state.dialogVisible) {
        if (!state.dialogVisible) { permissionNeeded = false; installerError = false }
    }
    LaunchedEffect(state.downloading) { if (state.downloading) installerError = false }
    // Both entry points receive the same installation status without creating a second launcher.
    SideEffect { model.installationNotice(when {
        installerError -> InstallationNotice.Error
        permissionNeeded -> InstallationNotice.Permission
        else -> null
    }) }
    if (state.dialogVisible && !state.pageVisible && update != null) UpdateAvailableDialog(state, update, model)
}

@Composable
internal fun UpdateAvailableDialog(state: UpdatesState, update: AppUpdate, model: UpdatesViewModel) {
    UpdateAvailablePanel(state, update, onDownload = { model.download(update) },
        onInstall = model::requestInstallation, onCancel = model::cancelDownload,
        onLater = model::later, onRemindTomorrow = model::remindTomorrow,
        onDismiss = model::dismiss)
}

@Composable
internal fun UpdateAvailablePanel(state: UpdatesState, update: AppUpdate, onDownload: () -> Unit,
    onInstall: () -> Unit, onCancel: () -> Unit, onLater: () -> Unit,
    onRemindTomorrow: () -> Unit, onDismiss: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    val secondary = ink.copy(alpha = .76f)
    val accent = if (ink.luminance() < .5f) lerp(AkiraBrand.accent, Color.Black, .16f) else AkiraBrand.accent
    val active = state.downloading || state.verifying || state.preparingInstaller
    val title = localizedText("Update available", "Actualización disponible")
    AkiraModalSheet(onDismissRequest = onDismiss,
        title = title, style = AkiraPanelStyle.Update) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val scrollAll = maxHeight < 540.dp || LocalDensity.current.fontScale > 1.3f
        Column(Modifier.fillMaxWidth().then(if (scrollAll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .70f), RoundedCornerShape(32.dp))
            .padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(50.dp).background(ink.copy(alpha = .055f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.SystemUpdateAlt, null, Modifier.size(27.dp), tint = accent)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, fontSize = 21.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold, color = ink,
                        modifier = Modifier.semantics { heading() })
                    Text(localizedText("A new version of Akira is ready", "Hay una nueva versión de Akira"),
                        style = MaterialTheme.typography.bodyMedium, color = secondary)
                }
            }
            Row(Modifier.fillMaxWidth().background(ink.copy(alpha = .075f), RoundedCornerShape(20.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VersionLabel(localizedText("Current", "Actual"), BuildConfig.VERSION_NAME, secondary, Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(22.dp), tint = accent)
                VersionLabel(localizedText("New", "Nueva"), update.versionName, accent, Modifier.weight(1f))
            }
            if (active || state.downloaded != null) {
                Column(Modifier.fillMaxWidth().background(ink.copy(alpha = .075f), RoundedCornerShape(20.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (active) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = accent)
                        else Icon(Icons.Rounded.CheckCircle, null, Modifier.size(22.dp), tint = accent)
                        Text(when {
                            state.cancelling -> localizedText("Cancelling…", "Cancelando…")
                            state.verifying || state.preparingInstaller -> localizedText("Verifying download…", "Verificando descarga…")
                            state.downloading -> localizedText("Downloading…", "Descargando…")
                            else -> localizedText("Ready to install", "Lista para instalar")
                        }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = ink,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                    if (state.verifying || state.preparingInstaller) LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(), color = accent, trackColor = ink.copy(alpha = .1f))
                    else {
                        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth(),
                            color = accent, trackColor = ink.copy(alpha = .1f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${megabytes(state.downloadedBytes)} / ${megabytes(update.size)} MB",
                                style = MaterialTheme.typography.bodySmall, color = secondary)
                            Text("${(state.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall,
                                color = accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Column(if (scrollAll) Modifier else Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(localizedText("What's new", "Novedades"), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = ink)
                    Box(Modifier.fillMaxWidth().then(if (scrollAll) Modifier else Modifier.weight(1f, fill = false).heightIn(max = 190.dp))
                        .clip(RoundedCornerShape(20.dp)).background(ink.copy(alpha = .075f))) {
                        Text(releaseNotes(update.releaseNotes).ifBlank {
                            localizedText("This release includes improvements and fixes.", "Esta versión incluye mejoras y correcciones.")
                        }, modifier = Modifier.then(if (scrollAll) Modifier else Modifier.verticalScroll(rememberScrollState())).padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp, color = secondary)
                    }
                }
            }
            val notice = when {
                state.failure != null -> updateFailureText(state.failure)
                state.installationNotice == InstallationNotice.Error -> localizedText("Couldn't open the installer. Try again.", "No se pudo abrir el instalador. Vuelve a intentarlo.")
                state.installationNotice == InstallationNotice.Permission -> localizedText("Allow installation in Android settings to continue.", "Permite la instalación en los ajustes de Android para continuar.")
                state.installerReturned -> localizedText("If installation was cancelled, you can try again.", "Si cancelaste la instalación, puedes volver a intentarlo.")
                state.downloadCancelled -> localizedText("Download cancelled", "Descarga cancelada")
                else -> null
            }
            if (notice != null) Text(notice, style = MaterialTheme.typography.bodySmall,
                color = if (state.failure != null || state.installationNotice == InstallationNotice.Error)
                    MaterialTheme.colorScheme.error else secondary)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.downloading) {
                    Button(onClick = onCancel, enabled = !state.cancelling,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ink.copy(alpha = .1f), contentColor = ink)) {
                        Text(localizedText("Cancel", "Cancelar"), fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Button(onClick = { if (state.downloaded == null) onDownload() else onInstall() },
                        enabled = !state.preparingInstaller && !state.checking,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AkiraBrand.accent, contentColor = Color.Black)) {
                        Icon(Icons.Rounded.Download, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.downloaded == null) localizedText("Download & install", "Descargar e instalar")
                            else localizedText("Install update", "Instalar actualización"), fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRemindTomorrow, enabled = !state.preparingInstaller,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ink.copy(alpha = .08f), contentColor = ink)) {
                            Text(localizedText("Remind tomorrow", "Recordar mañana"), fontSize = 14.sp, textAlign = TextAlign.Center)
                        }
                        Button(onClick = onLater, enabled = !state.preparingInstaller,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ink.copy(alpha = .08f), contentColor = ink)) {
                            Text(localizedText("Later", "Más tarde"), fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun VersionLabel(label: String, version: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f))
        Text(version, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            color = color, textAlign = TextAlign.Center)
    }
}

private fun megabytes(bytes: Long) = String.format(Locale.getDefault(), "%.1f", bytes / 1048576.0)

private fun releaseNotes(notes: String) = notes.take(16000).lineSequence().joinToString("\n") { line ->
    line.replace(Regex("^#{1,6}\\s+"), "").replace(Regex("^[-*]\\s+"), "• ").replace("**", "")
}.trim()
