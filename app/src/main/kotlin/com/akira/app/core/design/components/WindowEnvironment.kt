package com.akira.app.core.design.components

import androidx.compose.runtime.compositionLocalOf

/** True while Android presents this activity in split screen or a freeform window. */
val LocalMultiWindowMode = compositionLocalOf { false }
