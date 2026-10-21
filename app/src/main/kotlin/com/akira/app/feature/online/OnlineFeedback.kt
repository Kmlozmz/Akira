package com.akira.app.feature.online

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.components.localizedText
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.online.OnlineFailure

@Composable internal fun OnlineLoadFeedback(failure: OnlineFailure, cached: Boolean, onRetry: () -> Unit, onAccount: () -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp).fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(22.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AkiraIcons.Info, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Text(when(failure) {
                OnlineFailure.CONNECTION -> localizedText("Connection interrupted", "Se interrumpió la conexión")
                OnlineFailure.SESSION -> localizedText("Check your YouTube Music session", "Revisa tu sesión de YouTube Music")
                OnlineFailure.SERVICE -> localizedText("This content is unavailable", "Este contenido no está disponible")
            }, style = MaterialTheme.typography.titleMedium)
        }
        Text(when {
            cached -> localizedText("Showing previous results. You can keep browsing and try updating again.", "Se muestran los resultados anteriores. Puedes seguir explorando y reintentar la actualización.")
            failure == OnlineFailure.CONNECTION -> localizedText("Check your connection, then try again.", "Comprueba tu conexión y vuelve a intentarlo.")
            failure == OnlineFailure.SESSION -> localizedText("The service rejected this request. Check the linked account or continue as a guest.", "El servicio rechazó la solicitud. Revisa la cuenta vinculada o continúa como invitado.")
            else -> localizedText("YouTube Music couldn't return this page. Try again in a moment.", "YouTube Music no pudo devolver esta página. Vuelve a intentarlo en unos momentos.")
        }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onRetry) { Text(localizedText("Retry", "Reintentar")) }
            if(failure == OnlineFailure.SESSION) TextButton(onAccount) { Text(localizedText("Account", "Cuenta")) }
        }
    }
}
