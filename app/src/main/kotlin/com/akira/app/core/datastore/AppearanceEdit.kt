package com.akira.app.core.datastore

data class AppearanceEdit(
    val revision: Long,
    val theme: String,
    val accentArgb: Int,
    val glass: Boolean,
    val font: String,
    val fontName: String,
    val sizeIndex: Int,
    val reduceTransparency: Boolean,
    val fullscreenArtwork: Boolean,
    val animatedArtwork: Boolean,
    val opacity: Float,
    val diffusion: Float,
    val dockDiffusion: Boolean,
    val fontId: String?
)
