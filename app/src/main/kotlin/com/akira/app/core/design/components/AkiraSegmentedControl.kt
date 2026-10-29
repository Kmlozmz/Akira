package com.akira.app.core.design.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.akira.app.core.design.theme.AkiraFontFamily

/** One moving indicator, shared by the horizontal preference and sort controls. */
@Composable
fun AkiraSegmentedControl(options: List<Pair<String, String>>, selected: String,
    onSelect: (String) -> Unit, background: Color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
    fontFamily: androidx.compose.ui.text.font.FontFamily = AkiraFontFamily,
    cornerRadius: Dp = 16.dp, indicatorRadius: Dp = 12.dp,
    indicatorColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
    selectedTextColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary) {
    if (options.isEmpty()) return
    val reduced = LocalContentMotionReduced.current
    BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(cornerRadius))
        .background(background).padding(4.dp).selectableGroup()) {
        val cell = maxWidth / options.size
        val position by animateDpAsState(cell * options.indexOfFirst { it.first == selected }.coerceAtLeast(0),
            tween(if (reduced) 0 else 230), label = "segment_position")
        if (options.any { it.first == selected }) Box(Modifier.offset(x = position).width(cell).heightIn(min = 48.dp).padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(indicatorRadius)).background(indicatorColor))
        Row(Modifier.fillMaxWidth()) {
            options.forEach { (key, label) ->
                val active = key == selected
                Box(Modifier.weight(1f).heightIn(min = 48.dp).selectable(active,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null, role = Role.RadioButton, onClick = { onSelect(key) }),
                    contentAlignment = Alignment.Center) {
                    Text(label, fontFamily = fontFamily, fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) selectedTextColor else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}
