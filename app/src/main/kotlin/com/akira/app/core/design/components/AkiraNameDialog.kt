package com.akira.app.core.design.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.akira.app.core.i18n.LocalStrings

/** Shared name editor; feature modules own the persistence action. */
@Composable
fun AkiraNameDialog(initial: String = "", onDismiss: () -> Unit, onSave: (String) -> Unit, saving: Boolean = false) {
    val strings = LocalStrings.current
    var name by rememberSaveable(initial) { mutableStateOf(initial) }
    AkiraModalSheet(onDismissRequest = { if (!saving) onDismiss() },title=strings.library.name) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(strings.library.name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
            TextField(name, { name = it }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth(),
                label = { Text(strings.library.name) }, shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .06f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .06f), focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(strings.cancel, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
                }
                Button(onClick = { onSave(name.trim()) }, enabled = !saving && name.isNotBlank(),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface)) {
                    Text(strings.library.save)
                }
            }
        }
    }
}
