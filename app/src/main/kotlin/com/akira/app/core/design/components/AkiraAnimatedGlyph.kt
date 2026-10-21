package com.akira.app.core.design.components

import androidx.compose.animation.core.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import com.akira.app.core.design.components.LocalContentMotionReduced

/** One semantic control and one visible symbol. Rapid retargets cancel the old
 * sequence at its current scale; no queued taps or overlapping fading icons.
 */
@Composable
internal fun AkiraAnimatedGlyph(icon: ImageVector,label: String?,tint: Color,modifier: Modifier=Modifier) {
    val reduced=LocalContentMotionReduced.current
    var visible by remember { mutableStateOf(icon) }
    val scale=remember { Animatable(1f) }
    LaunchedEffect(icon,reduced) {
        if(reduced) { visible=icon;scale.snapTo(1f) }
        else {
            if(visible!=icon) {
                scale.animateTo(.09f,tween(65,easing=FastOutLinearInEasing))
                visible=icon
            }
            scale.animateTo(1f,spring(.72f,1200f,.002f))
        }
    }
    Icon(visible,label,tint=tint,modifier=modifier.graphicsLayer { scaleX=scale.value;scaleY=scale.value })
}
