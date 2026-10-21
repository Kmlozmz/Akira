package com.akira.app.core.design.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Respuesta háptica centralizada.
 *
 * Usa las constantes semánticas de Android (SEGMENT_TICK, TOGGLE_ON/OFF, CONFIRM...)
 * cuando el sistema las soporta y cae a equivalentes más antiguos si no. Pasa por
 * View.performHapticFeedback, así que respeta el ajuste "vibración al tocar" del
 * usuario y funciona con los motores hápticos modernos.
 */
class AkiraHaptics(private val view: View) {

    private fun perform(constant: Int) {
        try {
            view.performHapticFeedback(constant)
        } catch (_: Exception) {
            try { view.performHapticFeedback(constant) } catch (_: Exception) {}
        }
    }

    /** Selección discreta: elegir una opción, cambiar de pestaña. */
    fun tick() = perform(
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK
        else HapticFeedbackConstants.CLOCK_TICK
    )

    /** Muy suave y frecuente: pasos de un slider mientras se arrastra. */
    fun softTick() = perform(
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
        else HapticFeedbackConstants.CLOCK_TICK
    )

    /** Interruptores. */
    fun toggle(on: Boolean) = perform(
        when {
            Build.VERSION.SDK_INT >= 34 ->
                if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
            Build.VERSION.SDK_INT >= 30 -> HapticFeedbackConstants.CONFIRM
            else -> HapticFeedbackConstants.KEYBOARD_TAP
        }
    )

    /** Acción completada: restablecer, aplicar. */
    fun confirm() = perform(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.KEYBOARD_TAP
    )

    /** Toque ligero genérico (botones, volver). */
    fun light() = perform(HapticFeedbackConstants.KEYBOARD_TAP)

    /** Click estándar para botones e interacciones. */
    fun click() = perform(HapticFeedbackConstants.KEYBOARD_TAP)

    /** A single firm detent for committed transport actions, not a long buzz. */
    fun transport() = perform(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONTEXT_CLICK
        else HapticFeedbackConstants.KEYBOARD_TAP
    )

    /** Toggle sin argumentos. */
    fun toggle() = toggle(true)

    /** Pulsación larga / énfasis. */
    fun heavy() = perform(HapticFeedbackConstants.LONG_PRESS)
}

@Composable
fun rememberAkiraHaptics(): AkiraHaptics {
    val view = LocalView.current
    return remember(view) { AkiraHaptics(view) }
}
