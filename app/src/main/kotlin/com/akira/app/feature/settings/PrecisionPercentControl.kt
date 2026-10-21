package com.akira.app.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.util.AkiraHaptics
import kotlin.math.roundToInt

@Composable
internal fun PrecisionPercentControl(label: String, steps: List<Int>, value: Int,
    onValueChange: (Int) -> Unit, haptics: AkiraHaptics, valueLabel: (Int) -> String) {
    val min = steps.first()
    val max = steps.last()
    var editing by remember { mutableStateOf(false) }
    fun change(next: Int) { haptics.softTick(); onValueChange(next.coerceIn(min,max)) }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            TextButton(onClick = { editing = true }) { Text(valueLabel(value), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { change(value-1) }, enabled = value > min) { Text("−") }
            AudioSlider(value.toFloat(), { change(it.roundToInt()) }, min.toFloat()..max.toFloat(), label, Modifier.weight(1f))
            TextButton(onClick = { change(value+1) }, enabled = value < max) { Text("+") }
        }
    }
    if (editing) {
        AudioNumberEditor(label, value.toFloat(), min.toFloat()..max.toFloat(), 1f,
            onDismiss = { editing = false }, onConfirm = { change(it.roundToInt()); editing = false })
    }
}
