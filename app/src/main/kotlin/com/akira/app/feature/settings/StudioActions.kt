package com.akira.app.feature.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
internal fun AudioActionButtons(resetLabel: String, actionLabel: String, onReset: () -> Unit,
    onAction: () -> Unit, actionEnabled: Boolean = true) {
    val actionSource=com.akira.app.core.design.components.rememberAkiraMenuSource(icon=null)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(Triple(resetLabel, onReset, true), Triple(actionLabel, onAction, actionEnabled)).forEachIndexed { index, item ->
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(if (pressed) .97f else 1f, tween(140), label = "audio_action_press")
            Button(onClick = { if(index==1)actionSource.open(item.second)else item.second() }, enabled = item.third, interactionSource = interaction,
                modifier = Modifier.then(if(index==1)actionSource.modifier else Modifier).weight(1f).heightIn(min = 48.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (index == 0) MaterialTheme.colorScheme.surfaceContainerHigh else androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    contentColor = if (index == 0) androidx.compose.material3.MaterialTheme.colorScheme.onSurface else androidx.compose.material3.MaterialTheme.colorScheme.surface)) { Text(item.first) }
        }
    }
}
