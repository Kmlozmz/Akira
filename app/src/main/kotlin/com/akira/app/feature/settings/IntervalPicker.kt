package com.akira.app.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.app.core.design.components.AkiraModalSheet
import com.akira.app.core.design.components.AkiraSegmentedControl
import com.akira.app.core.i18n.LocalStrings

@Composable
internal fun IntervalPicker(title: String, description: String, selected: String,
    options: List<Pair<String, String>>, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var draft by rememberSaveable(selected) { mutableStateOf(selected) }
    val strings = LocalStrings.current
    AkiraModalSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedContent(draft, label = "interval_value") { value ->
                    Text(value, fontSize = 48.sp, fontWeight = FontWeight.SemiBold, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                }
                Text(if (draft == "1") com.akira.app.core.i18n.LocalStrings.current.ui(com.akira.app.core.i18n.AkiraUiText.Minute) else strings.library.minutes,
                    style = MaterialTheme.typography.bodyMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
            }
            AkiraSegmentedControl(options.map { it.first to it.first }, draft, { draft = it }, androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .06f))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(strings.cancel, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .75f))
                }
                Button(onClick = { onConfirm(draft) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, contentColor = androidx.compose.material3.MaterialTheme.colorScheme.surface)) {
                    Text(strings.ok)
                }
            }
        }
    }
}
