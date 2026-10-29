package com.akira.app.feature.settings

import com.akira.app.core.updates.AppUpdate
import com.akira.app.core.updates.UpdateCheck
import com.akira.app.core.updates.UpdateFailure
import java.io.File

internal enum class InstallationNotice { Permission, Error }
internal enum class UpdatePrimaryAction { Check, ViewUpdate, ViewProgress, Install }

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
) {
    val availableUpdate: AppUpdate?
        get() = (result as? UpdateCheck.Available)?.update

    val canCheckForUpdates: Boolean
        get() = !checking && !downloading && !verifying && !preparingInstaller

    val primaryAction: UpdatePrimaryAction
        get() = when {
            availableUpdate == null -> UpdatePrimaryAction.Check
            downloading || verifying || preparingInstaller -> UpdatePrimaryAction.ViewProgress
            downloaded != null -> UpdatePrimaryAction.Install
            else -> UpdatePrimaryAction.ViewUpdate
        }
}
