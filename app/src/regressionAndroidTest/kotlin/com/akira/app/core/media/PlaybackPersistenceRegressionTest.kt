package com.akira.app.core.media

import android.content.Context
import android.content.ContextWrapper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.test.platform.app.InstrumentationRegistry
import com.akira.app.core.media.MediaItemMapper.onlineQueueGeneration
import com.akira.app.core.media.MediaItemMapper.toMediaItem
import com.akira.app.core.media.MediaItemMapper.toSong
import com.akira.app.core.model.Song
import com.akira.app.core.online.SourceQueueStore
import com.akira.app.core.widget.WidgetPlaybackStore
import com.akira.app.core.widget.WidgetQueueSnapshot
import java.io.File
import java.util.UUID
import java.lang.reflect.Proxy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Android Bundle/Uri/AtomicFile must run on Android, not stubbed android.jar. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackPersistenceRegressionTest {
    private fun song() = Song("123", "Track", "Artist", "Album", 180_000, "content://media/external/audio/media/123",
        albumId = 47, mimeType = "audio/flac", trackNumber = 6, dateAddedSec = 100,
        dateModifiedSec = 200, hasLyrics = true, isFavorite = true, path = "/Music/track.flac",
        format = "FLAC", bitrate = "1000", year = 2026, genre = "Rock", queueEntryId = "occurrence")

    private fun isolatedContext(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val namespace = "regression_${UUID.randomUUID()}"
        return object : ContextWrapper(base) {
            private val directory = File(base.cacheDir, namespace).apply { mkdirs() }
            override fun getNoBackupFilesDir(): File = directory
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("${namespace}_$name", mode)
        }
    }

    @Test fun mediaSessionBundleAndQueueJsonPreserveSongMetadata() {
        val original = song()
        assertEquals(original, original.toMediaItem(original.queueEntryId).toSong())
        val transported = MediaItem.fromBundle(original.toMediaItem(original.queueEntryId).toBundle())
        assertEquals(original, transported.toSong())
        assertEquals(original, WidgetPlaybackStore.decodeSong(WidgetPlaybackStore.encodeSong(original)))
    }

    @Test fun legacySnapshotsKeepTheirDefaults() {
        val json = JSONObject().put("id", "old").put("title", "Old").put("artist", "Artist").put("uri", "content://old")
        val restored = WidgetPlaybackStore.decodeSong(json)
        assertEquals(0L, restored.albumId)
        assertEquals("audio/*", restored.mimeType)
        assertEquals("old", restored.queueEntryId)
    }

    @Test fun logoutRejectsPendingQueueWritesAndOldDeckRecaptures() {
        val context = isolatedContext()
        val online = song().copy(id = "youtube:abcdefghijk", contentUri = "akira-youtube://abcdefghijk")
        val oldItem = online.toMediaItem("old")
        val pending = WidgetQueueSnapshot(listOf(online), 0, 0, false, 0,
            onlineGeneration = oldItem.onlineQueueGeneration())
        SourceQueueStore.write(context, pending)
        assertNotNull(SourceQueueStore.read(context, true))
        SourceQueueStore.clearOnline(context)
        var widgetWriteExecuted = false
        SourceQueueStore.writeIfCurrent(pending) { widgetWriteExecuted = true }
        SourceQueueStore.write(context, pending)
        assertFalse(widgetWriteExecuted)
        assertNull(SourceQueueStore.read(context, true))
        val recaptured = pending.copy(onlineGeneration = oldItem.onlineQueueGeneration())
        SourceQueueStore.write(context, recaptured)
        assertNull(SourceQueueStore.read(context, true))
        val newItem = online.toMediaItem("new")
        SourceQueueStore.write(context, pending.copy(onlineGeneration = newItem.onlineQueueGeneration()))
        assertNotNull(SourceQueueStore.read(context, true))
    }

    @Test fun logoutPreservesOfflineQueueAndEmptyDeletionRemovesItsBookmark() {
        val context = isolatedContext()
        val snapshot = WidgetQueueSnapshot(listOf(song()), 0, 123, true, 0)
        SourceQueueStore.write(context, snapshot)
        SourceQueueStore.clearOnline(context)
        assertEquals(song(), SourceQueueStore.read(context, false)?.songs?.single())
        SourceQueueStore.write(context, snapshot.copy(songs = emptyList(), order = emptyList()))
        assertNull(SourceQueueStore.read(context, false))
    }

    @Test fun continuouslyPlayingIncomingDeckRecordsAtHandoverAndStaleDeckIsIgnoredAfterLogout() {
        val context = isolatedContext()
        val online = song().copy(id = "youtube:abcdefghijk", contentUri = "akira-youtube://abcdefghijk")
        val item = online.toMediaItem("incoming")
        var playing = true
        // No Player.Listener event is generated: handover itself must record it.
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            when (method.name) {
                "isPlaying" -> playing
                "getCurrentMediaItem" -> item
                else -> null
            }
        } as Player
        val recorded = mutableListOf<String>()
        recordActiveOnlineListening(player) { recorded.add(it.id) }
        assertEquals(listOf(online.id), recorded)
        playing = false
        recordActiveOnlineListening(player) { recorded.add(it.id) }
        assertEquals(1, recorded.size)
        playing = true
        SourceQueueStore.clearOnline(context)
        recordActiveOnlineListening(player) { recorded.add(it.id) }
        assertEquals("Logout cleanup may still have an old playing deck", 1, recorded.size)
    }
}
