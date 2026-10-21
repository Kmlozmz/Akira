package com.akira.app.core.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.AtomicFile
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import java.io.File

/** The transition starts from the same layout, fonts, colors and controls sent to the launcher. */
internal object WidgetSnapshotStore {
    fun capture(context: Context, views: RemoteViews, widthDp: Int, heightDp: Int): Bitmap {
        val scale = context.resources.displayMetrics.density
        val width = (widthDp * scale).toInt().coerceAtLeast(1)
        val height = (heightDp * scale).toInt().coerceAtLeast(1)
        val root = views.apply(context, FrameLayout(context))
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
    }

    fun write(context: Context, id: Int, songId: String?, bitmap: Bitmap) {
        val file = AtomicFile(File(context.cacheDir, "widget-motion-$id.png"))
        val output = file.startWrite()
        try {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            file.finishWrite(output)
            context.getSharedPreferences("akira_widget", Context.MODE_PRIVATE).edit()
                .putString("snapshot_song_$id", songId).apply()
        } catch (error: Exception) { file.failWrite(output); throw error }
    }

    fun read(context: Context, id: Int, songId: String?): Bitmap? = runCatching {
        if (id < 0 || songId == null || context.getSharedPreferences("akira_widget", Context.MODE_PRIVATE)
                .getString("snapshot_song_$id", null) != songId) return@runCatching null
        AtomicFile(File(context.cacheDir, "widget-motion-$id.png")).openRead().use(BitmapFactory::decodeStream)
    }.getOrNull()
}
