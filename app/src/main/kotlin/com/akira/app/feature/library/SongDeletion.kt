package com.akira.app.feature.library

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.akira.app.core.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun rememberSongDeleter(onDeleted: (String) -> Unit, onError: () -> Unit): (Song) -> Unit {
    val context = LocalContext.current
    val deleted by rememberUpdatedState(onDeleted)
    val failed by rememberUpdatedState(onError)
    var pending by rememberSaveable { mutableStateOf<SongDeletionState?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending = pending?.permissionResult(granted)
    }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        pending = pending?.consentResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(pending) {
        val request = pending ?: return@LaunchedEffect
        try {
            when (request.stage) {
                SongDeletionStage.REQUEST_WRITE -> {
                    pending = request.copy(stage = SongDeletionStage.WAIT_WRITE)
                    permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                SongDeletionStage.REQUEST_SYSTEM -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val intent = MediaStore.createDeleteRequest(context.contentResolver, listOf(Uri.parse(request.uri)))
                        pending = request.copy(stage = SongDeletionStage.WAIT_SYSTEM)
                        consent.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                    } else pending = request.copy(stage = SongDeletionStage.FAILURE)
                }
                SongDeletionStage.DELETE -> {
                    try {
                        val count = withContext(Dispatchers.IO) {
                            context.contentResolver.delete(Uri.parse(request.uri), null, null)
                        }
                        pending = request.copy(stage = if (count > 0) SongDeletionStage.SUCCESS else SongDeletionStage.FAILURE)
                    } catch (error: SecurityException) {
                        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && error is RecoverableSecurityException && !request.retried) {
                            pending = request.recoverableFailure()
                            consent.launch(IntentSenderRequest.Builder(error.userAction.actionIntent.intentSender).build())
                        } else pending = request.copy(stage = SongDeletionStage.FAILURE)
                    }
                }
                SongDeletionStage.SUCCESS -> {
                    // API 30+ already deleted the file; only update playback after approval.
                    pending = null
                    deleted(request.songId)
                }
                SongDeletionStage.FAILURE -> { pending = null; failed() }
                else -> Unit
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            pending = request.copy(stage = SongDeletionStage.FAILURE)
        }
    }
    return { song ->
        if (pending == null) {
            val writeGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            pending = SongDeletionState.start(song.id, song.contentUri, Build.VERSION.SDK_INT, writeGranted)
        }
    }
}
