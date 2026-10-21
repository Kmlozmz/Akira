package com.akira.app.feature.settings

data class EqPresetDef(val name: String, val gains: List<Float>, val bass: Float, val space: Float)

// Names are persisted identifiers: translating labels must not rename these values.
val EqPresets = listOf(
    EqPresetDef("Equilibrado", listOf(0f, 0f, 0f, 0f, 0f), 0f, 0f),
    EqPresetDef("Graves", listOf(6f, 3f, 0f, -1f, -2f), .8f, .4f),
    EqPresetDef("Voces", listOf(-2f, 1f, 5f, 4f, 2f), .2f, .3f),
    EqPresetDef("Dinámico", listOf(5f, 2f, -2f, 3f, 6f), .6f, .6f),
    EqPresetDef("Rock", listOf(4f, 2f, -1f, 2f, 4f), .5f, .3f),
    EqPresetDef("Electrónica", listOf(5f, 3f, 0f, 2f, 4f), .7f, .5f),
)
