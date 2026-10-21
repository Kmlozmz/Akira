package com.akira.app.core.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.akira.app.R
import com.akira.app.core.datastore.UserPreferences
import com.akira.app.core.datastore.UserPreferencesRepository
import com.akira.app.core.i18n.AkiraUiText
import com.akira.app.core.i18n.StringsEnglish
import com.akira.app.core.i18n.StringsSpanish
import com.akira.app.core.media.MediaItemMapper.toMediaItem
import com.akira.app.core.media.MusicLibraryService
import com.akira.app.core.model.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject

@UnstableApi
open class AkiraWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { AkiraWidgets.refresh(context) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        AkiraWidgets.refresh(context)
    }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action !in AkiraWidgets.transportActions) return
        val pending = goAsync()
        // onReceive gets ReceiverRestrictedContext, which cannot bind a MediaController service.
        val appContext = context.applicationContext
        val future = try {
            MediaController.Builder(appContext,
                SessionToken(appContext, ComponentName(appContext, MusicLibraryService::class.java))).buildAsync()
        } catch (error: Exception) {
            com.akira.app.core.logging.AkiraLog.e("Widget", "Connection failed: ${error.javaClass.simpleName}", error)
            pending.finish()
            return
        }
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)
        fun finish() { if (finished.compareAndSet(false, true)) { MediaController.releaseFuture(future); pending.finish() } }
        var commandJob: Job? = null
        future.addListener({
            if (finished.get()) return@addListener
            commandJob = CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            try {
                val player = future.get()
                val onlineRepository = com.akira.app.core.online.OnlineRepository.get(appContext)
                onlineRepository.awaitSessionInitialization()
                val online = onlineRepository.enabled.value
                fun matchesSource(song: Song) = song.contentUri.startsWith("akira-youtube:") == online
                val activeUri = player.currentMediaItem?.requestMetadata?.mediaUri
                if (player.mediaItemCount > 0 && (activeUri?.scheme == "akira-youtube") != online) {
                    player.pause(); player.stop(); player.clearMediaItems()
                }
                if (player.mediaItemCount == 0) {
                    val saved = withContext(Dispatchers.IO) {
                        WidgetPlaybackStore.read(appContext)?.takeIf { it.songs.all(::matchesSource) }
                            ?: com.akira.app.core.online.SourceQueueStore.read(appContext, online)
                    }
                    if (onlineRepository.enabled.value != online) return@launch
                    if (saved != null) {
                        player.setMediaItems(saved.songs.map { it.toMediaItem(it.queueEntryId) }, saved.safeIndex, saved.safePosition)
                        player.shuffleModeEnabled = saved.shuffle
                        player.repeatMode = saved.repeat
                        val restored = player.sendCustomCommand(androidx.media3.session.SessionCommand(
                            com.akira.app.core.media.QueueEditCommand.ACTION, Bundle.EMPTY), Bundle().apply {
                            putString(com.akira.app.core.media.QueueEditCommand.OP, "ORDER")
                            putStringArrayList(com.akira.app.core.media.QueueEditCommand.ORDER, ArrayList(saved.order))
                        })
                        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
                            restored.addListener({
                                if (continuation.isActive) continuation.resumeWith(runCatching { restored.get(); Unit })
                            }, ContextCompat.getMainExecutor(appContext))
                            continuation.invokeOnCancellation { restored.cancel(false) }
                        }
                        player.prepare()
                    } else AkiraWidgets.currentSong(appContext)?.takeIf(::matchesSource)?.let { player.setMediaItem(it.toMediaItem()); player.prepare() }
                }
                when (action) {
                    AkiraWidgets.TOGGLE -> if (player.playWhenReady) player.pause() else player.play()
                    AkiraWidgets.NEXT -> if (player.hasNextMediaItem()) player.seekToNextMediaItem()
                    AkiraWidgets.PREVIOUS -> if (player.currentPosition > 3000L) player.seekTo(0L)
                        else if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                com.akira.app.core.logging.AkiraLog.e("Widget", "Transport failed: ${error.javaClass.simpleName}", error)
            } finally {
                Handler(Looper.getMainLooper()).postDelayed({ finish() }, 250L)
            }
            }
        }, ContextCompat.getMainExecutor(context))
        // A failed/hung connection must not leave the broadcast running indefinitely.
        Handler(Looper.getMainLooper()).postDelayed({ commandJob?.cancel(); finish() }, 8000L)
    }
}

class FullArtworkWidget : AkiraWidgetProvider()
class SmallMusicWidget : AkiraWidgetProvider()
class MediumMusicWidget : AkiraWidgetProvider()

/** Event driven: no position timer or periodic widget update. Last song survives process death, always paused. */
@UnstableApi
object AkiraWidgets {
    const val EXTRA_VARIANT = "com.akira.app.widget.VARIANT"
    const val EXTRA_OPEN_PLAYER = "com.akira.app.widget.OPEN_PLAYER"
    const val TOGGLE = "com.akira.app.widget.TOGGLE"
    const val NEXT = "com.akira.app.widget.NEXT"
    const val PREVIOUS = "com.akira.app.widget.PREVIOUS"
    val transportActions = setOf(TOGGLE, NEXT, PREVIOUS)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var renderJob: Job? = null
    private var latestSong: Song? = null
    private var loaded = false
    private var playing = false
    private var nextAvailable = false
    private var prefs = UserPreferences()
    private var lastSignature: List<Any?>? = null
    private val providers = listOf(FullArtworkWidget::class.java, SmallMusicWidget::class.java, MediumMusicWidget::class.java)

    fun currentSong(context: Context): Song? {
        if (!loaded) {
            loaded = true
            val store = context.getSharedPreferences("akira_widget", Context.MODE_PRIVATE)
            val count = store.getInt("queue_count", 0)
            nextAvailable = count > 0 && (store.getInt("queue_index", 0) < count - 1 || store.getInt("queue_repeat", 0) == 2)
            latestSong = runCatching {
                val raw = context.getSharedPreferences("akira_widget", Context.MODE_PRIVATE).getString("song", null) ?: return@runCatching null
                val json = JSONObject(raw)
                WidgetPlaybackStore.decodeSong(json)
            }.getOrNull()
        }
        return latestSong
    }

    fun publish(context: Context, song: Song?, isPlaying: Boolean, preferences: UserPreferences, hasNext: Boolean = nextAvailable) {
        loaded = true
        latestSong = song
        playing = isPlaying
        nextAvailable = hasNext
        prefs = preferences
        com.akira.app.core.datastore.AppUiSnapshot.write(context, preferences)
        val signature = listOf(song?.queueEntryId, song?.title, song?.albumArtUri, isPlaying,
            preferences.language, preferences.dynamicColor, preferences.appearancePreset, hasNext)
        if (signature == lastSignature) return
        lastSignature = signature
        val record = song?.let { WidgetPlaybackStore.encodeSong(it).toString() }
        context.getSharedPreferences("akira_widget", Context.MODE_PRIVATE).edit().putString("song", record).apply()
        render(context.applicationContext)
    }

    fun refresh(context: Context) {
        currentSong(context)
        scope.launch {
            prefs = UserPreferencesRepository(context.applicationContext).userPreferences.first()
            render(context.applicationContext)
        }
    }

    fun refreshPreferences(context: Context, preferences: UserPreferences) {
        val song = currentSong(context)
        publish(context, song, playing, preferences)
    }

    private fun render(context: Context) {
        renderJob?.cancel()
        renderJob = scope.launch {
            val manager = AppWidgetManager.getInstance(context)
            val entries = providers.flatMap { type -> manager.getAppWidgetIds(ComponentName(context, type)).map { it to type } }
            if (entries.isEmpty()) return@launch
            val song = latestSong
            val snapshotPrefs = prefs
            val isPlaying = playing
            val canGoNext = nextAvailable
            val artwork = withContext(Dispatchers.IO) { WidgetArtwork.load(context, song) }
            val strings = if (snapshotPrefs.language == "es" || (snapshotPrefs.language == "SYSTEM" && java.util.Locale.getDefault().language == "es"))
                StringsSpanish else StringsEnglish
            for ((id, type) in entries) {
                ensureActive()
                val full = type == FullArtworkWidget::class.java
                val medium = type == MediumMusicWidget::class.java
                val options = manager.getAppWidgetOptions(id)
                val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, if (medium) 300 else 150).coerceAtLeast(if (medium) 240 else 120)
                val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 150).coerceIn(100, 260)
                context.getSharedPreferences("akira_widget", Context.MODE_PRIVATE).edit()
                    .putInt("background_color", WidgetArtwork.backgroundColor(artwork, snapshotPrefs)).apply()
                val views = RemoteViews(context.packageName, if (full) R.layout.widget_full_artwork else if (medium) R.layout.widget_medium else R.layout.widget_small)
                views.setImageViewBitmap(R.id.widget_background, WidgetArtwork.background(width, height, artwork, snapshotPrefs, full))
                if (android.os.Build.VERSION.SDK_INT >= 31) views.setColorStateList(android.R.id.background,
                    "setBackgroundTintList", android.content.res.ColorStateList.valueOf(WidgetArtwork.backgroundColor(artwork, snapshotPrefs)))
                views.setTextViewText(R.id.widget_title, song?.title ?: strings.ui(AkiraUiText.WidgetEmptyTitle))
                views.setTextViewText(R.id.widget_artist, song?.artist ?: strings.ui(AkiraUiText.WidgetEmptyDetail))
                views.setContentDescription(android.R.id.background, song?.let { "${it.title}, ${it.artist}" } ?: strings.ui(AkiraUiText.WidgetEmptyTitle))
                val variant = if (full) "full" else if (medium) "medium" else "small"
                // Explicit activity only. Mutable so RemoteViews can supply the actual screen bounds.
                val open = PendingIntent.getActivity(context, 1000 + id,
                    Intent(context, WidgetLaunchActivity::class.java).putExtra(EXTRA_OPEN_PLAYER, true).putExtra(EXTRA_VARIANT, variant)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        // A widget launch owns a short-lived task. Clearing the previous widget task avoids
                        // the launcher first moving an old full-screen surface and only then expanding it.
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                    // Each placed widget has its own token, including copies of the same size.
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE)
                views.setOnClickPendingIntent(android.R.id.background, open)
                if (!full) {
                    views.setImageViewBitmap(R.id.widget_cover, WidgetArtwork.cover(artwork.bitmap, if (medium) 12f else 20f))
                    views.setContentDescription(R.id.widget_cover, strings.appName)
                    views.setImageViewBitmap(R.id.widget_brand, WidgetArtwork.brand(context))
                    views.setContentDescription(R.id.widget_brand, strings.appName)
                }
                if (medium) {
                    views.setViewVisibility(R.id.widget_controls, if (song == null) View.GONE else View.VISIBLE)
                    listOf(R.id.widget_previous to PREVIOUS, R.id.widget_play to TOGGLE, R.id.widget_next to NEXT).forEach { (viewId, action) ->
                        val command = PendingIntent.getBroadcast(context, viewId, Intent(context, MediumMusicWidget::class.java).setAction(action),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        views.setOnClickPendingIntent(viewId, command)
                    }
                    views.setImageViewBitmap(R.id.widget_previous, WidgetArtwork.icon(com.akira.app.core.design.icon.AkiraIcons.FastRewind))
                    views.setImageViewBitmap(R.id.widget_play, WidgetArtwork.icon(if (isPlaying) com.akira.app.core.design.icon.AkiraIcons.Pause else com.akira.app.core.design.icon.AkiraIcons.Play))
                    views.setImageViewBitmap(R.id.widget_next, WidgetArtwork.icon(com.akira.app.core.design.icon.AkiraIcons.FastForward))
                    views.setContentDescription(R.id.widget_previous, strings.previousTrack)
                    views.setContentDescription(R.id.widget_play, if (isPlaying) strings.pause else strings.play)
                    views.setContentDescription(R.id.widget_next, strings.nextTrack)
                    views.setBoolean(R.id.widget_next, "setEnabled", canGoNext)
                    views.setFloat(R.id.widget_next, "setAlpha", if (canGoNext) 1f else .35f)
                }
                manager.updateAppWidget(id, views)
                // Capture once per widget update, never during an animation frame.
                runCatching {
                    val snapshot = WidgetSnapshotStore.capture(context, views, width, height)
                    try { withContext(Dispatchers.IO) { WidgetSnapshotStore.write(context, id, song?.id, snapshot) } }
                    finally { snapshot.recycle() }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    com.akira.app.core.logging.AkiraLog.w("Widget", "Launch snapshot unavailable", error)
                }
            }
        }
    }
}
