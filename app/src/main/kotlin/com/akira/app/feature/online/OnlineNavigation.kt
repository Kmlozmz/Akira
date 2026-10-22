@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.akira.app.feature.online

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akira.app.core.design.navigation.*
import com.akira.app.core.model.Song
import com.akira.app.core.online.OnlineRepository
import com.akira.app.feature.home.HomeDestination
import com.akira.innertube.models.*

internal data class OnlineRoute(val item: YTItem, val transitionKey: String? = null)

@Composable fun OnlineScreen(repository: OnlineRepository, destination: HomeDestination,
    current: Song?, playing: Boolean, bottomPadding: Dp,
    onPlay: (Song, List<Song>) -> Unit, onTogglePlay: () -> Unit, obscured: Boolean,
    playbackError: Boolean = false, requestedItem: YTItem? = null, onRequestConsumed: () -> Unit = {},
    onMore: (Song) -> Unit = {}, onSettings: () -> Unit = {}) {
    val account by repository.account.collectAsStateWithLifecycle()
    val sessionReady by repository.sessionReady.collectAsStateWithLifecycle()
    if (!sessionReady) return
    var routes by remember(account, destination) { mutableStateOf(emptyList<OnlineRoute>()) }
    LaunchedEffect(requestedItem) {
        requestedItem?.let { routes = listOf(OnlineRoute(it)); onRequestConsumed() }
    }
    fun back() { routes = routes.dropLast(1) }
    key(account, destination) {
        val pages = rememberSaveableStateHolder()
        SharedTransitionLayout(Modifier.fillMaxSize()) {
            val shared = this
            PageNavigationHost(routes, routes.dropLast(1), routes.isNotEmpty() && !obscured, ::back,
                forward = { from, to -> to.size > from.size }, modifier = Modifier.fillMaxSize(),
                navigationTransform = { from, to, reduced ->
                    val opening = to.size > from.size
                    val origin = (if(opening) to else from).lastOrNull()?.transitionKey
                    if(origin != null) collectionContainerTransform(opening, reduced) else pageNavigationTransform(opening, reduced)
                }) { path ->
                val settled = !transition.isRunning && !shared.isTransitionActive
                CompositionLocalProvider(LocalSharedTransitionScope provides shared, LocalNavAnimatedContentScope provides this) {
                    pages.SaveableStateProvider(path.joinToString("/") { "${it.item::class.simpleName}:${it.item.id}:${it.transitionKey}" }) {
                        OnlinePageScreen(repository, destination, current, playing, bottomPadding, onPlay, onTogglePlay,
                            obscured || path != routes || !settled, playbackError, path.lastOrNull(), path.size, settled,
                            onOpen = { item, source ->
                                if(routes.lastOrNull()?.item?.id != item.id) routes = routes + OnlineRoute(item, source)
                            }, onBack = ::back, onMore = onMore, onSettings = onSettings)
                    }
                }
            }
        }
    }
}

internal fun YTItem.onlineCollectionKey(prefix: String): String? = if(this is SongItem) null else "$prefix:${this::class.simpleName}:$id"
