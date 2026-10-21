package com.akira.app.feature.player

import com.akira.app.feature.library.*
import com.akira.app.feature.lyrics.*

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** The same player slots and state in both orientations; no second transport. */
@Composable
internal fun AkiraExpandedPlayerLayout(wide: Boolean, modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.SpaceBetween,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit) {
    if(!wide) Column(modifier,verticalArrangement,horizontalAlignment,content)
    else Column(modifier) {
        val columnScope=this
        Layout(content={columnScope.content()},modifier=Modifier.fillMaxSize()) { nodes, limits ->
            // Handle, gap, hero, title, gap, transport. Slots remain composed
            // while rotating, preserving playback, seeking and artwork anchors.
            require(nodes.size==6) { "Expanded player expects six persistent slots" }
            val gap=24.dp.roundToPx()
            val header=nodes[0].measure(Constraints(maxWidth=limits.maxWidth,maxHeight=24.dp.roundToPx()))
            val bodyHeight=(limits.maxHeight-header.height).coerceAtLeast(1)
            val artWidth=min((limits.maxWidth*.44f).toInt(),(bodyHeight-20.dp.roundToPx()).coerceAtLeast(1))
            val rightX=artWidth+gap
            val rightWidth=(limits.maxWidth-rightX).coerceAtLeast(1)
            val hero=nodes[2].measure(Constraints.fixed(artWidth,bodyHeight))
            val title=nodes[3].measure(Constraints(minWidth=rightWidth,maxWidth=rightWidth,maxHeight=bodyHeight))
            val controls=nodes[5].measure(Constraints(minWidth=rightWidth,maxWidth=rightWidth,
                maxHeight=(bodyHeight-title.height-8.dp.roundToPx()).coerceAtLeast(1)))
            nodes[1].measure(Constraints.fixed(0,0));nodes[4].measure(Constraints.fixed(0,0))
            layout(limits.maxWidth,limits.maxHeight) {
                header.placeRelative((limits.maxWidth-header.width)/2,0)
                hero.placeRelative(0,header.height)
                title.placeRelative(rightX,header.height)
                controls.placeRelative(rightX,header.height+title.height+8.dp.roundToPx())
            }
        }
    }
}
