package com.akira.app.feature.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akira.app.core.datastore.UserPreferences
import com.akira.app.core.datastore.UserPreferencesRepository
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.design.theme.AkiraFontFamily
import com.akira.app.core.i18n.LocalStrings
import com.akira.app.core.i18n.AkiraUiText
import com.akira.app.core.model.Song
import kotlinx.coroutines.launch
import com.akira.app.core.design.glass.liquidGlass
import com.akira.app.core.design.backdrop.backdrops.layerBackdrop
import com.akira.app.core.design.backdrop.backdrops.rememberLayerBackdrop

@Composable
internal fun LiquidGlassSettingsScreen(prefs: UserPreferences,repo: UserPreferencesRepository,song: Song?,onBack: ()->Unit) {
    AkiraStudioScreen("Liquid Glass",onBack) {
        Column(Modifier.fillMaxWidth().verticalScroll(com.akira.app.core.design.navigation.LocalPageScrollState.current ?: rememberScrollState())
            .padding(top=118.dp).padding(horizontal=24.dp,vertical=20.dp)) {
            GlassSettingsControls(prefs,repo,song)
        }
    }
}

/** Shared setup and Settings controls for every exposed optical parameter. */
@Composable internal fun GlassSettingsControls(prefs: UserPreferences,repo: UserPreferencesRepository,song: Song?,scope: kotlinx.coroutines.CoroutineScope=rememberCoroutineScope(), tonalReset: Boolean = false) {
        Surface(color=MaterialTheme.colorScheme.surfaceContainer,shape=RoundedCornerShape(22.dp)) {
            Column {
            Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end=16.dp)) {
                    Text("Liquid Glass",style=MaterialTheme.typography.titleMedium)
                    Text(com.akira.app.core.design.components.localizedText(
                        "Refraction and highlights on floating controls and panels. Off keeps a simple blur.",
                        "Refracción y reflejos en controles y paneles flotantes. Desactivado conserva un difuminado sencillo."),
                        style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                com.akira.app.core.design.components.AkiraSwitch(modifier=Modifier.semantics { contentDescription="Liquid Glass" },checked=prefs.liquidGlassEnabled,onCheckedChange={enabled->scope.launch{repo.setLiquidGlassEnabled(enabled)}},
                    enabled=android.os.Build.VERSION.SDK_INT>=31)
            }
            var opacity by remember { mutableFloatStateOf(prefs.liquidGlassOpacity) }
            var dragging by remember { mutableStateOf(false) }
            var pendingOpacity by remember { mutableStateOf<Float?>(null) }
            LaunchedEffect(prefs.liquidGlassOpacity,dragging) {
                if(!dragging && (pendingOpacity==null || pendingOpacity==prefs.liquidGlassOpacity)) {
                    opacity=prefs.liquidGlassOpacity
                    pendingOpacity=null
                    com.akira.app.core.design.glass.GlassOpacityPreview.value=null
                }
            }
            var diffusion by remember { mutableFloatStateOf(prefs.liquidGlassDiffusion) }
            var diffusing by remember { mutableStateOf(false) }
            var pendingDiffusion by remember { mutableStateOf<Float?>(null) }
            LaunchedEffect(prefs.liquidGlassDiffusion,diffusing) {
                if(!diffusing && (pendingDiffusion==null || pendingDiffusion==prefs.liquidGlassDiffusion)) {
                    diffusion=prefs.liquidGlassDiffusion;pendingDiffusion=null
                    com.akira.app.core.design.glass.GlassDiffusionPreview.value=null
                }
            }
            DisposableEffect(Unit) {
                onDispose {
                    com.akira.app.core.design.glass.GlassOpacityPreview.value=null
                    com.akira.app.core.design.glass.GlassDiffusionPreview.value=null
                }
            }
            LaunchedEffect(prefs.liquidGlassEnabled) {
                if(!prefs.liquidGlassEnabled) {
                    com.akira.app.core.design.glass.GlassOpacityPreview.value=null
                    com.akira.app.core.design.glass.GlassDiffusionPreview.value=null
                }
            }
            androidx.compose.animation.AnimatedVisibility(prefs.liquidGlassEnabled) {
            Column(Modifier.padding(start=20.dp,end=20.dp,bottom=16.dp)) {
                GlassOpacityPreviewCard(song,showDock=prefs.dockBackgroundDiffusion)
                Spacer(Modifier.height(16.dp))
                val opacityLabel=com.akira.app.core.design.components.localizedText("Glass opacity","Opacidad del cristal")
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(opacityLabel,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                    Text("${(opacity*100).toInt()}%",style=MaterialTheme.typography.labelLarge)
                }
                ExpressiveAudioSlider(opacity,{ value ->
                    opacity=value;dragging=true;pendingOpacity=null
                    com.akira.app.core.design.glass.GlassOpacityPreview.value=value
                },0f..1f,opacityLabel,Modifier.fillMaxWidth(),enabled=prefs.liquidGlassEnabled,
                    onFinished={
                        val selected=opacity
                        pendingOpacity=selected
                        dragging=false
                        scope.launch {
                            repo.setLiquidGlassOpacity(selected)
                        }
                    })
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(com.akira.app.core.design.components.localizedText("Clear","Claro"),style=MaterialTheme.typography.labelSmall)
                    Text(com.akira.app.core.design.components.localizedText("Tinted","Tintado"),style=MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(16.dp))
                val diffusionLabel=com.akira.app.core.design.components.localizedText("Background diffusion","Difuminado del fondo")
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(diffusionLabel,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                    Text("${(diffusion/24f*100).toInt()}%",style=MaterialTheme.typography.labelLarge)
                }
                ExpressiveAudioSlider(diffusion,{ value ->
                    diffusion=value;diffusing=true;pendingDiffusion=null
                    com.akira.app.core.design.glass.GlassDiffusionPreview.value=value
                },0f..24f,diffusionLabel,Modifier.fillMaxWidth(),enabled=prefs.liquidGlassEnabled,
                    onFinished={
                        val selected=diffusion;pendingDiffusion=selected;diffusing=false
                        scope.launch { repo.setLiquidGlassDiffusion(selected) }
                    })
                Text(com.akira.app.core.design.components.localizedText(
                    "Softens backgrounds in menus and panels without adding tint. The selection lens always stays clear.",
                    "Suaviza el fondo de menús y paneles sin añadir tinte. La lente de selección siempre permanece nítida."),
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(com.akira.app.core.design.components.localizedText("Also apply to dock", "Aplicar también al dock"),style=MaterialTheme.typography.bodyLarge)
                        Text(com.akira.app.core.design.components.localizedText("See your dock in the preview above.","Mira el dock en la vista previa de arriba."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val dockLabel=com.akira.app.core.design.components.localizedText("Apply to dock","Aplicar al dock")
                    com.akira.app.core.design.components.AkiraSwitch(checked=prefs.dockBackgroundDiffusion,enabled=prefs.liquidGlassEnabled,
                        modifier=Modifier.semantics {contentDescription=dockLabel},
                        onCheckedChange={ enabled -> scope.launch { repo.setDockBackgroundDiffusion(enabled) } })
                }

            }
            }
            }
        }

    val haptics=com.akira.app.core.design.util.rememberAkiraHaptics()
    androidx.compose.animation.AnimatedVisibility(prefs.liquidGlassEnabled) {
    val reset: () -> Unit = { haptics.confirm(); scope.launch {
        repo.setLiquidGlassOpacity(.25f); repo.setLiquidGlassDiffusion(8f); repo.setDockBackgroundDiffusion(false)
    }; Unit }
    val label = com.akira.app.core.design.components.localizedText("Restore glass defaults", "Restablecer el cristal")
    if (tonalReset) FilledTonalButton(reset, Modifier.fillMaxWidth().padding(top = 14.dp).heightIn(min = 52.dp),
        shape = RoundedCornerShape(18.dp)) { Text(label) }
    else OutlinedButton(reset, Modifier.fillMaxWidth().padding(top = 14.dp).heightIn(min = 50.dp),
        shape = RoundedCornerShape(18.dp)) { Text(label) }
    }
}

/** Real shared glass over local artwork: the slider can be judged in place. */
@Composable
internal fun GlassOpacityPreviewCard(song: Song?,showDock: Boolean=false) {
    val backdrop=rememberLayerBackdrop()
    val shape=RoundedCornerShape(18.dp)
    val bottom by androidx.compose.animation.core.animateDpAsState(if(showDock)66.dp else 14.dp,label="glassDockPreview")
    Box(Modifier.fillMaxWidth().height(210.dp).clip(shape)) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)
            .background(MaterialTheme.colorScheme.primaryContainer)) {
            coil.compose.AsyncImage(song?.albumArtUri,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(start=10.dp,end=10.dp,bottom=bottom.coerceAtLeast(0.dp)).fillMaxWidth()
            .liquidGlass(RoundedCornerShape(50),backdrop).padding(horizontal=16.dp,vertical=12.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Text(song?.title ?: "Liquid Glass",Modifier.weight(1f),maxLines=1,
                color=com.akira.app.core.design.glass.glassContentColor(),
                style=MaterialTheme.typography.titleSmall)
            Icon(AkiraIcons.Play,null,Modifier.size(22.dp),tint=com.akira.app.core.design.glass.glassContentColor())
        }
        androidx.compose.animation.AnimatedVisibility(showDock,Modifier.align(Alignment.BottomCenter),
            enter=androidx.compose.animation.fadeIn()+androidx.compose.animation.expandVertically(),exit=androidx.compose.animation.fadeOut()+androidx.compose.animation.shrinkVertically()) {
            androidx.compose.runtime.CompositionLocalProvider(com.akira.app.core.design.glass.LocalIsDockGlass provides true) {
                Row(Modifier.padding(10.dp).fillMaxWidth().liquidGlass(RoundedCornerShape(50),backdrop).padding(horizontal=18.dp,vertical=12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                    listOf(com.akira.app.R.drawable.ic_navigation_home_filled,com.akira.app.R.drawable.ic_navigation_library_filled,
                        com.akira.app.R.drawable.ic_navigation_search_outline).forEach {res->Icon(androidx.compose.ui.res.painterResource(res),null,Modifier.size(23.dp),tint=com.akira.app.core.design.glass.glassContentColor())}
                }
            }
        }
    }
}
