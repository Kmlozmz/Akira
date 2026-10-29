package com.akira.app.feature.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akira.app.BuildConfig
import com.akira.app.R
import com.akira.app.core.design.brand.AkiraBrand
import com.akira.app.core.design.components.localizedText
import com.akira.app.core.design.motion.rememberAkiraBouncingFling
import com.akira.app.core.design.navigation.LocalPageScrollState
import com.akira.app.core.updates.UpdateCheck
import com.akira.app.core.updates.UpdateFailure

@Composable
internal fun UpdatesScreen(onBack: () -> Unit, model: UpdatesViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var linkFailed by remember { mutableStateOf(false) }
    val update = state.availableUpdate
    DisposableEffect(model) {
        model.setPageVisible(true)
        onDispose { model.setPageVisible(false) }
    }
    LaunchedEffect(Unit) { if (!state.checked && !BuildConfig.DEBUG) model.check(showAvailable = false) }
    AkiraStudioScreen(localizedText("Updates", "Actualizaciones"), onBack) {
        Column(Modifier.fillMaxWidth()
            .verticalScroll(LocalPageScrollState.current ?: rememberScrollState(),
                flingBehavior = rememberAkiraBouncingFling())
            .padding(horizontal = 20.dp).padding(top = 138.dp, bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(92.dp).background(AkiraBrand.accent.copy(alpha = .12f), RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_akira_logo), null, Modifier.size(76.dp), tint = AkiraBrand.accent)
            }
            Spacer(Modifier.height(20.dp))
            Text("Akira", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(36.dp))
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer,
                RoundedCornerShape(28.dp)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.checking) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = AkiraBrand.accent, strokeWidth = 2.5.dp)
                } else {
                    Icon(when {
                        state.failure != null -> Icons.Rounded.CloudOff
                        update != null -> Icons.Rounded.SystemUpdateAlt
                        else -> Icons.Rounded.CheckCircle
                    }, null, Modifier.size(30.dp), tint = if (state.failure != null)
                        MaterialTheme.colorScheme.onSurfaceVariant else AkiraBrand.accent)
                }
                Spacer(Modifier.height(14.dp))
                Text(when {
                    state.checking -> localizedText("Checking for updates", "Buscando actualizaciones")
                    state.failure != null -> localizedText("Couldn't check for updates", "No se pudo comprobar")
                    update != null -> localizedText("An update is ready", "Hay una actualización")
                    state.completedVersion != null -> localizedText("Akira has been updated", "Akira se ha actualizado")
                    else -> localizedText("No updates available", "Sin actualizaciones disponibles")
                }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(when {
                    state.failure != null -> updateFailureText(state.failure!!)
                    update != null -> localizedText("See what's new in ${update.versionName}.", "Consulta las novedades de ${update.versionName}.")
                    BuildConfig.DEBUG -> localizedText("Development build", "Compilación de desarrollo")
                    state.checking -> localizedText("This will only take a moment.", "Solo tardará un momento.")
                    state.result == UpdateCheck.NoRelease -> localizedText("New releases will appear here.", "Las nuevas versiones aparecerán aquí.")
                    else -> localizedText("You're running the latest available version.", "Tienes la última versión disponible.")
                }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, lineHeight = 21.sp)
                Spacer(Modifier.height(22.dp))
                Button(onClick = {
                    if (state.primaryAction == UpdatePrimaryAction.Check) model.check()
                    else {
                        model.openDetails()
                        if (state.primaryAction == UpdatePrimaryAction.Install) model.requestInstallation()
                    }
                },
                    enabled = !BuildConfig.DEBUG && if(update==null)state.canCheckForUpdates else !state.checking,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors()) {
                    Text(when(state.primaryAction) {
                        UpdatePrimaryAction.ViewUpdate -> localizedText("View update", "Ver actualización")
                        UpdatePrimaryAction.ViewProgress -> localizedText("View download", "Ver descarga")
                        UpdatePrimaryAction.Install -> localizedText("Install update", "Instalar actualización")
                        UpdatePrimaryAction.Check -> if(state.failure!=null)localizedText("Retry","Reintentar")
                            else localizedText("Check for updates","Buscar actualizaciones")
                    }, fontWeight = FontWeight.SemiBold)
                }
                if (update != null) TextButton(onClick = { model.check(showAvailable=false) },
                    enabled = state.canCheckForUpdates && !BuildConfig.DEBUG,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(localizedText("Check again", "Volver a comprobar"))
                }
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,
                    "https://github.com/Kmlozmz/Akira/releases".toUri())) }.onFailure { linkFailed = true }
            }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(localizedText("Release history", "Historial de versiones"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.NorthEast, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (linkFailed) Text(localizedText("Couldn't open the link.", "No se pudo abrir el enlace."),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun updateFailureText(failure: UpdateFailure): String = when (failure) {
    UpdateFailure.NETWORK -> localizedText("Check your connection and try again.", "Comprueba tu conexión y vuelve a intentarlo.")
    UpdateFailure.RATE_LIMIT -> localizedText("The service is busy. Try again later.", "El servicio está ocupado. Vuelve a intentarlo más tarde.")
    UpdateFailure.INVALID_RELEASE -> localizedText("This update isn't ready to download yet.", "Esta actualización todavía no está lista para descargar.")
    UpdateFailure.INVALID_APK -> localizedText("The download couldn't be verified. Download it again.", "No se pudo verificar la descarga. Descárgala de nuevo.")
    UpdateFailure.STORAGE -> localizedText("Couldn't save the download. Check available storage.", "No se pudo guardar la descarga. Comprueba el espacio disponible.")
}
