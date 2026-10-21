package com.akira.app

import android.app.Application
import androidx.media3.common.util.UnstableApi
import com.akira.app.core.database.AppDatabase
import com.akira.app.core.datastore.UserPreferencesRepository
import kotlinx.coroutines.launch

@UnstableApi
class AkiraApplication : Application(), coil.ImageLoaderFactory {
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
        .components { add(com.akira.app.core.online.OnlineArtworkInterceptor()) }.build()

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val preferencesRepository: UserPreferencesRepository by lazy { UserPreferencesRepository(this) }

    override fun onCreate() {
        super.onCreate()
        com.akira.app.core.logging.AkiraLog.installUncaughtExceptionHandler()
        com.akira.app.core.logging.AkiraLog.i("Application", "Akira application initialized")
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main).launch {
            preferencesRepository.userPreferences.collect { com.akira.app.core.widget.AkiraWidgets.refreshPreferences(this@AkiraApplication, it) }
        }
    }
}
