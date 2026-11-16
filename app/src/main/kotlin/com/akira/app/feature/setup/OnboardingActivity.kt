package com.akira.app.feature.setup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akira.app.core.datastore.UserPreferencesRepository
import com.akira.app.core.design.glass.*
import com.akira.app.core.design.theme.AkiraTheme
import com.akira.app.core.design.theme.LocalThemeMode
import com.akira.app.core.i18n.*

/** Review the complete experience without clearing data or resetting completion. */
class OnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            val repo = remember { UserPreferencesRepository(this) }
            val loaded by repo.userPreferences.collectAsStateWithLifecycle(initialValue = null)
            val prefs = loaded
            if (prefs != null) AkiraTheme(themeMode = prefs.themeMode, appearancePreset = prefs.appearancePreset,
                accentColor = prefs.accentColor, spaceMode = prefs.spaceMode, spaceStyle = prefs.spaceStyle,
                appearanceFont = prefs.appearanceFont, appearanceFontId = prefs.appearanceFontId,
                appearanceSizeIndex = prefs.appearanceSizeIndex) {
                val light = LocalThemeMode.current == "LIGHT"
                SideEffect { WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = light; isAppearanceLightNavigationBars = light
                } }
                val strings = if (prefs.language == "es" || (prefs.language == "SYSTEM" && java.util.Locale.getDefault().language.startsWith("es"))) StringsSpanish else StringsEnglish
                val glass = rememberSharedGlassMaterial()
                CompositionLocalProvider(LocalStrings provides strings, LocalSharedGlassMaterial provides glass) {
                    OnboardingScreen(repo, onComplete = {
                        val reduced = com.akira.app.core.design.motion.InteractionPreferences.reducedMotion ||
                            com.akira.app.core.design.components.isReduceMotionActive(this@OnboardingActivity)
                        val enter = if (reduced) 0 else com.akira.app.R.anim.onboarding_return_enter
                        val exit = if (reduced) 0 else com.akira.app.R.anim.onboarding_return_exit
                        if (android.os.Build.VERSION.SDK_INT >= 34) {
                            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, enter, exit)
                            finish()
                        } else {
                            finish()
                            @Suppress("DEPRECATION") overridePendingTransition(enter, exit)
                        }
                    }, onExit = { finish() })
                }
            }
        }
    }
}
