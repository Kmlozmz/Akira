package com.akira.app.feature.setup.components

import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star

/**
 * High-definition M3 Expressive shapes matching Google's Shape Morph 3 spec.
 * Each shape is distinct, balanced, and normalized.
 */
object M3MorphShapes {

    // 1. 10-Point Scallop / Starburst
    val tenPointScallop: RoundedPolygon by lazy {
        RoundedPolygon.star(
            numVerticesPerRadius = 10,
            radius = 1.0f,
            innerRadius = 0.78f,
            rounding = CornerRounding(radius = 0.22f, smoothing = 0.5f),
            innerRounding = CornerRounding(radius = 0.22f, smoothing = 0.5f)
        )
    }

    // 2. Soft Pentagon (Clean 5-sided polygon with rounded vertices)
    val softPentagon: RoundedPolygon by lazy {
        RoundedPolygon(
            numVertices = 5,
            radius = 1.0f,
            rounding = CornerRounding(radius = 0.35f, smoothing = 0.6f)
        )
    }

    // 3. 8-Lobe Flower (Scallop)
    val eightLobeFlower: RoundedPolygon by lazy {
        RoundedPolygon.star(
            numVerticesPerRadius = 8,
            radius = 1.0f,
            innerRadius = 0.70f,
            rounding = CornerRounding(radius = 0.32f, smoothing = 0.6f),
            innerRounding = CornerRounding(radius = 0.18f, smoothing = 0.5f)
        )
    }

    // 4. 4-Corner Clover / Pebble
    val fourCornerClover: RoundedPolygon by lazy {
        RoundedPolygon.star(
            numVerticesPerRadius = 4,
            radius = 1.0f,
            innerRadius = 0.65f,
            rounding = CornerRounding(radius = 0.42f, smoothing = 0.7f),
            innerRounding = CornerRounding(radius = 0.25f, smoothing = 0.5f)
        )
    }

    // 5. Smooth Squircle (Super-ellipse)
    val smoothSquircle: RoundedPolygon by lazy {
        RoundedPolygon(
            numVertices = 4,
            radius = 1.0f,
            rounding = CornerRounding(radius = 0.55f, smoothing = 0.9f)
        )
    }

    val shapeSequence: List<RoundedPolygon> by lazy {
        listOf(
            tenPointScallop,
            softPentagon,
            eightLobeFlower,
            fourCornerClover,
            smoothSquircle
        )
    }
}
