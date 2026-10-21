package com.akira.app.feature.library

import com.akira.app.feature.lyrics.*
import com.akira.app.feature.player.*
import com.akira.app.feature.queue.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.akira.app.core.design.components.rememberAkiraMenuSource
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.design.util.rememberAkiraHaptics
import com.akira.app.core.i18n.LocalStrings
import com.akira.app.core.design.glass.liquidGlass
import com.akira.app.core.design.backdrop.Backdrop
import com.akira.app.core.design.backdrop.backdrops.rememberCanvasBackdrop

/** These controls sit on the page colour, not on overlapping rows. Recording
 * their containing page would sample their own RenderNode and replay old pixels. */
@Composable internal fun libraryControlsBackdrop(override: Backdrop?): Backdrop {
    val color=MaterialTheme.colorScheme.background
    val background=rememberCanvasBackdrop {drawRect(color)}
    return override ?: background
}

@Composable
internal fun LibraryActions(onPlay: () -> Unit,onShuffle: () -> Unit,onSort: () -> Unit,enabled: Boolean,
    backdropOverride: Backdrop? = null) {
    val strings=LocalStrings.current
    val haptics=rememberAkiraHaptics()
    val source=rememberAkiraMenuSource(icon=AkiraIcons.Filter)
    val shape=RoundedCornerShape(50)
    val backdrop=libraryControlsBackdrop(backdropOverride)
    val shadowStrength=if(MaterialTheme.colorScheme.background.luminance()>.5f).18f else 1f
    Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=12.dp),
        horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
        @Composable fun Action(icon: ImageVector,label: String,modifier: Modifier,onClick: () -> Unit) {
            Row(modifier.height(50.dp).liquidGlass(shape,backdrop,shadowStrength).clip(shape)
                .clickable(enabled=enabled,role=Role.Button) { haptics.confirm();onClick() }
                .padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.Center) {
                Icon(icon,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurface.copy(alpha=if(enabled)1f else .38f))
                Spacer(Modifier.width(8.dp))
                Text(label,style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.onSurface.copy(alpha=if(enabled)1f else .38f))
            }
        }
        Action(AkiraIcons.Play,strings.play,Modifier.weight(1f),onPlay)
        Action(AkiraIcons.Shuffle,strings.shuffle,Modifier.weight(1f),onShuffle)
        Box(Modifier.then(source.modifier).size(50.dp).liquidGlass(shape,backdrop,shadowStrength).clip(shape)
            .clickable(role=Role.Button) {haptics.light();source.open(onSort)},contentAlignment=Alignment.Center) {
            Icon(AkiraIcons.Filter,strings.sortTitle,Modifier.size(22.dp))
        }
    }
}
