package com.akira.app.core.media

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@androidx.annotation.OptIn(UnstableApi::class)
class ShufflePlaylistDeviceTest {
    @Test
    fun startingAndEnablingShuffleKeepsTheWholePlaylistAfterTheSelectedItem() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val exo = ExoPlayer.Builder(instrumentation.targetContext).build()
            try {
                val player = QueueTransactionPlayer(exo)
                val items = (0..4).map { index ->
                    MediaItem.Builder().setMediaId(index.toString()).setUri("content://media/audio/$index").build()
                }
                player.setMediaItems(items, 3, 0)
                player.shuffleModeEnabled = true
                assertEquals(3, playbackQueueIndexes(exo).first())

                val selected = items[4].buildUpon().setRequestMetadata(MediaItem.RequestMetadata.Builder()
                    .setExtras(Bundle().apply { putBoolean(QueueEditCommand.INITIAL_SHUFFLE, true) }).build()).build()
                player.setMediaItems(items.dropLast(1) + selected, 4, 0)
                assertEquals(4, exo.currentMediaItemIndex)
                assertEquals(4, playbackQueueIndexes(exo).first())
                assertEquals((0..4).toList(), playbackQueueIndexes(exo).sorted())
                assertTrue(exo.hasNextMediaItem())
            } finally {
                exo.release()
            }
        }
    }
}
