package com.akira.app.core.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Original compact Material switch, with native drag/accessibility and one
 * semantic haptic on each state change. Row owners may supply their own feedback. */
@Composable
fun AkiraSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    haptic: ((Boolean) -> Unit)? = null
) {
    val thumbColor by animateColorAsState(
        targetValue = when {
            !enabled -> Color(0xFF38384A)
            checked -> androidx.compose.material3.MaterialTheme.colorScheme.surface
            else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(160),
        label = "akira_switch_thumb"
    )
    val iconTint by animateColorAsState(
        targetValue = if (checked) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = tween(160),
        label = "akira_switch_icon"
    )
    val defaultHaptics = com.akira.app.core.design.util.rememberAkiraHaptics()

    Switch(
        checked = checked,
        onCheckedChange = { value ->
            if (haptic != null) {
                haptic.invoke(value)
            } else {
                defaultHaptics.toggle(value)
            }
            onCheckedChange(value)
        },
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = thumbColor,
            checkedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = thumbColor,
            uncheckedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest,
            uncheckedBorderColor = Color.Transparent,
            checkedBorderColor = Color.Transparent,
            disabledCheckedThumbColor = Color(0xFF38384A),
            disabledCheckedTrackColor = Color(0xFF22222A),
            disabledUncheckedThumbColor = Color(0xFF38384A),
            disabledUncheckedTrackColor = Color(0xFF16161C),
            disabledUncheckedBorderColor = Color.Transparent,
            disabledCheckedBorderColor = Color.Transparent
        ),
        thumbContent = {
            Box(
                modifier = Modifier.size(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                )
            }
        }
    )
}
