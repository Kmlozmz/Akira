// SPDX-License-Identifier: GPL-3.0-only
// Page AlbumScreen/ArtistScreen: local collection adapters, no network or fabricated artist data.
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
package com.akira.app.feature.collection

import com.akira.app.core.design.components.rememberAkiraMenuSource
import com.akira.app.core.design.motion.*

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.akira.app.R
import com.akira.app.core.model.Song
import com.akira.app.core.design.theme.AkiraFontFamily
import com.akira.app.core.design.components.*
import com.akira.app.core.design.navigation.*
import com.akira.app.core.design.rendering.*
import com.akira.app.feature.home.components.*
import com.akira.app.core.design.glass.LocalLiquidGlassEnabled
import com.akira.app.core.design.glass.liquidGlass
import com.akira.app.core.design.glass.LocalAppBackdrop
import com.akira.app.core.design.backdrop.backdrops.rememberLayerBackdrop
import com.akira.app.core.design.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.launch

data class DetailPage(val title: String, val artist: Boolean, val albumKey: String? = null, val transitionKey: String? = null)
fun localAlbumKey(song: Song): String = "${song.albumId}|${song.album.lowercase()}|${if(song.albumId==0L)song.artist.lowercase() else ""}"
fun albumDetail(song: Song, transitionKey: String? = null) = DetailPage(song.album, false, localAlbumKey(song), transitionKey)
fun artistDetail(name: String) = DetailPage(name, true)

@Composable
fun LocalCollectionDetail(initial: DetailPage, library: List<Song>, current: Song?, playing: Boolean,
    bottom: Dp, onPlay: (Song, List<Song>) -> Unit, onTogglePlay: () -> Unit,
    onMore: (Song) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var stack by remember(initial) { mutableStateOf(listOf(initial)) }
    fun back() { if(stack.size>1) stack=stack.dropLast(1) else onBack() }
    val obscured = LocalContentObscured.current
    BackHandler(!obscured) { back() }
    val sessions = LocalDetailSessions.current
    val session = remember { Any() }
    DisposableEffect(sessions, session) { sessions?.set(session, Unit); onDispose { sessions?.remove(session) } }
    val reduced = LocalContentMotionReduced.current
    val pageStates = rememberSaveableStateHolder()
    MaterialTheme(colorScheme = com.akira.app.core.design.theme.DarkColorScheme) {
    CompositionLocalProvider(LocalDetailInk provides Color.White) {
    SharedTransitionLayout(Modifier.fillMaxSize()) {
    val sharedScope = this
    AnimatedContent(stack, transitionSpec = {
        if(reduced) (EnterTransition.None togetherWith ExitTransition.None).using(null)
        else if(targetState.size > initialState.size)
            (EnterTransition.None togetherWith (scaleOut(tween(ContainerOpenMillis,easing=ContainerCloseEasing),targetScale=.92f)+fadeOut(tween(ContainerOpenMillis,easing=ContainerCloseEasing),targetAlpha=.45f))).using(null)
        else ((scaleIn(tween(ContainerCloseMillis,easing=ContainerCloseEasing),initialScale=.92f)+fadeIn(tween(ContainerCloseMillis),initialAlpha=.45f)) togetherWith ExitTransition.None).using(null)
    }, modifier=modifier.fillMaxSize(), label="localArtistAlbum") { pages ->
        val page = pages.last()
        pageStates.SaveableStateProvider(pages.joinToString("/") { "${it.artist}:${it.albumKey}:${it.title}:${it.transitionKey}" }) {
        key(page) {
            // The initial page belongs to the outer Home/Library transform.
            // Deeper pages use this scope, so source cards can morph to their destination.
            val inner = pages.size > 1
            val outerScope = LocalSharedTransitionScope.current
            val outerVisibility = LocalNavAnimatedContentScope.current
            CompositionLocalProvider(
                LocalSharedTransitionScope provides (if(inner) sharedScope else outerScope),
                LocalNavAnimatedContentScope provides (if(inner) this else outerVisibility)) {
                val tracks = remember(library,page) {
                    library.filter { if(page.artist) it.artist.equals(page.title,true) else localAlbumKey(it)==page.albumKey }
                        .sortedWith(compareBy<Song> { if(it.trackNumber>0)it.trackNumber else Int.MAX_VALUE }.thenBy { it.title })
                }
                val open: (DetailPage)->Unit = { next ->
                    val sourceKey = next.transitionKey ?: "detail-${pages.size}-${if(next.artist)"artist-${next.title}" else next.albumKey}"
                    val target = next.copy(transitionKey=sourceKey)
                    if(next.title!=stack.last().title || next.artist!=stack.last().artist)stack=stack+target
                }
                // Card sources must share the inner scope even on the initial page;
                // its screen root keeps the outer scope for the first entry/back.
                CompositionLocalProvider(LocalDetailCardScope provides sharedScope, LocalDetailCardVisibility provides this,
                    LocalDetailDepth provides pages.size) {
                    if (page.artist) LocalArtistPage(page,tracks,library,current,playing,bottom,onPlay,onTogglePlay,onMore,::back,open)
                    else LocalAlbumPage(page,tracks,library,current,playing,bottom,onPlay,onTogglePlay,onMore,::back,open)
                }
            }
        }
        }
    }
    }
    }
    }
}

@Composable private fun LocalAlbumPage(page: DetailPage, tracks: List<Song>, library: List<Song>, current: Song?, playing: Boolean,
    bottom: Dp, onPlay: (Song,List<Song>)->Unit,onToggle:()->Unit,onMore:(Song)->Unit,onBack:()->Unit,onOpen:(DetailPage)->Unit) {
    val state=rememberLazyListState()
    val lead=tracks.firstOrNull()
    val artists=remember(tracks){tracks.distinctBy{it.artist}.filter{it.artist.isNotBlank()}}
    val otherAlbums=remember(tracks,library){library.filter { s -> artists.any{it.artist.equals(s.artist,true)} && localAlbumKey(s)!=page.albumKey }.distinctBy(::localAlbumKey)}
    val reveal=rememberRevealSeenState()
    PlaylistDetailScaffold(page.transitionKey,lead?.albumArtUri,state,onBack,onBack,
        contentPadding=PaddingValues(bottom=bottom),onMoreClick=lead?.let{{onMore(it)}}) {
        item("header") { PlaylistHeader(page.title, artists.map{CreatorUi(it.artist,it.albumArtUri)},
            stats=listOfNotNull(lead?.year?.takeIf{it>0}?.toString(),localizedText("${tracks.size} songs","${tracks.size} canciones"),"${tracks.sumOf{it.durationMs}/60000} min").joinToString(" · "),
            isPlaying=playing&&tracks.any{it.id==current?.id},onPlayClick={
                if(tracks.any{it.id==current?.id})onToggle() else lead?.let{onPlay(it,tracks)}
            },onShuffleClick={tracks.shuffled().let{q->q.firstOrNull()?.let{onPlay(it,q)}}},
            onCreatorClick={onOpen(artistDetail(it.name))}) }
        itemsIndexed(tracks,key={_,s->s.id}) { i,s ->
            DetailSong(s,tracks,current,playing,onPlay,onToggle,onMore,Modifier.revealOnAppear("track-${s.id}",reveal,delayMillis=i.coerceAtMost(8)*28))
        }
        if(tracks.isEmpty()) item { Text(localizedText("No songs available","No hay canciones disponibles"),Modifier.padding(36.dp),color=LocalDetailInk.current) }
        item { PlaylistEndOfLine(tracks.size,tracks.sumOf{it.durationSeconds}) }
        if(artists.isNotEmpty()) {
            item { PlaylistSectionTitle(localizedText("Featured artists","Artistas del álbum"),Modifier.padding(top=25.dp),showChevron=false) }
            item { LazyRow(contentPadding=PaddingValues(horizontal=36.dp),horizontalArrangement=Arrangement.spacedBy(20.dp), flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()) {
                items(artists,key={it.artist}) { s -> Column(Modifier.width(96.dp).detailCardSource(artistDetail(s.artist),s.albumArtUri,48.dp).clickable{onOpen(artistDetail(s.artist))},horizontalAlignment=Alignment.CenterHorizontally) {
                    AsyncImage(s.albumArtUri,null,contentScale=ContentScale.Crop,modifier=Modifier.size(96.dp).clip(CircleShape).background(LocalDetailInk.current.copy(alpha=.08f)))
                    Text(s.artist,color=LocalDetailInk.current,fontFamily=AkiraFontFamily,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=7.dp))
                } }
            } }
        }
        if(otherAlbums.isNotEmpty()) {
            item { PlaylistSectionTitle(localizedText("More by ${lead?.artist.orEmpty()}","Más de ${lead?.artist.orEmpty()}"),Modifier.padding(top=30.dp),onClick={lead?.let{onOpen(artistDetail(it.artist))}}) }
            item { DetailAlbumShelf(otherAlbums,onOpen) }
        }
        item { Spacer(Modifier.height(50.dp)) }
    }
}

@Composable private fun DetailSong(s:Song,queue:List<Song>,current:Song?,playing:Boolean,onPlay:(Song,List<Song>)->Unit,
    onToggle:()->Unit,onMore:(Song)->Unit,modifier:Modifier=Modifier) {
    LocalSongSwipeRow(s) {
        PlaylistTrackRow(s.albumArtUri,s.title,"${s.artist} • ${s.durationMs/60000}:${(s.durationMs/1000%60).toString().padStart(2,'0')}",
            s.id==current?.id,playing,{if(s.id==current?.id)onToggle()else onPlay(s,queue)},{onMore(s)},{onMore(s)},modifier)
    }
}

@Composable private fun DetailAlbumShelf(albums:List<Song>,onOpen:(DetailPage)->Unit) {
    LazyRow(contentPadding=PaddingValues(horizontal=36.dp),horizontalArrangement=Arrangement.spacedBy(20.dp), flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()) {
        items(albums,key={localAlbumKey(it)}) { s -> Column(Modifier.width(137.dp).detailCardSource(albumDetail(s),s.albumArtUri).clickable{onOpen(albumDetail(s))}) {
            AsyncImage(s.albumArtUri,null,contentScale=ContentScale.Crop,modifier=Modifier.size(137.dp).clip(RoundedCornerShape(20.dp)).background(LocalDetailInk.current.copy(alpha=.08f)))
            Text(s.album,color=LocalDetailInk.current,fontFamily=AkiraFontFamily,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=6.dp))
            if(s.year>0)Text(s.year.toString(),color=LocalDetailInk.current.copy(alpha=.6f),fontFamily=AkiraFontFamily,fontSize=10.sp)
        } }
    }
}

@Composable private fun LocalArtistPage(page:DetailPage,tracks:List<Song>,library:List<Song>,current:Song?,playing:Boolean,bottom:Dp,
    onPlay:(Song,List<Song>)->Unit,onToggle:()->Unit,onMore:(Song)->Unit,onBack:()->Unit,onOpen:(DetailPage)->Unit) {
    val context=LocalContext.current
    val artistRepo=remember { com.akira.app.core.datastore.UserPreferencesRepository(context) }
    val artistPrefs by artistRepo.userPreferences.collectAsStateWithLifecycle(com.akira.app.core.datastore.UserPreferences())
    val artistKey=page.title.trim().lowercase(java.util.Locale.ROOT)
    val favorite=artistKey in artistPrefs.favoriteArtists
    var showArtistInfo by remember { mutableStateOf(false) }
    var showArtistMenu by remember { mutableStateOf(false) }
    val addedArtistMessage=localizedText("Added to Favorites","Se agregó a Favoritos")
    val removedArtistMessage=localizedText("Removed from Favorites","Se quitó de Favoritos")
    val lead=tracks.firstOrNull()
    val albums=remember(tracks){tracks.distinctBy(::localAlbumKey)}
    val state=rememberLazyListState()
    val height=playlistCoverHeight()
    val heightPx=with(LocalDensity.current){height.toPx()}
    val progress=remember(state,heightPx){derivedStateOf{if(state.firstVisibleItemIndex>0)1f else (state.firstVisibleItemScrollOffset/heightPx).coerceIn(0f,1f)}}
    val collapsed by remember{derivedStateOf{progress.value>.62f}}
    val reduced=LocalContentMotionReduced.current
    val morph=remember{Animatable(0f)}
    LaunchedEffect(collapsed,reduced){if(reduced)morph.snapTo(if(collapsed)1f else 0f)else morph.animateTo(if(collapsed)1f else 0f,spring(dampingRatio=if(collapsed).62f else .9f,stiffness=360f))}
    val theme by rememberCoverThemeColor(lead?.albumArtUri)
    val background=animateColorAsState(theme?:MaterialTheme.colorScheme.background,if(reduced)snap() else tween(600),label="artistTheme")
    val glass=remember{GlassState()}
    val pageBackdrop=rememberLayerBackdrop()
    val artworkBackdrop=rememberLayerBackdrop()
    val elastic=rememberAkiraBouncingEffect()
    val scope=rememberCoroutineScope()
    CompositionLocalProvider(LocalAppBackdrop provides pageBackdrop) {
    Box(Modifier.fillMaxSize().containerTransformTarget(page.transitionKey,48.dp).background(background.value)) {
        Box(Modifier.fillMaxSize().glassRoot(glass){morph.value>.01f}.layerBackdrop(pageBackdrop).background(background.value)) {
            // Record only the background, before the controls. Sampling the page
            // itself would feed the buttons back into their own glass texture.
            Box(Modifier.fillMaxSize().layerBackdrop(artworkBackdrop).background(background.value)) {
                Box(Modifier.akiraMediaStretch({elastic?.topPull?:0f})) { LocalArtistBanner(lead?.albumArtUri,height,{progress.value},background) }
            }
            LazyColumn(state=state,overscrollEffect=elastic,contentPadding=PaddingValues(bottom=bottom),modifier=Modifier.fillMaxSize(), flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()) {
                item("header") {
                    CompositionLocalProvider(LocalAppBackdrop provides artworkBackdrop) {
                    AkiraArtistMasthead(page.title,
                        localizedText("${tracks.size} songs · ${albums.size} albums in your library","${tracks.size} canciones · ${albums.size} álbumes en tu biblioteca"),
                        background.value,favorite,playing&&tracks.any{it.id==current?.id},
                        onPlay={if(tracks.any{it.id==current?.id})onToggle()else lead?.let{onPlay(it,tracks)}},
                        onFavorite={scope.launch {
                            artistRepo.setArtistFavorite(page.title,!favorite)
                            com.akira.app.core.design.components.AkiraNotices.show(
                                if(!favorite)addedArtistMessage else removedArtistMessage)
                        }},onInfo={showArtistInfo=true},
                        modifier=Modifier.padding(top=height*.56f).padding(horizontal=30.dp))
                    }
                }
                // Local equivalent of Page's release card. No fake bio, subscribers or remote artwork.
                albums.maxByOrNull{it.year.toLong()*100000000000L+it.dateAddedSec}?.let { recent -> item("release") {
                    val releasePage = albumDetail(recent,"detail-${LocalDetailDepth.current}-release-${localAlbumKey(recent)}")
                    Column(Modifier.padding(horizontal=30.dp,vertical=20.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).detailCardSource(releasePage,recent.albumArtUri,22.dp).background(LocalDetailInk.current.copy(alpha=.08f)).clickable{onOpen(releasePage)}.padding(16.dp)) {
                        Text(localizedText("From your library","De tu biblioteca"),color=LocalDetailInk.current,fontFamily=AkiraFontFamily,fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                        Row(Modifier.padding(top=10.dp),verticalAlignment=Alignment.CenterVertically){
                            AsyncImage(recent.albumArtUri,null,contentScale=ContentScale.Crop,modifier=Modifier.size(39.dp).clip(TrackShape))
                            Text(recent.album,color=LocalDetailInk.current,style=TrackTitleStyle,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f).padding(horizontal=11.dp))
                            CircleAction(R.drawable.ic_compact_play,localizedText("Play","Reproducir"),{val q=tracks.filter{localAlbumKey(it)==localAlbumKey(recent)};q.firstOrNull()?.let{onPlay(it,q)}})
                        }
                    }
                } }
                item { PlaylistSectionTitle(localizedText("Songs","Canciones"),showChevron=false) }
                items(tracks.take(5),key={it.id}){s->DetailSong(s,tracks,current,playing,onPlay,onToggle,onMore)}
                if(albums.isNotEmpty()) {
                    item { PlaylistSectionTitle(localizedText("Albums","Álbumes"),Modifier.padding(top=25.dp),showChevron=false) }
                    item { DetailAlbumShelf(albums,onOpen) }
                }
                if(tracks.size>5){
                    item { PlaylistSectionTitle(localizedText("In your library","En tu biblioteca"),Modifier.padding(top=25.dp),showChevron=false) }
                    items(tracks.drop(5),key={it.id}){s->DetailSong(s,tracks,current,playing,onPlay,onToggle,onMore)}
                }
                item { Spacer(Modifier.height(50.dp)) }
            }
        }
        ArtistBar(page.title,glass,morph.value,background.value,onBack,
            onShare={
                val intent=android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_TEXT,page.title)
                context.startActivity(android.content.Intent.createChooser(intent,page.title))
            },onMore={showArtistMenu=true}){scope.launch{state.animateScrollToItem(0)}}
        if(showArtistMenu) com.akira.app.core.design.components.AkiraContextMenu(
            onDismiss={showArtistMenu=false},groups=listOf(listOf(
                com.akira.app.core.design.components.AkiraContextAction(com.akira.app.core.design.icon.AkiraIcons.Play,
                    localizedText("Play top songs","Reproducir canciones destacadas")){
                    if(tracks.any{it.id==current?.id})onToggle()else lead?.let{onPlay(it,tracks)}
                },
                com.akira.app.core.design.components.AkiraContextAction(
                    if(favorite)com.akira.app.core.design.icon.AkiraIcons.StarFilled else com.akira.app.core.design.icon.AkiraIcons.Star,
                    if(favorite)localizedText("Remove from Favorites","Quitar de favoritos")else localizedText("Favorite artist","Artista favorito")){
                    scope.launch {artistRepo.setArtistFavorite(page.title,!favorite)}
                },
                com.akira.app.core.design.components.AkiraContextAction(com.akira.app.core.design.icon.AkiraIcons.Album,
                    localizedText("Browse albums","Ver álbumes")) {if(albums.isNotEmpty())scope.launch{state.animateScrollToItem(3+tracks.take(5).size)}},
                com.akira.app.core.design.components.AkiraContextAction(com.akira.app.core.design.icon.AkiraIcons.Artist,
                    localizedText("Artist information","Información del artista"),opensPresentation=true){showArtistInfo=true},
                com.akira.app.core.design.components.AkiraContextAction(com.akira.app.core.design.icon.AkiraIcons.Shuffle,
                    localizedText("Shuffle","Aleatorio")){tracks.shuffled().let{q->q.firstOrNull()?.let{onPlay(it,q)}}},
                com.akira.app.core.design.components.AkiraContextAction(com.akira.app.core.design.icon.AkiraIcons.Share,
                    localizedText("Share artist","Compartir artista")) {
                    context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT,page.title),page.title))
                }
            )))
        if(showArtistInfo) ArtistInformationSheet(page.title, lead?.albumArtUri, background.value,
            localizedText("${tracks.size} songs · ${albums.size} albums in your library", "${tracks.size} canciones · ${albums.size} álbumes en tu biblioteca"),
            localizedText("${tracks.sumOf{it.durationMs}/60000} minutes of music in your collection.",
                "${tracks.sumOf{it.durationMs}/60000} minutos de música en tu colección."),
            onDismiss = { showArtistInfo = false })

    }
    }
}

@Composable internal fun ArtistBar(title:String,glass:GlassState,p:Float,background:Color,onBack:()->Unit,
    onShare:()->Unit,onMore:()->Unit,onTitle:()->Unit) {
    val status=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val ink=LocalDetailInk.current
    val controlInk=lerp(Color.White,ink,p.coerceIn(0f,1f))
    val controlFill=lerp(Color.Black.copy(alpha=.5f),ink.copy(alpha=.12f),p.coerceIn(0f,1f))
    val menuSource=rememberAkiraMenuSource(hasOwnSurface=true,icon=null)
    val density=androidx.compose.ui.platform.LocalDensity.current
    menuSource.visualVerticalInsetPx=with(density){2.dp.toPx()}
    SideEffect {
        menuSource.surfaceContent = {
            Row(Modifier.size(104.dp,44.dp),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {
                    Icon(com.akira.app.core.design.icon.AkiraIcons.Share,null,Modifier.size(19.dp),tint=ink)
                }
                Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {
                    Icon(com.akira.app.core.design.icon.AkiraIcons.MoreHoriz,null,Modifier.size(19.dp),tint=ink)
                }
            }
        }
    }
    val capsule=remember(menuSource,density) {
        com.akira.app.core.design.motion.ElasticGlassShape({with(density){22.dp.toPx()}},{0f},
            { (menuSource.contactStrain?.invoke() ?: androidx.compose.ui.geometry.Offset.Zero) +
                androidx.compose.ui.geometry.Offset(.12f,.18f)*menuSource.responseAmount() },density)
    }
    Box(Modifier.fillMaxWidth().height(status+96.dp).graphicsLayer{clip=true;compositingStrategy=CompositingStrategy.Offscreen}) {
        if(p>.01f)ProgressiveGlassBackground(glass,maxBlurRadius={34f*p.coerceIn(0f,1f)},tint=background.copy(alpha=.4f),foundationColor=background,direction=BlurDirection.BottomToTop,steps=3,modifier=Modifier.matchParentSize().graphicsLayer{alpha=p.coerceIn(0f,1f)})
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top=6.dp,start=26.dp,end=28.dp).height(47.dp),verticalAlignment=Alignment.CenterVertically){
            DetailHeaderControl(R.drawable.ic_navigation_back,localizedText("Back","Atrás"),onBack)

            Text(title,color=ink,fontFamily=AkiraFontFamily,fontSize=19.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f).padding(horizontal=12.dp).graphicsLayer{alpha=p.coerceIn(0f,1f);translationY=10.dp.toPx()*(1f-p)}.clickable(onClick=onTitle))
            Box(Modifier.width(104.dp).height(48.dp).then(menuSource.modifier).then(menuSource.containerModifier)) {
                Box(Modifier.matchParentSize().padding(vertical=2.dp).clip(capsule)
                    .liquidGlass(capsule))
                Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically) {
                    com.akira.app.core.design.components.AkiraIconButton(com.akira.app.core.design.icon.AkiraIcons.Share,
                        localizedText("Share","Compartir"),onShare,Modifier.weight(1f),tint=ink,glyphSize=19.dp,showBackground=false)
                    com.akira.app.core.design.components.AkiraIconButton(com.akira.app.core.design.icon.AkiraIcons.MoreHoriz,
                        localizedText("Options","Opciones"),onMore,Modifier.weight(1f),tint=ink,glyphSize=19.dp,showBackground=false,menuSourceOverride=menuSource)
                }
            }
        }
    }
}

private val LocalDetailCardScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
private val LocalDetailCardVisibility = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }
private val LocalDetailDepth = staticCompositionLocalOf { 1 }
@Composable private fun Modifier.detailCardSource(page: DetailPage, cover: String?, radius: Dp = 20.dp): Modifier {
    val scope = LocalDetailCardScope.current
    val visibility = LocalDetailCardVisibility.current
    val depth = LocalDetailDepth.current
    var result: Modifier = this
    CompositionLocalProvider(LocalSharedTransitionScope provides scope,LocalNavAnimatedContentScope provides visibility) {
        result = this@detailCardSource.containerTransformSource(page.transitionKey ?: "detail-$depth-${if(page.artist)"artist-${page.title}" else page.albumKey}",radius,cover)
    }
    return result
}
@Composable internal fun DetailHeaderControl(icon: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null) {
    val menuSource=rememberAkiraMenuSource(hasOwnSurface=true)
    val haptics = com.akira.app.core.design.util.rememberAkiraHaptics()
    Box(modifier.then(if(icon==R.drawable.ic_more_horizontal)menuSource.modifier else Modifier).size(40.dp).clip(CircleShape)
        .liquidGlass(CircleShape)
        .combinedClickable(onClick={haptics.light();if(icon==R.drawable.ic_more_horizontal)menuSource.open(onClick)else onClick()},onLongClick=onLongClick),contentAlignment=Alignment.Center) {
        Icon(painterResource(icon),label,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurface)
    }
}
