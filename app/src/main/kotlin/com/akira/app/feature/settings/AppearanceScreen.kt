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
internal fun AppearanceScreen(prefs: UserPreferences, repo: UserPreferencesRepository,
    previewSongs: List<Song>, previewSong: Song?, onBack: () -> Unit) {
    var showGlass by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(false)}
    val scope = rememberCoroutineScope()
    val strings = LocalStrings.current
    val artworkLabel=com.akira.app.core.design.components.localizedText("Full-screen album art","Carátula a pantalla completa")
    val labels = listOf("SYSTEM" to com.akira.app.core.design.components.localizedText("System", "Sistema"),
        "LIGHT" to com.akira.app.core.design.components.localizedText("Light", "Claro"),
        "AMOLED" to com.akira.app.core.design.components.localizedText("Dark", "Oscuro"))
    AkiraStudioScreen(strings.ui(AkiraUiText.Appearance), onBack) {
        Column(Modifier.fillMaxWidth().verticalScroll(com.akira.app.core.design.navigation.LocalPageScrollState.current ?: rememberScrollState(), flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()).padding(top=118.dp).padding(24.dp)) {
        SettingsGroup {
        labels.forEach { (mode,label) ->
            val selected=if(mode=="AMOLED")prefs.themeMode=="AMOLED"||prefs.themeMode=="DARK" else prefs.themeMode==mode
            Surface(onClick={scope.launch{repo.setThemeMode(mode)}},color=if(selected)MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,shape=androidx.compose.ui.graphics.RectangleShape,modifier=Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(label,Modifier.weight(1f),fontFamily=AkiraFontFamily,fontSize=17.sp)
                    if(selected)Icon(AkiraIcons.Check,label)
                }
            }
        }
        }
        Spacer(Modifier.height(24.dp))
        SettingsGroup {
        Surface(color=MaterialTheme.colorScheme.surfaceContainer,shape=androidx.compose.ui.graphics.RectangleShape) {
            Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end=16.dp)) {
                    Text(artworkLabel,style=MaterialTheme.typography.titleMedium)
                    Text(com.akira.app.core.design.components.localizedText("Edge-to-edge artwork. Off keeps the framed cover.","Portada de borde a borde. Desactivado conserva la carátula enmarcada."),
                        style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                com.akira.app.core.design.components.AkiraSwitch(checked=prefs.fullscreenPlayerArtwork,
                    modifier=Modifier.semantics { contentDescription=artworkLabel },
                    onCheckedChange={scope.launch{repo.setFullscreenPlayerArtwork(it)}})
            }
        }
        if(prefs.fullscreenPlayerArtwork) Surface(color=MaterialTheme.colorScheme.surfaceContainer,shape=androidx.compose.ui.graphics.RectangleShape) {
            val animatedLabel=com.akira.app.core.design.components.localizedText("Animated album covers","Portadas animadas")
            Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end=16.dp)) {
                    Text(animatedLabel,style=MaterialTheme.typography.titleMedium)
                    Text(com.akira.app.core.design.components.localizedText(
                        "Apple Music video artwork when available in full-screen mode. Lyrics always use the dynamic background.",
                        "Vídeos de portada de Apple Music disponibles en modo de pantalla completa. Las letras siempre usan el fondo dinámico."),
                        style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                com.akira.app.core.design.components.AkiraSwitch(checked=prefs.animatedPlayerArtwork,
                    modifier=Modifier.semantics {contentDescription=animatedLabel},
                    onCheckedChange={scope.launch {repo.setAnimatedPlayerArtwork(it)}})
            }
        }
        }
        Spacer(Modifier.height(16.dp))
        SettingsGroup { SettingsRow(AkiraIcons.Appearance,"Liquid Glass",
            com.akira.app.core.design.components.localizedText("Material, opacity and background diffusion","Material, opacidad y difuminado del fondo"),{showGlass=true})
        }
    }
    }
    if(showGlass)LiquidGlassSettingsScreen(prefs,repo,previewSong ?: previewSongs.firstOrNull()) {showGlass=false}
}
