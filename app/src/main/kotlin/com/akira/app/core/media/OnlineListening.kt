package com.akira.app.core.media

import androidx.media3.common.Player
import com.akira.app.core.media.MediaItemMapper.onlineQueueGeneration
import com.akira.app.core.media.MediaItemMapper.toSong
import com.akira.app.core.model.Song
import com.akira.app.core.online.SourceQueueStore

/** Also called on deck handover, where isPlaying need not change. Old decks
 * awaiting logout cleanup must not add their tracks to the guest history. */
internal fun recordActiveOnlineListening(player: Player, record: (Song) -> Unit) {
    if (!player.isPlaying) return
    val item = player.currentMediaItem ?: return
    if (item.onlineQueueGeneration() != SourceQueueStore.onlineGeneration) return
    val song = item.toSong()
    if (song.contentUri.startsWith("akira-youtube:")) record(song)
}
