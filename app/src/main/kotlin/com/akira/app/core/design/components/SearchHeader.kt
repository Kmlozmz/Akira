// SPDX-License-Identifier: GPL-3.0-only
// Page bb28993 SearchDesign.kt: original title/field/avatar geometry and motion.
// Akira adapter: semantic colors and parent-owned status inset; plain header foundation.
package com.akira.app.core.design.components

import com.akira.app.core.design.motion.*
import com.akira.app.core.design.navigation.*
import com.akira.app.core.design.rendering.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.material3.*
private val SearchHorizontalPadding=24.dp
private val SearchFieldHeight=48.dp
private val AvatarSize=36.dp
private val SearchTitleBlockHeight=62.dp
private val SearchTopClearance=18.dp
@Composable
fun SearchGlassTopBar(
    progressProvider: () -> Float,
    title: String?,

    blurActive: Boolean = false,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,

    /** Small line under the title, e.g. "All songs on your library listed here." */
    subtitle: String? = null,

    /**
     * Overrides what renders inside the title block (e.g. a logo mark) while keeping the same
     * collapse fade/scale/translate/blur the plain text title gets. [title] must still be
     * non-null to reserve the block's height; [subtitle] is ignored when this is set.
     */
    titleContent: (@Composable () -> Unit)? = null,

    /**
     * Height of the title block. Defaults to the Search tab's title-only height; the Library
     * screens pass a taller value because their block carries a subtitle line as well. This is the
     * distance the block travels as it collapses, so it has to match what is actually rendered or
     * the search field lands in the wrong place at rest.
     */
    titleBlockHeight: Dp = SearchTitleBlockHeight,

    below: (@Composable () -> Unit)? = null,
    field: @Composable () -> Unit,
) {
    val statusBar = 0.dp
    val pageColor = MaterialTheme.colorScheme.background
    val density = LocalDensity.current
    val titleTravelPx = with(density) { titleBlockHeight.toPx() }

    val avatarReservePx = with(density) { (AvatarSize + 12.dp).toPx() }
    val avatarDropPx = with(density) { (SearchFieldHeight / 2 - titleBlockHeight / 2).toPx() }


    Box(modifier = modifier.fillMaxWidth()) {

        val showGlass by remember { derivedStateOf { progressProvider() > 0.01f } }
        if (showGlass) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithCache {
                        onDrawBehind {
                            val p = progressProvider().coerceIn(0f, 1f)

                            val boost = if (blurActive) 1f else 1.12f
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to pageColor.copy(alpha = (p * 0.95f * boost).coerceAtMost(1f)),
                                    0.45f to pageColor.copy(alpha = (p * 0.72f * boost).coerceAtMost(1f)),
                                    0.78f to pageColor.copy(alpha = (p * 0.34f * boost).coerceAtMost(1f)),
                                    1f to Color.Transparent,
                                )
                            )
                        }
                    }
            )
        }

        Column(modifier = Modifier.wrapContentHeight(align = Alignment.Top, unbounded = true).padding(top = statusBar + SearchTopClearance)) {
            if (title != null) {

                Box(
                    modifier = Modifier
                        .height(titleBlockHeight)
                        .fillMaxWidth()
                        .padding(horizontal = SearchHorizontalPadding)
                        .graphicsLayer {
                            val p = progressProvider().coerceIn(0f, 1f)
                            alpha = (1f - p * 1.6f).coerceIn(0f, 1f)
                            translationY = -p * titleTravelPx
                            val scale = 1f - p * 0.18f
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0.5f)

                        },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (titleContent != null) {
                        titleContent()
                    } else {
                        Column(verticalArrangement = Arrangement.Center) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 34.sp,
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                            )
                            if (subtitle != null) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        if (title != null) {
                            translationY = -progressProvider().coerceIn(0f, 1f) * titleTravelPx
                        }
                    },
            ) {
              Box(modifier = Modifier.fillMaxWidth().padding(horizontal = SearchHorizontalPadding)) {
                Box(
                    modifier = if (trailing == null) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier.layout { measurable, constraints ->

                            val reserve = if (title == null) {
                                avatarReservePx
                            } else {
                                avatarReservePx * progressProvider().coerceIn(0f, 1f)
                            }
                            val width = (constraints.maxWidth - reserve.toInt()).coerceAtLeast(0)
                            val placeable = measurable.measure(
                                constraints.copy(minWidth = width, maxWidth = width)
                            )
                            layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
                        }
                    },
                ) {
                    field()
                }
              }
              if (below != null) {
                Spacer(modifier = Modifier.height(16.dp))
                below()
              }
            }
        }

        if (trailing != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = statusBar + SearchTopClearance, end = SearchHorizontalPadding)
                    // As tall as this page's title block (taller on Library pages with a subtitle),
                    // so the drop below lands its centre on the search field's centre.
                    .height(if (title == null) SearchFieldHeight else titleBlockHeight)
                    .graphicsLayer {
                        if (title != null) {
                            translationY = progressProvider().coerceIn(0f, 1f) * avatarDropPx
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                trailing()
            }
        }
    }
}
