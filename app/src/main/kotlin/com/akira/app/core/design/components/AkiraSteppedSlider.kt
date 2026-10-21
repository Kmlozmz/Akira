package com.akira.app.core.design.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.app.core.design.theme.AkiraFontFamily
import com.akira.app.core.design.util.AkiraHaptics

/**
 * Slider con PASOS discretos y respuesta háptica al cruzar cada uno.
 *
 * El Slider continuo de Material 3 no sirve para valores que son un conjunto
 * cerrado de opciones (0, 3, 5, 10, 15...): el dedo nunca cae exactamente en el
 * valor y acaba redondeando a algo inesperado. Aquí:
 *  - el valor se limita a los pasos declarados,
 *  - cada cambio de paso dispara un tick (no en cada píxel),
 *  - hay marcas visuales para que se vea dónde están los valores.
 *
 * @param steps valores permitidos, ORDENADOS de menor a mayor. El primero es
 *   el mínimo y el último el máximo.
 */
@Composable
fun AkiraSteppedSlider(
    label: String,
    steps: List<Int>,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    haptics: AkiraHaptics? = null,
    valueLabel: (Int) -> String = { it.toString() }
) {
    require(steps.isNotEmpty()) { "AkiraSteppedSlider necesita al menos un paso" }
    val sorted = remember(steps) { steps.sorted() }
    val minValue = sorted.first().toFloat()
    val maxValue = sorted.last().toFloat()
    val range = (maxValue - minValue).coerceAtLeast(1f)

    // Índice del paso más cercano: es el "snap" que evita valores intermedios.
    val index = remember(value, sorted) {
        sorted.indices.minByOrNull { kotlin.math.abs(sorted[it] - value) } ?: 0
    }
    // Se recuerda el último paso para no repetir el tick si Compose recompone
    // con el mismo valor.
    var lastTickIndex by remember { mutableIntStateOf(index) }

    val effectiveHaptics = haptics ?: com.akira.app.core.design.util.rememberAkiraHaptics()

    Column(modifier = modifier) {
        if (label.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = AkiraFontFamily,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = valueLabel(value),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AkiraFontFamily,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
        }

        Slider(
            value = index.toFloat(),
            onValueChange = { raw ->
                val newIndex = kotlin.math.round(raw).toInt().coerceIn(0, sorted.lastIndex)
                if (newIndex != lastTickIndex) {
                    lastTickIndex = newIndex
                    effectiveHaptics.softTick()
                }
                onValueChange(sorted[newIndex])
            },
            valueRange = 0f..sorted.lastIndex.toFloat(),
            // Un paso intermedio menos: los valores posibles son los de `sorted`.
            steps = (sorted.lastIndex - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                activeTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                inactiveTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            modifier = Modifier.semantics {
                contentDescription = "$label: ${valueLabel(value)}"
            }
        )
    }
}
