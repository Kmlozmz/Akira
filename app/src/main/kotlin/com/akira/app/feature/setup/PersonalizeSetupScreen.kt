package com.akira.app.feature.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akira.app.core.datastore.*
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.model.Song
import com.akira.app.core.design.glass.liquidGlass
import com.akira.app.core.design.backdrop.backdrops.*
import com.akira.app.core.design.components.*
import com.akira.app.core.design.motion.*
import com.akira.app.core.design.navigation.*
import com.akira.app.core.design.rendering.*
import com.akira.app.feature.home.components.*
import com.akira.app.core.artwork.*
import com.akira.app.feature.library.*
import com.akira.app.feature.lyrics.*
import com.akira.app.feature.player.*
import com.akira.app.feature.queue.*
import com.akira.app.feature.settings.LibraryFilterReview
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

@Composable internal fun PersonalizeSetupScreen(repo: UserPreferencesRepository,onBack: ()->Unit,onComplete: ()->Unit) =
    SetupBrandTheme { PersonalizeSetupContent(repo, onBack, onComplete) }

@Composable private fun PersonalizeSetupContent(repo: UserPreferencesRepository,onBack: ()->Unit,onComplete: ()->Unit) {
    val prefs by repo.userPreferences.collectAsStateWithLifecycle(UserPreferences())
    val model: LibrarySetupViewModel=viewModel()
    val state by model.controller.state.collectAsStateWithLifecycle()
    val songs by model.allScannedSongs.collectAsStateWithLifecycle()
    var step by rememberSaveable {mutableIntStateOf(0)}
    var finishing by rememberSaveable {mutableStateOf(false)}
    var pending by remember {mutableIntStateOf(0)}
    var saveFailed by remember {mutableStateOf(false)}
    var showReview by rememberSaveable {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val reduced=setupReducedMotion()
    var presentedStep by rememberSaveable { mutableIntStateOf(step) }
    val pagePresence = remember { Animatable(1f) }
    val changingPage = presentedStep != step || pagePresence.isRunning
    LaunchedEffect(step, reduced) {
        if (presentedStep != step) {
            if (reduced) pagePresence.snapTo(0f)
            else pagePresence.animateTo(0f, tween(85, easing = LinearEasing))
            presentedStep = step
        }
        if (reduced) pagePresence.snapTo(1f)
        else pagePresence.animateTo(1f, tween(160, easing = FastOutSlowInEasing))
    }
    fun save(block: suspend ()->Unit) {
        pending++
        scope.launch {
            try {block();saveFailed=false}
            catch(cancel: kotlinx.coroutines.CancellationException) {throw cancel}
            catch(_: Exception) {saveFailed=true}
            finally {pending--}
        }
    }
    fun back() {if(showReview)showReview=false else if(!changingPage) {if(step>0)step-- else onBack()}}
    BackHandler(!finishing) {back()}
    BackHandler(finishing) {}
    LaunchedEffect(state.status, state.failure, state.saving, state.completed, finishing) {
        if (finishing) {
            if (state.failure != null) finishing = false
            else model.controller.resumeCompletion()
        }
    }
    val completion by rememberUpdatedState(onComplete)
    LaunchedEffect(state.completed) {if(state.completed)completion()}
    val sample=remember(songs) {songs.firstOrNull { !it.albumArtUri.isNullOrBlank() } ?: songs.firstOrNull()}
    SideEffect { if (step > 4) step = 4; if (presentedStep > 4) presentedStep = 4 }
    val titles = listOf(
        localizedText("Library filters", "Filtros de biblioteca"),
        localizedText("Appearance", "Apariencia"),
        localizedText("Player", "Reproductor"),
        localizedText("Playback", "Reproducción"),
        localizedText("Setup complete", "Configuración lista")
    )
    val subtitles = listOf(
        localizedText("Choose which audio files appear in your library.", "Elige qué archivos de audio aparecen en tu biblioteca."),
        localizedText("Choose a light or dark theme, or use your phone’s setting.", "Elige un tema claro, oscuro o el del teléfono."),
        localizedText("Preview the album cover layout.", "Comprueba cómo se verá la portada."),
        localizedText("Adjust song transitions and volume.", "Ajusta las transiciones y el volumen."),
        localizedText("Your library and settings are ready.", "Tu biblioteca y tus ajustes están listos.")
    )
    SetupLayout(titles[presentedStep.coerceAtMost(4)], subtitles[presentedStep.coerceAtMost(4)], { if (!finishing) back() },
        step = presentedStep + 2, pagePresence = { pagePresence.value }, footer = {
            val page = presentedStep
            SetupAction(if(finishing)localizedText("Preparing your collection…","Preparando tu colección…") else if(page==4)localizedText("Start listening","Empezar a escuchar") else localizedText("Continue","Continuar"),
                enabled=!finishing && !saveFailed) {
                if(changingPage) return@SetupAction
                // Wait for preference writes before advancing without blinking the footer disabled.
                scope.launch {
                    snapshotFlow {pending}.first {it==0}
                    if(!finishing && !saveFailed && step==page) {if(page<4)step++ else {finishing=true;model.controller.start()}}
                }
            }
        }) {
            key(presentedStep) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            when(presentedStep) {
                0 -> {
                    SetupCard {
                        Text(localizedText("Exclude folders","Excluir carpetas"),style=MaterialTheme.typography.titleMedium)
                        SetupToggle(localizedText("Hide WhatsApp audio","Ocultar audios de WhatsApp"),"WhatsApp" in prefs.excludedFolderPaths) {save {repo.setFolderExcluded("WhatsApp",it)}}
                        HorizontalDivider(color=MaterialTheme.colorScheme.onSurface.copy(alpha=.08f))
                        SetupToggle(localizedText("Hide ringtones","Ocultar tonos de llamada"),"Ringtones" in prefs.excludedFolderPaths) {save {repo.setFolderExcluded("Ringtones",it)}}
                    }
                    SetupCard {
                        Text(localizedText("Minimum length","Duración mínima"),style=MaterialTheme.typography.titleMedium)
                        SetupChoices(listOf("0" to localizedText("All","Todas"),"15" to "15 s","30" to "30 s","60" to "1 min"),prefs.minDurationSeconds.toString()) {save {repo.setMinDurationSeconds(it.toInt())}}
                        val excludedCount = remember(songs, prefs.minDurationSeconds, prefs.excludedFolderPaths, prefs.whitelistedSongIds) {
                            songs.count { !com.akira.app.core.data.passesLibraryFilters(it,prefs.minDurationSeconds,prefs.excludedFolderPaths,prefs.whitelistedSongIds) }
                        }
                        Text(localizedText("Review excluded files and include any songs you want to keep.","Revisa los archivos excluidos y recupera las canciones que quieras."),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        SetupSecondaryAction(localizedText("Review excluded files ($excludedCount)", "Revisar excluidos ($excludedCount)")) { showReview = !showReview }
                    }
                }
                1 -> {
                    SetupThemePreview(songs,prefs.themeMode)
                    SetupChoices(listOf("SYSTEM" to localizedText("System","Sistema"),"LIGHT" to localizedText("Light","Claro"),"DARK" to localizedText("Dark","Oscuro")),if(prefs.themeMode=="AMOLED")"DARK" else prefs.themeMode) {save {repo.setThemeMode(it)}}
                    Text(localizedText("System uses your phone’s appearance setting.","Sistema utiliza el tema del teléfono."),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                2 -> {
                    SetupPlayerPreview(sample,prefs.fullscreenPlayerArtwork)
                    SetupCard {
                        SetupToggle(localizedText("Full-screen cover","Portada a pantalla completa"),prefs.fullscreenPlayerArtwork) {save {repo.setFullscreenPlayerArtwork(it)}}
                        Text(if(prefs.fullscreenPlayerArtwork)localizedText("Fill the player with the album cover.","La portada ocupa toda la pantalla del reproductor.")
                            else localizedText("Show the cover above the playback controls.","La portada aparece sobre los controles de reproducción."),
                            style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        AnimatedVisibility(prefs.fullscreenPlayerArtwork) { Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        HorizontalDivider(color=MaterialTheme.colorScheme.onSurface.copy(alpha=.08f))
                        SetupToggle(localizedText("Animated covers","Portadas animadas"),prefs.animatedPlayerArtwork) {save {repo.setAnimatedPlayerArtwork(it)}}
                        Text(localizedText("Play video artwork when available.","Reproduce portadas en vídeo cuando estén disponibles."),
                            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        } }
                    }
                }
                3 -> {
                    SetupCard {
                        Text(localizedText("Between songs","Entre canciones"),style=MaterialTheme.typography.titleMedium)
                        SetupToggle("Crossfade",prefs.crossfadeSeconds>0) {value ->save {repo.setCrossfadeSeconds(if(value)prefs.lastCrossfadeSeconds.coerceAtLeast(1) else 0)}}
                        AnimatedVisibility(prefs.crossfadeSeconds>0) {
                            Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                            CrossfadePreview(prefs.crossfadeSeconds)
                            SetupDuration(prefs.crossfadeSeconds) {value ->save {repo.setCrossfadeSeconds(value)}}
                            Text(localizedText("Overlap the end of one song with the next.","Mezcla el final de una canción con el inicio de la siguiente."),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    SetupCard {
                        Text(localizedText("Volume normalization","Normalización de volumen"),style=MaterialTheme.typography.titleMedium)
                        SetupSoundChoice(localizedText("Original","Original"),localizedText("Keep each song’s original level","Conserva el nivel original de cada canción"),prefs.normalizationMode=="OFF",SetupAudioIcons.Original) {save {repo.setNormalizationMode("OFF")}}
                        SetupSoundChoice(localizedText("Balanced","Equilibrado"),localizedText("Consistent volume between songs","Volumen consistente entre canciones"),prefs.normalizationMode=="TRACK",SetupAudioIcons.Balanced) {save {repo.setNormalizationMode("TRACK")}}
                        SetupSoundChoice(localizedText("By album","Por álbum"),localizedText("Keep an album’s intended balance","Respeta el equilibrio de cada álbum"),prefs.normalizationMode=="ALBUM",AkiraIcons.Album) {save {repo.setNormalizationMode("ALBUM")}}
                    }
                }
                4 -> {
                    val included = remember(songs, prefs.minDurationSeconds, prefs.excludedFolderPaths, prefs.whitelistedSongIds) {
                        songs.filter { com.akira.app.core.data.passesLibraryFilters(it, prefs.minDurationSeconds, prefs.excludedFolderPaths, prefs.whitelistedSongIds) }
                    }
                    SetupReadyCollection(included, prefs)
                }
            }
                }
            }
            if(saveFailed)Text(localizedText("That choice couldn't be saved. Try it again.","No se pudo guardar ese ajuste. Vuelve a elegirlo."),color=MaterialTheme.colorScheme.error)
            state.failure?.let {
                Text(localizedText("We couldn't finish. Review music and notification access, then try again.","No se pudo terminar. Revisa el acceso a música y notificaciones y reintenta."),color=MaterialTheme.colorScheme.error)
                TextButton(onBack) {Text(localizedText("Review permissions","Revisar permisos"))}
            }
    }
    if(showReview) androidx.compose.ui.window.Dialog(onDismissRequest={showReview=false},
        properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth(.94f).widthIn(max=600.dp).fillMaxHeight(.86f),shape=RoundedCornerShape(32.dp),color=MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text(localizedText("Song filters","Filtros de canciones"),style=MaterialTheme.typography.headlineSmall)
                LibraryFilterReview(songs,prefs,repo,Modifier.fillMaxWidth().weight(1f),fontFamily=com.akira.app.core.design.brand.AkiraBrandFont,onDone={showReview=false})
            }
        }
    }
}

@Composable private fun SetupToggle(title: String,value: Boolean,onChange: (Boolean)->Unit) {
    val haptics=com.akira.app.core.design.util.rememberAkiraHaptics()
    fun change(next: Boolean) {if(next!=value){haptics.toggle(next);onChange(next)}}
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(value,role=Role.Switch,onValueChange=::change),verticalAlignment=Alignment.CenterVertically) {
        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(12.dp))
        com.akira.app.core.design.components.AkiraSwitch(value,::change,Modifier.clearAndSetSemantics {},haptic={})
    }
}
@Composable private fun SetupChoices(options: List<Pair<String,String>>,selected: String,onSelect: (String)->Unit) {
    val horizontal=androidx.compose.ui.platform.LocalDensity.current.fontScale<=1.3f
    if(horizontal)com.akira.app.core.design.components.AkiraSegmentedControl(options,selected,onSelect,MaterialTheme.colorScheme.surfaceContainerHigh,fontFamily=com.akira.app.core.design.brand.AkiraBrandFont)
    else Column {options.forEach {(id,label)->SetupSoundChoice(label,"",id==selected) {onSelect(id)}}}
}
@Composable private fun SetupSoundChoice(title: String,detail: String,selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector = AkiraIcons.Appearance,onClick: ()->Unit) {
    val haptics=com.akira.app.core.design.util.rememberAkiraHaptics()
    val fill by animateColorAsState(if(selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        if(setupReducedMotion())snap() else tween(180),label="setupSoundSelection")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
        .background(fill)
        .selectable(selected,role=Role.RadioButton,onClick={ if(!selected) {haptics.click();onClick()} }).padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
        Icon(icon,null,Modifier.padding(end=14.dp).size(23.dp),tint=MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title,style=MaterialTheme.typography.bodyLarge,fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal)
            if(detail.isNotEmpty())Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(selected)Icon(AkiraIcons.Check,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
    }
}
@Composable private fun SetupDuration(seconds: Int,onCommit: (Int)->Unit) {
    var draft by remember {mutableFloatStateOf(seconds.toFloat().coerceAtLeast(1f))}
    var dragging by remember {mutableStateOf(false)}
    LaunchedEffect(seconds) {if(!dragging)draft=seconds.toFloat().coerceAtLeast(1f)}
    Column {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(localizedText("Duration","Duración"),style=MaterialTheme.typography.bodyMedium)
            Text("${draft.roundToInt()} s",style=MaterialTheme.typography.titleMedium)
        }
        com.akira.app.feature.settings.ExpressiveAudioSlider(draft,{draft=it;dragging=true},1f..30f,localizedText("Crossfade duration","Duración del crossfade"),
            steps=28,onFinished={dragging=false;onCommit(draft.roundToInt())})
    }
}
@Composable private fun CrossfadePreview(seconds: Int) {
    val phase=setupAmbientPhase(4200)
    val progress by animateFloatAsState((seconds/30f).coerceIn(0f,1f),if(setupReducedMotion())snap() else tween(220),label="setupCrossfadePreview")
    val ink=MaterialTheme.colorScheme.primary
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(localizedText("Current song","Canción actual"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(localizedText("Next song","Siguiente canción"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().height(86.dp)) {
            val overlap=.08f+progress*.32f
            val leftEnd=.5f+overlap/2;val rightStart=.5f-overlap/2
            repeat(56) {i ->
                val x=(i+.5f)/56
                val wave=.28f+.65f*kotlin.math.abs(kotlin.math.sin(i*.83f+phase.value*2))
                val first=if(seconds==0) {if(x<.5f)1f else 0f} else ((leftEnd-x)/overlap).coerceIn(0f,1f)
                val second=if(seconds==0) {if(x>=.5f)1f else 0f} else ((x-rightStart)/overlap).coerceIn(0f,1f)
                val bar=2.dp.toPx()
                if(first>0)drawLine(ink.copy(alpha=.85f),Offset(x*size.width,size.height*.25f-wave*first*size.height*.18f),
                    Offset(x*size.width,size.height*.25f+wave*first*size.height*.18f),bar,StrokeCap.Round)
                if(second>0)drawLine(ink.copy(alpha=.4f),Offset(x*size.width,size.height*.75f-wave*second*size.height*.18f),
                    Offset(x*size.width,size.height*.75f+wave*second*size.height*.18f),bar,StrokeCap.Round)
            }
            drawRoundRect(ink.copy(alpha=.07f), Offset(size.width*rightStart,0f),
                androidx.compose.ui.geometry.Size(size.width*overlap,size.height), androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
        }
    }
}

@Composable private fun SetupThemePreview(songs: List<Song>,mode: String) {
    val light=mode=="LIGHT" || mode=="SYSTEM" && !isSystemInDarkTheme()
    val motion=if(setupReducedMotion())snap<Color>() else tween(240)
    val surface by animateColorAsState(if(light)Color(0xFFF2F2F5) else Color(0xFF18181D),motion,label="themeCanvas")
    val ink by animateColorAsState(if(light)Color(0xFF111114) else Color(0xFFF9F9FC),motion,label="themeInk")
    val accent=if(light)Color(0xFFCB1731) else Color(0xFFFF687C)
    val preview=remember(songs) {songs.take(3)}
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(surface).padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(localizedText("Library","Biblioteca"),Modifier.weight(1f),color=ink,style=MaterialTheme.typography.headlineMedium)
            SetupEclipse(Modifier.size(28.dp),accent)
        }
        Row(Modifier.fillMaxWidth().background(ink.copy(alpha=.06f),RoundedCornerShape(16.dp)).padding(14.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Icon(AkiraIcons.Info,null,Modifier.size(18.dp),tint=accent)
            Text(localizedText("Preview", "Vista previa"),color=ink,style=MaterialTheme.typography.labelLarge)
        }
        if(preview.isEmpty()) {
            Text(localizedText("No songs scanned yet.", "Todavía no se han encontrado canciones."),
                color=ink.copy(alpha=.7f),style=MaterialTheme.typography.bodyMedium)
        }
        preview.forEach {song ->
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(ink.copy(alpha=.06f)),contentAlignment=Alignment.Center) {
                    Icon(AkiraIcons.Album,null,Modifier.size(24.dp),tint=ink.copy(alpha=.45f))
                    coil.compose.AsyncImage(song.albumArtUri,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text(song.title,color=ink,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(song.artist,color=ink.copy(alpha=.65f),style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                Icon(AkiraIcons.MoreHoriz,null,Modifier.size(20.dp),tint=ink.copy(alpha=.6f))
            }
        }
    }
}
@Composable internal fun SetupPlayerPreview(song: Song?,fullscreen: Boolean) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val uri=song?.albumArtUri
    val request=remember(context,uri) {coil.request.ImageRequest.Builder(context).data(uri).allowHardware(false).size(640).build()}
    var palette by remember(uri) {mutableStateOf(PlayerArtworkColors(Color(0xFF38333E)))}
    val spec=if(setupReducedMotion())snap<androidx.compose.ui.unit.Dp>() else tween(220,easing=FastOutSlowInEasing)
    val artWidth by animateDpAsState(if(fullscreen)380.dp else 328.dp,spec,label="previewArtWidth")
    val artHeight by animateDpAsState(if(fullscreen)585.dp else 328.dp,spec,label="previewArtHeight")
    val artTop by animateDpAsState(if(fullscreen)0.dp else 60.dp,spec,label="previewArtTop")
    val label=localizedText("Player appearance preview","Vista previa del reproductor")
    Box(Modifier.fillMaxWidth().height(336.dp).semantics {contentDescription=label},contentAlignment=Alignment.Center) {
        Box(Modifier.layout {measurable,limits ->
            val baseWidth=380.dp.roundToPx();val baseHeight=800.dp.roundToPx()
            val scale=minOf(limits.maxWidth.toFloat()/baseWidth,limits.maxHeight.toFloat()/baseHeight)
            val child=measurable.measure(androidx.compose.ui.unit.Constraints.fixed(baseWidth,baseHeight))
            layout((baseWidth*scale).roundToInt(),(baseHeight*scale).roundToInt()) {
                child.placeWithLayer(0,0) {scaleX=scale;scaleY=scale;transformOrigin=TransformOrigin(0f,0f)}
            }
        }.clip(RoundedCornerShape(30.dp)).background(Color.Black)) {
            Box(Modifier.fillMaxSize().playerDynamicBackdrop(palette,!setupReducedMotion()))
            coil.compose.AsyncImage(request,null,Modifier.align(Alignment.TopCenter).offset(y=artTop.coerceAtLeast(0.dp))
                .size(artWidth.coerceAtLeast(1.dp),artHeight.coerceAtLeast(1.dp)).clip(RoundedCornerShape(if(fullscreen)0.dp else 20.dp))
                .then(if(fullscreen)Modifier.playerArtworkFade(with(androidx.compose.ui.platform.LocalDensity.current){380.dp.toPx()}) else Modifier),
                contentScale=ContentScale.Crop,onSuccess={result ->scope.launch {
                    palette=withContext(Dispatchers.Default) {artworkAmbientColors(result.result.drawable.toBitmap(),Color(0xFF38333E))}
                }})
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal=32.dp).padding(bottom=30.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(song?.title ?: localizedText("Your collection","Tu colección"),color=Color.White,fontSize=20.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(song?.artist ?: "Akira",color=Color.White.copy(alpha=.65f),fontSize=20.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                    Icon(AkiraIcons.Star,null,Modifier.padding(start=10.dp).size(22.dp),tint=Color.White.copy(alpha=.8f))
                    Icon(AkiraIcons.MoreHoriz,null,Modifier.padding(start=18.dp).size(24.dp),tint=Color.White.copy(alpha=.8f))
                }
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    PreviewTrack(.28f,7.dp)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text("0:42",color=Color.White.copy(alpha=.4f),fontSize=12.sp);Text("−2:14",color=Color.White.copy(alpha=.4f),fontSize=12.sp)}
                }
                Row(Modifier.fillMaxWidth().height(72.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Icon(AkiraIcons.FastRewind,null,Modifier.size(42.dp),tint=Color.White)
                    Icon(AkiraIcons.Play,null,Modifier.size(58.dp),tint=Color.White)
                    Icon(AkiraIcons.FastForward,null,Modifier.size(42.dp),tint=Color.White)
                }
                Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Icon(AkiraPlayerGlyphs.VolumeLow,null,Modifier.size(18.dp),tint=Color.White.copy(alpha=.65f))
                    Box(Modifier.weight(1f)) {PreviewTrack(.55f,7.dp)}
                    Icon(AkiraPlayerGlyphs.VolumeHigh,null,Modifier.size(20.dp),tint=Color.White.copy(alpha=.65f))
                }
                Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.SpaceAround) {
                    Icon(AkiraIcons.Lyrics,null,Modifier.size(25.dp),tint=Color.White.copy(alpha=.65f))
                    Icon(AkiraPlayerGlyphs.Airplay,null,Modifier.size(25.dp),tint=Color.White.copy(alpha=.65f))
                    Icon(AkiraIcons.Queue,null,Modifier.size(25.dp),tint=Color.White.copy(alpha=.65f))
                }
            }
        }
    }
}
@Composable private fun PreviewTrack(fraction: Float,height: androidx.compose.ui.unit.Dp) {
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha=.22f))) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Color.White.copy(alpha=.65f)))
    }
}
