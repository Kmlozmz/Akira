package com.akira.app.core.media

object TransitionMode {
    fun durationFor(mode: String, rememberedSeconds: Int): Int =
        if (mode == "CROSSFADE") rememberedSeconds.coerceIn(1, 30) else 0
}
