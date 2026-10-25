package com.akira.app.core.media

import androidx.media3.common.Player

internal fun playbackRequested(player: Player): Boolean = player.playWhenReady &&
    player.playerError == null && player.playbackState != Player.STATE_ENDED

internal fun togglePlaybackIntent(player: Player, onRetry: () -> Unit) {
    when {
        player.playerError != null -> {
            onRetry()
            player.prepare()
            player.play()
        }
        player.playbackState == Player.STATE_ENDED -> {
            player.seekToDefaultPosition()
            player.play()
        }
        player.playWhenReady -> player.pause()
        else -> player.play()
    }
}
