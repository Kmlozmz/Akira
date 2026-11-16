package com.akira.app.feature.setup

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.akira.app.R

/** These are the Library navigation glyph and the recognizable Music play-disc. */
@Composable internal fun OnboardingModeArt(online: Boolean, modifier: Modifier = Modifier) {
    val phase = onboardingPhase(16_000)
    Box(modifier.size(88.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        if (online) Icon(Icons.Rounded.PlayCircle, null, Modifier.size(58.dp).graphicsLayer {
            rotationZ = kotlin.math.sin(phase.value) * 4f
        }, tint = Color(0xFFFF2442))
        else Icon(painterResource(R.drawable.ic_navigation_library_filled), null, Modifier.size(52.dp).graphicsLayer {
            translationY = kotlin.math.sin(phase.value) * 3.dp.toPx()
        }, tint = MaterialTheme.colorScheme.primary)
    }
}
