package com.akira.app.feature.setup

/** One decision for the primary action: denial and scan failure never mean Continue. */
internal enum class SetupPrimaryAction { REQUEST_PERMISSION, OPEN_SETTINGS, START_SCAN, WAIT, CONTINUE }
internal enum class SetupPermission { AUDIO, NOTIFICATIONS }

internal fun requiredSetupPermission(audioGranted: Boolean, notificationsGranted: Boolean): SetupPermission? = when {
    !audioGranted -> SetupPermission.AUDIO
    !notificationsGranted -> SetupPermission.NOTIFICATIONS
    else -> null
}

internal fun notificationSetupAction(enabled: Boolean, runtimePermission: Boolean, requested: Boolean, rationale: Boolean): SetupPrimaryAction = when {
    enabled -> SetupPrimaryAction.CONTINUE
    !runtimePermission || requested && !rationale -> SetupPrimaryAction.OPEN_SETTINGS
    else -> SetupPrimaryAction.REQUEST_PERMISSION
}

internal fun librarySetupAction(audioGranted: Boolean, permanentlyDenied: Boolean, state: SetupState): SetupPrimaryAction = when {
    !audioGranted -> if (permanentlyDenied) SetupPrimaryAction.OPEN_SETTINGS else SetupPrimaryAction.REQUEST_PERMISSION
    state.status == SetupStatus.SCANNING -> SetupPrimaryAction.WAIT
    state.status == SetupStatus.READY && state.failure == null && !state.librarySkipped -> SetupPrimaryAction.CONTINUE
    else -> SetupPrimaryAction.START_SCAN
}
