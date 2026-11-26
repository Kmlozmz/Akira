package com.akira.app.feature.appearance

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.akira.app.core.design.navigation.NavigationBackSlot
import com.akira.app.core.i18n.AkiraUiText
import com.akira.app.core.i18n.LocalStrings
import com.akira.app.core.model.Song
import com.akira.app.feature.settings.AkiraStudioScreen
import com.akira.app.feature.settings.SettingsBackControl
import io.flutter.FlutterInjector
import io.flutter.embedding.android.FlutterTextureView
import io.flutter.embedding.android.FlutterView
import io.flutter.embedding.engine.FlutterEngine

private val FontTypes = arrayOf("font/ttf", "font/otf", "application/x-font-ttf",
    "application/x-font-opentype", "application/octet-stream")

/** One idle engine for Settings. The destination owns any engine it takes. */
internal class AppearanceEngineWarmup(context: Context) {
    private val app = context.applicationContext
    private var engine: FlutterEngine? = null
    private var preparing = false
    private var taken = false
    private var disposed = false

    fun start() = FlutterInjector.instance().flutterLoader().startInitialization(app)

    fun prepare() {
        taken = false
        if (engine != null || preparing || disposed) return
        preparing = true
        FlutterInjector.instance().flutterLoader().ensureInitializationCompleteAsync(
            app, null, Handler(Looper.getMainLooper()),
        ) {
            preparing = false
            if (!taken && !disposed) engine = FlutterEngine(app)
        }
    }

    fun take(): FlutterEngine {
        taken = true
        return engine.also { engine = null } ?: FlutterEngine(app)
    }

    fun dispose() {
        disposed = true
        engine?.destroy()
        engine = null
    }
}

@Composable
internal fun AppearanceDestination(warmup: AppearanceEngineWarmup, previewSongs: List<Song>, onClose: () -> Unit) {
    val close by rememberUpdatedState(onClose)
    val strings = LocalStrings.current
    val title = strings.ui(AkiraUiText.Appearance)
    var route by remember { mutableStateOf(AppearanceRoute.ROOT) }
    var session by remember { mutableStateOf<AppearanceSession?>(null) }
    val enterReady = remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        session?.onFontResult(uri)
    }
    val back = { if (route == AppearanceRoute.ROOT) close() else session?.pop() ?: Unit }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val backWidth = minOf(245f, LocalConfiguration.current.screenWidthDp * .32f).dp

    AkiraStudioScreen(title, back, embeddedHeader = {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(start = 20.dp, top = top + 14.dp)) {
                Box(Modifier.widthIn(max = backWidth)) {
                    if (route == AppearanceRoute.MODAL) NavigationBackSlot(placeholder = {}, content = {})
                    else SettingsBackControl(if (route == AppearanceRoute.ROOT) strings.settingsTitle else title, back)
                }
            }
        }
    }, enterReady = enterReady) {
        BackHandler(enabled = route != AppearanceRoute.ROOT, onBack = back)
        AndroidView(
            // Compose owns the window insets for the embedded texture as well as Back.
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            factory = { context ->
                enterReady.value = false
                val appearance = AppearanceSession(context, warmup.take(), previewSongs, { launcher.launch(FontTypes) }, { close() },
                    onReady = { enterReady.value = true }) { route = it }
                session = appearance
                FlutterView(context, FlutterTextureView(context)).apply {
                    attachToFlutterEngine(appearance.engine)
                }
            },
            onRelease = { view ->
                view.detachFromFlutterEngine()
                session?.dispose()
                session = null
            },
        )
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val appearance = session
        DisposableEffect(lifecycle, appearance) {
            if (appearance == null) return@DisposableEffect onDispose {}
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> appearance.engine.lifecycleChannel.appIsResumed()
                    Lifecycle.Event.ON_PAUSE -> appearance.engine.lifecycleChannel.appIsInactive()
                    Lifecycle.Event.ON_STOP -> appearance.engine.lifecycleChannel.appIsPaused()
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
    }
}
