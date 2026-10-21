package com.akira.app.feature.online

import android.content.Intent
import android.webkit.CookieManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akira.app.core.online.OnlineRepository
import com.akira.app.core.design.components.AkiraSwitch
import com.akira.app.core.design.components.AkiraModalSheet
import com.akira.app.core.design.components.AkiraPanelStyle
import com.akira.app.core.design.components.localizedText
import kotlinx.coroutines.launch

@Composable fun OnlineModeControl() {
    val context = LocalContext.current
    val repository = remember(context) { OnlineRepository.get(context) }
    val enabled by repository.enabled.collectAsStateWithLifecycle()
    val account by repository.account.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val themeMode = com.akira.app.core.design.theme.LocalThemeMode.current
    val appearance = com.akira.app.core.design.theme.LocalAppearancePreset.current
    var suggestAccount by remember { mutableStateOf(false) }
    var disconnecting by remember { mutableStateOf(false) }
    val disconnectError = localizedText("Couldn't disconnect. Try again.", "No se pudo desconectar. Reintenta.")
    fun linkAccount() {
        context.startActivity(Intent(context, YouTubeLoginActivity::class.java)
            .putExtra("themeMode", themeMode).putExtra("appearancePreset", appearance))
    }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(24.dp))
        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(localizedText("Online mode", "Modo online"), style = MaterialTheme.typography.titleMedium)
                Text(if (enabled) "YouTube Music" else localizedText("Music on this phone", "Música de este teléfono"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            AkiraSwitch(checked = enabled, onCheckedChange = { value ->
                repository.setEnabled(value)
                if (value && !account.connected) suggestAccount = true
            })
        }
        if (enabled) {
            if (!account.connected) {
                Text(localizedText("Connect your account for your own recommendations.", "Conecta tu cuenta para ver tus recomendaciones."), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = ::linkAccount, modifier = Modifier.fillMaxWidth()) { Text(localizedText("Connect YouTube Music", "Conectar YouTube Music")) }
            } else Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(24.dp))
                .padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    com.akira.app.core.design.components.AkiraAccountAvatar(account, Modifier.size(56.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(account.name.ifBlank { localizedText("Your Google account", "Tu cuenta de Google") }, style = MaterialTheme.typography.titleLarge)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(localizedText("YouTube Music connected", "YouTube Music conectado"), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(localizedText("Your playlists, likes and personal recommendations are available in Online.",
                    "Tus playlists, Me gusta y recomendaciones personales están disponibles en Online."),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(enabled = !disconnecting, onClick = { scope.launch {
                    disconnecting = true
                    try {
                        repository.disconnect()
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                        android.webkit.WebStorage.getInstance().deleteAllData()
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { com.akira.app.core.design.components.AkiraNotices.show(disconnectError) }
                    finally { disconnecting = false }
                } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = CircleShape) {
                    Icon(Icons.Rounded.LinkOff, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (disconnecting) localizedText("Disconnecting…", "Desconectando…") else localizedText("Disconnect account", "Desconectar cuenta"))
                }
            }
        }
    }
    if (suggestAccount) AkiraModalSheet(onDismissRequest = { suggestAccount = false }, style = AkiraPanelStyle.Compact,
        title = localizedText("Make it your music", "Trae tu música contigo")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .96f), RoundedCornerShape(28.dp))
            .verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicNote, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text("YouTube Music · " + localizedText("Optional", "Opcional"),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(localizedText("Your music, right here", "Tu música, aquí contigo"), style = MaterialTheme.typography.headlineSmall)
            Text(localizedText("Bring what you love from YouTube Music.", "Trae lo que más te gusta de YouTube Music."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(24.dp))
                .padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                listOf(Icons.Rounded.PlaylistPlay to localizedText("Your playlists", "Tus playlists"),
                    Icons.Rounded.Favorite to localizedText("Your likes and saved music", "Tus Me gusta y música guardada"),
                    Icons.Rounded.AutoAwesome to localizedText("Personal recommendations", "Recomendaciones personales")).forEach { (icon, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(icon, null, Modifier.size(24.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Button(onClick = { suggestAccount = false; linkAccount() }, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = CircleShape) {
                Text(localizedText("Link account", "Vincular cuenta"))
            }
            TextButton(onClick = { suggestAccount = false }, modifier = Modifier.fillMaxWidth()) {
                Text(localizedText("Continue without an account", "Seguir sin cuenta"))
            }
            Text(localizedText("You can link it later in Settings.", "Puedes vincularla después en Configuración."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
