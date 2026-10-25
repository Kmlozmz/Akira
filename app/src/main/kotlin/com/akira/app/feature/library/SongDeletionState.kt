package com.akira.app.feature.library

import java.io.Serializable

internal enum class SongDeletionStage {
    REQUEST_WRITE, WAIT_WRITE, DELETE, WAIT_CONSENT, REQUEST_SYSTEM, WAIT_SYSTEM, SUCCESS, FAILURE
}

internal data class SongDeletionState(
    val songId: String,
    val uri: String,
    val stage: SongDeletionStage,
    val retried: Boolean = false
) : Serializable {
    fun permissionResult(granted: Boolean): SongDeletionState =
        if (stage != SongDeletionStage.WAIT_WRITE) this
        else copy(stage = if (granted) SongDeletionStage.DELETE else SongDeletionStage.FAILURE)

    fun consentResult(approved: Boolean): SongDeletionState? = when {
        stage != SongDeletionStage.WAIT_CONSENT && stage != SongDeletionStage.WAIT_SYSTEM -> this
        !approved -> null
        stage == SongDeletionStage.WAIT_SYSTEM -> copy(stage = SongDeletionStage.SUCCESS)
        else -> copy(stage = SongDeletionStage.DELETE, retried = true)
    }

    fun recoverableFailure(): SongDeletionState = copy(
        stage = if (retried) SongDeletionStage.FAILURE else SongDeletionStage.WAIT_CONSENT
    )

    companion object {
        fun start(songId: String, uri: String, sdk: Int, hasWritePermission: Boolean) = SongDeletionState(
            songId, uri, when {
                sdk >= 30 -> SongDeletionStage.REQUEST_SYSTEM
                sdk <= 28 && !hasWritePermission -> SongDeletionStage.REQUEST_WRITE
                else -> SongDeletionStage.DELETE
            }
        )
    }
}
