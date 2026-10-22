package com.akira.app

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.akira.app.core.design.components.recordAkiraGlassBackdrop
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.captionBarPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.akira.app.core.datastore.UserPreferencesRepository
import com.akira.app.core.media.ExternalSongLoader
import com.akira.app.core.design.components.AkiraSplashIntro
import com.akira.app.core.design.theme.AkiraTheme
import com.akira.app.core.model.Song
import com.akira.app.feature.library.LibraryScreen
import com.akira.app.feature.setup.LibrarySetupScreen
import com.akira.app.feature.setup.WelcomeScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class OnboardingNav {
    WELCOME,
    LIBRARY_SETUP,
    PERSONALIZE,
    MAIN_LIBRARY
}

open class MainActivity : ComponentActivity() {
    companion object { const val ACTION_OPEN_PLAYER = "com.akira.app.action.OPEN_PLAYER" }

    /** Observable so ACTION_VIEW also works while this activity is already open. */
    private var pendingViewIntent by mutableStateOf<android.content.Intent?>(null)

    private var shouldShowIntro = false
    private var openPlayerFromWidget by mutableStateOf(false)
    private var openPlayerFromMediaSession by mutableStateOf(false)
    private var multiWindowMode by mutableStateOf(false)
    var widgetLaunchMotion by mutableStateOf<com.akira.app.core.widget.WidgetLaunchState?>(null)
        private set
    var widgetArtworkReady: Boolean = false
    var widgetInitialPositionMs: Long = 0L
        private set

    var widgetInitialSongId: String? = null
        private set

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_OPEN_PLAYER) openPlayerFromMediaSession = true
        if (intent.getBooleanExtra(com.akira.app.core.widget.AkiraWidgets.EXTRA_OPEN_PLAYER, false)) {
            openPlayerFromWidget = true
            widgetInitialPositionMs = getSharedPreferences("akira_widget", MODE_PRIVATE).getLong("queue_position", 0L)
            widgetInitialSongId = com.akira.app.core.widget.AkiraWidgets.currentSong(this)?.id
            intent.sourceBounds?.takeIf { !it.isEmpty }?.let { bounds ->
                if (Build.VERSION.SDK_INT >= 30) setTranslucent(true)
                val widgetId = intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                val snapshot = com.akira.app.core.widget.WidgetSnapshotStore.read(this, widgetId, widgetInitialSongId)
                widgetLaunchMotion = com.akira.app.core.widget.WidgetLaunchState(android.graphics.Rect(bounds),
                    intent.getStringExtra(com.akira.app.core.widget.AkiraWidgets.EXTRA_VARIANT) ?: "small",
                    widgetId, getSharedPreferences("akira_widget", MODE_PRIVATE).getLong("queue_position", 0L),
                    snapshot, widgetInitialSongId)
                if (Build.VERSION.SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
                else @Suppress("DEPRECATION") overridePendingTransition(0, 0)
            }
        }
        if (intent.action == android.content.Intent.ACTION_VIEW) {
            pendingViewIntent = intent
        }
    }

    fun returnToWidget(): Boolean {
        val motion = widgetLaunchMotion ?: return false
        val currentSongId = com.akira.app.core.widget.AkiraWidgets.currentSong(this)?.id
        if (currentSongId != motion.snapshotSongId) {
            com.akira.app.core.widget.WidgetSnapshotStore.read(this, motion.widgetId, currentSongId)?.let {
                motion.snapshot = it
                motion.snapshotSongId = currentSongId
            }
        }
        if (Build.VERSION.SDK_INT >= 30) setTranslucent(true)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        // Back returns to the actual widget bounds, independently of launcher's app-icon destination.
        motion.closing = true
        motion.finished = false
        return true
    }

    fun finishWidgetReturn() {
        if (Build.VERSION.SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        finishAndRemoveTask()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) widgetLaunchMotion?.windowReady = true
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        multiWindowMode = isInMultiWindowMode
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Seed visual state before connecting to playback or composing the first frame.
        val widgetRequest = this is com.akira.app.core.widget.WidgetLaunchActivity ||
            intent.getBooleanExtra(com.akira.app.core.widget.AkiraWidgets.EXTRA_OPEN_PLAYER, false)
        openPlayerFromMediaSession = intent.action == ACTION_OPEN_PLAYER
        widgetInitialPositionMs = if (widgetRequest) getSharedPreferences("akira_widget", MODE_PRIVATE).getLong("queue_position", 0L) else 0L
        widgetInitialSongId = if (widgetRequest) com.akira.app.core.widget.AkiraWidgets.currentSong(this)?.id else null
        val initialUi = com.akira.app.core.datastore.AppUiSnapshot.read(this)
        shouldShowIntro = com.akira.app.core.startup.processIntroSession.onLaunch(widgetRequest || openPlayerFromMediaSession, savedInstanceState != null)
        val widgetBounds = intent.sourceBounds
        if (widgetRequest && widgetBounds != null && !widgetBounds.isEmpty) {
            setTheme(R.style.Theme_Akira_WidgetLegacy)
            val widgetId = intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            val snapshot = com.akira.app.core.widget.WidgetSnapshotStore.read(this, widgetId, widgetInitialSongId)
            widgetLaunchMotion = com.akira.app.core.widget.WidgetLaunchState(
                android.graphics.Rect(widgetBounds), intent.getStringExtra(com.akira.app.core.widget.AkiraWidgets.EXTRA_VARIANT) ?: "small",
                widgetId, getSharedPreferences("akira_widget", MODE_PRIVATE).getLong("queue_position", 0L),
                snapshot, widgetInitialSongId)
        }
        super.onCreate(savedInstanceState)
        multiWindowMode = isInMultiWindowMode
        // Never hold the first frame for an image request: the launcher animates it immediately.
        if (widgetRequest) {
            // A widget launch must leave the launcher visible behind the exact
            // RemoteViews snapshot. Replacing the transparent theme background
            // with the saved palette here produced a one or two frame white
            // flash in light mode before the expansion started.
            window.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            )
        } else if (shouldShowIntro) {
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(
                com.akira.app.core.design.brand.AkiraBrand.accent.toArgb()))
        } else initialUi?.let { ui ->
            val dark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val light = com.akira.app.core.design.theme.resolveThemeMode(ui.themeMode, dark) == "LIGHT"
            val palette = com.akira.app.core.design.theme.presetColorScheme(
                com.akira.app.core.model.AppearancePreset.fromId(ui.appearancePreset), light, ui.accentColor,
                if (light) com.akira.app.core.design.theme.LightColorScheme else com.akira.app.core.design.theme.AmoledColorScheme)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(palette.background.toArgb()))
        }
        if (widgetRequest) onBackPressedDispatcher.addCallback(this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { if (!returnToWidget()) finish() }
            })
        if (widgetLaunchMotion != null && Build.VERSION.SDK_INT >= 30) {
            setTranslucent(true)
        }
        if (widgetRequest) {
            if (Build.VERSION.SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            else @Suppress("DEPRECATION") overridePendingTransition(0, 0)
        }
        pendingViewIntent = intent
        openPlayerFromWidget = widgetRequest
        // Enable true edge-to-edge system insets (status bar & gesture nav bar)
        // The app is always dark: force light icons on system bars.
        // Con enableEdgeToEdge() a secas, un teléfono en modo claro pintaba los
        // iconos de la barra de estado en oscuro sobre fondo oscuro.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        com.akira.app.core.online.OnlineRepository.get(this).prefetchHome()
        setContent {
            // Las preferencias se leen ANTES del tema: themeMode se guarda en
            // DataStore pero hasta ahora no lo consultaba nadie, así que el
            // tema estaba fijo. Con la lectura aquí arriba se puede pasar al
            // tema sin sacar el repositorio de la lambda del tema.
            val context = LocalContext.current
            val prefsRepo = remember { UserPreferencesRepository(context) }
            val loadedPrefs by prefsRepo.userPreferences.collectAsStateWithLifecycle(
                initialValue = null,
                minActiveState = androidx.lifecycle.Lifecycle.State.CREATED
            )

            val prefsState = loadedPrefs ?: initialUi
            AkiraTheme(themeMode = prefsState?.themeMode ?: "SYSTEM", appearancePreset = prefsState?.appearancePreset ?: "MIDNIGHT", accentColor = prefsState?.accentColor ?: "PRESET", spaceMode = prefsState?.spaceMode ?: false, spaceStyle = prefsState?.spaceStyle ?: "SPACE") {
                val activeLanguage = prefsState?.language ?: "SYSTEM"
                val currentStrings = remember(activeLanguage) {
                    when (activeLanguage) {
                        "es" -> com.akira.app.core.i18n.StringsSpanish
                        "en" -> com.akira.app.core.i18n.StringsEnglish
                        else -> {
                            val systemLocale = java.util.Locale.getDefault().language
                            if (systemLocale.startsWith("es")) com.akira.app.core.i18n.StringsSpanish
                            else com.akira.app.core.i18n.StringsEnglish
                        }
                    }
                }

                var showSplashIntro by remember { mutableStateOf(shouldShowIntro) }
                var introContentAllowed by remember { mutableStateOf(!shouldShowIntro) }
                val lightBars = com.akira.app.core.design.theme.LocalThemeMode.current == "LIGHT"
                androidx.compose.runtime.SideEffect {
                    androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = lightBars && !showSplashIntro
                        isAppearanceLightNavigationBars = lightBars && !showSplashIntro
                    }
                }
                val rootGlassBackdrop = com.akira.app.core.design.components.rememberAkiraGlassBackdrop()
                androidx.compose.runtime.CompositionLocalProvider(
                    com.akira.app.core.design.components.LocalMultiWindowMode provides multiWindowMode,
                    com.akira.app.core.design.components.LocalAkiraGlassBackdrop provides rootGlassBackdrop,
                    com.akira.app.core.i18n.LocalStrings provides currentStrings
                ) {
    // The visual snapshot can be restored by Android independently of DataStore.
    // It supplies colours only; completion is read from the authoritative preferences.
    var currentScreen by rememberSaveable { mutableStateOf<OnboardingNav?>(null) }
                    var scannedSongsList by remember { mutableStateOf<List<Song>>(emptyList()) }

                    // Navigation must not depend indefinitely on a preference read.
                    // This fallback only chooses a screen; it never writes/reset preferences.
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(com.akira.app.core.startup.StartupPolicy.PREFERENCES_WAIT_MS)
                        if (currentScreen == null) {
                            val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
                                else Manifest.permission.READ_EXTERNAL_STORAGE
                            val hasAudioPermission = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                            currentScreen = when (com.akira.app.core.startup.StartupPolicy.destination(null, hasAudioPermission, true)) {
                                com.akira.app.core.startup.StartupDestination.LIBRARY -> OnboardingNav.MAIN_LIBRARY
                                else -> OnboardingNav.WELCOME
                            }
                            com.akira.app.core.logging.AkiraLog.w("Startup", "Preferences delayed; showing navigation fallback")
                        }
                    }

                    // Open audio files supplied by another application.
                    // El intent de arranque y el de onNewIntent se tratan igual:
                    // si la app ya estaba abierta y el usuario elige otro
                    // archivo, se reproduce ese sin perder la sesion.
                    var externalSong by remember { mutableStateOf<Song?>(null) }
                    var externalError by remember { mutableStateOf<Boolean>(false) }
                    LaunchedEffect(externalError) {
                        if (externalError) {
                            com.akira.app.core.design.components.AkiraNotices.show(currentStrings.externalFileUnreadable)
                            externalError = false
                        }
                    }

                    LaunchedEffect(pendingViewIntent) {
                        val request = pendingViewIntent ?: return@LaunchedEffect
                        if (request.action != android.content.Intent.ACTION_VIEW) return@LaunchedEffect
                        val uri = request.data ?: return@LaunchedEffect
                        externalSong = null
                        externalError = false
                        showSplashIntro = false
                        val song = withContext(Dispatchers.IO) {
                            ExternalSongLoader.persistPermission(context, uri)
                            ExternalSongLoader.load(context, uri)
                        }
                        externalError = song == null
                        externalSong = song
                        // A configuration change must not replay an already handled file.
                        setIntent(android.content.Intent(this@MainActivity, MainActivity::class.java))
                        pendingViewIntent = null
                    }

LaunchedEffect(loadedPrefs) {
                    val prefs = loadedPrefs
                    if (prefs != null) com.akira.app.core.datastore.AppUiSnapshot.write(context, prefs)
                    // DataStore completion outranks an old UI snapshot after a fast close/reopen.
                    if (prefs != null && (currentScreen == null ||
                            (prefs.isOnboardingCompleted && currentScreen != OnboardingNav.MAIN_LIBRARY))) {
                        if (prefs.isOnboardingCompleted) {
                            currentScreen = OnboardingNav.MAIN_LIBRARY
                        } else {
                            currentScreen = OnboardingNav.WELCOME
                        }
                    }
                }

                // Si el archivo llego antes de terminar el onboarding, se
                // descarta al terminar: no tiene sentido saltar el setup.
                // (La navegación la hace LibraryScreen al recibir externalSong.)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Sigue al tema: en modo Oscuro el fondo no es negro
                        // puro. Antes estaba fijado a Color.Black y el ajuste
                        // de tema no tenía ninguna efecto visible.
                        .background(if (widgetLaunchMotion?.finished == false) Color.Transparent else MaterialTheme.colorScheme.background)
                        .captionBarPadding()
                ) {
                    val activeScreen = currentScreen
                    if (activeScreen != null && (!showSplashIntro || introContentAllowed)) {
                        // Authentic iOS Empalme Push/Pop Parallax Transition (Zero-Lag, using(null) to eliminate layout overhead)
                        AnimatedContent(
                            modifier = Modifier.recordAkiraGlassBackdrop(rootGlassBackdrop).graphicsLayer {
                                val motion = widgetLaunchMotion
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                                scaleX = 1f; scaleY = 1f; translationX = 0f; translationY = 0f
                                alpha = 1f
                                clip = motion != null && !motion.finished
                                if (motion != null && !motion.finished) {
                                    val frame = com.akira.app.core.widget.widgetContentFrame(motion.origin,
                                        size.width, size.height, motion.progress.value)
                                    scaleX = frame.scale; scaleY = frame.scale
                                    translationX = frame.surface.left; translationY = frame.surface.top
                                    alpha = com.akira.app.core.widget.widgetPlayerAlpha(motion)
                                    shape = com.akira.app.core.widget.widgetContentShape(motion,
                                        24f * resources.displayMetrics.density)
                                }
                            },
                            targetState = activeScreen,
                            transitionSpec = {
                                com.akira.app.core.design.navigation.pageNavigationTransform(
                                    targetState.ordinal>initialState.ordinal,
                                    android.provider.Settings.Global.getFloat(context.contentResolver,
                                        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f).using(null)
                        },
                        label = "ios_empalme_screen_transition"
                    ) { screen ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer()
                        ) {
                            when (screen) {
                                OnboardingNav.WELCOME -> {
                                    WelcomeScreen(
                                        onGetStartedClick = {
                                            currentScreen = OnboardingNav.LIBRARY_SETUP
                                        }
                                    )
                                }
                                OnboardingNav.LIBRARY_SETUP -> {
                                    LibrarySetupScreen(
                                        onNavigateBack = {
                                            currentScreen = OnboardingNav.WELCOME
                                        },
                                        onSetupComplete = { songs ->
                                            scannedSongsList = songs
                                            currentScreen = OnboardingNav.MAIN_LIBRARY
                                        },
                                        onCustomize={songs -> scannedSongsList=songs;currentScreen=OnboardingNav.PERSONALIZE}
                                    )
                                }
                                OnboardingNav.PERSONALIZE -> {
                                    com.akira.app.feature.setup.PersonalizeSetupScreen(prefsRepo,
                                        onBack={currentScreen=OnboardingNav.LIBRARY_SETUP},
                                        onComplete={scannedSongsList=emptyList();currentScreen=OnboardingNav.MAIN_LIBRARY})
                                }
                                OnboardingNav.MAIN_LIBRARY -> {
                                    // Prepare the destination during the pause after the signature settles.
                                    run {
                                        LibraryScreen(
                                            initialSongs = scannedSongsList,
                                            externalSong = externalSong,
                                            openPlayerFromWidget = openPlayerFromWidget,
                                            openPlayerFromMediaSession = openPlayerFromMediaSession,
                                            onMediaSessionPlayerOpened = {
                                                openPlayerFromMediaSession = false
                                                if (intent.action == ACTION_OPEN_PLAYER) intent.action = null
                                            },
                                            onReturnToWidget = ::returnToWidget,
                                            onWidgetPlayerOpened = {
                                                openPlayerFromWidget = false
                                                intent.removeExtra(com.akira.app.core.widget.AkiraWidgets.EXTRA_OPEN_PLAYER)
                                            },
                                            onExternalConsumed = {
                                                externalSong = null
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    widgetLaunchMotion?.let {
                        com.akira.app.core.widget.WidgetOpeningOverlay(it)
                        com.akira.app.core.widget.WidgetLaunchBackdrop(it)
                    }
                    com.akira.app.core.design.components.AkiraNoticeHost(
                        Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                            .padding(bottom = 110.dp))
                    }

                    // Keep the intro above the prepared destination until its reveal finishes.
                    if (showSplashIntro) {
                        AkiraSplashIntro(
                            isReadyToTransition = currentScreen != null && introContentAllowed,
                            onRevealFinished = { introContentAllowed = true },
                            onAnimationFinished = {
                                showSplashIntro = false
                            }
                        )
                    }
                }
                }
            }
        }
    }
}
