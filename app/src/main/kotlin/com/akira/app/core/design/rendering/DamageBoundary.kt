// SPDX-License-Identifier: GPL-3.0-only
// Adapted from realidkroo/page bb28993. See THIRD_PARTY_NOTICES.md.
package com.akira.app.core.design.rendering

import com.akira.app.core.design.components.*
import com.akira.app.core.design.motion.*
import com.akira.app.core.design.navigation.*
import android.graphics.RenderNode
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.nativeCanvas
@RequiresApi(Build.VERSION_CODES.Q)
private fun damageNode(width: Int, height: Int): Any =
    RenderNode("DamageBoundary").apply {
        setPosition(0, 0, width, height)
        // the whole point: the renderer only bounds a change by a node's size when the node clips
        // to it. Compose's own layers clip by outline instead, so to the renderer a change in any
        // of them is a change to the entire screen
        setClipToBounds(true)
    }

@RequiresApi(Build.VERSION_CODES.Q)
private fun damageNodeBeginRecording(node: Any): android.graphics.Canvas = (node as RenderNode).beginRecording()

@RequiresApi(Build.VERSION_CODES.Q)
private fun damageNodeEndRecording(node: Any) {
    (node as RenderNode).endRecording()
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun android.graphics.Canvas.drawDamageNode(node: Any) {
    drawRenderNode(node as RenderNode)
}

/**
 * Keeps whatever animates inside this from counting as a change to the rest of the screen.
 *
 * To the renderer, a change inside a Compose layer is a change to everything (Compose's layers clip
 * by outline, and the renderer only bounds a change by a node's size when it clips to bounds).
 * So a few bouncing equaliser bars, the hero's video or a marquee repainted the whole window on
 * every one of their frames, and made every glass surface that samples the app (the nav bar, the
 * mini player) render and blur its backdrop again although nothing under it had moved. Recorded
 * into a node that clips to its own bounds, a change in here only dirties this rectangle.
 *
 * Content is clipped to the bounds, so only use it where nothing draws outside them.
 */
fun Modifier.damageBoundary(): Modifier =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        this
    } else {
        this.drawWithCache {
            val width = size.width.toInt()
            val height = size.height.toInt()
            val node = if (width > 0 && height > 0) damageNode(width, height) else null
            onDrawWithContent {
                val target = drawContext.canvas
                if (node == null || !target.nativeCanvas.isHardwareAccelerated) {
                    drawContent()
                    return@onDrawWithContent
                }
                drawContext.canvas = Canvas(damageNodeBeginRecording(node))
                try {
                    drawContent()
                } finally {
                    drawContext.canvas = target
                    damageNodeEndRecording(node)
                }
                target.nativeCanvas.drawDamageNode(node)
            }
        }
    }
