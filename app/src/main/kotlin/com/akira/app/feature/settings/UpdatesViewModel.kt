package com.akira.app.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.akira.app.BuildConfig
import com.akira.app.core.updates.AppUpdate
import com.akira.app.core.updates.AppUpdates
import com.akira.app.core.updates.UpdateCheck
import com.akira.app.core.updates.UpdateException
import com.akira.app.core.updates.UpdateFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class UpdatesState(
    val checking: Boolean = false,
    val checked: Boolean = false,
    val result: UpdateCheck? = null,
    val failure: UpdateFailure? = null,
    val downloading: Boolean = false,
    val verifying: Boolean = false,
    val cancelling: Boolean = false,
    val downloadCancelled: Boolean = false,
    val installerReturned: Boolean = false,
    val completedVersion: String? = null,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0,
    val downloaded: File? = null,
    val dialogVisible: Boolean = false,
    val pageVisible: Boolean = false,
    val installWhenReady: Boolean = false,
    val preparingInstaller: Boolean = false,
    val installationNotice: InstallationNotice? = null,
)

internal class UpdatesViewModel(application: Application) : AndroidViewModel(application) {
    val updates = AppUpdates(application)
    private val preferences = application.getSharedPreferences("app_updates", Application.MODE_PRIVATE)
    private val mutable = MutableStateFlow(UpdatesState(completedVersion = completedVersion()))
    private var downloadJob: Job? = null
    private var automaticPromptDeferred = false
    val state = mutable.asStateFlow()

    fun check(showAvailable: Boolean = true) {
        if (mutable.value.checking || mutable.value.downloading) return
        mutable.update { it.copy(checking = true, failure = null, downloadCancelled = false, installerReturned = false) }
        viewModelScope.launch {
            try {
                val result = updates.check()
                mutable.update { it.copy(checking = false, checked = true, result = result,
                    dialogVisible = it.dialogVisible || (showAvailable && result is UpdateCheck.Available),
                    downloaded = if (it.result == result) it.downloaded else null) }
                preferences.edit().putLong("last_check_time", System.currentTimeMillis()).apply()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutable.update { it.copy(checking = false, checked = true, failure = error.reason()) }
            }
        }
    }

    fun checkAutomatically() {
        if (BuildConfig.DEBUG || mutable.value.checked) return
        val elapsed = System.currentTimeMillis() - preferences.getLong("last_check_time", 0)
        if (elapsed in 0 until TimeUnit.HOURS.toMillis(24)) return
        check(showAvailable = false)
    }

    fun presentIfEligible() {
        val update = (mutable.value.result as? UpdateCheck.Available)?.update ?: return
        if (automaticPromptDeferred || preferences.getLong("ignored_version_code", 0) == update.versionCode) return
        openDetails()
    }

    fun openDetails() {
        if (mutable.value.result is UpdateCheck.Available) mutable.update { it.copy(dialogVisible = true) }
    }

    fun setPageVisible(visible: Boolean) { mutable.update { it.copy(pageVisible = visible) } }

    fun requestInstallation() {
        if (!mutable.value.preparingInstaller) mutable.update { it.copy(installWhenReady = true, installerReturned = false) }
    }

    fun dismiss(skipVersion: Boolean = false) {
        automaticPromptDeferred = true
        if (skipVersion) (mutable.value.result as? UpdateCheck.Available)?.update?.let {
            preferences.edit().putLong("ignored_version_code", it.versionCode).apply()
        }
        if (mutable.value.downloading) cancelDownload()
        mutable.update { it.copy(dialogVisible = false, installWhenReady = false) }
    }

    fun download(update: AppUpdate) {
        if (mutable.value.checking || mutable.value.downloading) return
        mutable.update { it.copy(downloading = true, verifying = false, cancelling = false,
            progress = 0f, downloadedBytes = 0, failure = null, downloadCancelled = false,
            installerReturned = false, installWhenReady = true) }
        downloadJob = viewModelScope.launch {
            try {
                val file = updates.download(update,
                    onProgress = { bytes -> mutable.update { it.copy(downloadedBytes = bytes,
                        progress = (bytes.toDouble() / update.size).toFloat().coerceIn(0f, 1f)) } },
                    onVerifying = { mutable.update { it.copy(verifying = true) } })
                mutable.update { it.copy(downloaded = file, progress = 1f, downloadedBytes = update.size) }
            } catch (cancelled: CancellationException) {
                mutable.update { it.copy(downloadCancelled = true, installWhenReady = false) }
                throw cancelled
            }
            catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                mutable.update { it.copy(failure = error.reason(), installWhenReady = false) }
            } finally {
                mutable.update { it.copy(downloading = false, verifying = false, cancelling = false) }
            }
        }
    }

    fun cancelDownload() {
        if (!mutable.value.downloading || mutable.value.cancelling) return
        mutable.update { it.copy(cancelling = true, downloadCancelled = true, installWhenReady = false) }
        downloadJob?.cancel()
        updates.cancelRequests()
    }

    suspend fun prepareInstaller(update: AppUpdate): android.content.Intent = withContext(Dispatchers.IO) {
        val file = mutable.value.downloaded ?: throw UpdateException(UpdateFailure.INVALID_APK)
        val intent = updates.installerIntent(file, update)
        if (!preferences.edit().putLong("pending_install_code", update.versionCode).commit()) {
            throw UpdateException(UpdateFailure.STORAGE)
        }
        mutable.update { it.copy(installerReturned = false, failure = null) }
        intent
    }

    fun installerReturned() { mutable.update { it.copy(installerReturned = true) } }

    fun installationStarted(preparing: Boolean = false) {
        mutable.update { it.copy(installWhenReady = false, preparingInstaller = preparing) }
    }

    fun installerPrepared() { mutable.update { it.copy(preparingInstaller = false) } }

    fun installationNotice(notice: InstallationNotice?) {
        mutable.update { it.copy(installationNotice = notice) }
    }

    fun installationFailed(error: Exception) {
        mutable.update { it.copy(failure = error.reason(), downloaded = if (error is UpdateException &&
            error.reason == UpdateFailure.INVALID_APK) null else it.downloaded) }
    }

    private fun completedVersion(): String? {
        val pending = preferences.getLong("pending_install_code", 0)
        if (pending !in 1..BuildConfig.VERSION_CODE.toLong()) return null
        preferences.edit().remove("pending_install_code").apply()
        return BuildConfig.VERSION_NAME
    }

    override fun onCleared() { updates.cancelRequests(); super.onCleared() }

    private fun Exception.reason() = when (this) {
        is UpdateException -> reason
        is IOException -> UpdateFailure.NETWORK
        else -> UpdateFailure.INVALID_RELEASE
    }
}
