package com.akira.app.feature.setup

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import com.akira.app.feature.settings.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.akira.app.core.datastore.*
import com.akira.app.core.design.components.*
import com.akira.app.core.design.glass.ProvideGlassPreferences
import com.akira.app.core.design.icon.AkiraIcons
import com.akira.app.core.design.navigation.pageNavigationTransform
import com.akira.app.core.model.Song
import com.akira.app.core.online.OnlineRepository
import com.akira.app.feature.online.YouTubeLoginActivity
import com.akira.app.feature.settings.LibraryFilterReview

// NOTIFICATIONS retains its saved-state name and now contains both permission requests.
internal enum class OnboardingPage { WELCOME, STYLE, MODES, NOTIFICATIONS, LIBRARY, PLAYER, SOUND, READY }

@Composable internal fun OnboardingScreen(repo: UserPreferencesRepository, onComplete: (List<Song>) -> Unit,
    onExit: (() -> Unit)? = null) {
    val prefs by repo.userPreferences.collectAsStateWithLifecycle(initialValue = null)
    if (prefs == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else ProvideGlassPreferences(prefs!!) {
        val activity = LocalContext.current.setupActivity()
        val lightBars = MaterialTheme.colorScheme.background.luminance() > .5f
        SideEffect {
            activity?.let { androidx.core.view.WindowCompat.getInsetsController(it.window, it.window.decorView).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            } }
        }
        val parentAnchors = LocalAkiraMenuAnchors.current
        val menuAnchors = remember(parentAnchors) { parentAnchors ?: AkiraMenuAnchors() }
        val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
        CompositionLocalProvider(LocalAkiraMenuAnchors provides menuAnchors, LocalContentMotionReduced provides (setupReducedMotion() || !lifecycleState.isAtLeast(Lifecycle.State.STARTED))) {
            SetupBrandTheme { AkiraPresentationHost { OnboardingContent(repo, prefs!!, onComplete, onExit) } }
        }
    }
}

@Composable private fun OnboardingContent(repo: UserPreferencesRepository, prefs: UserPreferences,
    onComplete: (List<Song>) -> Unit, onExit: (() -> Unit)?) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentPrefs by rememberUpdatedState(prefs)
    val model: LibrarySetupViewModel = viewModel(key = "onboardingExperience")
    val state by model.controller.state.collectAsStateWithLifecycle()
    val rawSongs by model.allScannedSongs.collectAsStateWithLifecycle()
    val filtered by model.visibleSongs.collectAsStateWithLifecycle()
    val pending by model.writes.pending.collectAsStateWithLifecycle()
    val failures by model.writes.failures.collectAsStateWithLifecycle()
    val online = remember(context) { OnlineRepository.get(context) }
    val account by online.account.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(OnboardingPage.WELCOME) }
    val transition = updateTransition(page, label = "onboardingNavigation")
    val navigating = transition.currentState != transition.targetState
    val canProceed = pending == 0 && failures.isEmpty() && !state.saving && !state.completed && !navigating
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var audioRequested by rememberSaveable { mutableStateOf(false) }
    var awaitingAudioSettings by rememberSaveable { mutableStateOf(false) }
    var notificationsRequested by rememberSaveable { mutableStateOf(false) }
    var awaitingNotificationSettings by rememberSaveable { mutableStateOf(false) }
    var notificationDenied by rememberSaveable { mutableStateOf(false) }
    var audioDenied by rememberSaveable { mutableStateOf(false) }
    var audioGranted by remember { mutableStateOf(hasSetupAudioPermission(context)) }
    var notificationsGranted by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val audioPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
        notificationDenied = !notificationsGranted
    }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        audioGranted = granted
        audioDenied = !granted
    }
    val latestState by rememberUpdatedState(state)
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            audioGranted = hasSetupAudioPermission(context)
            notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
            if (!audioGranted) model.controller.reset()
            if (awaitingNotificationSettings) {
                awaitingNotificationSettings = false
                notificationDenied = !notificationsGranted
            }
            if (awaitingAudioSettings) {
                awaitingAudioSettings = false
                audioDenied = !audioGranted
            }
        } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    // ViewModel survives rotation. Process recreation must re-establish actual scan results.
    LaunchedEffect(page, audioGranted) {
        if (page.ordinal > OnboardingPage.LIBRARY.ordinal && state.status == SetupStatus.IDLE && state.failure == null) {
            if (audioGranted) model.controller.start() else page = OnboardingPage.NOTIFICATIONS
        }
    }
    LaunchedEffect(state.failure) {
        if (state.failure == SetupFailure.PERMISSION) {
            audioGranted = hasSetupAudioPermission(context)
            notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
            page = if (!notificationsGranted || !audioGranted) OnboardingPage.NOTIFICATIONS else OnboardingPage.LIBRARY
        }
    }
    val completion by rememberUpdatedState(onComplete)
    val departure = remember { androidx.compose.animation.core.Animatable(0f) }
    val reduced = setupReducedMotion()
    LaunchedEffect(state.completed) {
        if (state.completed) {
            if (!reduced) departure.animateTo(1f, androidx.compose.animation.core.tween(950, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            completion(model.completedSongs.value)
        }
    }
    fun back() {
        if (state.saving || state.completed || navigating) return
        if (sheet != null) sheet = null
        else if (page != OnboardingPage.WELCOME) {
            if (page == OnboardingPage.LIBRARY && state.status == SetupStatus.SCANNING) model.controller.reset()
            page = OnboardingPage.entries[page.ordinal - 1]
        } else if (pending == 0) onExit?.invoke()
    }
    BackHandler(state.saving || page != OnboardingPage.WELCOME || sheet != null || onExit != null) { back() }
    fun advance() { if (canProceed) page = OnboardingPage.entries[(page.ordinal + 1).coerceAtMost(OnboardingPage.READY.ordinal)] }
    fun openAudioSettings() {
        awaitingAudioSettings = true
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }
    val songs = filtered ?: state.songs
    fun requestAudio() {
        val permanentlyDenied = audioRequested && !audioGranted && context.setupActivity()?.let {
            !ActivityCompat.shouldShowRequestPermissionRationale(it, audioPermission)
        } == true
        if (permanentlyDenied) openAudioSettings()
        else { audioRequested = true; audioLauncher.launch(audioPermission) }
    }
    fun requestNotifications() {
        val rationale = if (Build.VERSION.SDK_INT >= 33) context.setupActivity()?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
        } == true else false
        when (notificationSetupAction(notificationsGranted, Build.VERSION.SDK_INT >= 33, notificationsRequested, rationale)) {
            SetupPrimaryAction.REQUEST_PERMISSION -> {
                notificationsRequested = true; notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            SetupPrimaryAction.OPEN_SETTINGS -> {
                awaitingNotificationSettings = true
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
            else -> Unit
        }
    }
    Box(Modifier.fillMaxSize()) {
    transition.AnimatedContent(transitionSpec = { pageNavigationTransform(targetState.ordinal > initialState.ordinal, reduced).using(null) },
        modifier = Modifier.fillMaxSize()) { scene ->
        val title = when (scene) {
            OnboardingPage.WELCOME -> localizedText("Meet Akira.", "Esto es Akira.")
            OnboardingPage.STYLE -> localizedText("Glass or Classic.", "Glass o Classic.")
            OnboardingPage.MODES -> localizedText("From here.\nFrom everywhere.", "De aquí.\nDe todas partes.")
            OnboardingPage.NOTIFICATIONS -> localizedText("Permissions", "Permisos")
            OnboardingPage.LIBRARY -> when {
                state.status == SetupStatus.SCANNING -> localizedText("Finding your music.", "Buscando tu música.")
                state.failure != null -> localizedText("Let's try again.", "Vamos a intentarlo de nuevo.")
                state.status == SetupStatus.READY && !state.librarySkipped && songs.isNotEmpty() -> localizedText("We found your music.", "Encontramos tu música.")
                state.status == SetupStatus.READY && !state.librarySkipped -> localizedText("No songs here yet.", "Aún no hay canciones.")
                else -> localizedText("Build your library.", "Prepara tu biblioteca.")
            }
            OnboardingPage.PLAYER -> localizedText("Your player, your way.", "Tu reproductor, a tu manera.")
            OnboardingPage.SOUND -> localizedText("Playback", "Reproducción")
            OnboardingPage.READY -> localizedText("Welcome to Akira.", "Bienvenido a Akira.")
        }
        val description = when (scene) {
            OnboardingPage.WELCOME -> localizedText("Play your files and explore YouTube Music in the same app.", "Reproduce tus archivos y explora YouTube Music en una misma aplicación.")
            OnboardingPage.STYLE -> localizedText("Choose the look of your player and menus.", "Elige el estilo de tu reproductor y tus menús.")
            OnboardingPage.MODES -> localizedText("The music you keep. The music you discover.", "La música que guardas. La que descubres.")
            OnboardingPage.NOTIFICATIONS -> localizedText("Allow access here. Android will ask you to confirm each permission.", "Activa los accesos aquí. Android te pedirá confirmar cada permiso.")
            OnboardingPage.LIBRARY -> when {
                !audioGranted -> localizedText("Allow audio access to find songs saved on this phone.", "Permite el acceso al audio para encontrar las canciones del teléfono.")
                state.failure == SetupFailure.SCAN -> localizedText("The library couldn't be read. You can retry the scan.", "No se pudo leer la biblioteca. Puedes reintentar el escaneo.")
                state.status == SetupStatus.SCANNING -> localizedText("Building your library from the audio on this phone.", "Preparando tu biblioteca con el audio de este teléfono.")
                state.status == SetupStatus.READY && songs.isEmpty() && !state.librarySkipped -> localizedText("Check your filters or add audio files to the phone, then scan again.", "Revisa los filtros o añade archivos de audio al teléfono y vuelve a buscar.")
                state.status == SetupStatus.READY && !state.librarySkipped -> localizedText("Choose what stays in your library.", "Elige qué quieres en tu biblioteca.")
                else -> localizedText("Choose what to include, then scan this phone.", "Elige qué incluir y busca la música de este teléfono.")
            }
            OnboardingPage.PLAYER -> localizedText("Choose a cover layout. See it change below.", "Elige cómo ver la portada y comprueba el cambio.")
            OnboardingPage.SOUND -> localizedText("Song transitions and volume balance.", "Transiciones entre canciones y equilibrio de volumen.")
            OnboardingPage.READY -> localizedText("Your next song starts here.", "Tu próxima canción empieza aquí.")
        }
        OnboardingFrame(title, description, onBack = if (scene != OnboardingPage.WELCOME || onExit != null) ({ back() }) else null,
            hero = when (scene) {
                OnboardingPage.READY -> ({ OnboardingBrandStage(finale = true) })
                OnboardingPage.WELCOME -> ({ OnboardingBrandStage() })
                else -> null
            }, footer = {
                if (failures.isNotEmpty()) {
                    Text(localizedText("A setting couldn't be saved.", "No se pudo guardar un ajuste."), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton({ model.writes.retry() }, enabled = pending == 0) { Text(localizedText("Retry saving", "Reintentar guardado")) }
                }
                when (scene) {
                    OnboardingPage.LIBRARY -> {
                        val permitted = audioGranted && notificationsGranted
                        val action = librarySetupAction(audioGranted, false, state)
                        val label = when {
                            !permitted -> localizedText("Review permissions", "Revisar permisos")
                            action == SetupPrimaryAction.WAIT -> localizedText("Scanning…", "Buscando…")
                            action == SetupPrimaryAction.CONTINUE -> localizedText("Continue", "Continuar")
                            else -> localizedText("Scan", "Buscar música")
                        }
                        SetupAction(label, enabled = canProceed && action != SetupPrimaryAction.WAIT) {
                            if (!permitted) page = OnboardingPage.NOTIFICATIONS
                            else when (action) {
                                SetupPrimaryAction.CONTINUE -> advance()
                                SetupPrimaryAction.START_SCAN -> model.controller.start()
                                else -> Unit
                            }
                        }
                    }
                    OnboardingPage.NOTIFICATIONS -> {
                        val permission = requiredSetupPermission(audioGranted, notificationsGranted)
                        SetupAction(when (permission) {
                            SetupPermission.AUDIO -> localizedText("Allow audio access", "Permitir acceso al audio")
                            SetupPermission.NOTIFICATIONS -> localizedText("Allow notifications", "Permitir notificaciones")
                            null -> localizedText("Continue", "Continuar")
                        }, enabled = canProceed) {
                            when (permission) {
                                SetupPermission.AUDIO -> requestAudio()
                                SetupPermission.NOTIFICATIONS -> requestNotifications()
                                null -> advance()
                            }
                        }
                    }
                    OnboardingPage.READY -> {
                        val ready = state.status == SetupStatus.READY && !state.librarySkipped && audioGranted && notificationsGranted
                        SetupAction(if (state.saving) localizedText("Saving…", "Guardando…") else if (ready) localizedText("Enter Akira", "Entrar a Akira") else if (!audioGranted || !notificationsGranted) localizedText("Review permissions", "Revisar permisos") else localizedText("Review library", "Revisar biblioteca"), enabled = canProceed) {
                            when {
                                !notificationsGranted || !audioGranted -> page = OnboardingPage.NOTIFICATIONS
                                ready -> model.controller.finish()
                                else -> page = OnboardingPage.LIBRARY
                            }
                        }
                        if (state.failure == SetupFailure.SAVE) Text(localizedText("Setup couldn't be saved. Please try again.", "No se pudo guardar la configuración. Vuelve a intentarlo."), color = MaterialTheme.colorScheme.error)
                    }
                    else -> SetupAction(if (scene == OnboardingPage.WELCOME) localizedText("Get started", "Empezar") else localizedText("Continue", "Continuar"), enabled = canProceed) { advance() }
                }
                if (scene == OnboardingPage.MODES) Text(localizedText("Switch modes whenever you like in Settings.", "Cambia de modo cuando quieras desde Ajustes."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }) {
            when (scene) {
                OnboardingPage.WELCOME -> Unit
                OnboardingPage.READY -> Unit
                OnboardingPage.STYLE -> OnboardingStyle(prefs.liquidGlassEnabled, canProceed) { glass ->
                    model.writes.submit("interfaceStyle") { repo.setLiquidGlassEnabled(glass) }
                }
                OnboardingPage.MODES -> {
                    OnboardingMode(false, localizedText("LOCAL · OFFLINE", "LOCAL · SIN INTERNET"), localizedText("On your phone.", "En tu teléfono."), localizedText("Songs and albums saved on your phone. No connection needed.", "Canciones y álbumes guardados en el teléfono. Sin depender de una conexión."))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
                    OnboardingMode(true, localizedText("ONLINE · WITH INTERNET", "EN LÍNEA · CON INTERNET"), "YouTube Music.", localizedText("Search and play online. Bring your playlists and favorites when you connect an account.", "Busca y escucha en línea. Conecta una cuenta para traer tus playlists y favoritos."))
                    FilledTonalButton({ sheet = "account" }, Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(50)) {
                        Icon(Icons.Rounded.Person, null, Modifier.size(20.dp))
                        Text(if (account.connected) localizedText("Account connected", "Cuenta conectada") else localizedText("Connect YouTube Music", "Conectar YouTube Music"), Modifier.padding(start = 10.dp))
                    }
                }
                OnboardingPage.NOTIFICATIONS -> {
                    OnboardingPermissions(audioGranted, notificationsGranted, canProceed, ::requestAudio, ::requestNotifications)
                    if ((notificationDenied && !notificationsGranted) || (audioDenied && !audioGranted)) Text(localizedText(
                        "A permission is still off. Use Allow to retry or open Android settings.",
                        "Hay un permiso desactivado. Usa Permitir para reintentarlo o abrir los ajustes de Android."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OnboardingPage.LIBRARY -> {
                    val ready = state.status == SetupStatus.READY && !state.librarySkipped && audioGranted && state.failure == null
                    if (ready) OnboardingCollection(songs) else if (state.status == SetupStatus.SCANNING) OnboardingLibraryStatus(state)
                    OnboardingLibraryFilters(prefs, rawSongs, repo, model, ready, onReview = { sheet = "excluded" })
                    if (ready) TextButton({ model.controller.start() }, enabled = canProceed) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                        Text(localizedText("Scan again", "Volver a buscar"), Modifier.padding(start = 8.dp))
                    }

                }
                OnboardingPage.PLAYER -> {
                    OnboardingPlayerPreview(songs.firstOrNull { !it.albumArtUri.isNullOrBlank() } ?: songs.firstOrNull(), prefs.fullscreenPlayerArtwork)
                    SettingsGroup {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(localizedText("Cover layout", "Diseño de portada"), style = MaterialTheme.typography.titleSmall)
                            AkiraSegmentedControl(listOf("COMPACT" to localizedText("Compact", "Compacta"), "FULL" to localizedText("Full screen", "Pantalla completa")),
                                if (prefs.fullscreenPlayerArtwork) "FULL" else "COMPACT", { value ->
                                    model.writes.submit("artwork") { repo.setFullscreenPlayerArtwork(value == "FULL") }
                                }, MaterialTheme.colorScheme.surfaceContainerHigh)
                        }
                        if (prefs.fullscreenPlayerArtwork) SettingsToggleRow(Icons.Rounded.Movie, localizedText("Animated covers", "Portadas animadas"),
                            localizedText("Play video artwork when available", "Reproduce portadas en vídeo cuando estén disponibles"), prefs.animatedPlayerArtwork) { value ->
                            model.writes.submit("animatedArt") { repo.setAnimatedPlayerArtwork(value) }
                        }
                    }
                }
                OnboardingPage.SOUND -> {
                    var lastNormalization by rememberSaveable { mutableStateOf(prefs.normalizationMode.takeIf { it != "OFF" } ?: "TRACK") }
                    SettingsGroup {
                        SettingsToggleRow(Icons.Rounded.Waves, "Crossfade", localizedText("Blend the end of one track into the next", "Mezcla el final de una pista con la siguiente"), prefs.crossfadeSeconds > 0) { value ->
                            model.writes.submit("crossfade") { repo.setCrossfadeSeconds(if (value) currentPrefs.lastCrossfadeSeconds.coerceAtLeast(1) else 0) }
                        }
                    }
                    AnimatedVisibility(prefs.crossfadeSeconds > 0, enter = if (reduced) EnterTransition.None else expandVertically() + fadeIn(),
                        exit = if (reduced) ExitTransition.None else shrinkVertically() + fadeOut()) {
                        OnboardingCrossfadeEditor(prefs) { value -> model.writes.submit("crossfade") { repo.setCrossfadeSeconds(value) } }
                    }
                    SettingsGroup {
                        SettingsToggleRow(AkiraIcons.Airplay, localizedText("Normalize volume", "Normalizar volumen"),
                            localizedText("Reduce loudness differences between tracks", "Reduce las diferencias de volumen entre pistas"), prefs.normalizationMode != "OFF") { value ->
                            model.writes.submit("normalization") { repo.setNormalizationMode(if (value) lastNormalization else "OFF") }
                        }
                        AnimatedVisibility(prefs.normalizationMode != "OFF") {
                            SettingsRow(Icons.Rounded.Tune, localizedText("Normalization mode", "Modo de normalización"),
                                if (prefs.normalizationMode == "ALBUM") localizedText("By album", "Por álbum") else localizedText("By song", "Por canción"),
                                { sheet = "normalization" }, opensSelector = true)
                        }
                    }
                    LaunchedEffect(prefs.normalizationMode) { if (prefs.normalizationMode != "OFF") lastNormalization = prefs.normalizationMode }
                }
            }
        }
    }
    if (state.completed && !reduced) OnboardingDeparture { departure.value }
    }
    if (sheet == "excluded") OnboardingExcludedFiles(rawSongs, prefs, repo, persist = { ids, keep ->
        model.writes.submit("exceptions") { repo.setSongsWhitelisted(ids, keep) }.join()
        check("exceptions" !in model.writes.failures.value)
    }, onClose = { if (pending == 0) sheet = null })
    when (sheet) {
        "normalization" -> SettingsChoiceSheet(localizedText("Normalize volume", "Normalizar volumen"),
            localizedText("By song balances each track. By album preserves the relative levels within an album.", "Por canción equilibra cada pista. Por álbum conserva las diferencias de volumen dentro de un álbum."),
            prefs.normalizationMode, listOf("TRACK" to localizedText("By song", "Por canción"), "ALBUM" to localizedText("By album", "Por álbum")),
            { sheet = null }, { value -> model.writes.submit("normalization") { repo.setNormalizationMode(value) }; sheet = null })
        "account" -> AkiraModalSheet(onDismissRequest = { sheet = null }, style = AkiraPanelStyle.Page, title = if (account.connected) localizedText("Account connected", "Cuenta conectada") else localizedText("Your YouTube Music", "Tu YouTube Music")) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(if (account.connected) account.name else localizedText("Connect your account to see your playlists, favorites and recommendations. You can also listen without connecting an account.", "Conecta tu cuenta para ver tus playlists, favoritos y recomendaciones. También puedes escuchar sin conectar una cuenta."), style = MaterialTheme.typography.bodyLarge)
                if (!account.connected) SetupAction(localizedText("Connect account", "Conectar cuenta")) {
                    sheet = null
                    context.startActivity(Intent(context, YouTubeLoginActivity::class.java).putExtra("themeMode", prefs.themeMode).putExtra("appearancePreset", prefs.appearancePreset))
                } else SetupAction(localizedText("Done", "Listo")) { sheet = null }
            }
        }
    }
}

@Composable private fun OnboardingMode(online: Boolean, eyebrow: String, title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = if (online) {
            if (com.akira.app.core.design.theme.LocalThemeMode.current == "LIGHT") androidx.compose.ui.graphics.Color(0xFF6951B1) else androidx.compose.ui.graphics.Color(0xFFC2B4FF)
        } else MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OnboardingModeArt(online)
        }
    }
}

private tailrec fun Context.setupActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.setupActivity()
    else -> null
}
