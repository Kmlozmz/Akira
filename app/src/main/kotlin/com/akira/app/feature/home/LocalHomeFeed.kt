// SPDX-License-Identifier: GPL-3.0-only
// Home composition adapted from realidkroo/page HomeScreen.kt (bb28993).
// Local Room/MediaStore collections replace platform recommendations. See THIRD_PARTY_NOTICES.md.
package com.akira.app.feature.home

import com.akira.app.feature.player.dock.*

import com.akira.app.core.design.motion.*

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import com.akira.app.core.design.backdrop.backdrops.rememberLayerBackdrop
import com.akira.app.core.design.backdrop.backdrops.layerBackdrop
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akira.app.R
import com.akira.app.core.database.AppDatabase
import com.akira.app.core.data.playlist.PlaylistStore
import com.akira.app.core.design.theme.AkiraFontFamily
import com.akira.app.core.model.Song
import com.akira.app.feature.collection.*
import com.akira.app.core.model.SongKey
import com.akira.app.core.design.components.*
import com.akira.app.core.design.navigation.*
import com.akira.app.core.design.rendering.*
import com.akira.app.feature.home.components.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn

class HomeFeedSources(context: android.content.Context, scope: kotlinx.coroutines.CoroutineScope) {
    private val db = AppDatabase.getInstance(context)
    private val store = PlaylistStore(context)
    private val started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000)
    val history = db.historyDao().observeHistory(500).stateIn(scope, started, emptyList())
    val stats = store.stats.stateIn(scope, started, emptyList())
    val playlists = store.playlists.stateIn(scope, started, emptyList())
    val items = store.items.stateIn(scope, started, emptyList())
}

@Composable internal fun rememberHomeFeedSources(): HomeFeedSources {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return remember(context, scope) { HomeFeedSources(context, scope) }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun LocalHomeFeed(songs: List<Song>, initialized: Boolean, loadFailed: Boolean, current: Song?, playing: Boolean,
    obscured: Boolean, onPlay: (Song, List<Song>) -> Unit, onTogglePlay: () -> Unit,
    onRandomPlay: (Song) -> Unit, onMore: (Song) -> Unit, onSettings: () -> Unit, onRetry: () -> Unit,
    modifier: Modifier = Modifier, scrollToTopRequest: Int = 0, playerSheetProgress: () -> Float = { 0f }, sources: HomeFeedSources? = null) {
    val context = LocalContext.current
    val retained = sources ?: rememberHomeFeedSources()
    val history by retained.history.collectAsStateWithLifecycle()
    val stats by retained.stats.collectAsStateWithLifecycle()
    val playlists by retained.playlists.collectAsStateWithLifecycle()
    val playlistEntries by retained.items.collectAsStateWithLifecycle()
    val byId = remember(songs) { songs.associateBy { it.id } }
    val byKey = remember(songs) { songs.associateBy { SongKey.of(it).encoded } }
    val statsMap = remember(stats) { stats.associateBy { it.songKey } }
    val recent = remember(history, songs, stats) {
        (history.mapNotNull { byId[it.songId] } + stats.sortedByDescending { it.lastPlayedAt }.mapNotNull { byKey[it.songKey] }).distinctBy { it.id }.take(30)
    }
    val most = remember(songs, stats) { songs.filter { (statsMap[SongKey.of(it).encoded]?.playCount ?: 0) > 0 }
        .sortedByDescending { statsMap[SongKey.of(it).encoded]?.playCount }.take(30) }
    val added = remember(songs) { songs.sortedByDescending { if (it.dateAddedSec > 0) it.dateAddedSec else it.dateModifiedSec }.take(30) }
    val favorites = remember(songs) { songs.filter { it.isFavorite } }
    val forgotten = remember(favorites, stats) { favorites.filter { (statsMap[SongKey.of(it).encoded]?.lastPlayedAt ?: 0) < System.currentTimeMillis() - 90L*86400000 }.take(20) }
    val albums = remember(songs) { songs.groupBy(::localAlbumKey).values.filter { it.first().album.isNotBlank() }.take(20) }
    val artists = remember(songs) { songs.groupBy { it.artist }.values.filter { it.first().artist.isNotBlank() }.take(20) }
    val userLists = remember(playlists, playlistEntries, songs) { playlists.map { list ->
        HomePlaylist(list.id, list.title, playlistEntries.filter { it.playlistId == list.id }.mapNotNull { byKey[it.songKey] ?: byId[it.songKey] })
    }.filter { it.songs.isNotEmpty() } }
    val continueLabel = localizedText("Continue", "Continuar")
    val repeatLabel = localizedText("On repeat", "En repetición")
    val favoriteLabel = localizedText("Don't forget these songs", "No olvides estas canciones")
    val recentLabel=localizedText("Recently Played","Escuchadas recientemente")
    val addedLabel=localizedText("Recently Added","Añadidas recientemente")
    val likedLabel=localizedText("Liked songs","Tus favoritos")
    val playlistsLabel=localizedText("Your playlists","Tus playlists")
    val artistsLabel=localizedText("Your artists","Tus artistas")
    val albumLabel = localizedText("From your library", "De tu biblioteca")
    // Keep the same hero identity while music changes; changing a track must not reset the pager.
    val seed = remember(songs.isNotEmpty()) { current?.id }
    val heroItems = remember(songs, recent, most, forgotten, userLists, seed, continueLabel, repeatLabel, favoriteLabel, albumLabel, addedLabel) {
        buildList {
            val first = byId[seed] ?: recent.firstOrNull() ?: added.firstOrNull()
            first?.let { add(HeroCarouselItem("continue", it.title, it.artist, it.albumArtUri, it, HeroLabel(if(recent.isEmpty() && seed==null)addedLabel else continueLabel))) }
            if (songs.size >= 4) add(HeroCarouselItem("gacha", "", isGacha = true))
            most.firstOrNull { it.id != first?.id }?.let { add(HeroCarouselItem("repeat", it.title, it.artist, it.albumArtUri, it, HeroLabel(repeatLabel))) }
            forgotten.firstOrNull { it.id != first?.id }?.let { add(HeroCarouselItem("forgotten", it.title, it.artist, it.albumArtUri, it, HeroLabel(favoriteLabel))) }
            userLists.take(2).forEach { playlist ->
                add(HeroCarouselItem("playlist-${playlist.id}", playlist.title, thumbnailUrl=playlist.songs.first().albumArtUri,
                    reason=HeroLabel(albumLabel), opensPage=true, collection=playlist.songs,
                    collectionRoute=HomeCollectionRoute.playlist(playlist.id, playlist.title, playlist.songs)))
            }
            albums.take(2).forEach { list ->
                val song = list.first()
                val identity = localAlbumKey(song)
                add(HeroCarouselItem("album-$identity", song.album, song.artist, song.albumArtUri,
                    reason=HeroLabel(albumLabel), opensPage=true, collection=list,
                    collectionRoute=HomeCollectionRoute.album(song, list, "home-album-$identity")))
            }
            recent.filter { candidate -> none { it.song?.id == candidate.id } }.take(2).forEach { s -> add(HeroCarouselItem("recent${s.id}",s.title,s.artist,s.albumArtUri,s,HeroLabel(continueLabel))) }
        }
    }
    var collectionRoute by rememberSaveable { mutableStateOf<HomeCollectionRoute?>(null) }
    fun openCollection(identity: String, title: String, list: List<Song>) {
        collectionRoute = HomeCollectionRoute.tracks(identity, title, list)
    }
    BackHandler(collectionRoute != null && !obscured) { collectionRoute=null }
    val listState = rememberLazyListState()
    val reveal = rememberRevealSeenState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(scrollToTopRequest) { if(scrollToTopRequest>0) { collectionRoute=null;listState.animateScrollToItem(0) } }
    val heroHeight = with(LocalDensity.current) { (LocalConfiguration.current.screenHeightDp * HeroHeightFraction).dp.toPx() }
    val heroProgress = remember(listState,heroHeight) { { if(listState.firstVisibleItemIndex>0)1f else (listState.firstVisibleItemScrollOffset/heroHeight).coerceIn(0f,1f) } }
    val homeGlassState = remember { GlassState() }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()+if(current!=null)166.dp else 90.dp
    val reducedMotion=LocalContentMotionReduced.current
    val feedPages=androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SharedTransitionLayout(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
        AnimatedContent(targetState=collectionRoute, transitionSpec={
            if((targetState ?: initialState)?.transitionKey==null) pageNavigationTransform(targetState != null,reducedMotion).using(null)
            else collectionContainerTransform(targetState != null,reducedMotion).using(null)
        }, label="pageLocalCollection",modifier=Modifier.fillMaxSize()) { route ->
        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
        if(route!=null) {
            val list = route.tracks(songs)
            val detail = route.detailPage()
            if (detail != null) {
                LocalCollectionDetail(detail,
                    songs,current,playing,bottom,onPlay,onTogglePlay,onMore,{collectionRoute=null})
            } else {
            val detailState = rememberLazyListState()
            val headerBackdrop = rememberLayerBackdrop()
            val headerGlass = com.akira.app.core.design.glass.isGlassSupported()
            val collapse by rememberPageCollapse(detailState)
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            LazyColumn(state=detailState, modifier=Modifier.fillMaxSize()
                .then(if (headerGlass) Modifier.layerBackdrop(headerBackdrop) else Modifier).background(MaterialTheme.colorScheme.background), contentPadding=PaddingValues(top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()+118.dp,bottom=bottom),verticalArrangement=Arrangement.spacedBy(14.dp), flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()) {
                items(list,key={it.id}) { s -> LocalSongSwipeRow(s) { HomeTrackRow(s.albumArtUri,s.title,s.artist,s.id==current?.id,playing,{onPlay(s,list)},{onMore(s)},{onMore(s)},Modifier.padding(horizontal=HomeContentInset)) } }
            }
            CompositionLocalProvider(LocalPageHeaderBackdrop provides headerBackdrop) {
                PagePageHeader(route.title, { collectionRoute=null }, collapse,
                    topInset=WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            }
            }
            }
        } else {
            feedPages.SaveableStateProvider("feed") {
            // AnimatedContent supplies full-page minimum constraints. A Box
            // releases those minimums for the header, as in Page HomeScreen.
            Box(Modifier.fillMaxSize()) {
            val elastic=rememberAkiraBouncingEffect(pinTopContent=true,allowTopPull=false)
            LazyColumn(state=listState,overscrollEffect=elastic,modifier=Modifier.glassRoot(homeGlassState, isActive = { heroProgress() > 0f }),contentPadding=PaddingValues(bottom=bottom), flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()) {
                item("hero") { CompositionLocalProvider(LocalAkiraMediaPull provides {elastic?.topPull?:0f}) { HeroCarousel(heroItems,songs,current?.id,playing,
                    onItemClick={ item -> if(item.opensPage)collectionRoute=item.collectionRoute else item.song?.let { if(it.id==current?.id)onTogglePlay() else onPlay(it,songs) } },
                    onRandomPlay=onRandomPlay,heroScrollProgressProvider=heroProgress,held={obscured||heroProgress()>.08f||elastic?.isInProgress==true},isLoading=!initialized && !loadFailed) } }
                if (initialized && loadFailed) {
                    item("refresh-error") { com.akira.app.feature.library.LibraryRefreshError(onRetry, Modifier.padding(HomeContentInset)) }
                }
                if(!initialized && loadFailed) {
                    item("load-error") { Column(Modifier.padding(HomeContentInset)) {
                        Text(localizedText("Couldn't read your library", "No se pudo leer tu biblioteca"), style=HomeTitleStyle)
                        TextButton(onClick=onRetry){Text(localizedText("Retry", "Reintentar"))}
                    } }
                } else if(!initialized) {
                    item("loading") { BonesHost { repeat(2) { Column(Modifier.padding(horizontal=HomeContentInset,vertical=17.dp)) { Bone(110.dp,16.dp);Spacer(Modifier.height(14.dp));Row(horizontalArrangement=Arrangement.spacedBy(HomeCardSpacing)){ repeat(3){Bone(137.dp,137.dp,shape=RoundedCornerShape(ContentCardCornerRadius))} } } } } }
                } else if(songs.isEmpty()) {
                    item("empty") { Column(Modifier.padding(HomeContentInset)) {
                        Text(localizedText("Your library is empty","Tu biblioteca está vacía"),style=HomeTitleStyle)
                        Text(localizedText("Add music from your device in Settings.","Añade música del dispositivo desde Ajustes."),color=MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick=if(loadFailed)onRetry else onSettings){Text(if(loadFailed)localizedText("Retry","Reintentar") else localizedText("Open settings","Abrir ajustes"))}
                    } }
                } else {
                    fun LazyListScope.cards(key: String,title: String,list: List<Song>) {
                        if(list.isEmpty())return
                        item("title$key") { HomeSectionTitle(title,onClick={openCollection(key,title,list)},modifier=Modifier.revealOnAppear("title$key",reveal,cascade=true)) }
                        item("cards$key") { LazyRow(contentPadding=PaddingValues(horizontal=HomeContentInset),horizontalArrangement=Arrangement.spacedBy(HomeCardSpacing), flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()) {
                            itemsIndexed(list,key={_,s->s.id}) { index,s -> HomeCoverCard(s.title,s.artist,s.albumArtUri,
                                Modifier.revealOnAppear("$key/${s.id}",reveal,delayMillis=index.coerceAtMost(4)*60).combinedClickable(interactionSource=null,indication=HomeCardHighlight,onClick={onPlay(s,list)},onLongClick={onMore(s)}),isActive=s.id==current?.id,isPlaying=playing) }
                        } }
                        item("space$key"){Spacer(Modifier.height(34.dp))}
                    }
                    cards("recent",recentLabel,recent.take(15))
                    if(most.isNotEmpty()) {
                        item("ranktitle"){HomeSectionTitle(localizedText("Most Played","Más escuchadas"),onClick={openCollection("most-played",repeatLabel,most)})}
                        item("ranks") {
                            val state=rememberLazyGridState();val rows=most.size.coerceAtMost(3)
                            LazyHorizontalGrid(rows=GridCells.Fixed(rows),state=state,flingBehavior=rememberSnapFlingBehavior(state),modifier=Modifier.height((54*rows+12*(rows-1)).dp),
                                contentPadding=PaddingValues(horizontal=HomeContentInset),horizontalArrangement=Arrangement.spacedBy(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                itemsIndexed(most,key={_,s->s.id}) { i,s -> HomeTrackRow(s.albumArtUri,s.title,s.artist,s.id==current?.id,playing,{onPlay(s,most)},{onMore(s)},{onMore(s)},Modifier.width((LocalConfiguration.current.screenWidthDp-72).dp),rank=i+1) }
                            }
                        }
                        item("rankspace"){Spacer(Modifier.height(34.dp))}
                    }
                    cards("added",addedLabel,added.take(15))
                    cards("favorites",likedLabel,favorites.take(15))
                    cards("forgotten",favoriteLabel,forgotten)
                    if(userLists.isNotEmpty()) {
                        item("playlisttitle"){HomeSectionTitle(playlistsLabel,onClick={openCollection("playlists",playlistsLabel,userLists.flatMap{it.songs}.distinctBy{it.id})})}
                        item("playlists"){LazyRow(contentPadding=PaddingValues(horizontal=HomeContentInset),horizontalArrangement=Arrangement.spacedBy(HomeCardSpacing), flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()){
                            items(userLists,key={it.id}) { playlist ->
                                val route = HomeCollectionRoute.playlist(playlist.id, playlist.title, playlist.songs)
                                HomeCoverCard(playlist.title, localizedText("${playlist.songs.size} songs","${playlist.songs.size} canciones"),
                                    playlist.songs.first().albumArtUri, Modifier.containerTransformSource(route.transitionKey,
                                        coverUrl=playlist.songs.first().albumArtUri).clickable { collectionRoute=route })
                            }
                        }}
                        item("playlistspace"){Spacer(Modifier.height(34.dp))}
                    }
                    item("artisttitle"){HomeSectionTitle(artistsLabel,onClick={openCollection("artists",artistsLabel,artists.flatMap{it})})}
                    item("artists"){LazyRow(contentPadding=PaddingValues(horizontal=HomeContentInset),horizontalArrangement=Arrangement.spacedBy(HomeCardSpacing), flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()){
                        items(artists,key={it.first().artist}){list->HomeCoverCard(list.first().artist,null,list.first().albumArtUri,Modifier.containerTransformSource("artist-${list.first().artist}",cornerRadius=68.5.dp,coverUrl=list.first().albumArtUri).clickable{collectionRoute=HomeCollectionRoute.artist(list.first().artist,list)},circular=true)}
                    }}
                    item("artistspace"){Spacer(Modifier.height(34.dp))}
                    item("albumtitle"){HomeSectionTitle(localizedText("Albums from your library","Álbumes de tu biblioteca"),onClick={openCollection("albums",albumLabel,albums.flatMap{it})})}
                    item("albums"){LazyRow(contentPadding=PaddingValues(horizontal=HomeContentInset),horizontalArrangement=Arrangement.spacedBy(HomeCardSpacing), flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()){
                        items(albums,key={localAlbumKey(it.first())}){list->HomeCoverCard(list.first().album,list.first().artist,list.first().albumArtUri,Modifier.containerTransformSource("home-album-${localAlbumKey(list.first())}",coverUrl=list.first().albumArtUri).clickable{collectionRoute=HomeCollectionRoute.album(list.first(),list,"home-album-${localAlbumKey(list.first())}")})}
                    }}
                    item("end"){Spacer(Modifier.height(30.dp))}
                    if(most.isNotEmpty()) {
                        item("album-banners-title"){HomeSectionTitle(localizedText("Albums on repeat","Álbumes en repetición"),onClick={openCollection("albums-on-repeat",repeatLabel,most)})}
                        item("album-banners") {
                            val width=(LocalConfiguration.current.screenWidthDp-72).dp
                            LazyRow(contentPadding=PaddingValues(horizontal=HomeContentInset),horizontalArrangement=Arrangement.spacedBy(HomeCardSpacing), flingBehavior = com.akira.app.core.design.motion.rememberAkiraBouncingFling()) {
                                items(most.distinctBy{it.albumId.toString()+it.album}.take(6),key={it.id}) { s ->
                                    val tracks=albums.firstOrNull{it.first().albumId==s.albumId && it.first().album==s.album}.orEmpty().ifEmpty{listOf(s)}
                                    HomeVideoCard(s.album,s.artist,s.albumArtUri,width,current?.album==s.album,playing,
                                        Modifier.containerTransformSource("home-repeat-${localAlbumKey(s)}",coverUrl=s.albumArtUri)
                                            .semantics {contentDescription="${s.album} · ${s.artist}"}
                                             .clickable{collectionRoute=HomeCollectionRoute.album(s,tracks,"home-repeat-${localAlbumKey(s)}")})
                                }
                            }
                        }
                    }
                }
            }
            HomeTopBar(heroProgress,homeGlassState,playerSheetProgress,heroHeight)
            }
            }
        }
        }
        }
        }
        }
    }

}

@Composable
internal fun HomeTopBar(progress:()->Float,glassState:GlassState,playerSheetProgress:()->Float,
    heroHeightPx:Float) {
    var profile by remember { mutableStateOf(false) }
    if (profile) com.akira.app.feature.online.OnlineAccountSheet { profile = false }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerHeight = statusTop + 74.dp
    // Page triggers the brand/title swap once half the hero has scrolled.
    // Preserve that threshold while mapping the transition to finger travel.
    val transitionFraction = (with(LocalDensity.current) { 68.dp.toPx() } /
        heroHeightPx.coerceAtLeast(1f)).coerceAtLeast(.001f)
    val p = ((progress() - .5f) / transitionFraction).coerceIn(0f, 1f)
    val background = MaterialTheme.colorScheme.background
    // Native backdrop nodes must never paint beyond this header's viewport.
    Box(Modifier.fillMaxWidth().height(headerHeight).graphicsLayer {
        clip = true
        compositingStrategy = CompositingStrategy.Offscreen
    }) {
        val showOverlay by remember { derivedStateOf { progress() > .01f } }
        if (showOverlay) {
            Box(Modifier.matchParentSize().graphicsLayer { alpha = progress().coerceIn(0f, 1f) }) {
                ProgressiveGlassBackground(state = glassState,
                    maxBlurRadius = { 50f * progress().coerceIn(0f, 1f) * (1f - playerSheetProgress().coerceIn(0f, 1f)) },
                    foundationColor = background, direction = BlurDirection.BottomToTop,
                    steps = 3, modifier = Modifier.fillMaxSize())
            }
            Box(Modifier.matchParentSize().drawWithCache {
                onDrawBehind {
                    val scroll = progress().coerceIn(0f, 1f)
                    drawRect(Brush.verticalGradient(0f to background.copy(alpha = scroll * .9f),
                        .3f to background.copy(alpha = scroll * .6f),
                        .6f to background.copy(alpha = scroll * .3f), 1f to Color.Transparent))
                }
            })
        }
        Row(Modifier.fillMaxWidth().padding(top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()+14.dp,bottom=16.dp,start=HomeContentInset,end=30.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Box(contentAlignment=Alignment.CenterStart) {
                Icon(painterResource(R.drawable.ic_akira_logo_header),"Akira",tint=MaterialTheme.colorScheme.onBackground,
                    modifier=Modifier.size(23.5.dp,25.dp).graphicsLayer{alpha=1f-p;scaleX=1f-p*.25f;scaleY=scaleX;transformOrigin=TransformOrigin(0f,.5f)})
                Text(localizedText("Home","Inicio"),style=TextStyle(fontFamily=AkiraFontFamily,fontWeight=FontWeight.Bold,fontSize=32.sp,letterSpacing=(-.06).em),
                    modifier=Modifier.graphicsLayer{alpha=p})
            }
            com.akira.app.core.design.components.AkiraAccountButton({ profile = true },
                Modifier.graphicsLayer { scaleX=1f-p*.1f;scaleY=scaleX }, alwaysShowProfile = true)
        }
    }
}
