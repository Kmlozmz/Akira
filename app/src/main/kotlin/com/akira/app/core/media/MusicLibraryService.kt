package com.akira.app.core.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.akira.app.MainActivity
import com.akira.app.R
import com.akira.app.core.audio.AudioFxEqualizerManager
import com.akira.app.core.audio.effectiveEqualizer
import com.akira.app.core.media.MediaItemMapper.toMediaItem
import com.akira.app.core.media.MediaItemMapper.queueIdentity
import com.akira.app.core.media.MediaItemMapper.toSong
import com.akira.app.core.datastore.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

@UnstableApi
class MusicLibraryService : MediaLibraryService() {

    companion object {
        const val CHANNEL_ID = "akira_music_playback_channel"
        const val NOTIFICATION_ID = 1001

    }

    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var deckA: ExoPlayer
    private lateinit var deckB: ExoPlayer
    private lateinit var activeDeck: ExoPlayer
    private lateinit var sessionDeckA: QueueTransactionPlayer
    private lateinit var sessionDeckB: QueueTransactionPlayer
    private var editingQueue=false
    private fun sessionPlayer(player: ExoPlayer)=if(player===deckA)sessionDeckA else sessionDeckB
    private lateinit var crossfadeEngine: CrossfadeEngine
    private lateinit var sleepTimerManager: SleepTimerManager

    // Foco de audio Ãºnico para los dos decks (ver PlaybackFocus).
    private lateinit var playbackFocus: PlaybackFocus
    // ReanudaciÃ³n pendiente por pÃ©rdida de foco TRANSITORIA (llamada, otra app).
    private var resumeAfterTransientLoss = false
    // Marca que la pausa actual la provocÃ³ una pÃ©rdida de foco, no el usuario.
    // Distingue "pausado porque me han quitado el foco" de "pausado a propÃ³sito":
    // sÃ³lo en el primer caso se puede reanudar solo al volver el foco.
    private var pausedByFocusLoss = false

    // Un manager POR DECK: audiofx ata los efectos al audioSessionId, y cada
    // ExoPlayer tiene el suyo. Si se compartiera, el crossfade dejarÃ­a una de
    // las dos pistas sonando sin EQ.
    private var eqManagerA = AudioFxEqualizerManager(UserPreferencesRepository.EQ_BAND_FREQUENCIES)
    private var eqManagerB = AudioFxEqualizerManager(UserPreferencesRepository.EQ_BAND_FREQUENCIES)

    // Ãšltimo estado de EQ conocido. Se guarda para poder reaplicarlo cuando un
    // deck pasa a STATE_READY y ya tiene sessionId real.
    @Volatile private var currentEqualizerPrefs: com.akira.app.core.datastore.UserPreferences? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var prefsRepo: UserPreferencesRepository
    // Ãšltimos modos conocidos (para sincronizar el deck entrante en cada handover)
    @Volatile private var lastKnownShuffle = false
    @Volatile private var lastKnownRepeat = Player.REPEAT_MODE_OFF
    private var pausedByBecomingNoisy = false
    private var bluetoothPausedAt: Long? = null
    private var lastHeadphoneRemovalAt: Long? = null
    private var audioManager: android.media.AudioManager? = null
    private var routeCallback: android.media.MediaRouter.Callback? = null
    private var audioDeviceCallback: android.media.AudioDeviceCallback? = null

    override fun onCreate() {
        super.onCreate()
        prefsRepo = UserPreferencesRepository(this)
        serviceScope.launch {
            recentAutoplayIds.clear()
            recentAutoplayIds.addAll(prefsRepo.userPreferences.first().recentAutoplayIds.takeLast(50))
            recentIdsLoaded = true
        }
        createNotificationChannel()
        initializePlayer()
        playbackFocus = PlaybackFocus(
            context = this,
            onLost = { willResume ->
                // Se captura playWhenReady ANTES de pausar: si no, la propia
                // pausa pondrÃ­a el flag a false y nunca se marcarÃ­a para reanudar.
                val wasPlaying = activeDeck.playWhenReady
                resumeAfterTransientLoss = willResume && wasPlaying
                pausedByFocusLoss = wasPlaying
                if (wasPlaying) activeDeck.pause()
            },
            onGained = {
                if (resumeAfterTransientLoss) {
                    resumeAfterTransientLoss = false
                    // Si el usuario ya lo reanudÃ³ a mano mientras esperaba, no
                    // hay que volver a llamar a play().
                    if (!activeDeck.playWhenReady) activeDeck.play()
                }
            }
        )
        initializeSession()
        initializeNotificationProvider()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager = getSystemService(android.media.AudioManager::class.java)
            audioDeviceCallback = object : android.media.AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
                    super.onAudioDevicesAdded(addedDevices)
                    refreshOutputProfile()
                    val headphonesAdded = addedDevices?.any {
                        com.akira.app.core.audio.BluetoothResumeDecision.supportsHeadphones(it.type, Build.VERSION.SDK_INT)
                    } == true
                    val prefs = currentEqualizerPrefs
                    if (com.akira.app.core.audio.BluetoothResumeDecision.shouldResume(
                            prefs?.bluetoothResume == true, bluetoothPausedAt, android.os.SystemClock.elapsedRealtime(),
                            prefs?.bluetoothWindowMinutes ?: 10, !pausedByBecomingNoisy, headphonesAdded)) {
                        bluetoothPausedAt = null
                        pausedByBecomingNoisy = false
                        runCatching { activeDeck.play() }.onFailure {
                            com.akira.app.core.logging.AkiraLog.e("BluetoothResume", "Background resume rejected: ${it.javaClass.simpleName}", it)
                        }
                    }
                }
                override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) {
                    if (removedDevices?.any {
                            com.akira.app.core.audio.BluetoothResumeDecision.supportsHeadphones(it.type, Build.VERSION.SDK_INT)
                        } == true) {
                        lastHeadphoneRemovalAt = android.os.SystemClock.elapsedRealtime()
                        if (pausedByBecomingNoisy) bluetoothPausedAt = lastHeadphoneRemovalAt
                    }
                    refreshOutputProfile()
                }
            }
            audioManager?.registerAudioDeviceCallback(audioDeviceCallback, android.os.Handler(android.os.Looper.getMainLooper()))
            refreshOutputProfile()
        }
        val routeRouter = getSystemService(android.media.MediaRouter::class.java)
        routeCallback = object : android.media.MediaRouter.SimpleCallback() {
            override fun onRouteSelected(router: android.media.MediaRouter, type: Int, route: android.media.MediaRouter.RouteInfo) { refreshOutputProfile() }
            override fun onRouteChanged(router: android.media.MediaRouter, route: android.media.MediaRouter.RouteInfo) { refreshOutputProfile() }
        }
        routeRouter.addCallback(android.media.MediaRouter.ROUTE_TYPE_LIVE_AUDIO, routeCallback)


        // El motor solo ve DeckPlayer (interfaz estrecha y testeable); el
        // servicio conserva los ExoPlayer reales y los desempaqueta en el
        // callback. Los wrappers se crean una sola vez para no romper la
        // identidad (el motor compara decks por referencia).
        val deckWrapperA = ExoDeckPlayer(deckA)
        val deckWrapperB = ExoDeckPlayer(deckB)
        crossfadeEngine = CrossfadeEngine(
            deckWrapperA, deckWrapperB, serviceScope,
            onSwapActivePlayer = { newActive ->
                val exo = newActive as? ExoDeckPlayer
                if (exo != null) {
                    val player = exo.unwrap()
                    // Sincroniza shuffle/repeat al deck entrante ANTES de exponerlo a la sesiÃ³n:
                // el deck inactivo conserva sus valores por defecto y sin esto la UI
                // parpadeaba (true -> false -> true) en cada handover.
                player.shuffleModeEnabled = lastKnownShuffle
                player.repeatMode = lastKnownRepeat
                activeDeck = player
                com.akira.app.core.audio.AudioFxAvailability.select(player.audioSessionId)
                mediaLibrarySession?.setPlayer(sessionPlayer(player))
                publishWidgets()
                player.currentMediaItem?.mediaId?.let { id ->
                    serviceScope.launch {
                        recordRecentPlay(id)
                        if (!id.startsWith("youtube:")) prefsRepo.savePlaybackState(id, player.currentPosition.coerceAtLeast(0L))
                    }
                }
            }
            CrossfadePreviewBus.clear()
        },
            onIncomingTrackReady = { incoming ->
                // La UI muestra la canciÃ³n entrante desde ya; el crossfade no se toca.
                CrossfadePreviewBus.setIncoming(incoming.mediaId)
            },
            onCrossfadeCancelled = { CrossfadePreviewBus.clear() }
        )
        crossfadeEngine.start()
        val onlineRepository = com.akira.app.core.online.OnlineRepository.get(this)
        serviceScope.launch {
            var wasConnected = false
            combine(onlineRepository.sessionReady, onlineRepository.account) { ready, account -> ready to account }
                .collect { (ready, account) ->
                    val disconnected = ready && wasConnected && !account.connected
                    if (ready) wasConnected = account.connected
                    if (disconnected && activeDeck.currentMediaItem?.toSong()?.contentUri?.startsWith("akira-youtube:") == true) {
                        crossfadeEngine.cancelCrossfade()
                        deckA.pause(); deckB.pause()
                        deckA.stop(); deckB.stop()
                        deckA.clearMediaItems(); deckB.clearMediaItems()
                        com.akira.app.core.widget.AkiraWidgets.publish(this@MusicLibraryService, null, false,
                            currentEqualizerPrefs ?: com.akira.app.core.datastore.UserPreferences(), false)
                    }
                }
        }
        val scanner = com.akira.app.core.data.MediaStoreScanner(this)
        serviceScope.launch {
            prefsRepo.userPreferences
                .map { Triple(it.minDurationSeconds, it.excludedFolderPaths, it.whitelistedSongIds) }
                .distinctUntilChanged()
                .combine(scanner.observeMediaStoreChanges()) { filters, _ -> filters }
                .collect { (minimum, excluded, whitelist) ->
                    autoplayLibrary = scanner.scanLocalSongs(minimum, excluded, whitelist)
                    val dao = com.akira.app.core.database.AppDatabase.getInstance(this@MusicLibraryService).playbackHistoryDao()
                    autoplayLibrary.forEach { song ->
                        val key = com.akira.app.core.model.SongKey.of(song).encoded
                        dao.resolveLegacy("legacy:${song.id}", key)
                        dao.resolveOffset(song.id, key)
                    }
                }
        }
        serviceScope.launch {
            while (isActive) {
                fillAutoplayQueue()
                samplePlayCounts()
                delay(250L)
            }
        }

        sleepTimerManager = SleepTimerManager(
            scope = serviceScope,
            crossfadeEngine = crossfadeEngine,
            getActiveDeck = { if (activeDeck === deckA) deckWrapperA else deckWrapperB },
            onEndItemModeChanged = { enabled ->
                deckA.setPauseAtEndOfMediaItems(enabled)
                deckB.setPauseAtEndOfMediaItems(enabled)
            }
        )
        crossfadeEngine.onSleepTimerSongEnded = { sleepTimerManager.onSongEndedNaturally() }
        crossfadeEngine.onSleepTimerQueueEnded = { sleepTimerManager.onQueueEnded() }
        SleepTimerBus.registerHandlers(
            onStart = { durationMs ->
                deckA.setPauseAtEndOfMediaItems(durationMs == SleepTimerBus.END_OF_SONG)
                deckB.setPauseAtEndOfMediaItems(durationMs == SleepTimerBus.END_OF_SONG)
                sleepTimerManager.start(durationMs)
            },
            onCancel = {
                deckA.setPauseAtEndOfMediaItems(false)
                deckB.setPauseAtEndOfMediaItems(false)
                sleepTimerManager.cancel()
            }
        )

        serviceScope.launch {
            prefsRepo.userPreferences.collect { prefs ->
                crossfadeEngine.crossfadeDurationMs = prefs.crossfadeSeconds * 1000L
                val outShape = runCatching { CrossfadeShape.valueOf(prefs.crossfadeOutShape) }.getOrDefault(CrossfadeShape.CURRENT)
                val inShape = runCatching { CrossfadeShape.valueOf(prefs.crossfadeInShape) }.getOrDefault(CrossfadeShape.CURRENT)
                crossfadeEngine.outgoingCurveSpec = CurveSpec(
                    shape = outShape,
                    curvature = prefs.crossfadeOutCurvature,
                    startRange = prefs.crossfadeOutStart,
                    endRange = prefs.crossfadeOutEnd
                )
                crossfadeEngine.incomingCurveSpec = CurveSpec(
                    shape = inShape,
                    curvature = prefs.crossfadeInCurvature,
                    startRange = prefs.crossfadeInStart,
                    endRange = prefs.crossfadeInEnd
                )
                if (deckA.skipSilenceEnabled != prefs.skipSilence) deckA.skipSilenceEnabled = prefs.skipSilence
                if (deckB.skipSilenceEnabled != prefs.skipSilence) deckB.skipSilenceEnabled = prefs.skipSilence
                if (prefs.repeatMode != lastKnownRepeat) {
                    lastKnownRepeat = prefs.repeatMode
                    if (deckA.repeatMode != prefs.repeatMode) deckA.repeatMode = prefs.repeatMode
                    if (deckB.repeatMode != prefs.repeatMode) deckB.repeatMode = prefs.repeatMode
                }
                if (prefs.shuffle != lastKnownShuffle) {
                    lastKnownShuffle = prefs.shuffle
                    if (deckA.shuffleModeEnabled != prefs.shuffle) deckA.shuffleModeEnabled = prefs.shuffle
                    if (deckB.shuffleModeEnabled != prefs.shuffle) deckB.shuffleModeEnabled = prefs.shuffle
                }
                // Se guarda el snapshot y se reintenta sobre cada deck: un deck que aÃºn no
                // ha arrancado tiene sessionId 0 y se aplicarÃ¡ en cuanto pase
                // a STATE_READY.
                currentEqualizerPrefs = prefs
                publishWidgets()
                updateNormalization(deckA)
                updateNormalization(deckB)
                applyEqualizer(deckA)
                applyEqualizer(deckB)
            }
        }
    }

    private val recentAutoplayIds = mutableListOf<String>()
    private val playTrackers = mutableMapOf<ExoPlayer, Pair<String, PlayCountTracker>>()
    private var lastPlaySampleAt = android.os.SystemClock.elapsedRealtime()
    private val normalization by lazy { com.akira.app.core.audio.NormalizationRepository(this) }
    private val normalizationKeys = mutableMapOf<ExoPlayer, String>()

    private fun updateNormalization(player: ExoPlayer) {
        if (!::crossfadeEngine.isInitialized) return
        val item = player.currentMediaItem ?: return
        val song = autoplayLibrary.firstOrNull { it.id == item.mediaId } ?: item.toSong()
        val mode = currentEqualizerPrefs?.normalizationMode ?: "OFF"
        val prefs = currentEqualizerPrefs
        val headroom = prefs?.effectiveEqualizer(com.akira.app.core.audio.OutputRoutes.current.value)?.headroom ?: 1f
        val key = "${item.mediaId}|${song.path}|${song.dateModifiedSec}|$mode|$headroom"
        if (normalizationKeys[player] == key) return
        normalizationKeys[player] = key
        val wrapper = if ((crossfadeEngine.activePlayer as ExoDeckPlayer).unwrap() === player) crossfadeEngine.activePlayer else crossfadeEngine.inactivePlayer
        crossfadeEngine.setNormalization(wrapper, headroom)
        if (mode == "OFF") return
        serviceScope.launch {
            val gain = normalization.read(song).factor(mode)
            if (normalizationKeys[player] == key && player.currentMediaItem?.mediaId == item.mediaId) crossfadeEngine.setNormalization(wrapper, gain * headroom)
        }
    }

    private suspend fun samplePlayCounts() {
        val now = android.os.SystemClock.elapsedRealtime()
        val elapsed = now - lastPlaySampleAt
        lastPlaySampleAt = now
        for (deck in listOf(deckA, deckB)) {
            val item = deck.currentMediaItem ?: continue
            val song = autoplayLibrary.firstOrNull { it.id == item.mediaId } ?: item.toSong()
            val key = com.akira.app.core.model.SongKey.of(song).encoded
            val entry = playTrackers[deck]
            val tracker = if (entry?.first == key) entry.second else PlayCountTracker(song.durationMs).also {
                playTrackers[deck] = key to it
            }
            if (tracker.sample(deck.currentPosition, deck.isPlaying, elapsed)) {
                com.akira.app.core.database.AppDatabase.getInstance(this).playbackHistoryDao()
                    .recordPlay(key, System.currentTimeMillis())
                com.akira.app.core.online.OnlineRepository.get(this).recordQualifiedPlay(song)
            }
        }
    }
    private var autoplayFilling = false
    private var autoplayLibrary = emptyList<com.akira.app.core.model.Song>()
    private var recentIdsLoaded = false

    private suspend fun recordRecentPlay(id: String) {
        if (!recentIdsLoaded || id.startsWith("youtube:")) return
        recentAutoplayIds.add(id)
        while (recentAutoplayIds.size > 50) recentAutoplayIds.removeAt(0)
        prefsRepo.saveRecentAutoplayIds(recentAutoplayIds.toList())
    }

    private suspend fun fillAutoplayQueue() {
        if (com.akira.app.core.online.OnlineRepository.get(this).enabled.value ||
            activeDeck.currentMediaItem?.mediaId?.startsWith("youtube:") == true ||
            !recentIdsLoaded || autoplayFilling || !activeDeck.playWhenReady ||
            activeDeck.mediaItemCount == 0 || activeDeck.repeatMode != Player.REPEAT_MODE_OFF ||
            SleepTimerBus.activeMode.value != 0L) return
        val player = activeDeck
        val traversal = playbackQueueIndexes(player)
        val upcoming = remainingQueueIndexes(traversal, player.currentMediaItemIndex)
        if (upcoming.isEmpty() || upcoming.size > 2) return
        val prefs = prefsRepo.userPreferences.first()
        if (!prefs.autoplay) return
        autoplayFilling = true
        try {
            val currentEntry = player.currentMediaItem?.queueIdentity()
            val queued = upcoming.map { player.getMediaItemAt(it).mediaId }.toSet()
            val favorites = com.akira.app.core.database.AppDatabase.getInstance(this)
                .favoritesDao().observeFavorites().first().map { it.songId }.toSet()
            val current = player.currentMediaItem?.mediaMetadata ?: return
            val selected = AutoplaySelector.select(
                autoplayLibrary.map { it.copy(isFavorite = it.id in favorites) },
                current.artist?.toString().orEmpty(), current.genre?.toString().orEmpty(),
                excludedIds = queued + recentAutoplayIds
            )
            if (player === activeDeck && player.currentMediaItem?.queueIdentity() == currentEntry &&
                player.playWhenReady && player.repeatMode == Player.REPEAT_MODE_OFF &&
                SleepTimerBus.activeMode.value == 0L && playbackQueueIndexes(player) == traversal &&
                upcoming.map { player.getMediaItemAt(it).mediaId }.toSet() == queued &&
                !com.akira.app.core.online.OnlineRepository.get(this).enabled.value &&
                prefsRepo.userPreferences.first().autoplay && selected.isNotEmpty()) {
                val firstAdded = player.mediaItemCount
                player.addMediaItems(selected.map { it.toMediaItem() })
                if (player.shuffleModeEnabled) {
                    player.setShuffleOrder(androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(
                        (traversal + (firstAdded until player.mediaItemCount)).toIntArray(), System.nanoTime()))
                }
            }
        } finally {
            autoplayFilling = false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.playback_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Akira player controls and status"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun initializeNotificationProvider() {
        val provider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.playback_channel_name)
            .setNotificationId(NOTIFICATION_ID)
            .build()
        provider.setSmallIcon(R.drawable.ic_notification)
        setMediaNotificationProvider(provider)
    }

    private fun initializePlayer() {
        val online = com.akira.app.core.online.OnlineRepository.get(this)
        val http = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent(com.akira.innertube.models.YouTubeClient.USER_AGENT_WEB)
            .setConnectTimeoutMs(15_000).setReadTimeoutMs(20_000)
        val sourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, http)
        val dataSource = androidx.media3.datasource.DataSource.Factory {
            com.akira.app.core.online.OnlineDataSource(sourceFactory.createDataSource(), online)
        }
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        deckA = ExoPlayer.Builder(this, audioRenderers(this))
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        deckB = ExoPlayer.Builder(this, audioRenderers(this))
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        activeDeck = deckA
        deckA.addAnalyticsListener(AudioDiagnostics("A"))
        deckB.addAnalyticsListener(AudioDiagnostics("B"))
        listOf(deckA, deckB).forEach { deck ->
            deck.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
                override fun onAudioSessionIdChanged(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                    audioSessionId: Int) {
                    applyEqualizer(deck)
                }
            })
        }
        sessionDeckA=QueueTransactionPlayer(deckA)
        sessionDeckB=QueueTransactionPlayer(deckB)

        deckA.addListener(createServicePlayerListener(deckA))
        deckB.addListener(createServicePlayerListener(deckB))
    }


    private fun createServicePlayerListener(player: ExoPlayer) = object : androidx.media3.common.Player.Listener {
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (player == activeDeck && lastKnownShuffle != shuffleModeEnabled) {
                lastKnownShuffle = shuffleModeEnabled
                val inactive = if (player === deckA) deckB else deckA
                if (inactive.shuffleModeEnabled != shuffleModeEnabled) {
                    inactive.shuffleModeEnabled = shuffleModeEnabled
                }
                serviceScope.launch {
                    prefsRepo.setShuffle(shuffleModeEnabled)
                }
                publishWidgets()
            }
        }
        override fun onRepeatModeChanged(repeatMode: Int) {
            if (player == activeDeck && lastKnownRepeat != repeatMode) {
                lastKnownRepeat = repeatMode
                val inactive = if (player === deckA) deckB else deckA
                if (inactive.repeatMode != repeatMode) {
                    inactive.repeatMode = repeatMode
                }
                serviceScope.launch {
                    prefsRepo.setRepeatMode(repeatMode)
                }
            }
        }
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            playTrackers.remove(player)
            updateNormalization(player)
            if (player == activeDeck) {
                if (player.isPlaying) mediaItem?.toSong()?.let {
                    com.akira.app.core.online.OnlineRepository.get(this@MusicLibraryService).recordListening(it)
                }
                publishWidgets()
                mediaItem?.mediaId?.let { id ->
                    serviceScope.launch {
                        recordRecentPlay(id)
                        if (!id.startsWith("youtube:")) prefsRepo.savePlaybackState(id, player.currentPosition.coerceAtLeast(0L))
                    }
                }
            }
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (::activeDeck.isInitialized && player === activeDeck) publishWidgets()
        }

        /**
         * El audioSessionId sÃ³lo existe una vez que el decoder ha arrancado. Si
         * sÃ³lo reaplicÃ¡ramos el EQ al cambiar de preferencias, en el arranque
         * se aplicarÃ­a sobre sessionId 0 y el ajuste no se oirÃ­a hasta el
         * siguiente cambio.
         */
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (player == activeDeck) publishWidgets()
            if (playbackState == Player.STATE_READY) {
                applyEqualizer(player)
            }
        }

        /**
         * El foco sigue al deck ACTIVO: se pide cuando empieza a sonar y se suelta al
         * pausar. El deck entrante del crossfade no toca el foco, asÃ­ que el handover
         * ya no provoca abandonar/pedir foco con los dos decks sonando.
         */
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            Log.d("AkiraPlayback", "service deck=${if (player === deckA) "A" else "B"} " +
                "playWhenReady=$playWhenReady reason=$reason active=${player === activeDeck}")
            if (player !== activeDeck) return
            if(!playWhenReady && reason==Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                ::crossfadeEngine.isInitialized && crossfadeEngine.isMixing)return
            publishWidgets()
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                SleepTimerBus.activeMode.value == SleepTimerBus.END_OF_SONG) {
                sleepTimerManager.onSongEndedNaturally()
                deckA.setPauseAtEndOfMediaItems(false)
                deckB.setPauseAtEndOfMediaItems(false)
            }
            if (playWhenReady) {
                // La acciÃ³n manual del usuario cancela cualquier reanudaciÃ³n
                // automÃ¡tica pendiente: si Ã©l toca play, la prÃ³xima pÃ©rdida de
                // foco no debe decidir por Ã©l cuÃ¡ndo volver a sonar.
                resumeAfterTransientLoss = false
                pausedByFocusLoss = false
                pausedByBecomingNoisy = false
                bluetoothPausedAt = null
                if (!playbackFocus.acquire()) player.pause()
            } else {
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) {
                    pausedByBecomingNoisy = true
                    val now = android.os.SystemClock.elapsedRealtime()
                    val wasHeadphones = com.akira.app.core.audio.BluetoothResumeDecision.supportsHeadphones(
                        com.akira.app.core.audio.OutputRoutes.current.value.type, Build.VERSION.SDK_INT) ||
                        lastHeadphoneRemovalAt?.let { now - it in 0..2000L } == true
                    if (wasHeadphones) bluetoothPausedAt = now
                } else if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                    bluetoothPausedAt = null
                    pausedByBecomingNoisy = false
                }
                // Si la pausa la provocÃ³ la propia pÃ©rdida de foco, el foco se
                // conserva para poder recibir el GAIN que la revierte. En
                // cualquier otro caso (pausa del usuario) se suelta.
                val keepFocus = pausedByFocusLoss && resumeAfterTransientLoss
                pausedByFocusLoss = false
                if (!keepFocus) {
                    resumeAfterTransientLoss = false
                    playbackFocus.release()
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying && player === activeDeck) player.currentMediaItem?.toSong()?.let {
                com.akira.app.core.online.OnlineRepository.get(this@MusicLibraryService).recordListening(it)
            }
            Log.d("AkiraPlayback", "service deck=${if (player === deckA) "A" else "B"} " +
                "isPlaying=$isPlaying active=${player === activeDeck}")
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (!editingQueue && ::activeDeck.isInitialized && player === activeDeck) com.akira.app.core.widget.WidgetPlaybackStore.capture(this@MusicLibraryService, player)
            if (reason == Player.DISCONTINUITY_REASON_SEEK && oldPosition.mediaItem?.mediaId == newPosition.mediaItem?.mediaId) {
                if (newPosition.positionMs == 0L) playTrackers.remove(player)
                else playTrackers[player]?.second?.seek()
            }
        }
    }

    private fun applyEqualizer(player: ExoPlayer) {
        val prefs = serviceScope.let { currentEqualizerPrefs } ?: return
        val sessionId = runCatching { player.audioSessionId }.getOrDefault(0)
        if (::activeDeck.isInitialized && player === activeDeck)
            com.akira.app.core.audio.AudioFxAvailability.select(sessionId)
        val effective = prefs.effectiveEqualizer(com.akira.app.core.audio.OutputRoutes.current.value)
        val manager = if (player === deckA) eqManagerA else eqManagerB
        manager.apply(sessionId, effective.enabled, effective.configuration.bands.toFloatArray(),
            effective.configuration.bass, effective.configuration.space)
    }

    private fun refreshOutputProfile() {
        val before = com.akira.app.core.audio.OutputRoutes.current.value
        val selectedRoute = getSystemService(android.media.MediaRouter::class.java).getSelectedRoute(android.media.MediaRouter.ROUTE_TYPE_LIVE_AUDIO)
        val bluetoothName = selectedRoute.takeIf { it.deviceType == android.media.MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH }?.name?.toString()
        com.akira.app.core.audio.OutputRoutes.refresh(audioManager?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS) ?: emptyArray(),
            selectedBluetoothName = bluetoothName, useDefaultRoute = bluetoothName == null)
        val after = com.akira.app.core.audio.OutputRoutes.current.value
        if (before != after) {
            updateNormalization(deckA)
            updateNormalization(deckB)
            applyEqualizer(deckA)
            applyEqualizer(deckB)
            val prefs = currentEqualizerPrefs ?: return
            val strings = if (prefs.language == "es" || (prefs.language == "SYSTEM" && java.util.Locale.getDefault().language == "es"))
                com.akira.app.core.i18n.StringsSpanish else com.akira.app.core.i18n.StringsEnglish
            val profileId = com.akira.app.core.audio.OutputProfiles.assignments(prefs.eqAssignmentsJson)[after.encoded]
            if (profileId != null && com.akira.app.core.audio.OutputProfiles.decode(prefs.eqProfilesJson).any { it.id == profileId })
                com.akira.app.core.design.components.AkiraNotices.show(strings.library.profileApplied)
        }
    }

    private fun initializeSession() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_PLAYER)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaLibrarySession = MediaLibrarySession.Builder(this, sessionPlayer(activeDeck), LibrarySessionCallback())
            .setSessionActivity(pendingIntent)
            .setId("Akira_MediaSession")
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        if (::activeDeck.isInitialized) com.akira.app.core.widget.AkiraWidgets.publish(this,
            activeDeck.currentMediaItem?.toSong() ?: com.akira.app.core.widget.AkiraWidgets.currentSong(this), false,
            currentEqualizerPrefs ?: com.akira.app.core.datastore.UserPreferences())
        routeCallback?.let { getSystemService(android.media.MediaRouter::class.java).removeCallback(it) }
        routeCallback = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
            audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
        }
        SleepTimerBus.unregisterHandlers()
        if (::sleepTimerManager.isInitialized) {
            sleepTimerManager.cancel()
        }
        serviceScope.cancel()
        crossfadeEngine.release()
        playbackFocus.release()
        eqManagerA.releaseEffects()
        eqManagerB.releaseEffects()
        mediaLibrarySession?.run {
            release()
            mediaLibrarySession = null
        }
        deckA.release()
        deckB.release()
        super.onDestroy()
    }

    private fun publishWidgets() {
        if(editingQueue)return
        if (!::activeDeck.isInitialized) return
        val song = activeDeck.currentMediaItem?.toSong() ?: return
        // A remote controller can still hold the pre-edit shuffled timeline.
        // Publish the actual service traversal with the playlist metadata.
        val order=playbackQueueIndexes(activeDeck).map {activeDeck.getMediaItemAt(it).queueIdentity()}
        val metadata=activeDeck.playlistMetadata
        if(metadata.extras?.getStringArrayList(QueueEditCommand.SERVICE_ORDER)!=order ||
            metadata.extras?.getBoolean(QueueEditCommand.SERVICE_SHUFFLE)!=activeDeck.shuffleModeEnabled) {
            activeDeck.playlistMetadata=metadata.buildUpon().setExtras(Bundle(metadata.extras ?: Bundle.EMPTY).apply {
                putStringArrayList(QueueEditCommand.SERVICE_ORDER,ArrayList(order))
                putBoolean(QueueEditCommand.SERVICE_SHUFFLE,activeDeck.shuffleModeEnabled)
            }).build()
        }
        com.akira.app.core.widget.WidgetPlaybackStore.capture(this, activeDeck)
        com.akira.app.core.widget.AkiraWidgets.publish(this, song,
            activeDeck.playWhenReady && activeDeck.playbackState != Player.STATE_ENDED,
            currentEqualizerPrefs ?: com.akira.app.core.datastore.UserPreferences(), activeDeck.hasNextMediaItem())
    }

    /** Main-thread transaction: edits the actual traversal and preserves the current audio item. */
    private fun editPlaybackQueue(args: Bundle): SessionResult {
        val start=android.os.SystemClock.elapsedRealtime()
        if (::crossfadeEngine.isInitialized) crossfadeEngine.prepareQueueEdit()
        val transaction=sessionPlayer(activeDeck)
        editingQueue=true
        transaction.begin()
        return try {
        val player = activeDeck
        val order = playbackQueueIndexes(player).map { player.getMediaItemAt(it).queueIdentity() }
        val current = player.currentMediaItem?.queueIdentity()
        @Suppress("DEPRECATION")
        val additions = args.getParcelableArrayList<Bundle>(QueueEditCommand.ITEMS).orEmpty().map { bundle ->
            val item = MediaItem.fromBundle(bundle)
            val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri
            require(uri?.scheme in setOf("content", "file", "akira-youtube"))
            item.buildUpon().setUri(uri).build()
        }
        val entry = args.getString(QueueEditCommand.ENTRY).orEmpty()
        val desired = when (args.getString(QueueEditCommand.OP)) {
            "INSERT" -> QueueOrder.insert(order, current, additions.map { it.queueIdentity() },
                args.getString(QueueEditCommand.MODE).orEmpty(), args.getString(QueueEditCommand.TARGET))
            "MOVE" -> QueueOrder.move(order, entry, args.getString(QueueEditCommand.TARGET).orEmpty())
            "REMOVE" -> order.filterNot { it == entry && it != current }
            "CLEAR" -> QueueOrder.keepCurrent(order, current)
            "ORDER" -> QueueOrder.restore(order, args.getStringArrayList(QueueEditCommand.ORDER).orEmpty())
            else -> throw IllegalArgumentException("Unknown queue operation")
        }
        val addedIds = HashSet(order)
        val fresh=additions.filter { addedIds.add(it.queueIdentity()) }
        if(fresh.isNotEmpty())player.addMediaItems(fresh)
        val keep = desired.toSet()
        val physicalBeforeRemoval=(0 until player.mediaItemCount).map { player.getMediaItemAt(it).queueIdentity() }
        QueueOrder.removalRanges(physicalBeforeRemoval,keep).forEach { range ->
            player.removeMediaItems(range.first,range.last+1)
        }
        if (player.shuffleModeEnabled) {
            val physical = (0 until player.mediaItemCount).associateBy { player.getMediaItemAt(it).queueIdentity() }
            player.setShuffleOrder(androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(
                desired.map { physical.getValue(it) }.toIntArray(), System.nanoTime()))
        } else {
            // Cache occurrence identities once. The old nested full queue scan
            // allocated O(nÂ²) Song objects on the UI looper even for one append.
            val physicalIds = (0 until player.mediaItemCount).map {
                player.getMediaItemAt(it).queueIdentity()
            }.toMutableList()
            val positions = physicalIds.withIndex().associate { it.value to it.index }.toMutableMap()
            desired.forEachIndexed { index, id ->
                val physical = positions.getValue(id)
                if (physical != index) {
                    player.moveMediaItem(physical, index)
                    physicalIds.add(index, physicalIds.removeAt(physical))
                    for (changed in minOf(index, physical)..maxOf(index, physical)) {
                        positions[physicalIds[changed]] = changed
                    }
                }
            }
        }
        if (args.getBoolean("start_added_item") && additions.isNotEmpty()) {
            val addedEntry = additions.first().queueIdentity()
            val selectedIndex = (0 until player.mediaItemCount).first { player.getMediaItemAt(it).queueIdentity() == addedEntry }
            player.seekToDefaultPosition(selectedIndex)
            player.prepare()
            player.play()
        } else if (player.mediaItemCount > 0 && player.playbackState == Player.STATE_IDLE) player.prepare()
        SessionResult(SessionResult.RESULT_SUCCESS)
    } catch (e: Exception) {
        com.akira.app.core.logging.AkiraLog.e("Queue", "Queue edit rejected: ${e.javaClass.simpleName}", e)
        SessionResult(androidx.media3.session.SessionError.ERROR_BAD_VALUE)
    } finally {
        editingQueue=false
        transaction.end()
        publishWidgets()
        com.akira.app.core.logging.AkiraLog.i("Queue", "Committed ${args.getString(QueueEditCommand.OP)}: count=${activeDeck.mediaItemCount}, elapsed=${android.os.SystemClock.elapsedRealtime()-start}ms")
    }
    }

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val base = super.onConnect(session, controller)
            if (controller.packageName != packageName || !base.isAccepted) return base
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(base.availablePlayerCommands)
                .setAvailableSessionCommands(base.availableSessionCommands.buildUpon()
                    .add(SessionCommand(QueueEditCommand.ACTION, Bundle.EMPTY))
                    .add(SessionCommand(SleepTimerBus.COMMAND_START_TIMER, Bundle.EMPTY))
                    .add(SessionCommand(SleepTimerBus.COMMAND_CANCEL_TIMER, Bundle.EMPTY)).build()).build()
        }

        override fun onPlayerCommandRequest(session: MediaSession, controller: MediaSession.ControllerInfo, playerCommand: Int): Int {
            if (playerCommand == Player.COMMAND_PLAY_PAUSE) {
                bluetoothPausedAt = null
                pausedByBecomingNoisy = false
            }
            return SessionResult.RESULT_SUCCESS
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = MediaItem.Builder()
                .setMediaId("ROOT_AKIRA")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(com.akira.app.core.i18n.StringsEnglish.appName)
                        .setIsPlayable(false)
                        .setIsBrowsable(true)
                        .build()
                )
                .build()

            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val currentItem = session.player.currentMediaItem
            return if (currentItem != null && currentItem.mediaId == mediaId) {
                Futures.immediateFuture(LibraryResult.ofItem(currentItem, null))
            } else {
                Futures.immediateFuture(LibraryResult.ofError(androidx.media3.session.SessionError.ERROR_BAD_VALUE))
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val updated: MutableList<MediaItem> = mediaItems.mapNotNull { item ->
                val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri
                    ?: android.net.Uri.parse(item.mediaId)
                if (uri.scheme !in setOf("content", "file", "akira-youtube")) return@mapNotNull null
                if (uri.scheme == "akira-youtube" && (controller.packageName != packageName ||
                        !uri.host.orEmpty().matches(Regex("[A-Za-z0-9_-]{11}")))) return@mapNotNull null
                item.buildUpon().setUri(uri).build()
            }.toMutableList()
            return Futures.immediateFuture(updated)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == QueueEditCommand.ACTION) {
                if (controller.packageName != packageName) return Futures.immediateFuture(SessionResult(androidx.media3.session.SessionError.ERROR_PERMISSION_DENIED))
                return Futures.immediateFuture(editPlaybackQueue(args))
            }
            when (customCommand.customAction) {
                SleepTimerBus.COMMAND_START_TIMER -> {
                    val durationMs = args.getLong(SleepTimerBus.EXTRA_DURATION_MS, 0L)
                    if (durationMs != 0L && ::sleepTimerManager.isInitialized) {
                        sleepTimerManager.start(durationMs)
                    }
                }
                SleepTimerBus.COMMAND_CANCEL_TIMER -> {
                    if (::sleepTimerManager.isInitialized) {
                        sleepTimerManager.cancel()
                    }
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }
}
